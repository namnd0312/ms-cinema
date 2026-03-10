# Phase 5: Auth Service Events (Optional)

## Context
- [Plan Overview](./plan.md)
- [Phase 1: Shared Events Module](./phase-01-shared-kafka-events-module.md)
- Current: auth-service has no Kafka dependency, user registration/activation handled internally

## Overview
- **Priority:** P3 (optional, low priority)
- **Status:** Pending
- **Effort:** 1h
- **Description:** Add event publishing to auth-service for user registration and activation. Enables future cross-service user profile sync.

## Key Insights
- Currently NO downstream service needs user events -- this is speculative
- Only implement if there is a concrete consumer use case (e.g., booking-service needs user profile cache)
- YAGNI principle suggests deferring this until a real need arises
- Included in plan for completeness and to show the pattern extends to auth-service

## Requirements

### Functional
- auth-service publishes `UserRegisteredEvent` on registration
- auth-service publishes `UserActivatedEvent` on account activation
- Events published to `auth-events` topic

### Non-Functional
- Fire-and-forget (auth flow must not break if Kafka unavailable)
- No sensitive data in events (no passwords, tokens)

## Architecture

### Event Flow
```
User registers / activates
    |
    v
AuthService
    |  saves to DB
    |  publishes event
    v
Kafka "auth-events"
    |
    v
(No consumers yet)
```

## Related Code Files

### Modify
- `auth-service/pom.xml` -- add spring-kafka + kafka-events dependencies (auth-service is the root module built from `./Dockerfile`)
- `auth-service/src/main/resources/application.yml` -- add Kafka producer config
- `docker-compose.yml` -- add `KAFKA_HOST: kafka` and `kafka` to auth-service depends_on

### Create
- `kafka-events/.../domain/UserRegisteredEvent.java` -- userId, email, username
- `kafka-events/.../domain/UserActivatedEvent.java` -- userId, email
- `auth-service/.../event/AuthEventPublisher.java`
- Add `AUTH_EVENTS = "auth-events"` to KafkaTopics.java

### May Need to Modify
- Auth service registration/activation flows -- add event publishing after DB commit

## Implementation Steps

1. Add `UserRegisteredEvent` and `UserActivatedEvent` records to kafka-events module
2. Add `AUTH_EVENTS` constant to `KafkaTopics.java`
3. Add spring-kafka + kafka-events dependencies to auth-service pom.xml
4. Update auth-service application.yml with Kafka bootstrap-servers
5. Update docker-compose.yml for auth-service Kafka access
6. Create `AuthEventPublisher` with fire-and-forget publishing
7. Wire event publishing into registration and activation service methods
8. Compile and verify

## Todo List
- [ ] Add auth event records to kafka-events module
- [ ] Add topic constant
- [ ] Add dependencies to auth-service
- [ ] Update configs
- [ ] Create AuthEventPublisher
- [ ] Wire into auth flows
- [ ] Verify compilation

## Success Criteria
- auth-service compiles with Kafka dependencies
- Registration and activation trigger events (verifiable via Kafka console consumer)
- Auth flows unaffected if Kafka unavailable

## Risk Assessment
- **YAGNI violation:** This phase has no consumer -- may be premature. Defer if time-constrained.
- **auth-service build context:** auth-service builds from root `./Dockerfile`, need to ensure kafka-events JAR available during Docker build

## Security Considerations
- Events must NOT contain passwords, JWT secrets, or tokens
- Only publish userId, email, username -- public profile data

## Next Steps
- Implement consumers only when a concrete use case arises
- Phase 6: Include auth events in integration test if implemented
