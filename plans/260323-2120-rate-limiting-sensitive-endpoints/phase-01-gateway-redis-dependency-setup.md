# Phase 1: Gateway Redis Dependency Setup

## Context Links

- [plan.md](./plan.md)
- [api-gateway pom.xml](../../api-gateway/pom.xml)
- [api-gateway application.yml](../../api-gateway/src/main/resources/application.yml)
- [docker-compose.yml](../../docker-compose.yml)

## Overview

- **Date:** 2026-03-23
- **Priority:** P2
- **Status:** pending
- **Review:** pending
- **Description:** Add `spring-boot-starter-data-redis` to api-gateway and configure Redis connection

## Key Insights

- Redis already runs as `redis-service` on port 6379 in docker-compose
- auth-service, booking-service, notification-service already use Redis
- API Gateway currently has NO Redis dependency
- Gateway is servlet-based (MVC), so use `spring-boot-starter-data-redis` (NOT reactive)
- Redis connection config likely already shared via config-server but gateway needs explicit dependency

## Requirements

### Functional
- api-gateway connects to Redis on startup
- Connection uses same Redis instance as other services

### Non-Functional
- Fail-open: if Redis unavailable, requests pass through (availability > enforcement)
- Connection pool via Lettuce (Spring Boot default)

## Architecture

```
Client -> API Gateway (RateLimitFilter) -> Redis (token bucket check) -> downstream service
                                              |
                                        ALLOW or 429
```

## Related Code Files

### Files to Modify
- `api-gateway/pom.xml` — add `spring-boot-starter-data-redis`
- `api-gateway/src/main/resources/application.yml` — add Redis host config
- `docker-compose.yml` — add `redis-service` dependency and `REDIS_HOST` env var for api-gateway service

### Files to Create
- None in this phase

## Implementation Steps

### Step 1: Add Redis dependency to api-gateway pom.xml

Add after the actuator dependency:

```xml
<!-- Redis for rate limiting -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### Step 2: Configure Redis connection in application.yml

Add under `spring:` section:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```

### Step 3: Update docker-compose.yml

In the api-gateway service block, add:
- `depends_on: redis-service`
- Environment variable: `REDIS_HOST: redis-service`

(Follow same pattern as auth-service, booking-service already in compose)

### Step 4: Verify compilation

```bash
cd api-gateway && mvn clean compile
```

## Todo List

- [ ] Add `spring-boot-starter-data-redis` to api-gateway pom.xml
- [ ] Add Redis host/port config to application.yml
- [ ] Update docker-compose.yml with Redis dependency for api-gateway
- [ ] Verify `mvn clean compile` passes

## Success Criteria

- api-gateway compiles with Redis dependency
- `StringRedisTemplate` bean available in Spring context
- Docker compose wires api-gateway to redis-service

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis connection failure on startup | Gateway fails to start | Use `spring.data.redis.timeout` and fail-open in filter |
| Version conflict with existing deps | Build failure | Spring Boot BOM manages Redis starter version |

## Security Considerations

- Redis connection within Docker `my-net` network — no external exposure
- No auth on Redis (matches existing setup)

## Next Steps

- Proceed to [Phase 2: Rate Limiter Configuration](./phase-02-rate-limiter-configuration.md)
