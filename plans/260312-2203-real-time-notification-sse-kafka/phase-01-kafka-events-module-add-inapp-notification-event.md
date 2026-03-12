# Phase 1: kafka-events Module — Add InAppNotificationEvent

## Context Links
- [KafkaTopics.java](../../kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java)
- [EventEnvelope.java](../../kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java)
- [NotificationRequestedEvent.java](../../kafka-events/src/main/java/com/namnd/kafka/events/domain/NotificationRequestedEvent.java)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1 (blocking Phase 2 & 3)
- **Status:** pending
- **Effort:** 30m

Add `InAppNotificationEvent` record to kafka-events shared module. This event carries userId, title, message, and notification type for in-app delivery via SSE. Distinct from existing `NotificationRequestedEvent` which is email-focused.

## Key Insights
- Existing `NotificationRequestedEvent` uses recipientEmail, not userId — wrong abstraction for in-app
- `EventEnvelope` already wraps all events with eventId, eventType, source, correlationId, timestamp
- New eventType discriminator: `"notification.in_app"` (follows existing `"notification.requested"` pattern)
- No new Kafka topic needed — reuse `notification-events` with different eventType

## Requirements

### Functional
- New `InAppNotificationEvent` record with: userId (Long), title (String), message (String), notificationType (String enum: PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM)
- Add `NotificationType` enum to kafka-events domain package

### Non-functional
- Record must be serializable/deserializable with Jackson
- `@JsonIgnoreProperties(ignoreUnknown = true)` for forward compatibility

## Architecture

```
kafka-events/src/main/java/com/namnd/kafka/events/
├── domain/
│   ├── InAppNotificationEvent.java    ← NEW
│   ├── NotificationType.java          ← NEW (enum)
│   ├── NotificationRequestedEvent.java (unchanged)
│   └── ... (existing events unchanged)
├── envelope/
│   └── EventEnvelope.java (unchanged)
└── topic/
    └── KafkaTopics.java (unchanged — reuse NOTIFICATION_EVENTS)
```

## Related Code Files

### Files to Create
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/InAppNotificationEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/NotificationType.java`

### Files Unchanged
- `KafkaTopics.java` — reuse existing `NOTIFICATION_EVENTS` constant
- `EventEnvelope.java` — generic, works with any payload type

## Implementation Steps

1. Create `NotificationType` enum in `kafka-events/src/main/java/com/namnd/kafka/events/domain/`:
   ```java
   public enum NotificationType {
       PAYMENT_SUCCESS,
       PAYMENT_FAILED,
       ADMIN_BROADCAST,
       SYSTEM
   }
   ```

2. Create `InAppNotificationEvent` record in same package:
   ```java
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record InAppNotificationEvent(
       Long userId,
       String title,
       String message,
       NotificationType notificationType
   ) {}
   ```

3. Run `mvn clean compile -pl kafka-events` to verify compilation

## Todo List
- [ ] Create `NotificationType.java` enum
- [ ] Create `InAppNotificationEvent.java` record
- [ ] Verify compilation with `mvn clean compile -pl kafka-events`

## Success Criteria
- `InAppNotificationEvent` compiles and is importable by notification-service and booking-service
- Jackson serialization/deserialization works with `EventEnvelope<InAppNotificationEvent>`

## Risk Assessment
- **Low risk** — additive change only, no modification to existing events
- Ensure `spring.json.trusted.packages` in consumers includes `com.namnd.kafka.events.*` (already configured)

## Security Considerations
- None — shared DTO module, no runtime exposure

## Next Steps
- Phase 2: notification-service consumes this event
- Phase 3: booking-service publishes this event
