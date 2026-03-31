# Code Review: Stripe Reconciliation Feature

## Scope
- **Files reviewed:** 30 files (21 Java, 6 TypeScript, 2 config, 1 pom.xml)
- **Lines analyzed:** ~1,050 Java, ~380 TypeScript
- **Review focus:** Security, correctness, error handling, performance, code quality
- **Build/tests:** `mvn compile` clean, 15/15 tests pass
- **Updated plans:** `plans/260331-2119-stripe-reconciliation-spring-batch/plan.md`

---

## Overall Assessment

Implementation is clean, well-structured, and follows existing patterns. No critical issues. A few medium issues around data integrity and one notable security concern in the application config.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — Stripe API key hardcoded default in `application.yml`
**File:** `payment-service/src/main/resources/application.yml` line 49
```yaml
stripe:
  api-key: ${STRIPE_API_KEY:sk_test_51O4nq6Jasu...}
```
A real `sk_test_*` key ships as the default fallback. Even if test-tier, this leaks a valid Stripe credential into the repo. The fallback should be an empty string or a clearly fake value.
**Fix:** `${STRIPE_API_KEY:}` — fail fast if not set rather than use a real key.

---

## Medium Priority Improvements

### M1 — `LocalPaymentReader` loads all payments into memory
**File:** `batch/LocalPaymentReader.java`
All matching payments are fetched in a single `List<Payment>` on first `read()`. For large date ranges (up to 31 days) with many payments, this defeats the purpose of chunk-oriented batch processing.
**Fix:** Use `JpaPagingItemReader` or `JdbcPagingItemReader` for proper streaming — or at minimum page the query (e.g. fetch page-by-page in `initialize()`).

### M2 — N+1 Stripe API calls, no rate-limit guard
**File:** `batch/ReconciliationProcessor.java` line 57
Each `Payment` triggers one `PaymentIntent.retrieve()` call. With chunk size 100 and 31-day range, thousands of API calls occur with no retry/backoff strategy. Stripe rate limit is 100 req/s in test mode, 25 req/s in restricted mode.
**Fix:** Add `RequestOptions` with idempotency key + configure a `StripeClient` with custom retry count, or inject a configurable delay between chunks.

### M3 — `ReconciliationProcessor` queries DB on every chunk's first item (lazy `run` load)
**File:** `batch/ReconciliationProcessor.java` lines 37–39
```java
if (run == null) {
    run = runRepository.findById(runId).orElseThrow();
}
```
`run` is loaded once per processor instance. Because `@StepScope` creates a new instance per step execution this works, but the pattern is fragile — if Spring scope ever reuses the bean across restarts it will use a stale `run`. Better to load in `@PostConstruct` or via constructor injection.

### M4 — `ReconciliationJobListener.afterJob` timezone mismatch for MISSING_LOCAL detection
**File:** `batch/ReconciliationJobListener.java` lines 59–61
```java
.setGte(run.getStartDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC))
```
The scheduler uses `Asia/Saigon` (UTC+7) to compute "yesterday", but the Stripe list query here uses UTC epoch seconds with `atStartOfDay()` (no timezone). This shifts the window by 7 hours, meaning Stripe records created between 00:00–07:00 local time will be missed or double-counted.
**Fix:** Use `ZoneId.of("Asia/Saigon")` when computing epoch seconds:
```java
run.getStartDate().atStartOfDay(ZoneId.of("Asia/Saigon")).toEpochSecond()
```

### M5 — `ReconciliationServiceImpl.triggerReconciliation` returns stale RUNNING status
**File:** `service/impl/ReconciliationServiceImpl.java` line 72
After the async job launcher starts the job, the method returns the `run` entity which always has status `RUNNING`. If the job fails synchronously during launch, the run is updated to FAILED and saved, but the returned DTO still reflects `RUNNING`. The caller gets incorrect data.
**Fix:** Re-fetch `run` from repository after the catch block before calling `toRunResponse(run)`, or reload on catch.

### M6 — `ResolveItemRequest.notes` has no validation
**File:** `dto/ResolveItemRequest.java`
Notes field is unbounded — could be any length. The DB column has no length constraint either.
**Fix:** Add `@Size(max = 1000)` to the record field.

