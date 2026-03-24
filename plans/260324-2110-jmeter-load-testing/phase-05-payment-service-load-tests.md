# Phase 5: Payment Service Load Tests

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: [Phase 4](./phase-04-booking-service-load-tests.md) (bookings needed for payments)

## Overview
- **Priority**: P1
- **Status**: pending
- **Description**: Load test payment creation, confirmation, and webhook processing using the fake-success endpoint

## Key Insights
- **POST /api/payments/fake-success** exists for testing — bypasses Stripe entirely
- Real Stripe has rate limits (~100 req/s) — use fake-success for load tests
- Idempotency key: `pay-{bookingId}` prevents duplicate payments
- PaymentCompleted/Failed events published to Kafka → booking-service confirms/cancels
- TransactionalEventListener ensures Kafka publish after DB commit

## Requirements

### Functional
- 500 concurrent payment creations
- Verify idempotency (same bookingId → same payment, not duplicate)
- Test payment status queries under load
- Test user payment history endpoint

### Non-Functional
- Payment creation p95 < 1s
- Zero duplicate payments for same booking
- Kafka event delivery within 5s of payment completion

## Implementation Steps

### Test Plan: `payment-load-test.jmx`

1. **Setup: Create Bookings First**
   - Login 500 users → reserve seats → extract bookingIds
   - Store bookingIds in JMeter variables

2. **Thread Group: Payment Creation**
   - Threads: 500, Ramp-up: 120s

   **Requests:**
   - POST /api/payments/fake-success
     ```json
     {
       "bookingId": ${BOOKING_ID}
     }
     ```
   - JSON Extractor: `$.id` → `PAYMENT_ID`
   - Response Assertion: HTTP 200

3. **Thread Group: Idempotency Test**
   - Threads: 100
   - Same bookingId submitted 5 times each
   - Verify same paymentId returned (not new payment)

4. **Thread Group: Query Load**
   - Threads: 300, Ramp-up: 60s
   - GET /api/payments/${PAYMENT_ID}
   - GET /api/payments/my (user payment history)

5. **Kafka Event Verification**
   - After payment, check booking status changed to CONFIRMED
   - GET /api/bookings/${BOOKING_ID} → assert status=CONFIRMED
   - Add Timer: 5s wait for async Kafka processing

## Todo
- [ ] Create `payment-load-test.jmx`
- [ ] Setup bookings before payment tests
- [ ] Test fake-success endpoint under load
- [ ] Add idempotency verification
- [ ] Add Kafka async event verification (booking status check)
- [ ] Add payment history query load test

## Success Criteria
- 500 concurrent payments with <1% error rate
- Zero duplicate payments (idempotency works)
- Booking status changes to CONFIRMED within 5s of payment
- Payment history query p95 < 500ms

## Risk Assessment
- **Risk**: Kafka consumer lag under high payment volume
  - **Mitigation**: Monitor Kafka consumer group lag via Kafdrop (:9000)
- **Risk**: DB connection pool exhaustion (payments + events + booking updates)
  - **Mitigation**: Monitor HikariCP pool metrics via Prometheus

## Next Steps
- Phase 6: Full User Journey E2E
