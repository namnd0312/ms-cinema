# Phase 1: Add Micrometer Prometheus Registry & Configure Actuator

## Context Links
- [Parent Plan](./plan.md)
- [Prometheus Research](./reports/researcher-01-prometheus-micrometer.md)
- [Codebase Summary](../../docs/codebase-summary.md)

## Overview
- **Priority:** P1 (blocking for Phase 2-3)
- **Status:** Pending
- **Description:** Add `micrometer-registry-prometheus` dependency to all 7 services and configure actuator to expose `/actuator/prometheus` endpoint. Add common `application` tag for Grafana filtering.

## Key Insights
- Spring Boot 3.4.3 BOM manages Micrometer version — no explicit version needed
- JVM, HikariCP, Redis/Lettuce, Kafka, Gateway metrics all auto-instrumented once Micrometer + Actuator present
- eureka-server and config-server lack actuator dependency — must add both actuator + micrometer
- Services using `jwt-auth-spring-boot-starter` need `/actuator/prometheus` in `public-paths` to allow unauthenticated scraping

## Requirements
- All 7 services expose `/actuator/prometheus` returning Prometheus text format
- All metrics tagged with `application=${spring.application.name}` for Grafana variable filtering
- No authentication required for `/actuator/prometheus` (internal Docker network only)

## Related Code Files

