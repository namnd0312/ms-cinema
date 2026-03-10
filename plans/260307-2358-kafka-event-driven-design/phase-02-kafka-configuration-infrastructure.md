# Phase 2: Kafka Configuration & Infrastructure

## Context
- [Plan Overview](./plan.md)
- [Research: Spring Kafka + KRaft](./research/researcher-01-spring-kafka-kraft.md)
- Current: booking-service uses StringDeserializer, payment-service uses StringSerializer, no DLT, no health checks

## Overview
- **Priority:** P1 (blocking for Phases 3-5)
- **Status:** Pending
- **Effort:** 1h
- **Description:** Centralize Kafka config, switch to JsonSerializer/Deserializer, add DLT error handling, enable Kafka health checks

## Key Insights
- Spring Boot auto-configures Kafka health indicator when `spring-kafka` is on classpath + actuator enabled -- just need `show-details: always`
- `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` is simpler than `@RetryableTopic` (fewer auto-created topics)
- JsonSerializer eliminates manual ObjectMapper usage in producers/consumers
- `spring.json.trusted.packages` required for deserialization security
- Shared config in config-server `application.yml` reduces per-service duplication

## Requirements

### Functional
- All Kafka producers use JsonSerializer (value)
- All Kafka consumers use JsonDeserializer with trusted packages
- DLT configured for all consumer groups (3 retries, exponential backoff)
- Kafka health check visible at `/actuator/health`

### Non-Functional
- Config centralized in config-server where possible
- Per-service overrides only for group-id and specific topics

## Architecture

### Config Hierarchy
```
config-server/application.yml (shared)
  └── spring.kafka.bootstrap-servers
  └── spring.kafka.producer.* (serializer defaults)
  └── spring.kafka.consumer.* (deserializer defaults, trusted packages)

Per-service application.yml (overrides)
  └── spring.kafka.consumer.group-id
  └── Service-specific KafkaConsumerConfig @Bean for DLT
```

### DLT Flow
```
message → consumer → exception → retry (3x, exp backoff) → DLT topic
                                                            ({topic}.DLT)
```

## Related Code Files

### Modify
- `config-server/src/main/resources/config-repo/application.yml` -- add shared Kafka config
- `booking-service/src/main/resources/application.yml` -- simplify Kafka section, keep group-id
- `payment-service/src/main/resources/application.yml` -- simplify Kafka section, add consumer config
- `booking-service/src/main/resources/application.yml` -- update management.endpoints to include health details
- `payment-service/src/main/resources/application.yml` -- update management.endpoints to include health details

### Create
- `booking-service/src/main/java/com/namnd/bookingservice/config/KafkaConsumerConfig.java` -- DLT error handler bean
- `payment-service/src/main/java/com/namnd/paymentservice/config/KafkaConsumerConfig.java` -- DLT error handler bean (for future consumers)

## Implementation Steps

1. **Update config-server shared config** (`application.yml`):
   ```yaml
   spring:
     kafka:
       bootstrap-servers: ${KAFKA_HOST:localhost}:9092
       producer:
         key-serializer: org.apache.kafka.common.serialization.StringSerializer
         value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
       consumer:
         key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
         value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
         auto-offset-reset: earliest
         properties:
           spring.json.trusted.packages: "com.namnd.kafka.events.*"
   ```

2. **Simplify booking-service application.yml**: Remove serializer/deserializer config (inherited from config-server), keep only `group-id: booking-service`

3. **Simplify payment-service application.yml**: Remove serializer config (inherited), keep only `group-id: payment-service` for future consumers

4. **Create KafkaConsumerConfig in booking-service**:
   - `DefaultErrorHandler` bean with `DeadLetterPublishingRecoverer`
   - `ExponentialBackOffWithMaxRetries(3)`, initial=1000ms, max=10000ms
   - Add `DeserializationException` and `MessageConversionException` to non-retryable

5. **Create KafkaConsumerConfig in payment-service** (same pattern, for future use when payment-service consumes events)

6. **Update actuator config** in both services:
   ```yaml
   management:
     endpoint:
       health:
         show-details: always
   ```

7. Verify with `mvn compile` across all modules

## Todo List
- [ ] Update config-server shared application.yml with Kafka defaults
- [ ] Simplify booking-service Kafka YAML
- [ ] Simplify payment-service Kafka YAML
- [ ] Create KafkaConsumerConfig.java in booking-service
- [ ] Create KafkaConsumerConfig.java in payment-service
- [ ] Update actuator health config in both services
- [ ] Verify compilation

## Success Criteria
- `mvn compile` succeeds for booking-service and payment-service
- Kafka health indicator appears in `/actuator/health` response
- DLT error handler configured and wired via Spring context

## Risk Assessment
- **Config-server unavailable at startup:** Mitigated by `optional:configserver:` prefix (already in place) + local fallback values
- **Trusted packages mismatch:** Must match exactly `com.namnd.kafka.events.*` -- wildcard covers all sub-packages
- **DLT producer needs KafkaTemplate<String, Object>:** DeadLetterPublishingRecoverer auto-discovers KafkaTemplate from context

## Security Considerations
- `spring.json.trusted.packages` restricts deserialization to project packages only (prevents arbitrary class instantiation)
- No SASL/SSL needed for internal Docker network

## Next Steps
- Phase 3: Refactor existing payment event flow to use shared DTOs + DLT
