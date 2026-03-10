# Phase 1: Project Scaffolding — Maven Modules, Docker, Gateway Routes

## Context Links
- [Parent Plan](./plan.md)
- [System Architecture](../../docs/system-architecture.md)
- [Codebase Summary](../../docs/codebase-summary.md)
- [Root pom.xml](../../pom.xml)
- [docker-compose.yml](../../docker-compose.yml)
- [Gateway application.yml](../../api-gateway/src/main/resources/application.yml)

## Overview
- **Priority:** P0 (blocks all other phases)
- **Status:** Pending
- **Effort:** 2h
- **Description:** Create 3 new Maven modules, Dockerfiles, docker-compose entries, gateway routes, and PostgreSQL databases. No business logic — pure scaffolding.

## Key Insights
- Existing pattern: each module has `pom.xml` with parent `com.namnd:spring-jwt`, `Dockerfile` copying from `target/`, `docker-compose.yml` entry
- New services use `jwt-auth-spring-boot-starter` (not spring-boot-starter-security directly)
- Config Server distributes shared JWT secret via `jwt.auth.secret` property
- Each service needs Eureka Client + Config Client + Actuator

## Requirements

### Functional
- 3 Maven modules: `movie-service`, `booking-service`, `payment-service`
- Each with Spring Boot main class, application.yml, Dockerfile
- docker-compose entries with dedicated PostgreSQL DBs
- Gateway routes for `/api/movies/**`, `/api/bookings/**`, `/api/payments/**`

### Non-functional
- Follow existing module patterns exactly (parent pom, build config, Dockerfile format)
- Services start and register with Eureka successfully
- JWT validation works via starter library

## Architecture

### New Module Layout (per service)
```
{service-name}/
├── pom.xml
├── Dockerfile
└── src/main/
    ├── java/com/namnd/{servicepkg}/
    │   └── {ServiceName}Application.java
    └── resources/
        └── application.yml
```

### Port Assignments
| Service | Port | DB |
|---------|------|----|
| movie-service | 8082 | moviedb |
| booking-service | 8083 | bookingdb |
| payment-service | 8084 | paymentdb |

## Related Code Files

### Files to Modify
- `pom.xml` (root) — add 3 new `<module>` entries
- `docker-compose.yml` — add 3 service entries + update postgres init
- `api-gateway/src/main/resources/application.yml` — add 3 route blocks

### Files to Create
Per service (x3):
- `{service}/pom.xml`
- `{service}/Dockerfile`
- `{service}/src/main/java/com/namnd/{pkg}/{Name}Application.java`
- `{service}/src/main/resources/application.yml`

## Implementation Steps

### 1. Root pom.xml — Add modules
Add to `<modules>` section:
```xml
<module>movie-service</module>
<module>booking-service</module>
<module>payment-service</module>
```

### 2. Create movie-service module

**pom.xml** — Key dependencies:
```xml
<parent>com.namnd:spring-jwt:0.0.1-SNAPSHOT</parent>
<artifactId>movie-service</artifactId>

Dependencies:
- spring-boot-starter-web
- spring-boot-starter-data-jpa
- spring-boot-starter-validation
- postgresql (runtime)
- lombok (optional)
- spring-boot-starter-test (test)
- spring-cloud-starter-netflix-eureka-client
- spring-cloud-starter-config
- spring-boot-starter-actuator
- com.namnd:jwt-auth-spring-boot-starter:${project.version}

Build: spring-boot-maven-plugin with mainClass, finalName=movie-service
```

**Application class:** `com.namnd.movieservice.MovieServiceApplication`
- `@SpringBootApplication`
- `main()` with `SpringApplication.run()`

