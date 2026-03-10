# Event-Driven Architecture Patterns for Movie Ticket Booking System

**Date:** March 7, 2026 | **Tech Stack:** Spring Boot 3.4.3, Java 21, PostgreSQL, Redis, Apache Kafka

---

## 1. Domain Events & Saga Pattern for Booking Flow

**Booking Saga (Choreography Pattern):**
```
Booking Service    Payment Service    Booking Service
      |                  |                   |
      |--BookingCreated-->|                   |
      |                   |--PaymentProcessed-|
      |                   |                   |
      |<---BookingConfirmed or BookingCancelled---
```

**Key Events:**
- `booking.events.v1.booking-created` - Initial booking request
- `payment.events.v1.payment-processed` - Payment success/failure signal
- `booking.events.v1.booking-confirmed` - Final booking confirmation
- `booking.events.v1.booking-cancelled` - Compensation for failed payment

**Event Payload Structure (DDD Aggregates):**
```json
{
  "bookingId": "uuid",
  "userId": "uuid",
  "movieId": "uuid",
  "showId": "uuid",
  "seatIds": ["A1", "A2"],
  "totalAmount": 250.00,
  "currency": "USD",
  "timestamp": "2026-03-07T10:30:00Z",
  "source": "booking-service"
}
```

---

## 2. Event Envelope Pattern

**Standardized Wrapper** (CloudEvents compatible):
```json
{
  "eventId": "evt_550e8400e29b41d4a716446655440000",
  "eventType": "booking.created",
  "eventVersion": "1.0",
  "timestamp": "2026-03-07T10:30:00Z",
  "source": "booking-service",
  "correlationId": "corr_123xyz",
  "causationId": "evt_previous_event_id",
  "aggregateId": "booking_abc123",
  "aggregateType": "Booking",
  "payload": { /* domain event data */ },
  "metadata": {
    "userId": "user_123",
    "traceId": "trace_xyz",
    "environment": "production"
  }
}
```

**Benefits:** Decouples events, enables tracing/correlation, supports versioning, ensures consistent metadata across services.

---

## 3. Shared Event Library Module (Multi-Module Project)

**Project Structure:**
```
jwt-auth-spring-boot-starter/
  └── src/main/java/com/namnd/springjwt/events/
      ├── domain/
      │   ├── DomainEvent.java (abstract base)
      │   ├── BookingCreatedEvent.java
      │   ├── PaymentProcessedEvent.java
      │   └── BookingConfirmedEvent.java
      ├── envelope/
      │   ├── EventEnvelope.java
      │   └── EventMetadata.java
      └── publisher/
          └── DomainEventPublisher.java (interface)
```

**Base Event Class:**
```java
public abstract class DomainEvent {
    private String eventId = UUID.randomUUID().toString();
    private LocalDateTime occurredAt = LocalDateTime.now();
    private String aggregateId;
    private String aggregateType;

    public abstract String getEventType();
}
```

**Maven Dependency Management:** Use `jwt-auth-spring-boot-starter` for shared event definitions, inherited by all services (auth, movie, booking, payment).

---

## 4. Transactional Outbox Pattern (Reliability)

**Solves "Dual-Write" Problem:**

Instead of: `UPDATE database` + `WRITE to Kafka` (2 async operations, risk of inconsistency)

Use:
```sql
-- Single transactional operation
BEGIN TRANSACTION;
  UPDATE bookings SET status='PENDING' WHERE id='booking_123';
  INSERT INTO outbox (aggregate_id, event_type, payload, created_at)
    VALUES ('booking_123', 'booking.created', '{...}', NOW());
COMMIT;

-- Separate polling/CDC process sends outbox events to Kafka
SELECT * FROM outbox WHERE sent=false ORDER BY created_at LIMIT 100;
```

**Implementation Options:**
1. **Polling Publisher** (Simple): Spring scheduled task reads outbox every N seconds, publishes to Kafka, marks as sent
2. **CDC with Debezium** (Robust): Captures PostgreSQL WAL, automatically publishes to Kafka

**Benefit:** Guarantees: "If database commit succeeds, Kafka publish will eventually succeed."

