# Phase 3: Booking-Service — Publish InAppNotificationEvent After Payment

## Context Links
- [PaymentEventListener.java](../../booking-service/src/main/java/com/namnd/bookingservice/listener/PaymentEventListener.java)
- [BookingServiceImpl.java](../../booking-service/src/main/java/com/namnd/bookingservice/service/impl/BookingServiceImpl.java)
- [BookingCreatedEvent.java](../../kafka-events/src/main/java/com/namnd/kafka/events/domain/BookingCreatedEvent.java)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 45m

Modify booking-service to publish `InAppNotificationEvent` to `notification-events` Kafka topic after processing payment completed/failed events. Booking-service is the right place because it has userId context (from the booking entity) and knows the payment outcome.

## Key Insights
- booking-service already consumes `PaymentCompletedEvent` and `PaymentFailedEvent` in `PaymentEventListener`
- Booking entity has `userId` — critical for targeted SSE delivery
- booking-service currently has NO KafkaTemplate/producer — only `KafkaConsumerConfig`
- Must add KafkaTemplate producer config + kafka-events dependency already present
- Publish AFTER confirm/cancel succeeds to ensure consistency
- Use `EventEnvelope.of()` factory with eventType `"notification.in_app"`

## Requirements

### Functional
- After `confirmBooking()` succeeds → publish InAppNotificationEvent with PAYMENT_SUCCESS
- After `cancelBooking()` due to payment failure → publish InAppNotificationEvent with PAYMENT_FAILED
- Event payload includes userId, descriptive title, message with booking/payment details

### Non-functional
- Non-blocking: notification publish failure must NOT roll back booking confirmation
- Use try-catch around Kafka publish to log errors without propagating

## Architecture

```
PaymentEventListener (existing)
  ├─ payment.completed → confirmBooking() → publish InAppNotificationEvent(PAYMENT_SUCCESS)
  └─ payment.failed → cancelBooking() → publish InAppNotificationEvent(PAYMENT_FAILED)
                                              ↓
                                   notification-events topic
                                              ↓
                                   notification-service consumes
```

## Related Code Files

### Files to Create
1. `booking-service/src/main/java/com/namnd/bookingservice/config/KafkaProducerConfig.java` — KafkaTemplate bean
2. `booking-service/src/main/java/com/namnd/bookingservice/service/NotificationPublisherService.java` — encapsulate publish logic

### Files to Modify
1. `booking-service/src/main/java/com/namnd/bookingservice/listener/PaymentEventListener.java` — inject NotificationPublisherService, call after confirm/cancel

### Files Unchanged
- `BookingServiceImpl.java` — no changes needed
- `BookingService.java` interface — unchanged

## Implementation Steps

### Step 1: Create KafkaProducerConfig

Create `booking-service/src/main/java/com/namnd/bookingservice/config/KafkaProducerConfig.java`:
```java
@Configuration
public class KafkaProducerConfig {
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }
}
```

### Step 2: Create NotificationPublisherService

Create `booking-service/src/main/java/com/namnd/bookingservice/service/NotificationPublisherService.java`:
```java
@Service
public class NotificationPublisherService {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Constructor injection

    public void notifyPaymentSuccess(Long userId, Long bookingId, String paymentId, Long amount) {
        var event = new InAppNotificationEvent(
            userId,
            "Payment Successful",
            String.format("Your payment of %d for booking #%d has been confirmed.", amount, bookingId),
            NotificationType.PAYMENT_SUCCESS
        );
        publishInAppNotification(event);
    }

    public void notifyPaymentFailed(Long userId, Long bookingId, String reason) {
        var event = new InAppNotificationEvent(
            userId,
            "Payment Failed",
            String.format("Payment for booking #%d failed: %s", bookingId, reason),
            NotificationType.PAYMENT_FAILED
        );
        publishInAppNotification(event);
    }

    private void publishInAppNotification(InAppNotificationEvent event) {
        try {
            var envelope = EventEnvelope.of(
                "booking-service", "notification.in_app", null, event);
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS, envelope);
            log.info("Published in-app notification for userId={}", event.userId());
        } catch (Exception e) {
            log.error("Failed to publish in-app notification: {}", e.getMessage(), e);
            // Do NOT rethrow — notification failure must not affect booking flow
        }
    }
}
```

### Step 3: Modify PaymentEventListener

Add `NotificationPublisherService` dependency. After each successful confirm/cancel, call the appropriate notify method:

```java
// In payment.completed handler, after confirmBooking():
notificationPublisher.notifyPaymentSuccess(
    booking.getUserId(), event.bookingId(), event.paymentId(), event.amount());

// In payment.failed handler, after cancelBooking():
notificationPublisher.notifyPaymentFailed(
    booking.getUserId(), event.bookingId(), event.reason());
```

Note: `booking` entity is already fetched in the listener for idempotency check — reuse it to get `userId`.

### Step 4: Verify booking-service has kafka producer dependencies

Check `booking-service/pom.xml` — `spring-kafka` already present (used for consumer). `JsonSerializer` comes from same dependency. No new deps needed.

### Step 5: Compile and verify

```bash
mvn clean compile -pl booking-service
```

## Todo List
- [ ] Create KafkaProducerConfig in booking-service
- [ ] Create NotificationPublisherService
- [ ] Modify PaymentEventListener to publish after confirm/cancel
- [ ] Verify compilation

## Success Criteria
- After payment.completed → InAppNotificationEvent(PAYMENT_SUCCESS) published to notification-events
- After payment.failed → InAppNotificationEvent(PAYMENT_FAILED) published to notification-events
- Notification publish failure does NOT block booking confirmation/cancellation
- PaymentEventListener stays under 200 LOC

## Risk Assessment
- **Kafka producer failure**: Try-catch ensures booking flow unaffected; log error for monitoring
- **Missing userId**: Booking entity always has userId (set during reserve) — no risk
- **Duplicate notifications**: notification-service dedup handles this via eventId in envelope

## Security Considerations
- No sensitive data in notification messages (no credit card info, no PII beyond bookingId)
- Amount shown in generic format (cents as integer)

## Next Steps
- Phase 4: API Gateway routes notification-service endpoints
- Phase 2 must be done first so notification-service can consume these events