### Files to MODIFY
| File | Change |
|------|--------|
| `auth-service/pom.xml` | Add `micrometer-registry-prometheus` |
| `api-gateway/pom.xml` | Add `micrometer-registry-prometheus` |
| `movie-service/pom.xml` | Add `micrometer-registry-prometheus` |
| `booking-service/pom.xml` | Add `micrometer-registry-prometheus` |
| `payment-service/pom.xml` | Add `micrometer-registry-prometheus` |
| `eureka-server/pom.xml` | Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `config-server/pom.xml` | Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` |
| `auth-service/src/main/resources/application.yml` | Expose prometheus endpoint, add metrics tag |
| `api-gateway/src/main/resources/application.yml` | Expose prometheus endpoint, add metrics tag |
| `movie-service/src/main/resources/application.yml` | Expose prometheus endpoint, add metrics tag, add public-path |
| `booking-service/src/main/resources/application.yml` | Expose prometheus endpoint, add metrics tag, add public-path |
| `payment-service/src/main/resources/application.yml` | Expose prometheus endpoint, add metrics tag, add public-path |
| `eureka-server/src/main/resources/application.yml` | Add management config block |
| `config-server/src/main/resources/application.yml` | Add management config block |
| `auth-service/.../config/security/SecurityConfig.java` | Add `/actuator/**` to permitAll |
| `auth-service/.../config/MetricsConfig.java` | **CREATE** — custom auth counters |
| `auth-service/.../controller/AuthController.java` | Inject MeterRegistry, increment counters |
| `booking-service/.../config/MetricsConfig.java` | **CREATE** — custom booking counters |
| `payment-service/.../config/MetricsConfig.java` | **CREATE** — custom payment counters |

## Implementation Steps

### Step 1: Add Maven dependency to 5 services (already have actuator)

Add to `auth-service/pom.xml`, `api-gateway/pom.xml`, `movie-service/pom.xml`, `booking-service/pom.xml`, `payment-service/pom.xml`:

```xml
<!-- Prometheus metrics export -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Place after existing `spring-boot-starter-actuator` dependency in each file.

### Step 2: Add actuator + micrometer to eureka-server and config-server

Add to both `eureka-server/pom.xml` and `config-server/pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Step 3: Update application.yml for all services

**Common management block** (add/update in each service):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  metrics:
    tags:
      application: ${spring.application.name}
```

**auth-service** — update existing `management` block (line 61-68): change `include: health,info` → `include: health,info,prometheus`, add `metrics.tags.application`.

**api-gateway** — update existing `management` block (line 49-53): change `include: health,info,gateway` → `include: health,info,gateway,prometheus`, add `metrics.tags.application`.

**movie-service** — update existing `management` block (line 32-36): change include, add metrics tag.

**booking-service** — update existing `management` block (line 43-47): change include, add metrics tag.

**payment-service** — update existing `management` block (line 42-46): change include, add metrics tag.

**eureka-server** — add entire `management` block (new).

**config-server** — add entire `management` block (new).

### Step 4: Add `/actuator/prometheus` to JWT starter public-paths

For services using `jwt-auth-spring-boot-starter` (movie, booking, payment), update `jwt.auth.public-paths`:

```yaml
jwt:
  auth:
    public-paths:
      - /actuator/health
      - /actuator/prometheus   # ← ADD THIS
```

**payment-service** also has `/api/payments/webhook` — keep it.

### Step 5: Fix auth-service SecurityConfig to permit actuator endpoints

**CRITICAL:** auth-service has its own `SecurityConfig.java` that only permits `/api/auth/**`. Without this fix, Prometheus gets 401/403 when scraping auth-service.

Update `auth-service/src/main/java/com/namnd/springjwt/config/security/SecurityConfig.java`:

Add `/actuator/**` to the `requestMatchers(...).permitAll()` chain:

```java
.requestMatchers("/api/auth/**", "/actuator/**").permitAll()
```

### Step 6: Add custom business metrics beans

Create Micrometer Counter/Timer beans for business events. Each service gets a `@Configuration` class that registers custom metrics.

**auth-service** — create `MetricsConfig.java`:
- `Counter auth.login.success` — incremented on successful login
- `Counter auth.login.failure` — incremented on failed login (bad creds or locked)
- `Counter auth.register` — incremented on user registration
- `Counter auth.token.refresh` — incremented on token refresh
- `Counter auth.logout` — incremented on logout

Then inject `MeterRegistry` into `AuthController` and increment counters at appropriate points.

**booking-service** — create `MetricsConfig.java`:
- `Counter booking.created` — incremented when booking is created
- `Counter booking.confirmed` — incremented when booking is confirmed
- `Counter booking.cancelled` — incremented when booking is cancelled

**payment-service** — create `MetricsConfig.java`:
- `Counter payment.initiated` — incremented when payment is started
- `Counter payment.completed` — incremented on successful payment
- `Counter payment.failed` — incremented on payment failure

Each counter should be tagged with `application=${spring.application.name}` (inherits from global tag set in application.yml).

### Step 7: Compile and verify

```bash
mvn clean compile -pl auth-service,api-gateway,movie-service,booking-service,payment-service,eureka-server,config-server
```

## Todo List
- [ ] Add `micrometer-registry-prometheus` to auth-service pom.xml
- [ ] Add `micrometer-registry-prometheus` to api-gateway pom.xml
- [ ] Add `micrometer-registry-prometheus` to movie-service pom.xml
- [ ] Add `micrometer-registry-prometheus` to booking-service pom.xml
- [ ] Add `micrometer-registry-prometheus` to payment-service pom.xml
- [ ] Add actuator + micrometer to eureka-server pom.xml
- [ ] Add actuator + micrometer to config-server pom.xml
- [ ] Update auth-service application.yml (expose prometheus, add tag)
- [ ] Update api-gateway application.yml (expose prometheus, add tag)
- [ ] Update movie-service application.yml (expose prometheus, add tag, public-path)
- [ ] Update booking-service application.yml (expose prometheus, add tag, public-path)
- [ ] Update payment-service application.yml (expose prometheus, add tag, public-path)
- [ ] Add management block to eureka-server application.yml
- [ ] Add management block to config-server application.yml
- [ ] Update auth-service SecurityConfig.java to permit `/actuator/**`
- [ ] Create MetricsConfig.java in auth-service (login/register/logout counters)
- [ ] Inject MeterRegistry into AuthController and increment counters
- [ ] Create MetricsConfig.java in booking-service (booking counters)
- [ ] Create MetricsConfig.java in payment-service (payment counters)
- [ ] Run `mvn clean compile` to verify no errors

## Success Criteria
- All 7 services compile without errors
- Each service exposes `/actuator/prometheus` returning Prometheus text metrics
- All metrics include `application` label matching service name

## Risk Assessment
- **Low risk:** Adding a read-only metrics endpoint; no business logic changes
- **eureka-server quirk:** Eureka may need `management.server.port` if main port conflicts — unlikely since 8761 is standard

## Security Considerations
- `/actuator/prometheus` unauthenticated but only accessible within Docker `my-net` network
- Not routed through API Gateway (gateway only routes `/api/**` paths)
- JWT starter public-paths ensure Prometheus can scrape without bearer token

## Next Steps
- Phase 2: Prometheus infrastructure (depends on this phase completing)
