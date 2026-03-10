# Phase 01: Add Fake Payment Success Endpoint

## Context Links
- [Parent Plan](./plan.md)
- [PaymentService interface](../../payment-service/src/main/java/com/namnd/paymentservice/service/PaymentService.java)
- [PaymentServiceImpl](../../payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java)
- [PaymentController](../../payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java)

## Overview
- **Priority:** P2
- **Status:** Complete
- **Description:** Add `fakePaymentSuccess()` to PaymentService and expose via `POST /api/payments/fake-success` endpoint. Skips Stripe entirely, saves COMPLETED payment, publishes Kafka event.

## Key Insights
- Existing `CreatePaymentIntentRequest` record already has `bookingId` + `amount` — reuse it
- `PaymentIntentResponse(paymentId, clientSecret, status)` — pass `null` for clientSecret in fake flow
- `PaymentEventPublisher.publishPaymentResult(bookingId, status)` handles Kafka publish
- `Payment` entity has `stripePaymentIntentId` — use `"FAKE-{bookingId}-{timestamp}"` to distinguish
- `paymentInitiatedCounter` and `paymentCompletedCounter` should both increment for metrics consistency

## Requirements
### Functional
- Endpoint accepts JWT-authenticated request with `{bookingId, amount}`
- Creates Payment with status=COMPLETED, paidAt=now
- Publishes Kafka "COMPLETED" event for booking-service
- Returns PaymentIntentResponse with null clientSecret

### Non-functional
- No Stripe SDK calls
- Transactional DB write

## Architecture
```
Client (JWT) → POST /api/payments/fake-success
  → PaymentController.fakeSuccess()
    → PaymentServiceImpl.fakePaymentSuccess(bookingId, amount, userId)
      → save Payment(status=COMPLETED, stripePaymentIntentId="FAKE-...")
      → PaymentEventPublisher.publishPaymentResult(bookingId, "COMPLETED")
    → return PaymentIntentResponse(id, null, "COMPLETED")
```

## Related Code Files
| Action | File |
|--------|------|
| Modify | `payment-service/src/main/java/com/namnd/paymentservice/service/PaymentService.java` |
| Modify | `payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java` |
| Modify | `payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java` |

## Implementation Steps

### Step 1: Add interface method to PaymentService.java
Add after `createPaymentIntent`:
```java
PaymentIntentResponse fakePaymentSuccess(Long bookingId, Long amount, Long userId);
```

### Step 2: Add implementation to PaymentServiceImpl.java
Add method:
```java
@Override
@Transactional
public PaymentIntentResponse fakePaymentSuccess(Long bookingId, Long amount, Long userId) {
    Payment payment = new Payment();
    payment.setBookingId(bookingId);
    payment.setUserId(userId);
    payment.setAmount(amount);
    payment.setCurrency("VND");
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setStripePaymentIntentId("FAKE-" + bookingId + "-" + System.currentTimeMillis());
    payment.setPaidAt(LocalDateTime.now());
    paymentRepository.save(payment);
    paymentInitiatedCounter.increment();
    paymentCompletedCounter.increment();

    paymentEventPublisher.publishPaymentResult(bookingId, "COMPLETED");

    return new PaymentIntentResponse(payment.getId(), null, "COMPLETED");
}
```

### Step 3: Add endpoint to PaymentController.java
Add after `createIntent`:
```java
@Operation(summary = "Fake a successful payment for testing (bypasses Stripe)")
@PostMapping("/fake-success")
public ResponseEntity<PaymentIntentResponse> fakeSuccess(
        @Valid @RequestBody CreatePaymentIntentRequest request) {
    JwtAuthenticatedUser user = currentUser();
    PaymentIntentResponse response = paymentService.fakePaymentSuccess(
            request.bookingId(), request.amount(), user.userId());
    return ResponseEntity.ok(response);
}
```

## Todo List
- [x] Add `fakePaymentSuccess` to `PaymentService` interface
- [x] Implement `fakePaymentSuccess` in `PaymentServiceImpl`
- [x] Add `POST /fake-success` endpoint to `PaymentController`
- [x] Compile check: `mvn compile -pl payment-service`
- [ ] Test via curl/Postman with valid JWT + bookingId

## Success Criteria
- `POST /api/payments/fake-success` returns 200 with `{paymentId, null, "COMPLETED"}`
- Payment row in DB with status=COMPLETED, stripePaymentIntentId starting with "FAKE-"
- Kafka message published to `payment-events` topic
- booking-service logs "Booking {id} confirmed via payment event"

## Risk Assessment
- **Low:** Fake endpoint exposed in production — mitigate by adding `@Profile("dev")` or `@PreAuthorize("hasRole('ADMIN')")` if needed later
- **Low:** Duplicate bookingId — existing unique constraint on `booking_id` column will prevent duplicates

## Security Considerations
- Endpoint requires JWT authentication (same as other payment endpoints)
- Consider restricting to ADMIN role or dev profile in production

## Next Steps
- Test full booking → fake-payment → confirm flow end-to-end
- Optionally add `@Profile("dev")` guard before deploying to production
