# Phase 6: Testing & Verification

## Context
- [Plan Overview](./plan.md)
- [Phase 3: Booking-Payment Saga](./phase-03-booking-payment-saga-events.md)
- All prior phases must be complete (at minimum Phases 1-3)

## Overview
- **Priority:** P1
- **Status:** Complete
- **Effort:** 1h
- **Description:** Verify Kafka event flows end-to-end, test DLT behavior, confirm idempotent consumption

## Key Insights
- `spring-kafka-test` provides `EmbeddedKafkaBroker` for unit/integration tests without Docker
- Alternatively, Testcontainers with `KafkaContainer` for more realistic tests
- EmbeddedKafka simpler and faster -- preferred for this project size
- Focus testing on the critical path: payment -> booking confirmation flow

## Requirements

### Functional
- Integration test: payment event published -> booking confirmed
- Integration test: duplicate payment event -> booking state unchanged (idempotent)
- Integration test: invalid event -> routed to DLT after retries
- Compilation test: all modules build successfully

### Non-Functional
- Tests run without external Kafka/Docker
- Tests complete in <30 seconds

## Architecture

### Test Strategy
```
EmbeddedKafkaBroker
    |
    ├── PaymentEventPublisher publishes to "payment-events"
    │       |
    │       v
    │   PaymentEventListener consumes and confirms booking
    │
    ├── Publish duplicate event -> verify no state change
    │
    └── Publish bad message -> verify lands in "payment-events.DLT"
```

## Related Code Files

### Create
- `booking-service/src/test/java/com/namnd/bookingservice/listener/PaymentEventListenerIntegrationTest.java`
- `payment-service/src/test/java/com/namnd/paymentservice/event/PaymentEventPublisherTest.java`

### Modify
- `booking-service/pom.xml` -- add `spring-kafka-test` test dependency (if not present)
- `payment-service/pom.xml` -- add `spring-kafka-test` test dependency (if not present)

## Implementation Steps

1. **Add test dependency** to booking-service and payment-service:
   ```xml
   <dependency>
       <groupId>org.springframework.kafka</groupId>
       <artifactId>spring-kafka-test</artifactId>
       <scope>test</scope>
   </dependency>
   ```

2. **Create PaymentEventPublisherTest** (payment-service):
   - Use `@EmbeddedKafka(partitions = 1, topics = "payment-events")`
   - Verify EventEnvelope serialization
   - Consume from embedded broker, assert payload structure

3. **Create PaymentEventListenerIntegrationTest** (booking-service):
   - Use `@EmbeddedKafka` + `@SpringBootTest`
   - **Test 1: Happy path** -- publish PaymentCompletedEvent, assert booking status = CONFIRMED
   - **Test 2: Idempotency** -- publish same event twice, assert booking stays CONFIRMED (no error)
   - **Test 3: Failed payment** -- publish PaymentFailedEvent, assert booking status = CANCELLED
   - **Test 4: DLT** -- publish malformed message, assert it lands in `payment-events.DLT`
   - Mock or use in-memory H2 for booking repository

4. **Docker-compose smoke test** (manual):
   - Start all services with `docker-compose up`
   - Create booking via API -> initiate payment -> verify booking confirmed via Kafka
   - Check `payment-events.DLT` topic is empty (no failures)
   - Verify `/actuator/health` shows Kafka UP on all Kafka-enabled services

5. Run `mvn test -pl booking-service,payment-service`

## Todo List
- [x] Add spring-kafka-test dependency to both services
- [x] Create PaymentEventPublisherTest (3 unit tests)
- [x] Create PaymentEventListenerIntegrationTest (happy path)
- [x] Add idempotency test
- [x] Add DLT test
- [x] Run full test suite — 7/7 pass, <20s
- [ ] Manual docker-compose smoke test (deferred — requires running infra)

## Success Criteria
- All unit/integration tests pass
- Payment->booking event flow works end-to-end in docker-compose
- DLT captures failed messages
- Idempotent consumption verified
- `/actuator/health` shows Kafka status on all services

## Risk Assessment
- **EmbeddedKafka port conflicts:** Use random ports via `@EmbeddedKafka(ports = 0)`
- **Test flakiness:** Kafka consumer poll delays -- use `Awaitility` or `CountDownLatch` with timeout
- **H2 vs PostgreSQL differences:** Keep tests simple, avoid DB-specific features

## Security Considerations
- Test data only, no real credentials
- EmbeddedKafka runs in-process, no network exposure

## Next Steps
- Monitor Kafka consumer lag via Grafana dashboards (future enhancement)
- Add Kafka metrics to Prometheus scrape config
