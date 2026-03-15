# Phase 4: Spring Boot K8s Adaptation

## Context Links
- [Parent Plan](./plan.md)
- [K8s Research](./research/researcher-02-k8s-architecture-decisions.md)
- Dependencies: Can run in parallel with Phase 1

## Overview
- **Date:** 2026-03-14
- **Priority:** P1
- **Status:** pending
- **Effort:** 3h
- **Review status:** not started

Add `application-k8s.yml` Spring profiles to all services. K8s profile uses K8s DNS for service discovery (no Eureka), K8s service names for DB/Redis/Kafka, and actuator health groups for K8s probes. Docker Compose path remains unchanged.

## Key Insights
- Current services use `optional:configserver:` import — `fail-fast: false` means they work without config server
- Eureka client registered via `eureka.client` properties — disable in k8s profile
- Gateway uses `lb://service-name` (Eureka-resolved) — K8s profile replaces with `http://service-name:port`
- Spring Boot actuator health groups (liveness/readiness) need explicit configuration
- `SPRING_PROFILES_ACTIVE=k8s` set via K8s Deployment env var
- Config loaded from `/config/application-k8s.yml` mounted via ConfigMap volume

## Requirements

### Functional
- `SPRING_PROFILES_ACTIVE=k8s` activates K8s-specific config
- Eureka client disabled in k8s profile
- Config server import skipped in k8s profile
- All service-to-service URLs use K8s DNS names
- Health endpoints respond at `/actuator/health/liveness` and `/actuator/health/readiness`

### Non-Functional
- Default profile (no SPRING_PROFILES_ACTIVE) still works with Docker Compose
- No breaking changes to existing config
- Minimal code changes (profile YAML only where possible)

## Architecture

### Profile Strategy
```
application.yml          # Default (Docker Compose / local dev) — UNCHANGED
application-k8s.yml      # K8s profile — NEW file per service
```

Spring resolution: `application.yml` loaded first, then `application-k8s.yml` overrides when `k8s` profile active.

### K8s DNS Names
| Target | K8s Service DNS |
|--------|-----------------|
| PostgreSQL | `postgres.cinema-dev.svc.cluster.local:5432` (short: `postgres:5432`) |
| Redis | `redis:6379` |
| Kafka | `local-kafka-kafka-bootstrap:9092` |
| auth-service | `auth-service:8081` |
| movie-service | `movie-service:8082` |
| booking-service | `booking-service:8083` |
| payment-service | `payment-service:8084` |
| notification-service | `notification-service:8085` |

### Health Group Config (all services)
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        liveness:
          include: livenessState
        readiness:
          include: readinessState, db, redis
```

### Gateway K8s Routes
Replace `lb://service-name` with direct HTTP URLs:
```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          - id: auth-service
            uri: http://auth-service:8081
            predicates:
              - Path=/api/auth/**
          # ... same pattern for all routes
```

## Related Code Files

### Files to Create
- `auth-service/src/main/resources/application-k8s.yml`
- `movie-service/src/main/resources/application-k8s.yml`
- `booking-service/src/main/resources/application-k8s.yml`
- `payment-service/src/main/resources/application-k8s.yml`
- `notification-service/src/main/resources/application-k8s.yml`
- `api-gateway/src/main/resources/application-k8s.yml`

### Files to Modify
- `auth-service/src/main/resources/application.yml` (add health groups)
- `movie-service/src/main/resources/application.yml` (add health groups)
- `booking-service/src/main/resources/application.yml` (add health groups)
- `payment-service/src/main/resources/application.yml` (add health groups)
- `notification-service/src/main/resources/application.yml` (add health groups)
- `api-gateway/src/main/resources/application.yml` (add health groups)

### Files to Delete
- None

## Implementation Steps

### Step 1: Add Health Groups to All Services
For each service's existing `application.yml`, add under `management:`:
```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```
This enables `/actuator/health/liveness` and `/actuator/health/readiness` endpoints. Works for both Docker Compose and K8s.

### Step 2: Create application-k8s.yml for auth-service
```yaml
spring:
  config:
    import: ""  # Disable config server
  datasource:
    url: jdbc:postgresql://postgres:5432/testdb
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  data:
    redis:
      host: redis
      port: 6379
  kafka:
    bootstrap-servers: local-kafka-kafka-bootstrap:9092

eureka:
  client:
    enabled: false
```

### Step 3: Create application-k8s.yml for movie-service
```yaml
spring:
  config:
    import: ""
  datasource:
    url: jdbc:postgresql://postgres:5432/moviedb
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  kafka:
    bootstrap-servers: local-kafka-kafka-bootstrap:9092

eureka:
  client:
    enabled: false
```

### Step 4: Create application-k8s.yml for booking-service
```yaml
spring:
  config:
    import: ""
  datasource:
    url: jdbc:postgresql://postgres:5432/bookingdb
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  data:
    redis:
      host: redis
      port: 6379
  kafka:
    bootstrap-servers: local-kafka-kafka-bootstrap:9092

eureka:
  client:
    enabled: false
```

