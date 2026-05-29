# Codebase Inventory: Slim JWT + Redis Roles + Kafka Invalidation Refactor

## A. Auth-Service: Token & Role State

### JwtService
- **Path**: `auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`
- **Key Methods**:
  - `generateTokenLogin(Authentication)` — builds JWT with `roles` & `userId` claims (lines 39-54)
  - `generateTokenFromEmail(email, userId, roles)` — overload for refresh flow (lines 68-78)
  - `getRolesFromToken(token)` — extracts `roles` claim (lines 129-136)
  - `getUserIdFromToken(token)` — extracts `userId` claim (lines 138-145)
  - `getJtiFromToken(token)` — extracts JTI for invalidation (lines 110-117)
  - `validateJwtToken(token)` — basic signature/expiry check (lines 80-99)

### AuthController
- **Path**: `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java`
- **Key Flows**:
  - `/api/auth/login` (line 102) — calls `jwtService.generateTokenLogin()` + creates RefreshToken (line 138)
  - `/api/auth/refresh-token` (line 295) — calls `jwtService.generateTokenFromEmail(email, userId, roleNames)` (lines 312-316)
  - `/api/auth/logout` (line 332) — calls `blacklistedTokenService.blacklistToken(jti, expiry)` (line 347)

### BlacklistedTokenServiceImpl
- **Path**: `auth-service/src/main/java/com/namnd/cinema/service/impl/BlacklistedTokenServiceImpl.java`
- **Current State**: Uses RedisService + `RedisKeyPrefix.BLACKLIST` (line 33)
- **TTL Strategy**: Token's remaining expiry time (lines 26-34)
- **Fail-Closed**: Returns `true` (deny) if Redis unavailable (line 46)

### RedisService (Interface)
- **Path**: `auth-service/src/main/java/com/namnd/cinema/service/RedisService.java`
- **Key Methods**: `set(key, value, timeout, unit)`, `get(key)`, `hasKey(key)`, `delete(key)`, hash/list/set/pub-sub/lock ops
- **No Impl Shown** — likely TemplateRedisServiceImpl elsewhere

### RedisKeyPrefix
- **Path**: `auth-service/src/main/java/com/namnd/cinema/config/RedisKeyPrefix.java`
- **Current Prefixes**: `BLACKLIST`, `LOCK`
- **Naming Pattern**: String constants (no enums)

### application.yml (auth-service)
- **Redis Config**: `spring.data.redis.host/port/password` (lines 18-21)
- **Kafka Config**: `spring.kafka.bootstrap-servers`, producer/listener observation enabled (lines 31-39)
- **JWT Config**: `namnd.app.jwtSecret`, `jwtExpiration` (900s), `jwtRefreshExpiration` (7d) (lines 42-45)

### Missing Inventory
- No RoleController/UserRoleController found (may not exist yet)
- No role-change endpoint identified
- No Kafka producer for role invalidation events yet

## B. jwt-auth-autoconfigure: Shared Module

### Structure
- **Path**: `jwt-auth-autoconfigure/`
- **AutoConfiguration.imports**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (references `JwtAutoConfiguration`)
- **No spring.factories** (modern Spring Boot 3.x)

### JwtAutoConfiguration
- **Path**: `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java`
- **Beans Exposed**:
  - `jwtTokenValidator` — validates token signature (line 35-37)
  - `jwtAuthenticationFilter` — intercepts requests, populates SecurityContext (line 41-43)
  - `jwtSecurityFilterChain` — configures stateless session policy (line 47-69)
  - `serviceNameHeaderFilter` — adds app name to response (line 73-81)
  - `traceIdResponseHeaderFilter` — adds X-Trace-Id to response (line 91-101)

### JwtAuthProperties
- **Path**: `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java`
- **Props**: `secret` (Base64 HS512), `enabled` (default true), `publicPaths[]` (ant patterns)

### pom.xml Dependencies
- **No spring-data-redis** (Redis not in autoconfigure module)
- **No spring-kafka** (Kafka not in autoconfigure module)
- **JJWT**: jjwt-api, jjwt-impl, jjwt-jackson (lines 44-56)
- **Micrometer Tracing**: optional (line 37-40)

### Other Files
- `JwtAuthenticationFilter` — extracts Bearer token, validates, sets SecurityContext
- `JwtTokenValidator` — calls `Jwts.parser().verify()`
- `TraceIdResponseHeaderFilter`, `ServiceNameHeaderFilter` — response headers
- No Redis/Kafka/Role abstraction layer yet

## C. Kafka-Events Module

### KafkaTopics
- **Path**: `kafka-events/src/main/java/com/namnd/kafka/events/topic/KafkaTopics.java`
- **Topic Constants**: `PAYMENT_EVENTS`, `MOVIE_EVENTS`, `NOTIFICATION_EVENTS`, `AUDIT_EVENTS`
- **Pattern**: Public String constants, no enums

### Audit Infrastructure
- `AuditEventPublisher`, `AuditAspect`, `AuditAutoConfiguration` — already wired
- Kafka producer pattern established for audit trail

### Missing
- No `RoleInvalidationEvent` class
- No `RoleChangeEvent` topic constant
- No role-event consumer/producer logic

## D. Downstream Services Dependency Matrix

| Service | jwt-auth-autoconfigure | spring-kafka | spring-data-redis |
|---------|:--:|:--:|:--:|
| booking-service | ✓ | ✓ | ✗ |
| movie-service | ✓ | ✓ | ✗ |
| notification-service | ✓ | ✓ | ✗ |
| payment-service | ✓ | ✓ | ✗ |
| audit-service | ✓ | ✓ | ✗ |

**Key Finding**: No downstream service has Redis yet; all consume jwt-autoconfigure + Kafka.

### booking-service KafkaProducerConfig
- **Path**: `booking-service/src/main/java/com/namnd/bookingservice/config/KafkaProducerConfig.java`
- **Status**: Modified (git status) — check producer bean pattern

## E. Tests

### auth-service Tests Location
- `auth-service/src/test/java/` — structure present
- Test framework: Spring Boot Test (implied by pom.xml, not inspected)
- **Missing**: No Testcontainers integration found in grep

### No integration test patterns confirmed for Redis or Kafka in auth-service

## Unresolved Questions

1. Where is `TemplateRedisServiceImpl` (RedisService implementation)?
2. Does a RoleController or role-mutation endpoint exist? (Not found in grep)
3. Is Kafka role-invalidation event domain/model already defined?
4. What is the current refresh-token invalidation mechanism (DB lookup only)?
5. How do downstream services currently invalidate cached roles (if at all)?

---

**Report Generated**: 2026-05-13  
**Paths Verified**: Auth-service, jwt-auth-autoconfigure, kafka-events, downstream services (pom.xml + app.yml)
