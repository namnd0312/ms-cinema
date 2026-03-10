# Phase 3: Booking & Payment Flows

## Context Links
- [BookingController.java](../../booking-service/src/main/java/com/namnd/bookingservice/controller/BookingController.java) (85 lines)
- [BookingServiceImpl.java](../../booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingServiceImpl.java) (164 lines)
- [SeatLockServiceImpl.java](../../booking-service/src/main/java/com/namnd/bookingservice/service/impl/SeatLockServiceImpl.java) (63 lines)
- [PaymentEventListener.java](../../booking-service/src/main/java/com/namnd/bookingservice/listener/PaymentEventListener.java) (74 lines)
- [BookingExpiryScheduler.java](../../booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingExpiryScheduler.java) (45 lines)
- [PaymentController.java](../../payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java) (89 lines)
- [PaymentServiceImpl.java](../../payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java) (239 lines)
- [StripeWebhookController.java](../../payment-service/src/main/java/com/namnd/paymentservice/controller/StripeWebhookController.java) (69 lines)
- [PaymentEventPublisher.java](../../payment-service/src/main/java/com/namnd/paymentservice/event/PaymentEventPublisher.java) (60 lines)
- [MovieServiceClient.java](../../booking-service/src/main/java/com/namnd/bookingservice/client/MovieServiceClient.java) (Feign)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Mermaid sequence diagrams for the complete booking lifecycle: seat reservation with Redis locking, payment intent creation (Stripe), webhook processing, Kafka event-driven confirmation/cancellation, and booking expiry scheduler.

## Key Insights from Code

### Seat Reservation (BookingServiceImpl.reserve)
1. Feign call to movie-service: getShowtime(showtimeId), getSeatsForShowtime(showtimeId)
2. SeatLockService.lockSeats() — Redis SETNX with 300s TTL, sorted to prevent deadlocks, rollback on partial failure
3. Calculate totalAmount from basePrice * seatType.priceMultiplier
4. Save Booking(PENDING, expiresAt=now+300s) with BookingSeats to PostgreSQL
5. Return BookingResponseDto

### Payment Intent (PaymentServiceImpl.createPaymentIntent)
1. Create Stripe PaymentIntent with idempotency key "pay-{bookingId}"
2. Save Payment(PENDING, stripePaymentIntentId) to PostgreSQL
3. Return clientSecret to frontend

### Fake Payment (PaymentServiceImpl.fakePaymentSuccess)
1. Save Payment(COMPLETED) directly
2. Publish PaymentCompletedEvent to Kafka topic "payment-events"

### Stripe Webhook (PaymentServiceImpl.handleWebhookEvent)
1. StripeWebhookController verifies Stripe-Signature header
2. Dedup: skip if payment.stripeEventId already set
3. payment_intent.succeeded -> COMPLETED -> publishPaymentCompleted()
4. payment_intent.payment_failed -> FAILED -> publishPaymentFailed()

### Kafka: Payment -> Booking (PaymentEventListener)
1. Consume from "payment-events" topic, group "booking-service"
2. EventEnvelope discriminator: "payment.completed" -> confirmBooking(), "payment.failed" -> cancelBooking(null)
3. Idempotency: skip if booking already in terminal state (CONFIRMED/CANCELLED)
4. confirmBooking(): set CONFIRMED, release Redis seat locks
5. cancelBooking(): set CANCELLED, release Redis seat locks

### Booking Expiry (BookingExpiryScheduler)
1. @Scheduled(fixedRate=60000) — every 60 seconds
2. Find PENDING bookings where expiresAt < now
3. Set status EXPIRED, release Redis seat locks

### Confirm Payment Status (PaymentServiceImpl.confirmPaymentStatus)
1. If already COMPLETED/FAILED, return as-is
2. Otherwise call PaymentIntent.retrieve() from Stripe API
3. Update status + publish Kafka event if terminal

## Diagrams to Create (5 total)

### 1. Seat Reservation Flow
Participants: Client, API Gateway, JwtAuthenticationFilter, BookingController, BookingServiceImpl, MovieServiceClient, movie-service, SeatLockServiceImpl, BookingRepository, Redis, PostgreSQL
- POST /api/bookings/reserve
- Include: Feign call to movie-service, Redis SETNX lock, rollback on partial failure

### 2. Payment Intent Creation Flow
Participants: Client, API Gateway, JwtAuthenticationFilter, PaymentController, PaymentServiceImpl, Stripe API, PaymentRepository, PostgreSQL
- POST /api/payments/create-intent
- Include: Stripe idempotency key, clientSecret return

### 3. Stripe Webhook -> Kafka -> Booking Confirmation Flow (end-to-end)
Participants: Stripe, StripeWebhookController, PaymentServiceImpl, PaymentRepository, PaymentEventPublisher, Kafka, PaymentEventListener, BookingServiceImpl, BookingRepository, SeatLockServiceImpl, Redis, PostgreSQL
- POST /api/payments/webhook
- Include: signature verification, dedup check, Kafka publish, consumer confirm/cancel, seat lock release

### 4. Fake Payment -> Kafka -> Booking Confirmation Flow
Participants: Client, API Gateway, PaymentController, PaymentServiceImpl, PaymentEventPublisher, Kafka, PaymentEventListener, BookingServiceImpl, BookingRepository, SeatLockServiceImpl, Redis, PostgreSQL
- POST /api/payments/fake-success
- Simpler variant: no Stripe, direct COMPLETED + Kafka publish

### 5. Booking Expiry Scheduler Flow
Participants: BookingExpiryScheduler, BookingRepository, SeatLockServiceImpl, Redis, PostgreSQL
- @Scheduled every 60s
- Include: query stale PENDING bookings, set EXPIRED, unlock seats

## Source Files to Reference
- `booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingServiceImpl.java`
- `booking-service/src/main/java/com/namnd/bookingservice/service/impl/SeatLockServiceImpl.java`
- `booking-service/src/main/java/com/namnd/bookingservice/listener/PaymentEventListener.java`
- `booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingExpiryScheduler.java`
- `booking-service/src/main/java/com/namnd/bookingservice/client/MovieServiceClient.java`
- `payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java`
- `payment-service/src/main/java/com/namnd/paymentservice/controller/StripeWebhookController.java`
- `payment-service/src/main/java/com/namnd/paymentservice/event/PaymentEventPublisher.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/PaymentCompletedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/PaymentFailedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java`

## Todo
- [ ] Seat reservation sequence diagram (with Feign + Redis lock)
- [ ] Payment intent creation sequence diagram (with Stripe)
- [ ] Stripe webhook -> Kafka -> booking confirmation end-to-end diagram
- [ ] Fake payment -> Kafka -> booking confirmation diagram
- [ ] Booking expiry scheduler diagram
- [ ] Verify Kafka topic names match KafkaTopics.java constants
- [ ] Verify EventEnvelope fields match record definition

## Success Criteria
- All 5 flows have sequence diagrams
- Kafka message flow clearly shows: producer -> topic -> consumer
- Redis seat lock/unlock operations visible
- Stripe webhook signature verification shown
- Idempotency checks shown (Stripe dedup, booking terminal state skip)
- EventEnvelope structure (eventId, eventType, source, correlationId, timestamp, payload) documented
