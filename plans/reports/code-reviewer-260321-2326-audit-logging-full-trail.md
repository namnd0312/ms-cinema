# Code Review: FR-4.1 Audit Logging — Full Trail

**Date:** 2026-03-21
**Reviewer:** code-reviewer agent
**Plan:** `/plans/260321-2251-audit-logging-full-trail/plan.md`

---

## Scope

- **Files reviewed:** 22 Java files, 3 YAML files, docker-compose.yml, init-databases.sql, Dockerfile
- **Lines analyzed:** ~650 (new code only)
- **Review focus:** Security, correctness, AOP behavior, architecture, plan compliance

---

## Overall Assessment

Solid architecture for the audit logging feature. The after-commit pattern, idempotency key, and ADMIN-only access control are implemented correctly. The implementation deviates from the plan in two notable ways (interceptor location, no Hibernate Envers) — acceptable pragmatic simplifications. Three issues require attention: a security concern about token leakage in login afterState, incorrect index column names, and dead code.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — LOGIN afterState may serialize JWT tokens into the audit log

**File:** `kafka-events/src/main/java/com/namnd/kafka/events/audit/AuditAspect.java` line 121
**File:** `auth-service/.../AuthController.java` line 100

`serializeResult()` skips serialization only for `DELETE` and `LOGOUT`. For `LOGIN`, it attempts `objectMapper.writeValueAsString(result)` where `result` is a `ResponseEntity<JwtResponseDto>`. If Jackson partially serializes this (extracting the body field), `afterState` will contain the `token` and `refreshToken` values.

In practice, Jackson fails to fully serialize `ResponseEntity` (falls to the catch → `null`), so the current risk is low — but this is fragile and depends on Jackson's internal behavior, which could change.

**Fix:** Add `LOGIN` to the skip list:
```java
if (result == null || action == AuditAction.DELETE
        || action == AuditAction.LOGOUT || action == AuditAction.LOGIN) {
    return null;
}
```

---

### H2 — @Index columnList uses Java field names, not physical DB column names

**File:** `audit-service/.../domain/AuditLog.java` lines 18-21

```java
@Index(name = "idx_audit_user_id",     columnList = "userId"),      // wrong: should be "user_id"
@Index(name = "idx_audit_entity_type", columnList = "entityType"),  // wrong: should be "entity_type"
@Index(name = "idx_audit_created_at",  columnList = "createdAt")    // wrong: should be "created_at"
```

Spring Boot 3 / Hibernate 6 uses `CamelCaseToUnderscoresNamingStrategy` by default — physical column names are snake_case. `@Index.columnList` must use physical column names. With `ddl-auto: update`, Hibernate may create indexes pointing to non-existent column names, silently skipping them or failing.

**Fix:**
```java
@Index(name = "idx_audit_user_id",      columnList = "user_id"),
@Index(name = "idx_audit_action",       columnList = "action"),
@Index(name = "idx_audit_entity_type",  columnList = "entity_type"),
@Index(name = "idx_audit_created_at",   columnList = "created_at")
```

---

## Medium Priority Improvements

### M1 — AuditEntityListener is dead code

**File:** `kafka-events/.../audit/AuditEntityListener.java`

The class is fully implemented but never applied — no entity uses `@EntityListeners(AuditEntityListener.class)`. It was likely scaffolded for a future Envers-based approach (per plan action items). As-is it's dead code that adds cognitive overhead and maintenance burden.

**Options:**
1. Delete it (YAGNI) — preferred, since plan decided on `@Auditable` AOP approach
2. Document it clearly as "v2 — not active yet" with a TODO

---

### M2 — Idempotency check lacks @Transactional (TOCTOU window)

**File:** `audit-service/.../consumer/AuditEventConsumer.java` lines 30-34

```java
if (repository.existsByEventId(envelope.eventId())) { return; }  // check
repository.save(AuditLogMapper.toEntity(envelope));               // use
```

Two consumer threads (e.g., two partitions, rebalance edge case) could both pass the check simultaneously before either persists. The DB unique constraint on `eventId` is the real guard — a second insert throws `DataIntegrityViolationException`, which propagates up and triggers DLT after retries. This is not catastrophic but causes unnecessary DLT noise.

**Fix:** Add `@Transactional` + catch `DataIntegrityViolationException`:
```java
@Transactional
@KafkaListener(...)
public void consume(EventEnvelope<AuditEvent> envelope) {
    try {
        if (repository.existsByEventId(envelope.eventId())) { return; }
        repository.save(AuditLogMapper.toEntity(envelope));
    } catch (DataIntegrityViolationException e) {
        log.debug("Duplicate audit eventId={} (concurrent insert, ignoring)", envelope.eventId());
    }
}
```

---

### M3 — cancelBooking uses AuditAction.UPDATE instead of a semantic action

**File:** `booking-service/.../BookingServiceImpl.java` line 133

`cancelBooking` is annotated `@Auditable(action = AuditAction.UPDATE, entityType = "Booking")`. While `UPDATE` technically covers state changes, it obscures cancellation semantics in the audit log. `CUSTOM` with a note field, or adding a `CANCEL` enum value, would improve audit trail readability.

Low-urgency but worth addressing before compliance review.

---

### M4 — AuditLog.createdAt is not null-protected from envelope.timestamp()

**File:** `audit-service/.../mapper/AuditLogMapper.java` line 35

`createdAt` is set from `envelope.timestamp()`. The `EventEnvelope.of()` factory always sets a timestamp, so this is safe under normal conditions. However, if a malformed event arrives with a null timestamp, the `@Column(nullable = false)` constraint will throw on save and route to DLT — acceptable behavior, but worth a defensive null check:

