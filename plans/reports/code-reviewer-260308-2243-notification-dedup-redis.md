# Code Review: Redis Deduplication — notification-service

**Date:** 2026-03-08
**Plan:** `plans/260308-2237-notification-dedup-redis/`
**Reviewer:** code-reviewer agent

---

## Code Review Summary

### Scope
- Files reviewed: 6 (pom.xml, application.yml, NotificationDeduplicationService.java, NotificationEventListener.java, NotificationDeduplicationServiceTest.java, docker-compose.yml)
- Lines analyzed: ~220
- Review focus: correctness, security, edge cases per user request

### Overall Assessment
Implementation is clean, well-scoped, and follows KISS/YAGNI. The core dedup mechanism is correct for the single-consumer-group topology. Three issues warranted attention (one high, two medium). Tests pass (3/3).

---

### Critical Issues
None.

---

### High Priority Findings

**H1: SETNX-before-send means a send failure permanently blocks retry for that eventId**

- **File:** `NotificationDeduplicationService.java` L31-40, `NotificationEventListener.java` L41-45
- **Problem:** `tryMarkProcessed` sets the Redis key before `emailSenderService.sendEmail()` is called. If SMTP throws (transient failure), Kafka DLT retries the same `eventId` — but the Redis key already exists → `tryMarkProcessed` returns `false` → email is permanently skipped.
- **Plan note:** The plan explicitly acknowledges this trade-off in `phase-01` and accepts it: _"ambiguous failures (SMTP timeout) may have sent; producer generates new eventId for genuine retries."_
- **Assessment:** This is a **deliberate design decision**, not an oversight. However, the listener code swallows the trade-off silently. The re-throw on line 57 will trigger DLT, but DLT retries will always be skipped → DLT becomes a dead queue for transient SMTP errors with no alerting. The DLT should emit a warning that dedup is preventing retry.
- **Recommendation:** Add a log at WARN level in the `handleNotificationEvent` method after the dedup check fails, distinguishing _already-processed_ from _send-failed-and-marked_. Alternatively emit a Micrometer counter. No code change required if the team accepts silent DLT stalling — but document this explicitly.

---

### Medium Priority Improvements

**M1: No null guard on `envelope.eventId()`**

- **File:** `NotificationEventListener.java` L41, `NotificationDeduplicationService.java` L34
- **Problem:** `EventEnvelope.eventId` has no `@NonNull` annotation. If a malformed Kafka message arrives with `null` eventId (e.g., producer bug, schema mismatch), the Redis key becomes `"notification:processed:null"` and deduplication silently stops working for all subsequent null-eventId events.
- **Recommendation:** Add a null check before dedup:
  ```java
  if (envelope.eventId() == null) {
      log.error("Received event with null eventId, skipping dedup: correlationId={}", envelope.correlationId());
      // proceed to send (or throw to DLT depending on policy)
  }
  ```
  Or guard in `tryMarkProcessed` itself and return `true` (fail-open) with a WARN log.

**M2: Gmail app password hardcoded in `application.yml` as fallback default**

- **File:** `notification-service/src/main/resources/application.yml` L28
- **Problem:** `password: ${MAIL_PASSWORD:sdxm fmia vuzf bvmq}` — a real Gmail app password is embedded as a default. Same issue exists in `docker-compose.yml` L215. While this is a pre-existing issue (not introduced by this PR), the dedup feature adds a new `MAIL_PASSWORD` reference in docker-compose that preserves and re-exposes it.
- **Risk:** Source control leakage. App passwords can be used to access the Gmail account.
- **Recommendation:** Remove default values — require explicit env vars. Use `${MAIL_PASSWORD}` with no fallback so startup fails fast rather than silently using a real credential. Rotate the app password immediately.

---

### Low Priority Suggestions

**L1: Test uses `anyString()` for key prefix — doesn't verify correct key construction**

- **File:** `NotificationDeduplicationServiceTest.java` L33
- The mock uses `anyString()` for the key argument. A test that asserts the exact key `"notification:processed:event-123"` would catch a future key prefix regression.
- Not critical since the prefix is a constant, but low-cost to add.

**L2: Redis service has no persistence or auth in docker-compose**

- **File:** `docker-compose.yml` L40-47
- Redis runs with default config: no password, no AOF/RDB persistence. On container restart, all dedup keys are lost — duplicates can fire immediately after restart.
- For dev/test: acceptable. For staging/prod: document the restart behavior. Consider adding `--appendonly yes` command or a volume mount.

**L3: `REDIS_PORT` env var in `application.yml` has no corresponding entry in `docker-compose.yml`**

- `application.yml` exposes `${REDIS_PORT:6379}` but docker-compose never sets `REDIS_PORT`. Default `6379` is correct, so no functional impact — just minor inconsistency.

---

### Positive Observations

- `setIfAbsent(key, value, TTL)` maps directly to Redis `SET NX EX` — correct atomic primitive, not GET-then-SET.
- `Boolean.TRUE.equals(result)` correctly handles the nullable `Boolean` return from Spring Data Redis — avoids NPE.
- Fail-open exception handling is appropriate for a notification side-feature; blocks no critical path.
- Dedup check placed before event-type routing — correct position to gate all downstream logic.
- Tests are focused, fast (no Spring context), and cover all three branches.
- Plan accurately documents the SETNX-before-send trade-off in writing — good engineering hygiene.
- `KEY_PREFIX` and `TTL` as named constants — easy to adjust.
- No sensitive data stored in Redis (UUID key + literal "1" value).

---

### Task Completeness Verification

**Phase 1 todos:**
- [x] Add `spring-boot-starter-data-redis` to pom.xml
- [x] Add Redis host/port config to application.yml
- [x] Create `NotificationDeduplicationService.java`
- [x] Update `NotificationEventListener.java` with dedup check
- [x] Compile passes (build verified via `mvnw test`)

**Phase 2 todos:**
- [x] Add `redis-service` dependency + `REDIS_HOST` env to notification-service in docker-compose.yml
- [x] Create `NotificationDeduplicationServiceTest.java`
- [x] All 3 tests pass (`BUILD SUCCESS`)

All plan tasks are complete.

---

### Recommended Actions

1. **(H1 — optional accept)** Document or log when DLT retry is blocked by dedup key. Add `log.warn` with eventId in listener when `tryMarkProcessed` returns false and SMTP previously threw.
2. **(M1 — fix)** Add null guard on `envelope.eventId()` in listener before calling `tryMarkProcessed`.
3. **(M2 — fix immediately)** Remove Gmail app password defaults from `application.yml` and `docker-compose.yml`. Rotate the credential.
4. **(L1 — optional)** Tighten dedup test to assert exact Redis key `"notification:processed:event-123"`.
5. **(L2 — document)** Note Redis restart clears dedup keys in ops runbook.

---

### Metrics
- Test Coverage (dedup service): 100% branch coverage (3 branches tested)
- Tests: 3 passed, 0 failed
- Build: SUCCESS
- Linting issues: 0 compile errors

---

### Unresolved Questions
1. What is the retry/alert strategy when DLT receives a message that dedup will permanently skip? Silent DLT stalling is the current behavior.
2. Is the Gmail app password in `application.yml`/`docker-compose.yml` intentionally committed (dev convenience) or accidental? Either way it should be rotated.
3. Should Redis restart (dedup key loss) be covered by a startup grace period or accepted as a known gap?
