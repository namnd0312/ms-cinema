# Code Review: Kafka Event-Driven Design

**Date:** 2026-03-08
**Plan:** `/plans/260307-2358-kafka-event-driven-design/`
**Score: 8 / 10**

---

## Scope

- Files reviewed: 12 source files + 5 domain event records
- Lines of code analyzed: ~500 LOC total
- Review focus: EventEnvelope correctness, idempotency, DLT error handling, security, LOC limits

---

## Overall Assessment

Implementation is solid and consistent. EventEnvelope pattern applied uniformly. Idempotency via booking-status check is correct. DLT wiring present. No sensitive data in events. All files well under 200 LOC. Three issues require attention before Phase 6 testing proceeds.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — `PaymentEventListener`: raw `EventEnvelope` type warning + deserializer mismatch risk

**File:** `booking-service/.../listener/PaymentEventListener.java:46`

```java
public void handlePaymentEvent(EventEnvelope envelope) {  // raw type
```

The method uses a raw `EventEnvelope` (no generic parameter). This is intentional per plan (Option A, type erasure workaround), but the Kafka consumer config sets `value-deserializer: JsonDeserializer` with `spring.json.trusted.packages`. At runtime Spring Kafka's `JsonDeserializer` needs to know the target class to deserialize into. Without a `spring.json.value.default.type` or `spring.json.trusted.packages` consumer property pointing to `EventEnvelope`, deserialization may fall back to `LinkedHashMap` at the top level—meaning `envelope.eventType()` would NPE or CCE.

**Verify:** Add `spring.json.value.default.type: com.namnd.kafka.events.envelope.EventEnvelope` to booking-service consumer config, **or** confirm the `KafkaListenerContainerFactory` is configured with a custom deserializer that targets `EventEnvelope.class`. Without this, the integration is broken at runtime even though it compiles.

### H2 — `KafkaConsumerConfig`: `setMaxElapsedTime` does not reliably enforce exactly 3 retries

**File:** `booking-service/.../config/KafkaConsumerConfig.java:29`

```java
backOff.setMaxElapsedTime(3 * 10000L); // ensures ~3 retries
```

`ExponentialBackOff.setMaxElapsedTime(30000)` is wall-clock elapsed time, not attempt count. With initial=1000ms and multiplier=2.0: retry 1 at ~1s, retry 2 at ~2s, retry 3 at ~4s (total elapsed ~7s). The 30s ceiling allows more than 3 retries (up to ~5). Plan specifies exactly 3. Use `ExponentialBackOffWithMaxRetries(3)` instead for precise control.

### H3 — `confirmBooking` not `@Transactional` — race condition on idempotency check

**File:** `booking-service/.../service/impl/BookingServiceImpl.java:118`
**Cross-reference:** `PaymentEventListener.java:52-57`

The listener does:
1. `bookingRepository.findById()` — check status
2. `bookingService.confirmBooking()` — update status

`confirmBooking` itself has no `@Transactional` annotation (only `cancelBooking` is also missing it). Two concurrent duplicate events could both pass the status check before either commits. Low probability in practice (Kafka delivers in-order per partition, and bookingId is the message key), but the guard in `confirmBooking` (`if (booking.getStatus() != PENDING) return;`) is a second check that does NOT throw—so the silent skip works. Still, the `findById` + `confirmBooking` should be one atomic unit. Mark `confirmBooking` and `cancelBooking` `@Transactional`.

---

## Medium Priority Improvements

### M1 — `PaymentServiceImpl`: duplicate event publishing in two code paths

**File:** `PaymentServiceImpl.java:137` and `:172`

`handleWebhookEvent` and `confirmPaymentStatus` both publish `PaymentCompletedEvent` / `PaymentFailedEvent` for the same payment. If both paths fire (webhook received + user polls confirmPaymentStatus), duplicate events reach booking-service. The idempotency guard in `PaymentEventListener` catches this, but it is an avoidable upstream duplication. The `confirmPaymentStatus` path should only publish if `stripeEventId` is NOT yet set (i.e., webhook was not previously processed).

### M2 — `PaymentCompletedEvent.amount` type is `Long`, plan spec says `Double`

**File:** `kafka-events/.../domain/PaymentCompletedEvent.java:13`
**Plan:** `phase-01` spec says `PaymentCompletedEvent(Long bookingId, String paymentId, Double amount, String currency)`

`amount` is `Long` in implementation, `Double` in plan spec. The implementation is actually more correct for VND (integer currency, no decimals), and consistent with `PaymentServiceImpl` which uses `Long amount`. The plan spec should be updated to match. Minor inconsistency but documents a deliberate deviation.

### M3 — `EventEnvelope.timestamp` uses `LocalDateTime` instead of `Instant`

**File:** `kafka-events/.../envelope/EventEnvelope.java:18`

`LocalDateTime.now()` has no timezone context. When services run in different JVM timezones (Docker default is UTC, but this is implicit), timestamps are misleading for cross-service correlation. `Instant` is preferred for event timestamps. Low risk for a single-region deployment but worth noting.

### M4 — `MovieEventPublisher`: outer `try-catch` around async `kafkaTemplate.send()` is redundant

**File:** `movie-service/.../event/MovieEventPublisher.java:30-37`

```java
try {
    kafkaTemplate.send(...).whenComplete(...)
} catch (Exception e) { ... }
```

