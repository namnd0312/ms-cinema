# Phase 4: Payment Service — Stripe PaymentIntent & Webhooks

## Context Links
- [Parent Plan](./plan.md)
- [Phase 3: Booking Service](./phase-03-booking-service-seat-locking-and-reservations.md)
- [Stripe Research](./research/researcher-01-stripe-feign-report.md)

## Overview
- **Priority:** P1
- **Status:** Pending
- **Effort:** 3h
- **Description:** Stripe PaymentIntent integration with webhook-driven status updates. Creates payment intent on booking, receives async confirmation via Stripe webhook, notifies booking-service to confirm/fail booking.

## Key Insights
- Server creates PaymentIntent → returns clientSecret → frontend confirms via Stripe.js
- Webhook is the source of truth for payment status (not client callback)
- Webhook endpoint must be public (no JWT) but verifies Stripe signature
- Use raw request body for webhook (not @RequestBody with JSON parser — breaks signature)
- Idempotency keys per operation: `pay-{bookingId}` for creates, `refund-{bookingId}` for refunds
- Store Stripe PaymentIntent ID for refund operations

## Requirements

### Functional
- Create Stripe PaymentIntent for a booking
- Handle webhook events: payment_intent.succeeded, payment_intent.payment_failed
- On success: call booking-service to confirm booking
- On failure: call booking-service to cancel booking
- Refund support for cancelled bookings
- Payment history per user

### Non-functional
- Webhook idempotency (store processed event IDs)
- Fast webhook response (return 200 immediately, process async if needed)
- Secure: Stripe signature verification on all webhooks

## Architecture

### Package Structure
```
com.namnd.paymentservice/
├── PaymentServiceApplication.java     # @EnableFeignClients
├── config/
│   └── StripeConfig.java              # Set Stripe.apiKey on startup
├── client/
│   └── BookingServiceClient.java      # Feign → booking-service
├── controller/
│   ├── PaymentController.java         # /api/payments
│   └── StripeWebhookController.java   # /api/payments/webhook
├── dto/
│   ├── CreatePaymentIntentRequest.java  # {bookingId, amount, currency}
│   ├── PaymentIntentResponse.java       # {paymentId, clientSecret, status}
│   ├── PaymentHistoryResponse.java
│   └── RefundRequest.java               # {paymentId}
├── model/
│   ├── Payment.java
│   └── PaymentStatus.java              # enum
├── repository/
│   └── PaymentRepository.java
├── service/
│   ├── PaymentService.java            # interface
│   └── impl/
│       └── PaymentServiceImpl.java
└── exception/
    └── PaymentException.java
```

### Payment Flow
```
1. booking-service → POST /api/payments/create-intent {bookingId, amount, userId}
   → PaymentService creates Stripe PaymentIntent
   → Saves Payment (PENDING) in DB with stripe_payment_intent_id
   → Returns {clientSecret, paymentId}

2. Frontend confirms payment via Stripe.js (client-side, no server call)

3. Stripe → POST /api/payments/webhook (payment_intent.succeeded)
   → Verify signature
   → Update Payment status → COMPLETED
   → Feign: POST booking-service /api/bookings/{id}/confirm

4. Stripe → POST /api/payments/webhook (payment_intent.payment_failed)
   → Update Payment status → FAILED
   → Feign: POST booking-service /api/bookings/{id}/cancel
```

### Data Model
```sql
CREATE TABLE payments (
  id                       BIGSERIAL PRIMARY KEY,
  booking_id               BIGINT NOT NULL UNIQUE,
  user_id                  BIGINT NOT NULL,
  amount                   NUMERIC(10,2) NOT NULL,
  currency                 VARCHAR(3) NOT NULL DEFAULT 'USD',
  status                   VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  stripe_payment_intent_id VARCHAR(255),
  stripe_event_id          VARCHAR(255),       -- webhook idempotency
  paid_at                  TIMESTAMPTZ,
  created_at               TIMESTAMPTZ DEFAULT now(),
  CONSTRAINT chk_payment_status CHECK (status IN ('PENDING','COMPLETED','FAILED','REFUNDED'))
);

CREATE INDEX idx_payments_booking ON payments (booking_id);
CREATE INDEX idx_payments_user ON payments (user_id);
CREATE INDEX idx_payments_stripe ON payments (stripe_payment_intent_id);
```

## Related Code Files

### Files to Create
- All files in package structure above (~13 files)

## Implementation Steps

### 1. StripeConfig
```java
@Configuration
public class StripeConfig {
    @Value("${stripe.api-key}")
    private String apiKey;

    @PostConstruct
    public void init() {
        Stripe.apiKey = apiKey;
    }
}
```

### 2. Payment Entity
```java
@Entity @Table(name = "payments")
@Data
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long bookingId;     // unique
    private Long userId;
    private BigDecimal amount;
    private String currency;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
    private String stripePaymentIntentId;
    private String stripeEventId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
```

### 3. BookingServiceClient (Feign)
```java
@FeignClient(name = "booking-service")
public interface BookingServiceClient {
    @PostMapping("/api/bookings/{id}/confirm")
    void confirmBooking(@PathVariable("id") Long bookingId);

    @PostMapping("/api/bookings/{id}/cancel")
    void cancelBooking(@PathVariable("id") Long bookingId);
}
```

