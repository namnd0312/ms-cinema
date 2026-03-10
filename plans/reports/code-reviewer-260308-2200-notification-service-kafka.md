# Code Review — Notification Service Kafka Implementation

**Date:** 2026-03-08
**Plan:** `plans/260308-1603-notification-service-kafka/`
**Scope:** Phases 1–4 (kafka-events module, notification-service, auth-service refactor, infra)

---

## Scope

- Files reviewed: 16 files across 6 modules
- Lines of code analyzed: ~450 LOC (new/modified)
- Review focus: new notification-service, auth-service Kafka refactor, infra integration

---

## Overall Assessment

Implementation is **solid and follows existing patterns** faithfully. The EventEnvelope + DLT + exponential backoff approach mirrors booking-service/payment-service exactly. One **critical serialization bug** exists in auth-service's `EmailServiceImpl` that will cause runtime deserialization failure in notification-service. Several medium/low issues noted below.

---

## Critical Issues

### 1. Serialization Contract Mismatch — auth-service vs notification-service

**File:** `auth-service/src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java`

`EmailServiceImpl` uses `KafkaTemplate<String, String>` and manually serializes `EventEnvelope` to JSON string via `ObjectMapper.writeValueAsString()`, then sends it as a raw string.

```java
private final KafkaTemplate<String, String> kafkaTemplate;
// ...
String json = objectMapper.writeValueAsString(envelope);
kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS, event.recipientEmail(), json);
```

`auth-service/application.yml` confirms:
```yaml
producer:
  key-serializer: org.apache.kafka.common.serialization.StringSerializer
  value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

But `notification-service/application.yml` expects:
```yaml
consumer:
  properties:
    spring.json.value.default.type: com.namnd.kafka.events.envelope.EventEnvelope
```

This means the consumer's `JsonDeserializer` receives a **JSON string** (a quoted, escaped string literal), not a JSON object. It will fail to deserialize into `EventEnvelope`, trigger retries, then land in DLT — **emails will never be delivered**.

**Contrast with payment-service (correct pattern):**
```java
private final KafkaTemplate<String, Object> kafkaTemplate;
// sends EventEnvelope directly — JsonSerializer handles serialization
kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, key, envelope);
```
And `payment-service` + `booking-service` use `JsonSerializer` implicitly (Spring Boot auto-configures it when value type is `Object`).

**Fix required in auth-service:**

1. Change `KafkaTemplate<String, String>` to `KafkaTemplate<String, Object>`
2. Remove manual `objectMapper.writeValueAsString()` call
3. Send the `EventEnvelope` object directly
4. Remove explicit `key-serializer`/`value-serializer` from `application.yml` (let Spring Boot auto-configure `JsonSerializer`, same as payment-service) — OR add:
   ```yaml
   producer:
     value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
   ```

---

## High Priority Findings

### 2. `from` address not set on `SimpleMailMessage`

**File:** `notification-service/src/main/java/com/namnd/notification/service/EmailSenderService.java`

`message.setFrom()` is never called. Most SMTP servers require a `From` header. Without it, delivery depends on `spring.mail.username` being automatically used as sender — this is Gmail-specific and not guaranteed. Explicit `setFrom()` is the standard pattern.

**Fix:**
```java
@Value("${spring.mail.username}")
private String fromAddress;

