# Kafka Event-Driven Architecture Research

## Executive Summary
Project has mature event-driven infrastructure: Apache Kafka 3.7.0, EventEnvelope pattern, typed domain events, dead-letter queues, and exponential backoff error handling. Payment-service → booking-service flow exemplifies producer-consumer pattern. Notification-service can follow same architecture without major changes.

## 1. Kafka Topics & Event Registry

**File:** `/kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java`

- **PAYMENT_EVENTS**: "payment-events" topic (payment.completed, payment.failed events)
- **MOVIE_EVENTS**: "movie-events" topic (movie catalog lifecycle events)
- No existing notification-related topics defined
- Pattern: centralized topic constant registry prevents duplication

## 2. Event Envelope Pattern (Serialization)

**File:** `/kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java`

```java
EventEnvelope<T>(
  String eventId,        // UUID for idempotency
  String eventType,      // e.g. "payment.completed"
  String source,         // originating service name
  String correlationId,  // distributed tracing
  LocalDateTime timestamp,
  T payload
)
```

- Generic record-based wrapper with metadata
- Factory method: `of(source, eventType, correlationId, payload)`
- Handles type erasure via explicit eventType discriminator (needed for polymorphic deserialization)
- JsonIgnoreProperties for forward compatibility

## 3. Domain Events

**Location:** `/kafka-events/src/main/java/com/namnd/kafka/events/domain/`

Existing events (all records):
- PaymentCompletedEvent(bookingId, paymentId, amount, currency)
- PaymentFailedEvent(bookingId, reason)
- BookingCreatedEvent, MovieCreatedEvent, ShowtimeCreatedEvent

Pattern: Immutable records with minimal payload (only essential business data).

## 4. Producer Implementation (Payment-Service)

**File:** `/payment-service/.../PaymentEventPublisher.java`

- KafkaTemplate<String, Object> injected
- Key: bookingId.toString() (partition affinity for saga ordering)
- Async completion callbacks with error logging
- Methods: publishPaymentCompleted(), publishPaymentFailed()
- No transaction/outbox pattern yet

## 5. Consumer Implementation (Booking-Service)

**File:** `/booking-service/.../PaymentEventListener.java`

- @KafkaListener(topics=KafkaTopics.PAYMENT_EVENTS, groupId="booking-service")
- EventEnvelope received raw; ObjectMapper converts payload based on eventType
- Idempotency: checks booking terminal states before state transitions
- Exception handling: unhandled exceptions trigger DLT via DefaultErrorHandler

## 6. Error Handling & Resilience

**File:** `/payment-service/.../KafkaConsumerConfig.java`

- DefaultErrorHandler with DeadLetterPublishingRecoverer
- Exponential backoff: 3 retries (1s → 2s → 4s, max 10s)
- Non-retryable: SerializationException, MessageConversionException
- Dead-letter topics auto-created with "-dlt" suffix pattern

## 7. Kafka Broker Configuration

**File:** `/docker-compose.yml`

- Apache Kafka 3.7.0 (Kraft mode, no ZooKeeper)
- Port: 9092 (PLAINTEXT advertised as kafka:9092)
- Services depending on Kafka: movie-service, booking-service, payment-service
- Environment variable: KAFKA_HOST=kafka (for Spring config)

## 8. Service Integration Status

| Service | Role | Kafka Usage |
|---------|------|-------------|
| payment-service | Producer | Publishes payment.* events |
| booking-service | Consumer | Listens payment-events topic |
| movie-service | Producer | Publishes movie.* events |
| auth-service | None | Not connected to Kafka |
| notification-service | TBD | Not yet created |

## Key Architectural Decisions

1. **EventEnvelope for all messages** - consistent metadata across topics
2. **Typed events in kafka-events module** - single source of truth, reusable across services
3. **Partition key = business entity ID** - ensures message ordering per entity
4. **ObjectMapper-based polymorphism** - eventType discriminator routes payload deserialization
5. **Idempotent consumers** - terminal state checks prevent duplicate processing
6. **DLT for dead messages** - failed messages captured for analysis
7. **No outbox pattern yet** - risk of loss if publish fails after DB commit

## Implications for Notification-Service

✓ Reuse EventEnvelope, KafkaTopics constants, domain events structure
✓ Add notification events to kafka-events module (e.g., NotificationSentEvent, NotificationFailedEvent)
✓ Follow booking-service consumer pattern (@KafkaListener with eventType routing)
✓ Inherit KafkaConsumerConfig error handling
✓ Consider adding outbox pattern for critical notifications (booking.confirmed → notification.send)

## Unresolved Questions

1. **Outbox Pattern**: Should notification-service use outbox table to guarantee delivery before notification sent?
2. **Notification Events**: Which events should trigger notifications (payment.completed, booking.cancelled, etc.)?
3. **Email Service Integration**: Async email with failure retry via Kafka or synchronous with outbox?
4. **Topic Naming**: Should notification-related topics be "notification-events" or domain-specific (e.g., "booking-notifications")?