### Step 5: Create application-k8s.yml for payment-service
```yaml
spring:
  config:
    import: ""
  datasource:
    url: jdbc:postgresql://postgres:5432/paymentdb
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  kafka:
    bootstrap-servers: local-kafka-kafka-bootstrap:9092

eureka:
  client:
    enabled: false
```

### Step 6: Create application-k8s.yml for notification-service
```yaml
spring:
  config:
    import: ""
  datasource:
    url: jdbc:postgresql://postgres:5432/notificationdb
    username: ${POSTGRES_USER:postgres}
    password: ${POSTGRES_PASSWORD:postgres}
  kafka:
    bootstrap-servers: local-kafka-kafka-bootstrap:9092

eureka:
  client:
    enabled: false
```

### Step 7: Create application-k8s.yml for api-gateway
```yaml
spring:
  config:
    import: ""
  cloud:
    gateway:
      mvc:
        routes:
          - id: auth-service
            uri: http://auth-service:8081
            predicates:
              - Path=/api/auth/**
          - id: auth-service-users
            uri: http://auth-service:8081
            predicates:
              - Path=/api/users/**
          - id: movie-service
            uri: http://movie-service:8082
            predicates:
              - Path=/api/movies/**
          - id: movie-service-showtimes
            uri: http://movie-service:8082
            predicates:
              - Path=/api/showtimes/**
          - id: movie-service-theaters
            uri: http://movie-service:8082
            predicates:
              - Path=/api/theaters/**
          - id: movie-service-comments
            uri: http://movie-service:8082
            predicates:
              - Path=/api/comments/**
          - id: booking-service
            uri: http://booking-service:8083
            predicates:
              - Path=/api/bookings/**
          - id: payment-service
            uri: http://payment-service:8084
            predicates:
              - Path=/api/payments/**
          - id: notification-service
            uri: http://notification-service:8085
            predicates:
              - Path=/api/notifications/**
          # Swagger routes
          - id: auth-service-docs
            uri: http://auth-service:8081
            predicates:
              - Path=/auth-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: movie-service-docs
            uri: http://movie-service:8082
            predicates:
              - Path=/movie-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: booking-service-docs
            uri: http://booking-service:8083
            predicates:
              - Path=/booking-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: payment-service-docs
            uri: http://payment-service:8084
            predicates:
              - Path=/payment-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: notification-service-docs
            uri: http://notification-service:8085
            predicates:
              - Path=/notification-service/v3/api-docs/**
            filters:
              - StripPrefix=1

eureka:
  client:
    enabled: false
```

### Step 8: Verify Compilation
```bash
mvn clean compile -pl auth-service,movie-service,booking-service,payment-service,notification-service,api-gateway
```

### Step 9: Verify Default Profile Unchanged
Run locally with Docker Compose to confirm no regressions.

## Todo List

- [ ] Add health probe config to all 6 services' application.yml
- [ ] Create auth-service/application-k8s.yml
- [ ] Create movie-service/application-k8s.yml
- [ ] Create booking-service/application-k8s.yml
- [ ] Create payment-service/application-k8s.yml
- [ ] Create notification-service/application-k8s.yml
- [ ] Create api-gateway/application-k8s.yml (full route rewrite)
- [ ] Verify `mvn clean compile` passes
- [ ] Verify Docker Compose still works (regression test)

## Success Criteria
- All services compile with `mvn clean compile`
- `SPRING_PROFILES_ACTIVE=k8s` activates K8s-specific config
- Default profile (no env var) uses existing Eureka + Config Server path
- `/actuator/health/liveness` returns 200 on all services
- `/actuator/health/readiness` returns 200 when dependencies are up
- Gateway routes resolve via K8s DNS in k8s profile

## Risk Assessment
- **Profile override conflicts**: `application-k8s.yml` must fully override connection strings. If parent `application.yml` sets `spring.datasource.url`, the k8s profile override takes precedence. Verified by Spring Boot profile precedence rules.
- **Eureka client still auto-registered**: `eureka.client.enabled: false` prevents registration. If Eureka dependency still on classpath, that's fine — it just won't activate.
- **Config server import**: Setting `spring.config.import: ""` in k8s profile overrides the `optional:configserver:` import. Empty string disables it.

## Security Considerations
- DB passwords come from K8s Secrets via env vars (`${POSTGRES_PASSWORD}`)
- JWT secret from K8s Secret (`${JWT_SECRET}`)
- No hardcoded secrets in application-k8s.yml files
- Stripe/mail credentials from K8s Secrets

## Next Steps
- Phase 1 provides the ConfigMaps that mount these files
- Phase 5 builds services with `mvn package` (profiles embedded in JAR)
- Phase 6 validates end-to-end on Minikube
