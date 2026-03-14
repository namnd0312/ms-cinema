# Code Review: Real-Time Notification System (SSE + Kafka)

**Date:** 2026-03-14
**Reviewer:** code-reviewer subagent
**Plan:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260312-2203-real-time-notification-sse-kafka/`

---

## Scope

- **Files reviewed:** 28 files across 6 phases (kafka-events, notification-service, booking-service, api-gateway, Angular frontend, docker)
- **LOC analyzed:** ~800 (backend) + ~300 (frontend)
- **Review focus:** Security, thread safety, error handling, resource leaks, architectural consistency

---

## Overall Assessment

Solid implementation. Architecture is clean, SSE/Kafka integration is well-structured, and the unique-consumer-group-per-instance broadcast pattern is correctly applied. Three significant issues require attention before production: a credential leak in `application.yml`, a critical broadcast deduplication bug, and SSE token exposure in server logs. Several medium issues around error handling and race conditions are also noted.

---

## Critical Issues

### C1 — Hardcoded SMTP credentials in `application.yml` (SECURITY)

**File:** `notification-service/src/main/resources/application.yml` lines 41–42

```yaml
username: ${MAIL_USERNAME:nghiemducnam0312@gmail.com}
password: ${MAIL_PASSWORD:sdxm fmia vuzf bvmq}
```

Real Gmail address and app password are hardcoded as fallback defaults. If `MAIL_USERNAME`/`MAIL_PASSWORD` env vars are absent (e.g. local dev, CI), the real credentials are used silently. Worse, they are committed to git history.

**Fix:** Remove defaults entirely — fail fast if missing:
```yaml
username: ${MAIL_USERNAME}
password: ${MAIL_PASSWORD}
```
Then rotate the exposed app password immediately.

---

### C2 — Broadcast causes N×M duplicate DB writes (CORRECTNESS/PERFORMANCE)

**File:** `notification-service/.../listener/InAppNotificationEventListener.java` lines 59–73

`handleBroadcast()` uses `notificationRepository.findAll()` to discover known users. With unique consumer group per instance, **every notification-service instance** receives the broadcast message and calls `findAll()` independently — each saves one `Notification` row per known user. With `k` instances and `n` users, the result is `k × n` rows written instead of `n`.

This is a design conflict: unique consumer group is needed for SSE push, but causes duplicate persistence for broadcasts.

**Fix options (pick one):**
1. Use a shared consumer group for persistence and a unique group for SSE push (two separate `@KafkaListener` methods — one shared for saving, one unique-group for pushing).
2. Use Redis dedup on `(eventId, userId)` before saving — the `NotificationDeduplicationService` already exists.
3. Save broadcast notifications via a separate shared-group listener, push via in-memory signal.

Option 2 is least invasive: wrap the `saveAndPush` call in `handleBroadcast` with dedup check using `envelope.eventId() + ":" + userId`.

---

### C3 — JWT token exposed in SSE URL visible in server access logs

**File:** `notification-service/.../controller/NotificationSseController.java` line 33

`GET /api/notifications/stream?token=<JWT>` — the full token appears in Tomcat/Spring access logs by default (query string is logged). The plan's risk assessment acknowledges this but the mitigation ("not logging query params") was never implemented.

**Fix:** Add log masking config or suppress query params in access log. In `application.yml`:
```yaml
server:
  tomcat:
    accesslog:
      enabled: false  # or use pattern without %q
