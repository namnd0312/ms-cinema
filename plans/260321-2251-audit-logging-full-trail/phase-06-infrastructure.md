# Phase 6: Infrastructure

## Context Links
- [Plan overview](plan.md)
- [Phase 3 — audit-service](phase-03-create-audit-service.md)
- Existing: `docker-compose.yml`, `api-gateway/src/main/resources/application.yml`, `config-server/src/main/resources/config-repo/`

## Overview
- **Priority:** P2
- **Status:** pending
- **Effort:** 1h
- Docker Compose config for auditdb + audit-service, gateway route, config-server profile, Kafka topic creation

## Key Insights
- Existing docker-compose uses single `postgres-service` container — add `auditdb` as additional database via init script or separate container
- Simpler: use same postgres container, create `auditdb` database via init SQL
- Gateway route pattern: `/api/audit/**` → `lb://audit-service`
- Config-server: add `audit-service.yml` to config-repo

## Requirements

### Functional
- auditdb PostgreSQL database accessible by audit-service
- audit-service container in docker-compose
- API gateway routes `/api/audit/**` to audit-service
- Kafka `audit-events` topic auto-created (or declared in config)
- config-server has audit-service profile

### Non-Functional
- Consistent with existing docker-compose patterns
- Eureka-registered audit-service visible in service registry

## Architecture

```
docker-compose.yml additions:
  - postgres init: CREATE DATABASE auditdb
  - audit-service container (port 8086)

api-gateway routes:
  - /api/audit/** → lb://audit-service

config-server:
  - config-repo/audit-service.yml
```

## Related Code Files

### Modify
- `docker-compose.yml` — add auditdb init + audit-service container
- `api-gateway/src/main/resources/application.yml` — add audit-service route
- Root `pom.xml` — already modified in phase 3

### Create
- `config-server/src/main/resources/config-repo/audit-service.yml`
- `docker-compose-init/init-auditdb.sql` (or inline in docker-compose)

## Implementation Steps

1. **Add auditdb to PostgreSQL init:**

   Option A (init script):
   Create `docker/init-databases.sql`:
   ```sql
   CREATE DATABASE auditdb;
   ```
   Mount in docker-compose postgres-service volumes.

   Option B (simpler — add env var if postgres supports multiple DBs, or use existing init script pattern from project).

   Check existing postgres init pattern first — if using `POSTGRES_MULTIPLE_DATABASES` env or init scripts.

2. **Add audit-service container to docker-compose.yml:**
   ```yaml
   audit-service:
     build:
       context: ./audit-service
       dockerfile: Dockerfile
     ports:
       - "8086:8086"
     environment:
       - SPRING_PROFILES_ACTIVE=docker
       - DB_HOST=postgres-service
       - DB_USER=postgres
       - DB_PASS=postgres
       - KAFKA_BROKERS=kafka:9092
       - EUREKA_HOST=eureka-server
       - CONFIG_SERVER_URI=http://config-server:8888
     depends_on:
       - postgres-service
       - kafka
       - eureka-server
       - config-server
     networks:
       - cinema-network
   ```

3. **Create Dockerfile for audit-service** (if not using shared Dockerfile pattern):
   - Copy from existing service Dockerfile (e.g., notification-service)
   - Multi-stage: maven build + JRE runtime

4. **Add gateway route:**
   ```yaml
   # In api-gateway application.yml, under spring.cloud.gateway.routes:
   - id: audit-service
     uri: lb://audit-service
     predicates:
       - Path=/api/audit/**
   - id: audit-service-swagger
     uri: lb://audit-service
     predicates:
       - Path=/v3/api-docs/audit-service
   ```

5. **Create config-server config:**
   `config-server/src/main/resources/config-repo/audit-service.yml`:
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://${DB_HOST:localhost}:5432/auditdb
       username: ${DB_USER:postgres}
       password: ${DB_PASS:postgres}
     jpa:
       hibernate:
         ddl-auto: update
     kafka:
       bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
       consumer:
         group-id: audit-service
         auto-offset-reset: earliest
         properties:
           spring.json.trusted.packages: "com.namnd.kafka.events.*"
   ```

6. **Kafka topic creation:**
   - Option A: Auto-created by first producer/consumer (Spring Kafka default)
   - Option B: Add `NewTopic` bean in audit-service config:
     ```java
     @Bean
     public NewTopic auditEventsTopic() {
         return TopicBuilder.name(KafkaTopics.AUDIT_EVENTS)
             .partitions(3)
             .replicas(1)
             .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(90 * 24 * 60 * 60 * 1000L)) // 90 days
             .build();
     }
     ```

7. **Verify full stack:** `docker-compose up --build` — check:
   - auditdb created
   - audit-service registered in Eureka
   - Gateway routes to audit-service
   - Kafka audit-events topic exists

## Todo List
- [ ] Add auditdb PostgreSQL database init
- [ ] Add audit-service to docker-compose.yml
- [ ] Create/copy Dockerfile for audit-service
- [ ] Add gateway route for audit-service
- [ ] Create audit-service.yml in config-repo
- [ ] Add Kafka topic bean with retention config
- [ ] Test full docker-compose stack

## Success Criteria
- `docker-compose up` starts audit-service alongside other services
- audit-service visible in Eureka dashboard
- `GET http://localhost:8080/api/audit/v1/admin/audit-logs` routed through gateway
- auditdb database created with audit_logs table
- Kafka audit-events topic exists with 90-day retention

## Risk Assessment
- **Low:** Docker Compose additions are isolated
- **Medium:** Gateway route path conflict — ensure `/api/audit/**` doesn't collide with existing routes (checked: no conflict)
- **Low:** PostgreSQL init script ordering — depends_on ensures postgres starts first

## Security Considerations
- Database credentials via environment variables
- Admin API behind JWT + ADMIN role check (configured in phase 4 SecurityConfig)
- No public audit endpoints — all behind gateway auth

## Next Steps
- Full integration testing across all services
- Monitor Kafka consumer lag for audit-events topic
- Future: add table partitioning if data volume grows significantly
