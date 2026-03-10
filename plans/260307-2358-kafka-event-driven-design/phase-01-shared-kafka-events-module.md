# Phase 1: Shared Kafka Events Module

## Context
- [Plan Overview](./plan.md)
- [Research: EDD Patterns](./research/researcher-02-event-driven-patterns.md)
- Current: `PaymentEventDto` duplicated in booking-service, no shared event envelope

## Overview
- **Priority:** P1 (blocking for all other phases)
- **Status:** Pending
- **Effort:** 1.5h
- **Description:** Create `kafka-events` Maven module with event envelope, domain events, and topic constants shared across all services

## Key Insights
- Separate module from `jwt-auth-spring-boot-starter` keeps concerns clean (JWT auth vs messaging)
- Event envelope enables correlation/tracing across saga without external tooling
- Simple record-based events with Lombok-free design (records are immutable by default)
- Topic constants in one place prevent typo-related bugs

## Requirements

### Functional
- Event envelope wrapping all domain events with metadata (eventId, timestamp, source, type, correlationId)
- Domain event records: PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent, MovieCreatedEvent, ShowtimeCreatedEvent
- Topic name constants class
- Maven module buildable as JAR dependency

### Non-Functional
- No Spring Boot dependency (plain Java + Jackson annotations only)
- Each file <200 LOC
- Java 21 records preferred over Lombok classes

## Architecture

```
kafka-events/
  pom.xml
  src/main/java/com/namnd/kafka/events/
    envelope/
      EventEnvelope.java          -- generic wrapper <T> with metadata
    domain/
      PaymentCompletedEvent.java  -- bookingId, paymentId, amount, currency
      PaymentFailedEvent.java     -- bookingId, reason
      BookingCreatedEvent.java    -- bookingId, userId, showtimeId, seatIds, totalAmount
      MovieCreatedEvent.java      -- movieId, title
      ShowtimeCreatedEvent.java   -- showtimeId, movieId, theaterId, startTime, availableSeats
    topic/
      KafkaTopics.java            -- static final String constants
```

### EventEnvelope<T> Structure
```java
public record EventEnvelope<T>(
    String eventId,           // UUID
    String eventType,         // e.g. "payment.completed"
    String source,            // e.g. "payment-service"
    String correlationId,     // trace across saga
    LocalDateTime timestamp,
    T payload
) {}
```

## Related Code Files

### Create
- `kafka-events/pom.xml`
- `kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/PaymentCompletedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/PaymentFailedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/BookingCreatedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/MovieCreatedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/ShowtimeCreatedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java`

### Modify
- `pom.xml` (root) -- add `<module>kafka-events</module>`
- `booking-service/pom.xml` -- add `kafka-events` dependency
- `payment-service/pom.xml` -- add `kafka-events` dependency

## Implementation Steps

1. Create `kafka-events/pom.xml` with parent `spring-jwt`, packaging `jar`, no Spring Boot plugin, dependencies: `jackson-annotations` only
2. Create `EventEnvelope<T>` record with static factory method `of(source, type, correlationId, payload)` that auto-generates eventId + timestamp
3. Create `KafkaTopics` class with constants: `PAYMENT_EVENTS = "payment-events"`, `BOOKING_EVENTS = "booking-events"`, `MOVIE_EVENTS = "movie-events"`
4. Create domain event records (all as Java records with Jackson `@JsonIgnoreProperties(ignoreUnknown=true)` for forward compatibility):
   - `PaymentCompletedEvent(Long bookingId, String paymentId, Double amount, String currency)`
   - `PaymentFailedEvent(Long bookingId, String reason)`
   - `BookingCreatedEvent(Long bookingId, Long userId, Long showtimeId, List<Long> seatIds, Double totalAmount, String currency)`
   - `MovieCreatedEvent(Long movieId, String title)`
   - `ShowtimeCreatedEvent(Long showtimeId, Long movieId, Long theaterId, LocalDateTime startTime, Integer availableSeats)`
5. Add `<module>kafka-events</module>` to root `pom.xml`
6. Add `kafka-events` dependency to `booking-service/pom.xml` and `payment-service/pom.xml`
7. Run `mvn compile -pl kafka-events` to verify

## Todo List
- [ ] Create `kafka-events/pom.xml`
- [ ] Create `EventEnvelope.java`
- [ ] Create `KafkaTopics.java`
- [ ] Create domain event records (5 files)
- [ ] Add module to root pom.xml
- [ ] Add dependency to booking-service and payment-service pom.xml
- [ ] Verify compilation

## Success Criteria
- `mvn compile -pl kafka-events` succeeds
- All services that depend on `kafka-events` compile successfully
- Event records serializable/deserializable via Jackson

## Risk Assessment
- **Jackson version mismatch:** Mitigated by inheriting from Spring Boot parent BOM (Jackson managed)
- **Circular dependency:** None -- kafka-events has no dependency on any service module

## Security Considerations
- No sensitive data in event payloads (no passwords, tokens)
- Event payloads travel over internal Docker network only

## Next Steps
- Phase 2: Configure JsonSerializer/Deserializer in service YAML configs
- Phase 3: Wire events into booking-payment saga flow
