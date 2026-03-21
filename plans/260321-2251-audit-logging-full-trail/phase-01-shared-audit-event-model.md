# Phase 1: Shared Audit Event Model

## Context Links
- [Plan overview](plan.md)
- [Research: Kafka schema](research/researcher-01-aop-envers-schema.md)
- Existing: `kafka-events/src/main/java/com/namnd/kafka/events/`

## Overview
- **Priority:** P1 (blocking for all other phases)
- **Status:** pending
- **Effort:** 1h
- Add `AuditEvent` record, `AuditAction` enum, and `AUDIT_EVENTS` topic constant to kafka-events shared library

## Key Insights
- Reuse existing `EventEnvelope<T>` wrapper — AuditEvent becomes the payload type
- Keep schema flat: beforeState/afterState as JSON strings (not nested objects) for flexibility
- `eventId` from EventEnvelope provides idempotency key

## Requirements

### Functional
- AuditEvent captures: userId, userIp, action, entityType, entityId, beforeState, afterState, sourceService, traceId
- AuditAction enum: CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, CUSTOM
- Topic constant `AUDIT_EVENTS = "audit-events"` in KafkaTopics

### Non-Functional
- Backward compatible — no changes to existing event classes
- Zero runtime dependencies added (kafka-events is a plain lib)

## Architecture

```
kafka-events/
  src/main/java/com/namnd/kafka/events/
    domain/
      AuditEvent.java          (NEW - record)
      AuditAction.java         (NEW - enum)
    topic/
      KafkaTopics.java         (MODIFY - add AUDIT_EVENTS constant)
```

## Related Code Files

### Modify
- `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java` — add `AUDIT_EVENTS`

### Create
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/AuditEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/AuditAction.java`

## Implementation Steps

1. Create `AuditAction` enum in `kafka-events/src/main/java/com/namnd/kafka/events/domain/`
   ```java
   public enum AuditAction {
       CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, CUSTOM
   }
   ```

2. Create `AuditEvent` record in same package
   ```java
   @JsonIgnoreProperties(ignoreUnknown = true)
   public record AuditEvent(
       String userId,
       String userIp,
       AuditAction action,
       String entityType,
       String entityId,
       String beforeState,   // JSON string
       String afterState,    // JSON string
       String sourceService,
       String traceId
   ) {}
   ```

3. Add topic constant to `KafkaTopics.java`:
   ```java
   public static final String AUDIT_EVENTS = "audit-events";
   ```

4. Run `mvn -pl kafka-events clean compile` to verify

## Todo List
- [ ] Create AuditAction enum
- [ ] Create AuditEvent record
- [ ] Add AUDIT_EVENTS to KafkaTopics
- [ ] Compile kafka-events module

## Success Criteria
- `mvn -pl kafka-events clean compile` passes
- AuditEvent serializable/deserializable with Jackson
- No changes to existing event classes

## Risk Assessment
- **Low risk** — additive changes only to shared lib
- Risk: field naming mismatch with consumer — mitigated by defining schema here first

## Security Considerations
- `beforeState`/`afterState` may contain PII — consumers must handle data masking
- No secrets in audit event payloads

## Next Steps
- Phase 2 uses AuditEvent + AuditAction for the interceptor library