```java
.createdAt(envelope.timestamp() != null ? envelope.timestamp() : LocalDateTime.now())
```

---

### M5 — Plan deviations not documented in plan

**Plan file:** `plans/260321-2251-audit-logging-full-trail/plan.md`

The plan specified:
1. "Move interceptor code to new `audit-commons` module" → interceptors are in `kafka-events` instead
2. "Replace JPA EntityListener with Hibernate Envers" → AOP-only approach was implemented; EntityListener exists but is unused (see M1)
3. Phase statuses all show `pending` even though implementation is complete

These should be updated for future reference.

---

## Low Priority Suggestions

### L1 — X-Forwarded-For is blindly trusted

**File:** `kafka-events/.../audit/AuditHttpContext.java` line 20

`X-Forwarded-For` is taken from client request headers without validating that it comes from a trusted proxy. A client can spoof their IP in the audit log. In this architecture where requests pass through the API Gateway, this is lower risk (gateway sets/overwrites the header), but explicit proxy trust configuration (e.g., via `server.forward-headers-strategy: FRAMEWORK`) is more robust.

---

### L2 — AuditLogMapper uses a static ObjectMapper instance

**File:** `audit-service/.../mapper/AuditLogMapper.java` line 17

```java
private static final ObjectMapper objectMapper = new ObjectMapper();
```

`ObjectMapper` with default config (no modules registered) may fail to deserialize `LocalDateTime` in `afterState` JSON. This is fine since `parseJson` falls back to raw string on failure, but using the Spring-managed `ObjectMapper` bean (which has JavaTimeModule registered) would be cleaner. Since the mapper is a static utility class, inject via constructor or pass as parameter if needed.

---

### L3 — JWT_SECRET not passed to audit-service in docker-compose

**File:** `docker-compose.yml`

`audit-service` environment block does not include `JWT_SECRET`. It falls back to the hardcoded default in `application.yml`. This is consistent with other services (same hardcoded default pattern), but it's a pre-existing concern — all services share the same well-known fallback secret, which must be overridden in any production deployment. This is noted, not introduced by this PR.

---

### L4 — AuditAction enum missing REGISTER / CANCEL / CHANGE_PASSWORD actions

`AuditAction.CREATE` is used for both `register` (create user) and `createMovie`. This is fine, but `change-password` mapped to `UPDATE` and `cancelBooking` mapped to `UPDATE` reduces audit discriminability. The `CUSTOM` value exists for extension but is not used.

---

## Positive Observations

- **After-commit pattern** (`@TransactionalEventListener` with `fallbackExecution=true`) is textbook-correct. Kafka publish never rolls back the business transaction.
- **Idempotency key** (`eventId` UUID on `EventEnvelope`) with DB unique constraint is a solid guarantee against message replay duplicates.
- **AuditAspect exception isolation**: `proceed()` is called before the try-catch — business logic exceptions propagate cleanly; audit failures are swallowed with a WARN log. Correct design.
- **Admin API**: `@PreAuthorize("hasRole('ADMIN')")` at class level (not just method level) prevents missing-annotation gaps. Page size cap at 100 is good defensive practice.
- **AuditAutoConfiguration** `@ConditionalOnClass(KafkaTemplate.class)` ensures the interceptor library doesn't activate in services without Kafka — clean dependency design.
- **Dead code path in AuditEntityListener**: even though unused, the `publishEntityAudit` method has proper null-check for `ApplicationContext` (line 43) and user context extraction.
- **`@JsonIgnore` on `User.password`** prevents password hash from appearing in audit `afterState`.
- **DLT configuration** with exponential backoff and `SerializationException` non-retryable exclusion is production-ready.

---

## Recommended Actions

1. **[H1 — Required]** Add `LOGIN` to `serializeResult` skip list in `AuditAspect`.
2. **[H2 — Required]** Fix `@Index.columnList` to use snake_case physical column names in `AuditLog`.
3. **[M1 — Recommended]** Remove `AuditEntityListener` (dead code) or add `@EntityListeners` to intended entities.
4. **[M2 — Recommended]** Add `@Transactional` + `DataIntegrityViolationException` catch to `AuditEventConsumer.consume()`.
5. **[M5 — Required]** Update plan phase statuses to `completed` and document implementation deviations.
6. **[M4 — Low effort]** Add null-guard on `envelope.timestamp()` in `AuditLogMapper`.

---

## Metrics

- **Type Coverage:** Java — no TypeScript. Compilation confirmed passing (12 modules BUILD SUCCESS).
- **Test Coverage:** 0% for new code — `audit-service/src/test` directory does not exist.
- **Linting Issues:** No syntax errors. Minor style issues (field-name indexes, static ObjectMapper).
- **Dead Code:** 1 class (`AuditEntityListener` — 91 lines unused).
- **Plan Compliance:** 4/6 phases technically implemented; interceptors placed in `kafka-events` instead of `audit-commons` (plan deviation); Hibernate Envers not used (plan deviation).

---

## Unresolved Questions

1. Is `AuditEntityListener` intentionally kept for v2 Envers integration, or should it be removed?
2. Should `ddl-auto: update` be changed to `validate` + explicit Flyway/Liquibase migration before production? (Pre-existing concern, now relevant since `auditdb` schema is being created fresh.)
3. The `audit-service-docs` Swagger route is exposed without JWT protection at the gateway level — intentional for dev convenience?