message.setFrom(fromAddress);
```

### 3. Mail config duplicated between `application.yml` and config-server

`notification-service/src/main/resources/application.yml` contains the full SMTP config block. `config-server/src/main/resources/config-repo/notification-service.yml` also has the full SMTP block. On startup, the config-server version will override the local one (correct behavior), but the duplication is confusing and risks drift. The local `application.yml` should retain only bootstrap/fallback config; SMTP credentials block belongs only in config-server.

**Fix:** Remove the `spring.mail.*` block from `notification-service/src/main/resources/application.yml`, keeping only the `config.import` line as the source of truth.

---

## Medium Priority Improvements

### 4. `notification-service` has no `@EnableDiscoveryClient` annotation

`NotificationServiceApplication.java` is bare `@SpringBootApplication`. Other services (auth, booking, payment) rely on Spring Boot's auto-configuration of Eureka client, which works — but `notification-service` also omits `@EnableScheduling`, `@EnableFeignClients`, etc. Not a bug (auto-config handles Eureka), just worth noting it's consistent with other services. No action required.

### 5. DLT KafkaTemplate type mismatch in notification-service

**File:** `notification-service/src/main/java/com/namnd/notification/config/KafkaConsumerConfig.java`

```java
public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate)
```

The `DeadLetterPublishingRecoverer` is given `KafkaTemplate<String, Object>`, but notification-service has no explicit producer config (no `KafkaTemplate<String, Object>` bean explicitly defined). Spring Boot will auto-create a `KafkaTemplate<Object, Object>` from the auto-configured producer factory. The `<String, Object>` generic is type-erased at runtime so injection will succeed, but the DLT messages written to `notification-events-dlt` could use the wrong serializer (String by default from auto-config). This matches the same pattern as booking-service and payment-service, so it won't regress — but it's a latent inconsistency that could cause DLT deserialization issues in a future Kafka UI/consumer.

This is identical in booking-service and payment-service so acceptable as-is.

### 6. `sendEmail` has no null/empty guard on subject or body

**File:** `EmailSenderService.java`

If `NotificationRequestedEvent.subject()` or `body()` is null (malformed event), `SimpleMailMessage.setSubject(null)` throws `IllegalArgumentException`, which will propagate and trigger DLT retry unnecessarily. A guard + warn log would prevent wasted retries on bad data.

```java
if (event.subject() == null || event.body() == null) {
    log.warn("Dropping malformed notification event: missing subject or body, channel={}", event.channel());
    return;
}
```

### 7. Fire-and-forget Kafka publish in `EmailServiceImpl` loses errors silently

**File:** `auth-service/src/main/java/com/namnd/springjwt/service/impl/EmailServiceImpl.java`

The `.whenComplete()` callback only logs; the calling service thread gets no feedback. If Kafka is down, the registration flow returns 200 OK to the client but no email is ever sent. This is an acceptable tradeoff for async decoupling — but the behavior should be documented. Consider adding a metric counter or alerting hook for production use.

### 8. `maskEmail` duplicated in two services

`maskEmail()` is copy-pasted identically in `EmailServiceImpl` (auth-service) and `EmailSenderService` (notification-service). By DRY principle it belongs in a shared utility in `kafka-events` module or a common library. Low impact now, but will diverge over time.

---

## Low Priority Suggestions

### 9. Dockerfile missing `HEALTHCHECK`

**File:** `notification-service/Dockerfile`

All other services lack it too, so not a regression. But a `HEALTHCHECK` would allow Docker and docker-compose `depends_on: condition: service_healthy` to work properly.

### 10. No `multiplier` set on `ExponentialBackOffWithMaxRetries`

Default multiplier is `2.0`. With `initialInterval=1000`, `maxInterval=10000`, and 3 retries: 1s, 2s, 4s. This is fine and matches booking-service/payment-service exactly.

### 11. `@KafkaListener` `groupId` hardcoded as string literal

```java
@KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS, groupId = "notification-service")
```

`groupId` is also set in `application.yml` under `consumer.group-id`. The annotation value overrides the config — these are consistent now but could diverge. Prefer referencing the config property:
```java
groupId = "${spring.kafka.consumer.group-id}"
```
This matches no other service currently, so not a regression — just a suggestion for consistency.

---

## Positive Observations

- `KafkaTopics.NOTIFICATION_EVENTS` constant used correctly in both producer and consumer — no raw strings
- Email masking implemented correctly and consistently in both services
- `@JsonIgnoreProperties(ignoreUnknown = true)` on `NotificationRequestedEvent` for forward compatibility
- DLT + exponential backoff pattern is identical to booking-service and payment-service
- Config-server integration is correct — notification-service registered, SMTP credentials externalized via env vars
- Prometheus scrape job added correctly at port 8085
- Docker compose `depends_on` for notification-service correctly lists eureka-server, config-server, and kafka
- `MAIL_USERNAME` and `MAIL_PASSWORD` correctly use env var substitution with empty defaults (no hardcoded credentials)
- `notification-service` module correctly added to root `pom.xml`
- `Dockerfile` uses `eclipse-temurin:21-jre-alpine` (matches other services, minimal image)

---

## Task Completeness Verification

From `plan.md`:

| Phase | Status (Plan) | Verified |
|-------|---------------|----------|
| 1 — Kafka Events Module | Done | Yes — `KafkaTopics.NOTIFICATION_EVENTS` + `NotificationRequestedEvent` exist |
| 2 — Notification Service | Done | Yes — all files present and functional |
| 3 — Auth-Service Refactor | Done | Yes — `EmailServiceImpl` refactored to Kafka producer |
| 4 — Docker & Config Server | Done | Yes — docker-compose, config-server.yml, prometheus.yml all updated |
| 5 — E2E Testing | **Pending** | Not started — all todos unchecked |

Phase 5 remains pending. Implementation is otherwise complete for Phases 1–4, with the critical serialization bug in Phase 3 needing a fix before Phase 5 testing can pass.

---

## Recommended Actions

1. **[Critical — fix before Phase 5]** Fix `EmailServiceImpl` serialization: change `KafkaTemplate<String, String>` to `KafkaTemplate<String, Object>`, remove manual JSON serialization, send `EventEnvelope` object directly, update producer serializer config in `auth-service/application.yml`.

2. **[High]** Add `message.setFrom(fromAddress)` to `EmailSenderService.sendEmail()` with `@Value("${spring.mail.username}")`.

3. **[Medium]** Remove duplicate SMTP config from `notification-service/src/main/resources/application.yml` — leave it only in config-server.

4. **[Medium]** Add null guard on `subject`/`body` in `EmailSenderService.sendEmail()` to avoid DLT churn on malformed events.

5. **[Low]** Extract `maskEmail()` to a shared utility to eliminate duplication.

6. **[Ongoing]** Complete Phase 5 E2E testing once item #1 is fixed.

---

## Updated Plan

`plan.md` — Phase 5 remains Pending. No status change warranted until fix #1 is applied.

---

## Metrics

- Type Coverage: N/A (Java, no static type coverage tool)
- Test Coverage: No unit tests for notification-service (Phase 5 is E2E only)
- Linting Issues: 0 compile-blocking issues (aside from the runtime bug in item #1)
- Critical: 1 | High: 1 | Medium: 3 | Low: 3

---

## Unresolved Questions

1. Is fire-and-forget acceptable for email delivery (no retry at the auth-service level if Kafka is down)? The current implementation has no fallback — caller gets 200 OK but email may be lost. Acceptable for MVP but should be documented.
2. Should `notification-service` eventually handle SMS/PUSH notifications? The `notificationType` field and routing logic are in place, but no SMS/push implementation exists. YAGNI applies — no action needed unless planned.