---

## 5. Topic Naming Conventions

**Format:** `{domain}.{event-type}.{version}` or `{service}-{entity}-{action}`

**Booking System Topics:**
```
booking.events.v1.booking-created
booking.events.v1.booking-confirmed
booking.events.v1.booking-cancelled
payment.events.v1.payment-processed
payment.events.v1.payment-failed
movie.events.v1.show-updated
```

**Rules:**
- Use snake_case (not camelCase)
- Avoid service names (they change); use domain + event type instead
- Include version for schema evolution (v1, v2)
- Max 249 characters
- Immutable: don't change once created

---

## 6. Kafka Consumer Error Handling & Retry Strategy

**Spring Boot Implementation (@RetryableTopic):**
```java
@KafkaListener(topics = "booking.events.v1.booking-created")
@RetryableTopic(
    attempts = "3",
    delay = "1000",
    multiplier = "2.0",
    maxDelay = "10000",
    retryTopicSuffix = "-retry",
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopic = "booking.events.v1.booking-created-dlt"
)
public void handleBookingCreated(BookingCreatedEvent event) {
    // Process event, throw exception to trigger retry
}
```

**Flow:**
```
booking-created (fail)
    ↓
booking-created-retry-1 (1000ms delay)
    ↓
booking-created-retry-2 (2000ms delay)
    ↓
booking-created-retry-3 (4000ms delay, max 10s)
    ↓
booking-created-dlt (Dead Letter Topic for manual review)
```

**Best Practices:**
- Classify exceptions: retriable (network, timeout) vs non-retriable (validation, business logic)
- Preserve original message + error metadata in DLT for debugging
- Use exponential backoff (1s → 2s → 4s)
- Max 3-5 retries before DLT
- Non-blocking retries (don't block main consumer)

---

## Key Architecture Decision Summary

| Pattern | Purpose | Implementation |
|---------|---------|-----------------|
| **Choreography Saga** | Booking-Payment consistency | Services react to domain events, no orchestrator |
| **Event Envelope** | Standardized messaging | Wrap payloads with eventId, timestamp, correlationId |
| **Shared Library** | Code reuse | jwt-auth-spring-boot-starter exports event classes |
| **Transactional Outbox** | Reliability | Database + outbox table in single transaction |
| **Topic Naming** | Clarity & evolution | `{domain}.{type}.{version}` (e.g., booking.events.v1.booking-created) |
| **@RetryableTopic** | Resilience | Auto-retry with exponential backoff, DLT for failures |

---

## Next Steps for Implementation

1. **Create event classes** in shared library (BookingCreatedEvent, PaymentProcessedEvent, etc.)
2. **Define EventEnvelope** wrapper with metadata standard
3. **Implement Kafka producers** in booking & payment services
4. **Add outbox table** to booking-service schema
5. **Implement event listeners** with @RetryableTopic annotations
6. **Configure DLT topics** and monitoring
7. **Document topic registry** (which service owns which topics)

---

**Sources:**
- [Event-driven architecture on the modern stack of Java technologies](https://romankudryashov.com/blog/2024/07/event-driven-architecture/)
- [Choreography Saga Pattern With Spring Boot](https://www.vinsguru.com/choreography-saga-pattern-with-spring-boot/)
- [Designing Event-Driven Microservices - The Transactional Outbox Pattern](https://developer.confluent.io/courses/microservices/the-transactional-outbox-pattern/)
- [Kafka Topic Naming Conventions: Best Practices](https://www.confluent.io/learn/kafka-topic-naming-convention/)
- [Robust Kafka Consumer Error Handling on a Spring Boot 3 Application](https://medium.com/javarevisited/robust-kafka-consumer-error-handling-on-a-spring-boot-3-application-6fc95e92c956)
- [Spring Kafka Error Handling Documentation](https://docs.spring.io/spring-kafka/reference/kafka/annotation-error-handling.html)
- [Understanding the Domain Event Pattern in Microservices with Spring Boot](https://naveen-metta.medium.com/understanding-the-domain-event-pattern-in-microservices-with-spring-boot-5470fa1a34b0)
