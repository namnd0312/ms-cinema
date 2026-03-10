# Phase 3: Booking-Payment Saga Events

## Context
- [Plan Overview](./plan.md)
- [Phase 1: Shared Events Module](./phase-01-shared-kafka-events-module.md)
- [Phase 2: Kafka Config](./phase-02-kafka-configuration-infrastructure.md)
- Current flow: booking REST -> payment REST -> Stripe -> webhook -> `PaymentEventPublisher` (manual JSON) -> Kafka `payment-events` -> `PaymentEventListener` (manual deser, no idempotency)

## Overview
- **Priority:** P1 (highest -- fixes fragile existing implementation)
- **Status:** Pending
- **Effort:** 2h
- **Description:** Refactor existing payment->booking Kafka flow to use shared DTOs, EventEnvelope, JsonSerializer, idempotent consumption, and DLT. Optionally add booking->payment event publishing.

## Key Insights
- Existing `PaymentEventListener` swallows all exceptions in catch block -- errors silently lost, never reach DLT
- `PaymentEventPublisher` uses manual `ObjectMapper.writeValueAsString` -- replaced by Spring Kafka JsonSerializer
- Idempotency achievable via `BookingStatus` check: if booking already CONFIRMED/CANCELLED, skip processing (no separate table needed)
- booking-service does NOT need to publish `BookingCreatedEvent` to Kafka yet -- payment is initiated via REST call from frontend, not event-driven. Adding it now would be YAGNI.

## Requirements

### Functional
- Payment-service publishes `EventEnvelope<PaymentCompletedEvent>` and `EventEnvelope<PaymentFailedEvent>` to `payment-events`
- Booking-service consumes `EventEnvelope` from `payment-events` with type-safe deserialization
- Idempotent consumption: skip if booking already in terminal state (CONFIRMED/CANCELLED)
- Failed messages route to `payment-events.DLT` after 3 retries
- Remove duplicated `PaymentEventDto` from booking-service

### Non-Functional
- No silent exception swallowing -- let exceptions propagate to DLT handler
- Structured logging with bookingId for each event processed

## Architecture

### Updated Flow
```
Stripe Webhook
    |
    v
PaymentService.handleWebhook()
    |
    v
PaymentEventPublisher.publishPaymentCompleted(bookingId, paymentId, amount, currency)
    |  publishes EventEnvelope<PaymentCompletedEvent> to "payment-events"
    v
Kafka "payment-events"
    |
    v
PaymentEventListener.handlePaymentEvent(EventEnvelope<?> envelope)
    |  checks booking status (idempotent)
    |  confirms or cancels booking
    v
BookingService.confirmBooking() / cancelBooking()
```

### Message Format (after refactor)
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "payment.completed",
  "source": "payment-service",
  "correlationId": "booking-123",
  "timestamp": "2026-03-07T10:30:00",
  "payload": {
    "bookingId": 123,
    "paymentId": "pi_abc123",
    "amount": 250.00,
    "currency": "USD"
  }
}
```

## Related Code Files

### Modify
- `payment-service/.../event/PaymentEventPublisher.java` -- use EventEnvelope + KafkaTemplate<String, Object> + shared events
- `booking-service/.../listener/PaymentEventListener.java` -- use EventEnvelope deserialization, add idempotency, remove try-catch swallowing

### Delete
- `booking-service/.../dto/PaymentEventDto.java` -- replaced by shared `PaymentCompletedEvent` / `PaymentFailedEvent`

### May Need to Modify
- `booking-service/.../service/BookingService.java` (interface) -- verify `confirmBooking`/`cancelBooking` return booking status for idempotency check
- `payment-service/.../service/*` -- verify where `publishPaymentResult` is called, update method signatures

## Implementation Steps

1. **Refactor PaymentEventPublisher** (payment-service):
   - Change `KafkaTemplate<String, String>` to `KafkaTemplate<String, Object>`
   - Remove `ObjectMapper` dependency
   - Replace `publishPaymentResult(Long bookingId, String status)` with two methods:
     - `publishPaymentCompleted(Long bookingId, String paymentId, Double amount, String currency)`
     - `publishPaymentFailed(Long bookingId, String reason)`
   - Each wraps payload in `EventEnvelope.of("payment-service", "payment.completed", bookingId.toString(), payload)`
   - Use `KafkaTopics.PAYMENT_EVENTS` constant
   - Add `.whenComplete()` callback for logging

2. **Update all callers** of `publishPaymentResult`:
   - Find usages in payment-service (likely in PaymentService impl or webhook handler)
   - Replace with `publishPaymentCompleted(...)` or `publishPaymentFailed(...)` as appropriate

3. **Refactor PaymentEventListener** (booking-service):
   - Change method signature: `handlePaymentEvent(ConsumerRecord<String, EventEnvelope> record)`
   - Or use `@Payload EventEnvelope envelope` with `@Header` for metadata
   - Extract event type from envelope, cast payload accordingly
   - **Add idempotency**: before processing, check current booking status:
     ```java
     Booking booking = bookingRepository.findById(bookingId);
     if (booking.getStatus() == CONFIRMED || booking.getStatus() == CANCELLED) {
         log.info("Booking {} already in terminal state, skipping", bookingId);
         return;
     }
     ```
   - **Remove try-catch swallowing** -- let exceptions propagate to DLT error handler
   - Keep business logic: COMPLETED -> confirmBooking, FAILED -> cancelBooking

4. **Delete `PaymentEventDto.java`** from booking-service

5. **Handle EventEnvelope deserialization**: Since `EventEnvelope<T>` is generic, the JsonDeserializer needs type info. Options:
   - **Option A (recommended):** Deserialize as `EventEnvelope` (raw) and use `ObjectMapper.convertValue(envelope.payload(), PaymentCompletedEvent.class)` for payload -- simple, no Spring Kafka type headers needed
   - **Option B:** Add `spring.kafka.producer.properties.spring.json.add.type.headers=true` and configure consumer type mapping -- more complex

6. **Verify** Stripe webhook handler and fake-success endpoint still call updated publisher methods

7. Run `mvn compile -pl payment-service,booking-service`

## Todo List
- [ ] Refactor PaymentEventPublisher to use EventEnvelope + shared events
- [ ] Update all callers of publishPaymentResult
- [ ] Refactor PaymentEventListener with idempotency + remove exception swallowing
- [ ] Delete PaymentEventDto.java
- [ ] Handle generic EventEnvelope deserialization
- [ ] Verify webhook/fake-success flows
- [ ] Compile and test

## Success Criteria
- Payment events published as structured EventEnvelope JSON
- Booking-service correctly confirms/cancels bookings from Kafka events
- Duplicate events are safely ignored (idempotent)
- Failed messages appear in `payment-events.DLT` after 3 retries
- No manual ObjectMapper usage in publisher/listener

## Risk Assessment
- **Generic type erasure:** `EventEnvelope<T>` payload deserializes as `LinkedHashMap` at runtime. Mitigated by Option A: manual `ObjectMapper.convertValue` for payload field
- **Breaking change during refactor:** Both publisher and consumer must be deployed together. Mitigated by coordinating changes in same PR
- **Missing caller updates:** Use IDE "Find Usages" on `publishPaymentResult` to catch all call sites

## Security Considerations
- Payment amounts in events are informational only (source of truth is Stripe + payment DB)
- No credit card data in events, only Stripe payment intent IDs

## Next Steps
- Phase 4: Add movie-service event publishing
- Phase 6: Integration test for full booking->payment->confirmation flow
