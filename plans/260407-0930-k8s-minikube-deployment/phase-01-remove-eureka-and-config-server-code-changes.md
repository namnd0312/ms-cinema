# Phase 1: Remove Eureka & Config-Server — Spring Boot Code Changes

## Context Links
- [Plan Overview](./plan.md)
- [api-gateway application.yml](/api-gateway/src/main/resources/application.yml)
- [config-server config-repo](/config-server/src/main/resources/config-repo/application.yml)
- [booking-service MovieServiceClient.java](/booking-service/src/main/java/com/namnd/bookingservice/client/MovieServiceClient.java)

## Overview
- **Priority:** Critical (prerequisite for all K8s phases)
- **Status:** Pending
- **Description:** Disable Eureka and config-server across all services via K8s Spring profile. Migrate config-server values to env vars. Switch api-gateway to static URI routing. Fix FeignClient.

## Key Insights

### Eureka Usage (all 8 services)
- All services have `eureka.client.service-url.defaultZone` in application.yml
- api-gateway: 13+ routes using `lb://service-name`
- booking-service: 1 FeignClient (`MovieServiceClient`) using Eureka name resolution
- No other inter-service Feign/RestTemplate calls

### Config-Server Provides (via classpath config-repo)
- `namnd.app.jwtSecret` / `namnd.app.jwtAlias` → move to K8s **Secret**
- `spring.kafka.bootstrap-servers` + serializer/deserializer config → move to K8s **ConfigMap** env vars
- `spring.kafka.properties.spring.json.trusted.packages` → ConfigMap env var
- `management.zipkin.tracing.endpoint` → ConfigMap env var
- Services use `spring.config.import: "optional:configserver:..."` — `optional:` means they start fine without it

### Strategy
- Use `application-k8s.yml` Spring profile per service where needed
- `SPRING_PROFILES_ACTIVE=k8s` in K8s ConfigMap enables K8s mode
- Docker Compose mode (default profile) still works with Eureka + config-server

## Requirements
### Functional
- All services start without Eureka and config-server in K8s
- Config values (JWT, Kafka, Zipkin) provided via env vars
- api-gateway routes via static URIs
- Docker Compose mode unaffected

### Non-functional
- No pom.xml changes — backward compatible

## Implementation Steps

### Step 1: Create `api-gateway/src/main/resources/application-k8s.yml`
Overrides routes with static URIs and disables Eureka:

```yaml
spring:
  cloud:
    gateway:
      routes:
        # Auth routes
        - id: auth-service
          uri: http://auth-service:8081
          predicates:
            - Path=/api/auth/**
        - id: auth-users
          uri: http://auth-service:8081
          predicates:
            - Path=/api/users/**
        - id: oauth2-auth
          uri: http://auth-service:8081
          predicates:
            - Path=/oauth2/authorization/**
        - id: oauth2-callback
          uri: http://auth-service:8081
          predicates:
            - Path=/login/oauth2/code/**
        # Movie routes
        - id: movie-service
          uri: http://movie-service:8082
          predicates:
            - Path=/api/movies/**
        - id: showtime-service
          uri: http://movie-service:8082
          predicates:
            - Path=/api/showtimes/**
        - id: theater-service
          uri: http://movie-service:8082
          predicates:
            - Path=/api/theaters/**
        - id: comment-service
          uri: http://movie-service:8082
          predicates:
            - Path=/api/comments/**
        # Booking routes
        - id: booking-ws
          uri: http://booking-service:8083
          predicates:
            - Path=/ws/**
        - id: booking-service
          uri: http://booking-service:8083
          predicates:
            - Path=/api/bookings/**
        # Other services
        - id: payment-service
          uri: http://payment-service:8084
          predicates:
            - Path=/api/payments/**
        - id: notification-service
          uri: http://notification-service:8085
          predicates:
            - Path=/api/notifications/**
        - id: audit-service
          uri: http://audit-service:8086
          predicates:
            - Path=/api/audit/**
        # Swagger docs routes
        - id: auth-service-docs
          uri: http://auth-service:8081
          predicates:
            - Path=/auth-service/v3/api-docs/**
        - id: movie-service-docs
          uri: http://movie-service:8082
          predicates:
            - Path=/movie-service/v3/api-docs/**
        - id: booking-service-docs
          uri: http://booking-service:8083
          predicates:
            - Path=/booking-service/v3/api-docs/**
        - id: payment-service-docs
          uri: http://payment-service:8084
          predicates:
            - Path=/payment-service/v3/api-docs/**

eureka:
  client:
    enabled: false
```