**application.yml:**
```yaml
server:
  port: ${SERVER_PORT:8082}
spring:
  application:
    name: movie-service
  config:
    import: "optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888"
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/moviedb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
jwt:
  auth:
    secret: ${JWT_SECRET:kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==}
    public-paths:
      - /api/movies/**
      - /api/showtimes/**
      - /actuator/health
eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}
management:
  endpoints:
    web:
      exposure:
        include: health,info
```

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/movie-service.jar movie-service.jar
ENTRYPOINT ["java", "-jar", "movie-service.jar"]
```

### 3. Create booking-service module (same pattern)
- Package: `com.namnd.bookingservice`
- Port: 8083, DB: bookingdb
- Extra deps: `spring-boot-starter-data-redis`, `spring-cloud-starter-openfeign`
- application.yml: add Redis config, Feign config
- Public paths: `/actuator/health` only (all booking endpoints require auth)
- Add `@EnableFeignClients` on main class

### 4. Create payment-service module (same pattern)
- Package: `com.namnd.paymentservice`
- Port: 8084, DB: paymentdb
- Extra deps: `spring-cloud-starter-openfeign`, `com.stripe:stripe-java:28.2.0`
- Add Stripe config properties in application.yml
- Public paths: `/api/payments/webhook/**`, `/actuator/health`
- Add `@EnableFeignClients` on main class

### 5. Update docker-compose.yml

Add to existing postgres-service an init script that creates 3 DBs, OR create separate postgres entries. **Simplest approach:** single postgres instance, init script creates all DBs.

Add init script volume mount to postgres-service:
```yaml
postgres-service:
  volumes:
    - postgres-data:/var/lib/postgresql/data
    - ./init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql
```

Create `init-databases.sql`:
```sql
CREATE DATABASE moviedb;
CREATE DATABASE bookingdb;
CREATE DATABASE paymentdb;
```

Add 3 new service entries following auth-service pattern:
```yaml
movie-service:
  build:
    context: ./movie-service
    dockerfile: Dockerfile
  ports: ["8082:8082"]
  networks: [my-net]
  depends_on: [postgres-service, eureka-server, config-server]
  environment:
    - SERVER_PORT=8082
    - EUREKA_HOST=eureka-server
    - CONFIG_SERVER_HOST=config-server
    - DB_HOST=postgres-service
    - DB_USERNAME=postgres
    - DB_PASSWORD=postgres
  restart: unless-stopped

booking-service:
  # same pattern, port 8083, add REDIS_HOST=redis-service

payment-service:
  # same pattern, port 8084, add STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET
```

### 6. Update API Gateway routes

Add to `api-gateway/src/main/resources/application.yml`:
```yaml
- id: movie-service
  uri: lb://movie-service
  predicates:
    - Path=/api/movies/**
- id: movie-service-showtimes
  uri: lb://movie-service
  predicates:
    - Path=/api/showtimes/**
- id: movie-service-theaters
  uri: lb://movie-service
  predicates:
    - Path=/api/theaters/**
- id: booking-service
  uri: lb://booking-service
  predicates:
    - Path=/api/bookings/**
- id: payment-service
  uri: lb://payment-service
  predicates:
    - Path=/api/payments/**
```

### 7. Build & Verify
```bash
mvn clean install -pl movie-service,booking-service,payment-service
docker compose up -d --build
# Verify all services register in Eureka dashboard (:8761)
```

## Todo List
- [ ] Add 3 modules to root pom.xml
- [ ] Create movie-service scaffolding (pom, app class, application.yml, Dockerfile)
- [ ] Create booking-service scaffolding
- [ ] Create payment-service scaffolding
- [ ] Create init-databases.sql for PostgreSQL
- [ ] Update docker-compose.yml with 3 new services
- [ ] Update API Gateway routes
- [ ] Build all modules with `mvn clean install`
- [ ] Docker compose up and verify Eureka registration

## Success Criteria
- All 3 new services compile and produce JARs
- Docker compose starts all 9 services (6 existing + 3 new)
- All 3 new services visible in Eureka dashboard
- Gateway routes `/api/movies/test`, `/api/bookings/test`, `/api/payments/test` reach correct services
- JWT validation works (401 on protected endpoints without token)

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Port conflicts | Build blocked | Use unique ports 8082-8084 |
| DB init script not running | Services fail to start | Use docker-entrypoint-initdb.d (only runs on fresh volume) |
| Starter library version mismatch | JWT validation fails | Use `${project.version}` reference |

## Security Considerations
- JWT secret shared via Config Server (same as auth-service)
- movie-service browse endpoints are public; admin CRUD requires ROLE_ADMIN
- payment-service webhook endpoint is public but uses Stripe signature verification
- All other endpoints require valid JWT

## Next Steps
- Phase 2: Implement movie-service entities and API
