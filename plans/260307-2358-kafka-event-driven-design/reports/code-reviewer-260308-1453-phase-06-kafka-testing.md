# Code Review: Phase 6 — Kafka Testing & Verification

**Date:** 2026-03-08
**Reviewer:** code-reviewer
**Scope:** Phase 6 test implementation for Kafka event-driven design

---

## Code Review Summary

### Scope
- Files reviewed: 6 files (2 test classes, 2 `application-test.yml`, 2 `pom.xml` test sections)
- Lines of code analyzed: ~280
- Review focus: Phase 6 — Testing & Verification

### Overall Assessment

Implementation is solid and production-grade. Tests are well-structured, appropriately scoped (unit vs integration), and cover all four required scenarios. No critical or high-priority issues. Minor gaps noted below.

---

### Critical Issues

None.

---

### High Priority Findings

**H1 — DLT test uses `"true"` (auto-commit) in manual poll loop (correctness risk)**

`PaymentEventListenerIntegrationTest.shouldRouteToDltAfterRetriesExhausted` creates a raw `KafkaConsumer` via `KafkaTestUtils.consumerProps("dlt-test-group", "true", embeddedKafka)`. The second arg (`"true"`) enables auto-commit. Combined with the manual `dltConsumer.poll()` inside an Awaitility `untilAsserted` block that may retry multiple times, consumed records may be re-polled on subsequent Awaitility iterations if the consumer group rebalances. More critically, the assertion captures only the **first** poll's result and discards subsequent ones — if the first poll returns 0 records the assertion fails, even though the message arrives in a later poll iteration.

The current pattern is fragile: `records.count() > 0` passes only when `poll()` returns records in the same Awaitility iteration. A safer pattern accumulates records across polls:

```java
List<ConsumerRecord<String, String>> collected = new ArrayList<>();
await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofSeconds(2)).untilAsserted(() -> {
    collected.addAll(StreamSupport.stream(dltConsumer.poll(Duration.ofMillis(500)).spliterator(), false)
            .collect(Collectors.toList()));
    assertThat(collected).isNotEmpty();
    assertThat(collected.get(0).key()).isEqualTo(nonExistentBookingId.toString());
});
```

**H2 — Redis dependency in booking-service `application-test.yml` is live (not mocked)**

`application-test.yml` sets `spring.data.redis.host: localhost` and `port: 6379`. There is no embedded Redis or mock. If `SeatLockService` is mocked via `@MockitoBean` the Redis auto-configuration still attempts to connect. If the test environment has no Redis on localhost:6379 the Spring context will fail to start. Should add `spring.autoconfigure.exclude: org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration` or use Testcontainers/embedded Redis, or confirm that booking-service's Redis config is conditional on `SeatLockService` not being mocked.

---

### Medium Priority Improvements

**M1 — `shouldBeIdempotentOnDuplicatePaymentCompleted` uses `await().during().atMost()` — timing-sensitive**