**Note:** Read current `application.yml` routes carefully to replicate all predicates/filters exactly.

### Step 2: Disable Eureka + Config-Server in All Services
K8s ConfigMap will set `SPRING_PROFILES_ACTIVE=k8s`. Each service needs K8s handling:

For services **without** a `application-k8s.yml` file, env vars handle it:
```
EUREKA_CLIENT_ENABLED=false
SPRING_CLOUD_CONFIG_ENABLED=false
```

These map to:
- `eureka.client.enabled=false`
- `spring.cloud.config.enabled=false`

### Step 3: Migrate Config-Server Values to Env Vars
Config-server served these shared properties. In K8s, inject via ConfigMap/env:

| Config-Server Property | K8s Env Var | Where |
|----------------------|-------------|-------|
| `namnd.app.jwtSecret` | `NAMND_APP_JWTSECRET` | Secret |
| `namnd.app.jwtAlias` | `NAMND_APP_JWTALIAS` | ConfigMap |
| `spring.kafka.bootstrap-servers` | `SPRING_KAFKA_BOOTSTRAP_SERVERS` | ConfigMap |
| `spring.kafka.consumer.value-deserializer` | `SPRING_KAFKA_CONSUMER_VALUE_DESERIALIZER` | ConfigMap |
| `spring.kafka.producer.value-serializer` | `SPRING_KAFKA_PRODUCER_VALUE_SERIALIZER` | ConfigMap |
| `spring.kafka.consumer.auto-offset-reset` | `SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET` | ConfigMap |
| `spring.kafka.properties.spring.json.trusted.packages` | `SPRING_KAFKA_PROPERTIES_SPRING_JSON_TRUSTED_PACKAGES` | ConfigMap |
| `management.zipkin.tracing.endpoint` | `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | ConfigMap |

Spring Boot auto-maps `UPPER_CASE_ENV` to `lower.case.property` — no code changes needed.

### Step 4: Fix FeignClient in booking-service
```java
@FeignClient(name = "movie-service", url = "${movie-service.url:}")
```

Empty default → falls back to Eureka (Docker Compose). K8s ConfigMap sets:
```
MOVIE_SERVICE_URL=http://movie-service:8082
```

Add `movie-service.url: ${MOVIE_SERVICE_URL:}` in booking-service application-k8s.yml or rely on Spring relaxed binding.

### Step 5: Verify Compilation
```bash
mvn clean compile -pl api-gateway,booking-service
```

## Todo List
- [ ] Read current api-gateway routes (exact predicates, filters, metadata)
- [ ] Create `api-gateway/src/main/resources/application-k8s.yml`
- [ ] Update `MovieServiceClient.java` FeignClient with url property
- [ ] Create `booking-service/src/main/resources/application-k8s.yml` (if needed)
- [ ] Compile all modules: `mvn clean compile`
- [ ] Verify Docker Compose mode unaffected

## Success Criteria
- `mvn clean compile` passes for all modules
- api-gateway starts with `--spring.profiles.active=k8s` without Eureka/config-server
- Config values (JWT, Kafka, Zipkin) injectable via env vars
- Docker Compose (default profile) still works

## Risk Assessment
- **Risk:** Route definitions don't match original (missing filters)
  - **Mitigation:** Read current application.yml carefully before creating k8s profile
- **Risk:** FeignClient `url=""` empty string behavior varies
  - **Mitigation:** Test locally; alternative: `${movie-service.url:#{null}}`
- **Risk:** Spring relaxed binding doesn't map nested Kafka properties
  - **Mitigation:** Use exact Spring env var naming (SPRING_KAFKA_BOOTSTRAP_SERVERS works)

## Security Considerations
- JWT secret moves to K8s Secret (was in config-server classpath — same security level for dev)
- No new attack surface

## Next Steps
- Phase 2: Deploy infrastructure (PostgreSQL, Kafka, Redis, Zipkin) on K8s
- Phase 3: Create ConfigMap with migrated config-server values