### M7 — `exportCsv()` fetches up to 10,000 items client-side
**File:** `reconciliation-detail.component.ts` line 179
```ts
this.service.getRunItems(this.runId, 0, 10000).subscribe(...)
```
This is unbounded on the frontend side and relies on backend not blowing up at size 10000. For large runs (31 days × high volume), this can cause OOM on the Angular side.
**Fix:** Add a server-side export endpoint returning a CSV stream, or cap export at a reasonable limit with a user warning.

---

## Low Priority Suggestions

### L1 — `getSummary()` returns `null` (not 204)
**File:** `service/impl/ReconciliationServiceImpl.java` line 108
Returns `null` when no runs exist, which serializes to `null` JSON body with 200 OK. Prefer returning `ResponseEntity` with 204 No Content for clarity.

### L2 — `ReconciliationItemResponse.discrepancyType` uses `String` not enum
**File:** `dto/ReconciliationItemResponse.java` line 14
All other status fields also use `String`. Using the enum type directly gives compile-time safety without extra code.

### L3 — `loadSummary` silently swallows errors
**File:** `reconciliation-dashboard.component.ts` line 129
```ts
error: () => {}
```
Empty error handler means network failures on summary are invisible to admins. At minimum log or show a stale-data indicator.

### L4 — `show-sql: true` in production `application.yml`
Should be `false` or profile-controlled to avoid SQL flooding in production logs.

### L5 — `PageImpl` serialization warning in tests
Tests emit a Spring Data warning about `PageImpl` JSON stability. Configure `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)` globally.

---

## Positive Observations

- Class-level `@PreAuthorize("hasRole('ADMIN')")` cleanly enforces admin-only on all reconciliation endpoints — no per-method gaps.
- `@ConditionalOnProperty` on `ReconciliationScheduler` provides clean opt-in/opt-out for auto-scheduling.
- `StripeConfig` uses `@PostConstruct` for SDK initialization — correct pattern.
- Two-pass reconciliation (local-first processor + MISSING_LOCAL afterJob) matches the validated architectural decision.
- `findByDateRangeWithStripeId` correctly excludes null Stripe IDs from the JPQL query.
- Status mapping in `mapStripeStatus()` covers all 7 Stripe PI states.
- `LocalDate`-based date range parameters (not `LocalDateTime`) in job parameters is clean.
- Test coverage: unit tests for processor (5 cases), service (6 cases), controller (4 cases including 403 check) — solid baseline.
- `GlobalExceptionHandler` correctly handles both `AccessDeniedException` and `AuthorizationDeniedException` (Spring Security 6 regression).
- Angular model types use proper union types for `DiscrepancyType` and `ReconciliationRun.status`.

---

## Recommended Actions

1. **[H1] Remove real Stripe key default** — `${STRIPE_API_KEY:}` in `application.yml`
2. **[M4] Fix timezone in `afterJob` Stripe list query** — use `Asia/Saigon` ZoneId for epoch conversion
3. **[M1] Replace in-memory reader** — use `JpaPagingItemReader` to avoid full-table load
4. **[M2] Add Stripe retry/backoff config** — configure `StripeClient` max network retries
5. **[M5] Fix stale response on job-launch failure** — reload run entity before returning
6. **[M6] Add `@Size` validation on `ResolveItemRequest.notes`**
7. **[M7] Cap or server-side CSV export** — avoid 10,000-item client-side fetch
8. **[L4] Set `show-sql: false`** in base `application.yml`

---

## Metrics

- **Build:** Clean (0 errors)
- **Tests:** 15/15 pass
- **Critical issues:** 0
- **High issues:** 1 (Stripe key in config fallback)
- **Medium issues:** 7
- **Low issues:** 5

---

## Plan Action Items Status

Both action items from `plan.md` Validation Summary are **implemented**:
- [x] Local-first approach: `LocalPaymentReader` queries `findByDateRangeWithStripeId` (not Stripe-first)
- [x] `ZoneId.of("Asia/Saigon")` used in `ReconciliationScheduler` (**note: not applied in `afterJob` Stripe query — see M4**)
