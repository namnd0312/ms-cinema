# Scout: Existing Auth Patterns in ms-cinema

## 1. DB Migration Tool

**Status**: None configured. Using Hibernate DDL auto.

- **auth-service/pom.xml**: No liquibase-core or flyway-core dependency.
- **auth-service/src/main/resources/**: No `db/changelog/` or `db/migration/` dirs.
- **Configuration**: `spring.jpa.hibernate.ddl-auto: update` (auth-service/src/main/resources/application.yml:8)
- **Implication**: Schema auto-generated from JPA entities. Manual migration scripts not tracked (roles.sql exists at auth-service/src/main/resources/roles.sql:1, but not part of managed migration system).
- **Limitation**: No versioned migration history. Fresh DB auto-creates schema, but audit/compliance cannot track schema changes.

---

## 2. Existing JwtService + JwtAuthenticationFilter (auth-service)

### JwtService (`auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`)

**Secret & Algorithm**:
- Line 26: `@Value("${namnd.app.jwtSecret}")` → config key `namnd.app.jwtSecret`
- Line 27: SECRET_KEY (Base64 decoded, line 35)
- Line 36: `Keys.hmacShaKeyFor(keyBytes)` → **HS512 algorithm**
- Default value in application.yml:43: `kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==`

**Token Expiration**:
- Line 29-30: `@Value("${namnd.app.jwtExpiration}")` → config key `namnd.app.jwtExpiration` (900000 ms = 15 min, line 44)
- Also: `namnd.app.jwtRefreshExpiration` = 604800000 (7 days, line 45)

**Method Signatures**:
- `generateTokenLogin(Authentication)` — Line 39: generates token with roles + userId
- `generateTokenFromEmail(String email)` — Line 57: legacy, no roles/userId
- `generateTokenFromEmail(String email, Long userId, List<String> roles)` — Line 68: with claims
- `validateJwtToken(String)` — Line 80: boolean return
- `getEmailFromJwtToken(String)` — Line 101: extracts subject
- `getJtiFromToken(String)` — Line 110: extracts JTI
- `getExpirationFromToken(String)` — Line 119
- `getRolesFromToken(String)` — Line 129: `List<String>`
- `getUserIdFromToken(String)` — Line 138: `Long`

**JWT Claims**:
- Line 46: `subject()` ← email
- Line 47: `id()` ← UUID (JTI)
- Line 48: `claim("roles", roles)` ← list of role strings
- Line 49: `claim("userId", userId)` ← Long ID
- Line 50-51: `issuedAt()`, `expiration()`

**No `kid` (key ID) or algorithm in header** — HS512 is implicit.

### JwtAuthenticationFilter (`auth-service/src/main/java/com/namnd/cinema/config/filter/JwtAuthenticationFilter.java`)

- Line 23: Extends `OncePerRequestFilter`
- Line 38: Extracts token via `getJwtFromRequest()` (line 65)
- Line 40: Validates via `jwtService.validateJwtToken(jwt)`
- Line 42-43: Checks JTI blacklist (Redis-backed, `BlacklistedTokenService`)
- Line 46: Loads `UserDetails` from `userService.loadUserByUsername(email)`
- Line 49-51: Creates `UsernamePasswordAuthenticationToken(userDetails, null, authorities)`
- Line 55: Sets `SecurityContextHolder` auth
- Line 66-69: Bearer token extraction from Authorization header

**Filters out blacklisted tokens by JTI** before setting SecurityContext.

---

## 3. jwt-auth-autoconfigure Shared Lib

### Directory Tree
```
jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/
├── JwtAutoConfiguration.java
├── JwtAuthProperties.java
├── JwtAuthenticationFilter.java
├── JwtTokenValidator.java
├── JwtAuthenticatedUser.java
├── ServiceNameHeaderFilter.java
├── TraceIdResponseHeaderFilter.java
└── TrailingSlashWebMvcConfig.java
```

### Auto-Configuration (`JwtAutoConfiguration.java:26`)

- `@AutoConfiguration` before `SecurityAutoConfiguration` (line 26)
- `@ConditionalOnProperty("jwt.auth.enabled", "true", default: true)` (line 29)
- `@EnableConfigurationProperties(JwtAuthProperties.class)` (line 30)
- Registered in META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports (line 1)

### JwtAuthProperties (`JwtAuthProperties.java:10`)

- Prefix: `jwt.auth`
- Fields:
  - `secret` (String) — Line 14: Base64-encoded HS512 key (required)
  - `enabled` (boolean) — Line 17: default true
  - `publicPaths` (String[]) — Line 20: ant patterns for public endpoints

**No `kid`, `algorithm`, `issuer`, `audience` properties** — all hardcoded HS512, no algorithm negotiation.

### JwtAuthenticationFilter Shared (`JwtAuthenticationFilter.java`)

- Line 24: Lightweight version for downstream services
- Line 42: Parses claims via `tokenValidator.parseClaims(jwt)` (returns Claims or null)
- Line 45-47: Extracts email, userId, roles from claims
- Line 53: Creates `JwtAuthenticatedUser` principal (record, line 10)
- Line 55-56: Sets `UsernamePasswordAuthenticationToken` with authorities
- Line 72-78: Bearer token extraction (identical to auth-service filter)

**No DB lookup, no blacklist check** — claims-only validation. Assumes auth-service validated and signed.

### JwtAuthenticatedUser (`JwtAuthenticatedUser.java`)

Record with fields:
- `Long userId`
- `String email`
- `List<String> roles`

No dependency on auth-service UserPrinciple or JPA.

### JwtTokenValidator (`JwtTokenValidator.java`)

- Line 25-27: Constructor takes base64Secret, decodes to SecretKey
- `parseClaims(String token)` — Line 34: returns Claims or null (soft-fail on invalid/expired)
- `getEmail(Claims)` — Line 47: `claims.getSubject()`
- `getUserId(Claims)` — Line 51: `claims.get("userId", Long.class)`
- `getRoles(Claims)` — Line 56: `claims.get("roles", List.class)` with empty-list fallback

**Algorithm**: Implicit HS512 via `Keys.hmacShaKeyFor()` (line 27).

---

## 4. @Auditable Annotation Pattern

### @Auditable Definition (`kafka-events/src/main/java/com/namnd/kafka/events/audit/Auditable.java`)

- Line 15-16: Method-level annotation, retained at runtime
- Parameters:
  - `action` (AuditAction enum, required) — Line 18
  - `entityType` (String, optional) — Line 19: empty string default (inferred from class name if omitted)

### AuditAction Enum (`kafka-events/src/main/java/com/namnd/kafka/events/domain/AuditAction.java`)

Values: `CREATE`, `READ`, `UPDATE`, `DELETE`, `LOGIN`, `LOGOUT`, `CUSTOM`

### Aspect Interceptor (`AuditAspect.java`)

- Line 21: `@Aspect` class
- Line 41-42: `@Around("@annotation(auditable)")` — intercepts annotated methods
- **Flow** (lines 42-75):
  1. Proceed with method execution (line 43)
  2. Extract userId from SecurityContext (line 46, via reflection on principal)
  3. Extract entityId from method args[0] or result.getId() (line 47)
  4. Infer entityType if not provided (line 48-49, strips "ServiceImpl"/"Service"/"Controller" suffix)
  5. Serialize result to JSON as `afterState` (line 50, skip for DELETE/LOGOUT/LOGIN)
  6. Create `AuditEvent` object (line 53-64)
  7. Publish `AuditSpringEvent` via ApplicationEventPublisher (line 66)

### Event Payload: AuditEvent Record (`kafka-events/src/main/java/com/namnd/kafka/events/domain/AuditEvent.java`)

```java
record AuditEvent(
    String userId,
    String userIp,
    AuditAction action,
    String entityType,
    String entityId,
    String beforeState,
    String afterState,
    String sourceService,
    String traceId,
    String requestPath
)
```

- `userId`: Extracted from JWT (email) or fallback to auth.getName()
- `userIp`: Via `AuditHttpContext.getClientIp()`
- `action`: From annotation
- `entityType`: From annotation or inferred
- `entityId`: From method args[0] or return value's getId()
- `beforeState`: Always null (no pre-image capture currently)
- `afterState`: Serialized result JSON (null for DELETE/LOGOUT/LOGIN)
- `sourceService`: From `spring.application.name`
- `traceId`: From Micrometer Tracer (MDC)
- `requestPath`: Via `AuditHttpContext.getRequestPath()`

### Kafka Publishing (`AuditEventPublisher.java`)

- Line 26-41: Publishes to `KafkaTopics.AUDIT_EVENTS` topic (line 31, string constant "audit-events")
- Line 27: Message key = `entityType:entityId` for partition ordering
- Line 28-29: Wraps AuditEvent in `EventEnvelope<AuditEvent>` with metadata
- Async send via `kafkaTemplate.send()` with callback logging (line 32-40)

### Example Usage (`auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java:100`)

```java
@Auditable(action = AuditAction.LOGIN, entityType = "User")
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequestDto loginRequest) { ... }
```

---

## Unresolved Questions

1. **DB Migrations**: Should SSO/OAuth2 integration require versioned schema migrations (Liquibase/Flyway)? Current auto-ddl approach insufficient for audit trail?
2. **Token TTL**: Is 15-min access token + 7-day refresh optimal for OAuth2 partner flow? Any vendor-specific constraints?
3. **kid (Key ID) Header**: No support for key rotation or multi-key scenarios (needed for partner OAuth2 integration?).
4. **beforeState in Audit**: Currently null. Should OAuthProvider tokens / state changes capture pre-image?
5. **HS512 Secret Sharing**: Shared libs use same secret as auth-service. Multi-tenant or per-partner key isolation planned?