```
Alternatively, implement a short-lived one-time SSE token exchange: client obtains a short-TTL opaque ticket from a REST endpoint, uses the ticket for SSE. This is the proper solution for sensitive tokens in URLs.

---

## High Priority Findings

### H1 — Race condition in `SseEmitterRegistryService.removeEmitter` (THREAD SAFETY)

**File:** `notification-service/.../service/SseEmitterRegistryService.java` lines 35–43

```java
userEmitters.remove(emitter);
if (userEmitters.isEmpty()) {
    emitters.remove(userId);  // ← another thread may add to userEmitters between these lines
}
```

`CopyOnWriteArrayList` is thread-safe for individual operations, but the `isEmpty()` check + `ConcurrentHashMap.remove()` is not atomic. A new emitter could be added between `isEmpty()` returning `true` and `emitters.remove(userId)`, silently losing the newly added emitter for that user.

**Fix:** Use `computeIfPresent` for atomic conditional removal:
```java
emitters.computeIfPresent(userId, (k, list) -> {
    list.remove(emitter);
    return list.isEmpty() ? null : list;
});
```

---

### H2 — `handleBroadcast` loads ALL notifications into memory (PERFORMANCE/SAFETY)

**File:** `InAppNotificationEventListener.java` line 61

```java
var userIds = notificationRepository.findAll().stream()...
```

`findAll()` on a large `notifications` table loads every row to extract distinct userIds. This will OOM as data grows.

**Fix:** Add a repository query:
```java
@Query("SELECT DISTINCT n.userId FROM Notification n")
List<Long> findDistinctUserIds();
```

---

### H3 — SSE endpoint bypasses JWT filter entirely (SECURITY)

**File:** `application.yml` line 67: `/api/notifications/stream` is in `public-paths`.

The `JwtAuthenticationFilter` only reads from the `Authorization` header (line 73 of `JwtAuthenticationFilter.java`) — it never reads the `?token=` query param. So SSE requests arrive with no `SecurityContext`. The controller manually calls `tokenValidator.parseClaims(token)` and rejects nulls, which is correct.

However, the endpoint is listed as a public path, so **any authenticated endpoint check is fully bypassed** — the manual validation in the controller is the sole security gate. If the controller is ever refactored to use `@AuthenticationPrincipal`, it will silently fail.

**Risk:** Acceptable for now given the explicit validation, but fragile. Document this clearly or implement a `OncePerRequestFilter` that reads `?token=` for SSE paths.

---

### H4 — `NotificationBellComponent` has subscription leak on snackbar action (FRONTEND)

**File:** `cinema-frontend/.../notification-bell.component.ts` lines 44–45

```typescript
this.snackBar.open(notification.title, 'View', { duration: 5000 })
  .onAction().subscribe(() => this.goToNotifications());
```

`onAction()` returns an Observable that is subscribed but never unsubscribed. Each new notification creates a dangling subscription. Over time (many notifications), this accumulates.

**Fix:** Use `take(1)` or manage with `Subscription`:
```typescript
this.snackBar.open(notification.title, 'View', { duration: 5000 })
  .onAction().pipe(take(1)).subscribe(() => this.goToNotifications());
