# Phase 3: Create audit-service

## Context Links
- [Plan overview](plan.md)
- [Phase 1 — event model](phase-01-shared-audit-event-model.md)
- [Research: architecture](research/researcher-02-admin-api-retention-architecture.md)
- Reference: notification-service module structure

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 3h
- New Spring Boot module: audit-service (port 8086), Kafka consumer for `audit-events`, PostgreSQL auditdb, AuditLog JPA entity

## Key Insights
- Follow notification-service as template (same Kafka consumer + JPA + security pattern)
- Idempotent consumer using `event_id` unique constraint — Kafka retries won't duplicate
- JSONB columns for beforeState/afterState — flexible schema, queryable via PostgreSQL
- Single `audit_logs` table (no partitioning yet — YAGNI)

## Requirements

### Functional
- Consume `EventEnvelope<AuditEvent>` from Kafka `audit-events` topic
- Persist to `audit_logs` table in `auditdb` PostgreSQL database
- Idempotent: skip if eventId already exists
- DLT (dead letter topic) for failed messages after 3 retries

### Non-Functional
- Port 8086
- Registers with Eureka
- Config from config-server
- Structured JSON logging (Loki-compatible)

## Architecture

```
audit-service/
  pom.xml
  src/main/java/com/namnd/auditservice/
    AuditServiceApplication.java
    config/
      KafkaConsumerConfig.java
      SecurityConfig.java
    domain/
      AuditLog.java                (JPA entity)
    repository/
      AuditLogRepository.java      (JpaRepository + JpaSpecificationExecutor)
    consumer/
      AuditEventConsumer.java      (KafkaListener)
    mapper/
      AuditLogMapper.java          (EventEnvelope<AuditEvent> -> AuditLog)
  src/main/resources/
    application.yml
    db/migration/                  (if using Flyway, else JPA auto-DDL)
```

## Related Code Files

### Create
- `audit-service/pom.xml`
- `audit-service/src/main/java/com/namnd/auditservice/AuditServiceApplication.java`
- `audit-service/src/main/java/com/namnd/auditservice/config/KafkaConsumerConfig.java`
- `audit-service/src/main/java/com/namnd/auditservice/config/SecurityConfig.java`
- `audit-service/src/main/java/com/namnd/auditservice/domain/AuditLog.java`
- `audit-service/src/main/java/com/namnd/auditservice/repository/AuditLogRepository.java`
- `audit-service/src/main/java/com/namnd/auditservice/consumer/AuditEventConsumer.java`
- `audit-service/src/main/java/com/namnd/auditservice/mapper/AuditLogMapper.java`
- `audit-service/src/main/resources/application.yml`

### Modify
- `pom.xml` (root) — add `<module>audit-service</module>`

## Implementation Steps

1. **Create `audit-service/pom.xml`:**
   - Parent: `com.namnd:ms-cinema`
   - Dependencies: spring-boot-starter-web, spring-kafka, kafka-events, spring-boot-starter-data-jpa, postgresql, spring-boot-starter-security, jwt-auth-autoconfigure, spring-boot-starter-validation, spring-boot-starter-actuator, spring-cloud-starter-netflix-eureka-client, spring-cloud-starter-config, springdoc-openapi, micrometer-tracing, lombok

2. **Add module to root pom.xml:**
   ```xml
   <module>audit-service</module>
   ```

3. **Create `AuditServiceApplication.java`:**
   - `@SpringBootApplication` + `@EnableDiscoveryClient`

4. **Create `AuditLog` JPA entity:**
   ```java
   @Entity @Table(name = "audit_logs")
   public class AuditLog {
       @Id @GeneratedValue(strategy = IDENTITY)
       private Long id;

       @Column(nullable = false, unique = true)
       private String eventId;        // idempotency key from EventEnvelope

       @Column(nullable = false)
       private String userId;

       private String userIp;

       @Column(nullable = false, length = 20)
       @Enumerated(EnumType.STRING)
       private AuditAction action;

       @Column(nullable = false, length = 100)
       private String entityType;

       private String entityId;

       @Column(columnDefinition = "jsonb")
       private String beforeState;

       @Column(columnDefinition = "jsonb")
       private String afterState;

       @Column(nullable = false)
       private String sourceService;

       private String traceId;

       @Column(nullable = false)
       private LocalDateTime createdAt;
   }
   ```

5. **Create `AuditLogRepository`:**
   - Extend `JpaRepository<AuditLog, Long>` and `JpaSpecificationExecutor<AuditLog>`
   - Method: `boolean existsByEventId(String eventId)`

6. **Create `AuditLogMapper`:**
   - Static method: `toEntity(EventEnvelope<AuditEvent> envelope) -> AuditLog`
   - Maps envelope metadata (eventId, timestamp) + AuditEvent fields

7. **Create `AuditEventConsumer`:**
   ```java
   @Component
   public class AuditEventConsumer {
       @KafkaListener(topics = KafkaTopics.AUDIT_EVENTS, groupId = "audit-service")
       public void consume(EventEnvelope<AuditEvent> envelope) {
           if (repository.existsByEventId(envelope.eventId())) {
               log.debug("Duplicate eventId={}, skipping", envelope.eventId());
               return;
           }
           repository.save(AuditLogMapper.toEntity(envelope));
       }
   }
   ```

8. **Create `KafkaConsumerConfig`:**
   - Configure `ConsumerFactory` with `JsonDeserializer` for `EventEnvelope`
   - ErrorHandler with 3 retries + DLT
   - Follow pattern from notification-service

9. **Create `SecurityConfig`:**
   - Permit actuator/health endpoints
   - Admin endpoints require ADMIN role (for phase 4)
   - Use jwt-auth-autoconfigure

10. **Create `application.yml`:**
    ```yaml
    server:
      port: 8086
    spring:
      application:
        name: audit-service
      datasource:
        url: jdbc:postgresql://${DB_HOST:localhost}:5432/auditdb
        username: ${DB_USER:postgres}
        password: ${DB_PASS:postgres}
      jpa:
        hibernate:
          ddl-auto: update
        properties:
          hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
      kafka:
        bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
        consumer:
          group-id: audit-service
    eureka:
      client:
        service-url:
          defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka
    ```

11. **Compile:** `mvn -pl audit-service clean compile`

## Todo List
- [ ] Create audit-service pom.xml
- [ ] Add module to root pom.xml
- [ ] Create AuditServiceApplication
- [ ] Create AuditLog entity
- [ ] Create AuditLogRepository
- [ ] Create AuditLogMapper
- [ ] Create AuditEventConsumer
- [ ] Create KafkaConsumerConfig
- [ ] Create SecurityConfig
- [ ] Create application.yml
- [ ] Compile module

## Success Criteria
- Module compiles as part of reactor build
- Kafka consumer deserializes EventEnvelope<AuditEvent> correctly
- Idempotent: duplicate eventId skipped without error
- AuditLog entity creates `audit_logs` table with JSONB columns

## Risk Assessment
- **Medium:** Jackson deserialization of generic EventEnvelope<AuditEvent> — need TypeReference config in consumer factory
- **Low:** Database connection — standard PostgreSQL pattern same as other services

## Security Considerations
- auditdb credentials via environment variables (not in git)
- Admin API endpoints (phase 4) require ADMIN role
- Audit data is append-only — no UPDATE/DELETE endpoints

## Next Steps
- Phase 4 adds admin REST API on top of this service
