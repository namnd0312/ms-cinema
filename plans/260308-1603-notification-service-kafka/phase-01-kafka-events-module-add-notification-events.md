# Phase 1: Kafka Events Module — Add Notification Events

## Context Links
- [Plan Overview](./plan.md)
- [Kafka Research](./research/researcher-01-kafka-setup.md)
- Existing events: `/kafka-events/src/main/java/com/namnd/kafka/events/`

## Overview
- **Priority:** High (blocks Phase 2 & 3)
- **Status:** Pending
- **Description:** Add notification domain events and topic constant to shared kafka-events module

## Key Insights
- EventEnvelope<T> pattern already exists — reuse it
- KafkaTopics.java centralizes topic constants
- Domain events are immutable Java records with minimal payload
- eventType string discriminator handles polymorphic deserialization

## Requirements
- Define `NOTIFICATION_EVENTS` topic constant
- Create notification domain event records for: activation email, password reset email, account locked
- Keep events generic enough for future notification types (SMS, push)

## Architecture
```
kafka-events module (shared library)
├── topic/KafkaTopics.java          ← ADD: NOTIFICATION_EVENTS
└── domain/
    ├── PaymentCompletedEvent.java   (existing)
    └── NotificationRequestedEvent.java  ← NEW
```

## Related Code Files

**Modify:**
- `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java` — add NOTIFICATION_EVENTS constant

**Create:**
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/NotificationRequestedEvent.java`

## Implementation Steps

1. Add to `KafkaTopics.java`:
   ```java
   public static final String NOTIFICATION_EVENTS = "notification-events";
   ```

2. Create `NotificationRequestedEvent.java` as a record:
   ```java
   public record NotificationRequestedEvent(
       String notificationType,  // "EMAIL"
       String channel,           // "ACTIVATION", "PASSWORD_RESET", "ACCOUNT_LOCKED"
       String recipientEmail,
       String subject,
       String body
   ) {}
   ```
   - Single event type covers all email use cases
   - `channel` field differentiates email subtypes
   - `body` contains pre-rendered content (producer builds the message)
   - Extensible: add `notificationType` "SMS"/"PUSH" later

3. Build kafka-events module: `mvn clean install -pl kafka-events`

## Todo List
- [ ] Add NOTIFICATION_EVENTS to KafkaTopics
- [ ] Create NotificationRequestedEvent record
- [ ] Build and verify module compiles

## Success Criteria
- kafka-events module compiles
- All dependent services still compile
- NotificationRequestedEvent importable from other modules

## Risk Assessment
- **Low risk** — additive change to shared module, no breaking modifications

## Security Considerations
- Event payload contains recipient email — Kafka topic should be internal only
- No passwords or tokens in event payload (body contains rendered link, not raw token)

## Next Steps
- Phase 2 uses these events in notification-service consumer
- Phase 3 uses these events in auth-service producer