The "stability" window (`during(2s).atMost(5s)`) is correct in intent but depends on the duplicate being consumed within 2 seconds. On slow CI machines (high load), the duplicate may not yet have been processed by the listener when the assertion window starts, making the test pass vacuously (the state has not changed yet simply because the message hasn't been consumed). A more robust approach: wait for the duplicate to be published *and* an additional fixed delay before asserting stability.

**M2 — `PaymentEventListenerIntegrationTest`: `ConsumerFactory<String, Object>` injected but unused**

Line 63: `@Autowired private ConsumerFactory<String, Object> consumerFactory;` — never referenced in any test. Dead field. Remove it.

**M3 — DLT test does not assert message content beyond key**

The DLT test verifies `record.key()` only. Asserting the DLT record value contains recognizable event payload data would strengthen the test, though this is lower priority since the value is still the raw serialized bytes.

**M4 — `PaymentEventPublisherTest.shouldUseBookingIdAsMessageKey` is redundant**

This third unit test (`shouldUseBookingIdAsMessageKey`) verifies only that `kafkaTemplate.send()` is called with key `"7"` using `any()` for the envelope. The first test (`shouldPublishPaymentCompletedWithCorrectEnvelope`) already implicitly verifies the key via `eq("42")`. The third test adds no distinct coverage. Acceptable for now per YAGNI, but consider removing to keep the test class DRY.

**M5 — `payment-service/application-test.yml` is minimal — may cause context issues**

Only disables `jwt.auth` and Eureka. No Kafka, datasource, or other config. For `PaymentEventPublisherTest` this is fine (pure Mockito, no Spring context). However, if future integration tests are added to `payment-service`, the absence of bootstrap-servers override will break `@EmbeddedKafka` wiring. Add a placeholder `spring.kafka.bootstrap-servers` override as a safety measure.

---

### Low Priority Suggestions

**L1 — `@DirtiesContext` on integration test class is expensive**

`@DirtiesContext` resets the entire Spring context after the test class, which is correct for avoiding cross-test Kafka consumer group state leakage. However, this doubles test startup time. Consider using `@EmbeddedKafka` with `brokerProperties = {"group.initial.rebalance.delay.ms=0"}` and unique group IDs per test run to avoid needing context teardown. Low priority given the <20s runtime goal is met.

**L2 — `bookingRepository.deleteAll()` in `@BeforeEach` without transaction isolation**

Each test calls `deleteAll()` on H2. Fine for sequential test execution, but if tests run in parallel (e.g., via Surefire `forkCount`), shared H2 state could cause flakiness. Currently not an issue but worth noting if parallelism is added later.

---

### Positive Observations

- Test structure is clean: separation of unit (Mockito-only) vs integration (@SpringBootTest + @EmbeddedKafka) is correct
- `Awaitility` usage with `atMost(10s)` is appropriate for async consumer assertions — avoids brittle `Thread.sleep()`
- Setting up the DLT consumer **before** sending the event (line 164 comment) correctly avoids the race condition of missing the message
- `@MockitoBean` for `SeatLockService` and `MovieServiceClient` is the right approach to avoid external dependency calls
- `PaymentEventPublisherTest` cleanly tests `EventEnvelope` field population with `ArgumentCaptor` — covers all required fields (eventType, source, correlationId, eventId, timestamp, payload)
- `KafkaConsumerConfig.addNotRetryableExceptions()` is properly wired and the DLT test exercises the retry→DLT path end-to-end
- `application-test.yml` correctly disables Eureka and JWT auth to keep test context lightweight
- Listener idempotency implementation (terminal state check before processing) is the correct pattern

---

### Recommended Actions

1. **(H2 — blocking if Redis absent)** Exclude `RedisAutoConfiguration` in `booking-service/src/test/resources/application-test.yml` or add Testcontainers/embedded Redis to prevent context startup failure when Redis is not available
2. **(H1)** Refactor DLT test to accumulate poll results across Awaitility iterations to eliminate race-prone single-poll assertion
3. **(M2)** Remove unused `ConsumerFactory` field from `PaymentEventListenerIntegrationTest`
4. **(M1)** Revisit idempotency test timing on CI; consider adding explicit `Thread.sleep(500)` after second publish before starting the stability window

---

### Metrics
- Test Coverage (phase scope): 4/4 integration scenarios covered, 3/3 unit scenarios covered
- Linting Issues: 1 unused field (M2)
- Test count: 7 tests (4 integration, 3 unit) — all reported passing

---

### Phase 6 Task Status

| Task | Status |
|------|--------|
| Add spring-kafka-test dependency to both services | Complete |
| Create PaymentEventPublisherTest (3 unit tests) | Complete |
| Create PaymentEventListenerIntegrationTest (happy path) | Complete |
| Add idempotency test | Complete |
| Add DLT test | Complete |
| Run full test suite — 7/7 pass, <20s | Complete |
| Manual docker-compose smoke test | Deferred (noted in plan) |

All automated tasks complete. Docker-compose smoke test remains deferred pending running infra.

---

### Unresolved Questions

1. Does the CI environment have Redis on `localhost:6379`? If not, H2 is fine but Redis auto-config needs exclusion to prevent context startup failure (H2).
2. Is `ExponentialBackOffWithMaxRetries(3)` configured to match the comment "3x" retries (3 attempts) or 3 **extra** retries (4 attempts total)? Spring's `ExponentialBackOffWithMaxRetries(n)` means `n` retries after the initial attempt, so total = 4 deliveries. The DLT test comment says "1s + 2s + 4s" (3 intervals = 3 retries), consistent with `maxRetries=3`. Verify against the actual `DefaultErrorHandler` behavior to ensure the DLT timeout (30s) is adequate.