`kafkaTemplate.send()` is non-blocking and returns a `CompletableFuture`. Synchronous exceptions (e.g., serialization errors before send) are very rare and already handled by `.whenComplete`. The outer `try-catch` adds noise. `PaymentEventPublisher` correctly uses only `.whenComplete` with no outer try-catch—`MovieEventPublisher` should match that pattern.

---

## Low Priority Suggestions

### L1 — `KafkaTopics`: `BOOKING_EVENTS` constant removed per plan scope adjustment, but `BookingCreatedEvent.java` still exists in kafka-events module

`BookingCreatedEvent` has no publisher or consumer and no `BOOKING_EVENTS` topic constant. Per the plan's scope adjustment, `booking-events` was removed. The class is harmless but violates YAGNI. Remove or keep as documented dead code pending Phase 5/future use.

### L2 — `PaymentServiceImpl` uses `Collectors.toList()` (mutable) instead of `.toList()` (Java 16+ immutable)

**File:** `PaymentServiceImpl.java:224`
`MovieServiceImpl` already uses `.toList()` consistently. Align for uniformity.

### L3 — `PaymentEventListener`: `orElseThrow()` uses default exception (NoSuchElementException)

**File:** `PaymentEventListener.java:52, 62`

```java
Booking booking = bookingRepository.findById(event.bookingId()).orElseThrow();
```

A bare `orElseThrow()` throws `NoSuchElementException` with no message. Prefer `orElseThrow(() -> new IllegalStateException("Booking not found: " + event.bookingId()))` for better DLT diagnostics.

---

## Positive Observations

- EventEnvelope pattern applied consistently across all producers (payment-service and movie-service)
- `KafkaTopics` constants used everywhere—no raw string topic names in producers/listeners
- `PaymentEventPublisher` correctly uses `bookingId` as partition key for ordering guarantees
- DLT wiring via `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` is properly configured
- `spring.json.trusted.packages` restricted to `com.namnd.kafka.events.*`—no wildcard on `*`
- No sensitive data in any event payload (no tokens, passwords, full card numbers)
- All domain events use Java records with `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility
- `PaymentServiceImpl` webhook idempotency via `stripeEventId` is correct and independent of Kafka idempotency
- `BookingServiceImpl.cancelBooking` accepts `userId = null` for system-initiated cancels—clean design
- All files well under 200 LOC

---

## Recommended Actions

1. **[BLOCKING]** Verify `JsonDeserializer` target type config for `EventEnvelope` in booking-service consumer YAML or `KafkaConsumerConfig` — without this the listener will receive `LinkedHashMap` not `EventEnvelope` (H1)
2. **[HIGH]** Replace `ExponentialBackOff` + `setMaxElapsedTime` with `ExponentialBackOffWithMaxRetries(3)` in `KafkaConsumerConfig` (H2)
3. **[HIGH]** Add `@Transactional` to `confirmBooking` and `cancelBooking` in `BookingServiceImpl` (H3)
4. **[MEDIUM]** Guard `confirmPaymentStatus` event publishing: only publish if `stripeEventId` was null before this call (M1)
5. **[LOW]** Update plan spec to `Long amount` or add comment explaining deviation from `Double` (M2)
6. **[LOW]** Remove outer `try-catch` from `MovieEventPublisher` — use `.whenComplete` only, matching `PaymentEventPublisher` pattern (M4)
7. **[LOW]** Replace `Collectors.toList()` with `.toList()` in `PaymentServiceImpl` (L2)
8. **[LOW]** Replace bare `orElseThrow()` with a descriptive exception in `PaymentEventListener` (L3)

---

## Task Completeness (Plan Phases 1–4)

| Phase | Status | Notes |
|-------|--------|-------|
| Phase 1: Shared kafka-events module | **COMPLETE** | All 5 domain events + EventEnvelope + KafkaTopics created |
| Phase 2: Kafka configuration & infrastructure | **COMPLETE** | Config-server YAML updated, KafkaConsumerConfig wired (H2 fix needed) |
| Phase 3: Booking-Payment saga events | **COMPLETE** | PaymentEventPublisher + PaymentEventListener refactored, PaymentEventDto deleted |
| Phase 4: Movie service events | **COMPLETE** | MovieEventPublisher created, MovieServiceImpl publishes events |
| Phase 5: Auth service events | **DEFERRED** | Per plan scope adjustment |
| Phase 6: Testing & verification | **NOT STARTED** | No integration tests created, spring-kafka-test not added |

**Blocking for Phase 6:** H1 must be resolved first to ensure runtime correctness before writing integration tests.

---

## Metrics

- LOC per file: max ~239 (PaymentServiceImpl — slightly over 200 LOC limit, borderline)
- Type Coverage: Records are fully typed; raw `EventEnvelope` in listener is intentional (documented)
- Linting Issues: 0 syntax errors, 1 raw-type warning (H1)
- Integration Tests: 0 created (Phase 6 pending)

---

## Unresolved Questions

1. **H1 — deserialization target:** Is `spring.json.value.default.type: com.namnd.kafka.events.envelope.EventEnvelope` configured in booking-service YAML (not shown in review)? If not, the listener receives `LinkedHashMap` and `envelope.eventType()` throws `ClassCastException` at runtime.
2. **M1 — dual-path publishing:** Is the `confirmPaymentStatus` endpoint expected to be called after webhook delivery, or only as a fallback? If it's always called, duplicate events to booking-service are guaranteed and the idempotency guard is load-bearing rather than defensive.