```

---

### H5 — `show-sql: true` must be disabled in production (SECURITY/PERFORMANCE)

**File:** `application.yml` line 21

SQL logging exposes query structure and potential data values in logs. This is dev-only config that leaks to production if env is not overriding it.

**Fix:** Either remove it (defaults to false) or gate behind a Spring profile:
```yaml
spring.jpa.show-sql: false
```

---

## Medium Priority Improvements

### M1 — `markAllAsRead` missing `@Transactional` annotation

**File:** `NotificationRepository.java` — `markAllAsReadByUserId` is a `@Modifying` query. The calling method `markAllAsRead` in `InAppNotificationServiceImpl` is annotated `@Transactional`, which is correct. However `markAllAsRead` does not return a count, so partial failures are silent. Low risk but worth noting.

---

### M2 — `saveAndPush` has TOCTOU between `hasEmitter` and `sendToUser`

**File:** `InAppNotificationServiceImpl.java` lines 55–57

```java
if (sseRegistry.hasEmitter(event.userId())) {
    sseRegistry.sendToUser(event.userId(), dto);
}
```

Emitter could be removed between `hasEmitter` and `sendToUser`. The `IOException` catch in `sendToUser` handles this gracefully, but the `hasEmitter` check is an unnecessary optimization that adds the TOCTOU window. `sendToUser` already returns early if no emitters exist (line 48). Remove the `hasEmitter` guard.

---

### M3 — Broadcast `notifyPaymentSuccess` formats `amount` as integer (UX)

**File:** `NotificationPublisherService.java` line 31

```java
String.format("Your payment of %d for booking #%d has been confirmed.", amount, bookingId)
```

`%d` for `amount` (Long) will render as raw cents/units — e.g. "10000" instead of "$100.00". Depends on business domain but likely needs currency formatting.

---

### M4 — `NotificationListComponent` missing error handling on API calls

**File:** `notification-list.component.ts` — `loadNotifications()`, `markAsRead()`, `markAllAsRead()` all have no `error` handler in subscribe. Silent failures give no user feedback.

---

### M5 — `ddl-auto: update` is risky for production

**File:** `application.yml` line 20. Acceptable for dev, but should be `validate` or `none` in production to prevent accidental schema mutations.

---

### M6 — `SecurityException` thrown from service layer returns 500

**File:** `InAppNotificationServiceImpl.java` line 77

`throw new SecurityException("Not authorized...")` — `SecurityException` is a Java runtime exception, not Spring's `AccessDeniedException`. Spring Security's 403 translation won't apply; it will result in a 500 response without a custom exception handler.

**Fix:** Throw `org.springframework.security.access.AccessDeniedException` instead.

---

## Low Priority Suggestions

### L1 — `KafkaProducerConfig` in booking-service is likely redundant

**File:** `booking-service/.../config/KafkaProducerConfig.java` — Spring Boot auto-configures `KafkaTemplate` when `spring.kafka.bootstrap-servers` is set. This manual config duplicates what autoconfigure provides. If booking-service already had a working `KafkaTemplate` bean before this feature, this creates a duplicate bean conflict.

**Check:** If `booking-service/src/main/resources/application.yml` already defines `spring.kafka`, remove this config class.

---

### L2 — `reconnectAttempts` not reset on `disconnect()` → `connect()` cycle

**File:** `notification-sse.service.ts` line 65 — `disconnect()` resets `reconnectAttempts = 0`. `connect()` calls `disconnect()` first, so it is reset. OK as-is.

---

### L3 — `createdAt` set in Java, not DB (`DEFAULT now()`)

**File:** `Notification.java` line 43 — `createdAt = LocalDateTime.now()` is set at object construction time. Bulk inserts in the same transaction will have identical timestamps (millisecond resolution). Consider `@CreationTimestamp` or a DB default. Minor for a notification system.

---

## Positive Observations

- Unique-consumer-group pattern for multi-instance SSE broadcast is architecturally sound and well-documented.
- `NotificationPublisherService` correctly isolates notification publishing and swallows exceptions to prevent booking flow disruption.
- `SseEmitterRegistryService` heartbeat + cleanup callbacks are correct.
- JWT ownership check in `markAsRead` (`!notification.getUserId().equals(userId)`) prevents IDOR.
- Frontend: exponential backoff reconnect with max attempts is well-implemented.
- Frontend: `OnDestroy` cleanup in both `NotificationBellComponent` and `NotificationSseService` is correct.
- `InAppNotificationEventListener` correctly filters by `eventType` discriminator before processing.
- `BroadcastRequestDto` uses `@NotBlank` validation.
- `@PreAuthorize("hasRole('ROLE_ADMIN')")` correctly gates the broadcast endpoint.

---

## Recommended Actions (Prioritized)

1. **[IMMEDIATE]** Rotate the exposed Gmail app password. Remove hardcoded credentials from `application.yml` (C1).
2. **[BEFORE PROD]** Fix broadcast deduplication — use Redis `NotificationDeduplicationService` or split consumer groups (C2).
3. **[BEFORE PROD]** Fix atomic `removeEmitter` using `computeIfPresent` (H1).
4. **[BEFORE PROD]** Replace `findAll()` with `findDistinctUserIds()` query (H2).
5. **[BEFORE PROD]** Add `take(1)` to snackbar subscription in `NotificationBellComponent` (H4).
6. **[BEFORE PROD]** Replace `SecurityException` with Spring's `AccessDeniedException` (M6).
7. **[BEFORE PROD]** Set `show-sql: false` / `ddl-auto: validate` for production profile (H5, M5).
8. **[NICE TO HAVE]** Remove `hasEmitter` guard before `sendToUser` (M2).
9. **[NICE TO HAVE]** Add error handlers to Angular API calls in `NotificationListComponent` (M4).
10. **[NICE TO HAVE]** Verify `KafkaProducerConfig` in booking-service isn't duplicating autoconfigured bean (L1).

---

## Metrics

- **Type Coverage:** N/A (Java) / TypeScript: good, no unsafe casts observed
- **Test Coverage:** 0 new tests added for this feature — untested
- **Critical Issues:** 3
- **High Issues:** 5
- **Medium Issues:** 6
- **Low Issues:** 3

---

## Unresolved Questions

1. Does `NotificationDeduplicationService` use Redis with a TTL suitable for dedup within the broadcast window (concurrent consumer instances)? If TTL is too short, dedup will miss duplicates from slow instances.
2. Is there a production Spring profile that overrides `ddl-auto` and `show-sql`? If yes, M5/H5 are already mitigated.
3. Was the Gmail app password already in git history before this commit? If so, rotating it is urgent regardless of the local fix.
4. Is `booking-service` already publishing to Kafka (pre-existing `KafkaTemplate` bean)? If yes, `KafkaProducerConfig` may cause `NoUniqueBeanDefinitionException` at startup.