### 4. PaymentService Implementation

**createPaymentIntent(bookingId, amount, userId, currency):**
```java
PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
    .setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue()) // cents
    .setCurrency(currency)
    .setAutomaticPaymentMethods(
        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
            .setEnabled(true).build())
    .putMetadata("bookingId", bookingId.toString())
    .putMetadata("userId", userId.toString())
    .build();

RequestOptions opts = RequestOptions.builder()
    .setIdempotencyKey("pay-" + bookingId)
    .build();

PaymentIntent intent = PaymentIntent.create(params, opts);

// Save to DB
Payment payment = new Payment();
payment.setBookingId(bookingId);
payment.setUserId(userId);
payment.setAmount(amount);
payment.setCurrency(currency);
payment.setStatus(PaymentStatus.PENDING);
payment.setStripePaymentIntentId(intent.getId());
payment.setCreatedAt(LocalDateTime.now());
paymentRepository.save(payment);

return new PaymentIntentResponse(payment.getId(), intent.getClientSecret(), "PENDING");
```

**handleWebhookEvent(event):**
```java
String type = event.getType();
PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
    .getObject().orElseThrow();
String piId = intent.getId();

Payment payment = paymentRepository.findByStripePaymentIntentId(piId)
    .orElseThrow();

// Idempotency check
if (payment.getStripeEventId() != null) return; // already processed

payment.setStripeEventId(event.getId());

if ("payment_intent.succeeded".equals(type)) {
    payment.setStatus(PaymentStatus.COMPLETED);
    payment.setPaidAt(LocalDateTime.now());
    paymentRepository.save(payment);
    bookingServiceClient.confirmBooking(payment.getBookingId());
} else if ("payment_intent.payment_failed".equals(type)) {
    payment.setStatus(PaymentStatus.FAILED);
    paymentRepository.save(payment);
    bookingServiceClient.cancelBooking(payment.getBookingId());
}
```

**refund(paymentId):**
```java
Payment payment = paymentRepository.findById(paymentId).orElseThrow();
RefundCreateParams params = RefundCreateParams.builder()
    .setPaymentIntent(payment.getStripePaymentIntentId())
    .build();
RequestOptions opts = RequestOptions.builder()
    .setIdempotencyKey("refund-" + payment.getBookingId())
    .build();
Refund.create(params, opts);
payment.setStatus(PaymentStatus.REFUNDED);
paymentRepository.save(payment);
```

### 5. StripeWebhookController
```java
@RestController
@RequestMapping("/api/payments/webhook")
public class StripeWebhookController {
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<String> handleWebhook(
            HttpServletRequest request,
            @RequestHeader("Stripe-Signature") String sigHeader) throws IOException {

        String payload = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            return ResponseEntity.status(400).body("Invalid signature");
        }
        paymentService.handleWebhookEvent(event);
        return ResponseEntity.ok("received");
    }
}
```

### 6. PaymentController
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/payments/create-intent | Authenticated | Create Stripe PaymentIntent |
| GET | /api/payments/{id} | Authenticated | Get payment detail |
| GET | /api/payments/my | Authenticated | User's payment history |
| POST | /api/payments/{id}/refund | ROLE_ADMIN | Refund a payment |
| POST | /api/payments/webhook | Public | Stripe webhook (signature verified) |

### 7. application.yml additions
```yaml
stripe:
  api-key: ${STRIPE_API_KEY:sk_test_placeholder}
  webhook-secret: ${STRIPE_WEBHOOK_SECRET:whsec_placeholder}
```

### 8. Compile & Test
```bash
cd payment-service && mvn clean compile
```

## Todo List
- [ ] Create StripeConfig
- [ ] Create Payment entity + PaymentStatus enum
- [ ] Create PaymentRepository
- [ ] Create BookingServiceClient (Feign)
- [ ] Create DTOs (4)
- [ ] Create PaymentService interface + impl (create, webhook, refund)
- [ ] Create StripeWebhookController (public, signature verify)
- [ ] Create PaymentController (authenticated endpoints)
- [ ] Create exception classes
- [ ] Compile and verify

## Success Criteria
- Create PaymentIntent returns clientSecret
- Webhook correctly updates payment status PENDING → COMPLETED
- On payment success: booking-service booking status → CONFIRMED
- On payment failure: booking-service booking status → CANCELLED
- Refund works for completed payments
- Webhook rejects invalid signatures (returns 400)
- Duplicate webhook events are idempotent (no double processing)

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Stripe API key invalid | Payments fail | Validate on startup, clear error message |
| Webhook not reachable (Docker) | Payments stuck PENDING | Use Stripe CLI for local testing |
| booking-service down when webhook fires | Booking not confirmed | Log error, manual retry or scheduled reconciliation |
| Stripe SDK version mismatch | Compile errors | Pin version in pom.xml |

## Security Considerations
- Stripe API key stored as env variable, never in code
- Webhook secret for signature verification
- Webhook endpoint public but cryptographically verified
- Amount comes from booking-service (server-side), not from client
- Refund restricted to ROLE_ADMIN

## Next Steps
- Phase 5: End-to-end integration testing across all services
