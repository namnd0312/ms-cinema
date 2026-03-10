# System Architecture

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Java Version:** 21 LTS
**Spring Boot:** 3.4.3
**Spring Cloud:** 2024.0.1
**Architecture Pattern:** Multi-Module Microservice with Spring Cloud
**Last Updated:** March 2026 (post-microservice migration)

## Architecture Overview

MS Cinema has migrated from a single-module monolith to an **8-module Maven multi-module project** with a Spring Cloud infrastructure layer and event-driven notification system. All external traffic enters through the API Gateway (port 8080); auth-service runs on port 8081, notification-service runs on port 8085.

### Module Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                      CLIENT (Web/Mobile)                         │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTP (port 8080)
┌──────────────────────────────▼──────────────────────────────────┐
│                    api-gateway  (:8080)                          │
│  spring-cloud-starter-gateway-mvc (servlet, NOT WebFlux)         │
│  Routes: /api/auth/** → lb://auth-service                        │
│          /api/users/** → lb://auth-service                       │
└──────────┬──────────────────────────────────┬───────────────────┘
           │ service discovery                 │ config fetch
           ▼                                   ▼
┌─────────────────────┐          ┌─────────────────────────┐
│  eureka-server      │          │  config-server  (:8888)  │
│  (:8761)            │◀─────────│  Serves shared JWT       │
│  Netflix Eureka     │ register │  secret + app configs    │
└─────────────────────┘          └─────────────────────────┘
           │ discovers
           ▼
┌──────────────────────────────────────────────────────────────┐
│                   auth-service  (:8081)                       │
│  Layered Architecture with JWT Authentication                 │
│                                                               │
│  ┌──────────────────────────────────────────────────────┐    │
│  │  PRESENTATION LAYER                                  │    │
│  │  AuthController (@RestController)                    │    │
│  │  POST /api/auth/login                                │    │
│  │  POST /api/auth/register                             │    │
│  │  GET  /api/auth/activate                             │    │
│  │  POST /api/auth/resend-activation                    │    │
│  │  POST /api/auth/validate-token  (microservice use)   │    │
│  │  GET  /api/users/me             (microservice use)   │    │
│  │                                                       │    │
│  │  EmailServiceImpl (now publishes Kafka events)        │    │
│  │  POST /notifications → Kafka (notification-events)   │    │
│  └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### JWT Starter Library & Event Modules

```
jwt-auth-spring-boot-autoconfigure
  ├─ JwtAutoConfiguration (@AutoConfiguration)
  │  ├─ @ConditionalOnClass(SecurityFilterChain.class)
  │  ├─ @ConditionalOnWebApplication(SERVLET)
  │  └─ @ConditionalOnProperty(jwt.auth.enabled=true, default)
  ├─ JwtAuthProperties (@ConfigurationProperties prefix=jwt.auth)
  ├─ JwtTokenValidator  — validates signature via shared secret
  ├─ JwtAuthenticationFilter — sets SecurityContext from token
  └─ JwtAuthenticatedUser — principal model for downstream services

jwt-auth-spring-boot-starter
  └─ Thin wrapper: declares autoconfigure + spring-boot-starter as deps
     (downstream services add this one dep to get JWT auth out of the box)

kafka-events
  └─ Domain event records (shared across services)
     └─ NotificationRequestedEvent (topic: notification-events)
```

### auth-service Internal Layers

```
                            │
        ┌───────────────────┴───────────────────┐
        │                                       │
┌───────▼────────────────────────────────────┐ │
│  SECURITY LAYER                            │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  SecurityConfig (Spring Security 6.x)   │ │
│ │  - SecurityFilterChain bean pattern     │ │
│ │  - @EnableMethodSecurity annotation     │ │
│ │  - PasswordEncoder (BCrypt)             │ │
│ │  - AuthenticationManager                │ │
│ │  - CSRF disabled, CORS enabled          │ │
│ └──────────────────────────────────────────┘  │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  JwtAuthenticationFilter             │  │ │
│ │  - Extracts Bearer token             │  │ │
│ │  - Validates JWT signature           │  │ │
│ │  - Sets SecurityContext              │  │ │
│ └──────────────────────────────────────┘  │ │
│ ┌──────────────────────────────────────┐  │ │
│ │  CustomAccessDeniedHandler           │  │ │
│ │  - Returns 403 on access denied      │  │ │
│ └──────────────────────────────────────┘  │ │
└────────────────────────────────────────────┘ │
                                               │
┌──────────────────────────────────────────────▼─────┐
│               BUSINESS LOGIC LAYER                   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserService (interface)                   │   │
│  │  ├─ save(User)                             │   │
│  │  ├─ findByEmail(String)                    │   │
│  │  ├─ existsByEmail(String)                  │   │
│  │  └─ loadUserByUsername(String) [queries by email] │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserServiceImpl                             │   │
│  │  ├─ delegates to UserRepository             │   │
│  │  └─ loadUserByUsername queries by email     │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  JwtService (@Component)                    │   │
│  │  ├─ generateTokenLogin(Authentication)      │   │
│  │  ├─ generateTokenFromEmail(String)          │   │
│  │  ├─ validateJwtToken(String)                │   │
│  │  └─ getEmailFromJwtToken(String)            │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  BlacklistedTokenServiceImpl                 │   │
│  │  ├─ delegates to RedisService               │   │
│  │  ├─ blacklistToken(jti, expiry)             │   │
│  │  └─ isTokenBlacklisted(jti)                 │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleService (interface)                    │   │
│  │  ├─ save(Role)                              │   │
│  │  ├─ findByName(String)                      │   │
│  │  └─ flush()                                 │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleServiceImpl                             │   │
│  │  └─ delegates to RoleRepository              │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│          SHARED UTILITIES LAYER (Redis)              │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisService (interface, 46 lines)        │   │
│  │  ├─ Key-Value: set, get, delete, expire    │   │
│  │  ├─ Hash: hSet, hGet, hGetAll, hDelete     │   │
│  │  ├─ List: lPush, rPush, lRange, lLen       │   │
│  │  ├─ Set: sAdd, sMembers, sIsMember         │   │
│  │  ├─ Pub/Sub: publish(channel, message)     │   │
│  │  └─ Lock: tryLock(key, timeout), unlock()  │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisServiceImpl (276 lines)                │   │
│  │  ├─ Injected: StringRedisTemplate           │   │
│  │  ├─ Injected: RedisTemplate<String, Object>│   │
│  │  ├─ Try-catch error handling per method     │   │
│  │  └─ Jackson2Json serialization              │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisKeyPrefix (constants)                 │   │
│  │  ├─ BLACKLIST = "blacklist:"                │   │
│  │  └─ LOCK = "lock:"                          │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RedisConfig (@Configuration)               │   │
│  │  └─ Provides RedisTemplate bean             │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│            DATA ACCESS LAYER (Repositories)         │
│  ┌────────────────────────────────────────────┐   │
│  │  UserRepository extends JpaRepository       │   │
│  │  ├─ findByUsername(String)                 │   │
│  │  ├─ existsByUsername(String)               │   │
│  │  ├─ findByEmail(String)                    │   │
│  │  └─ existsByEmail(String)                  │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  RoleRepository extends JpaRepository       │   │
│  │  └─ findByName(String)                     │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│           DATA PERSISTENCE LAYER (Models)           │
│  ┌────────────────────────────────────────────┐   │
│  │  User (@Entity, table: users)               │   │
│  │  ├─ id: Long (PK)                          │   │
│  │  ├─ username: String (unique)              │   │
│  │  ├─ password: String (BCrypt-encoded)      │   │
│  │  ├─ fullName: String                       │   │
│  │  ├─ failedAttempts: int (default 0)        │   │
│  │  ├─ lockTime: Date (nullable)              │   │
│  │  └─ roles: Set<Role> (ManyToMany, EAGER)   │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  Role (@Entity, table: roles)               │   │
│  │  ├─ id: Long (PK)                          │   │
│  │  └─ name: String (ROLE_USER, etc)          │   │
│  └────────────────────────────────────────────┘   │
│  ┌────────────────────────────────────────────┐   │
│  │  UserPrinciple (implements UserDetails)     │   │
│  │  ├─ adapts User for Spring Security        │   │
│  │  ├─ id, username, password, fullName       │   │
│  │  ├─ authorities (from Role names)          │   │
│  │  ├─ isAccountNonLocked() - real lock state │   │
│  │  └─ isEnabled() - returns user.active      │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│         DATABASE LAYER (PostgreSQL 16)              │
│  ┌─────────────────┐  ┌──────────┐  ┌────────────────┐ │
│  │ users           │  │ roles    │  │ user_roles     │ │
│  │ ─────────────── │  │ ──────── │  │ ────────────── │ │
│  │ id (PK)         │  │ id (PK)  │  │ user_id (FK)   │ │
│  │ username        │  │ name     │  │ role_id (FK)   │ │
│  │ password        │  │          │  │                │ │
│  │ full_name       │  │          │  │                │ │
│  │ active          │  │          │  │                │ │
│  │ failed_attempts │  │          │  │                │ │
│  │ lock_time       │  │          │  │                │ │
│  └─────────────────┘  └──────────┘  └────────────────┘ │
└─────────────────────────────────────────────────────┘
```

## Request Flow Diagrams

### Authentication Flow (Login)

```
CLIENT                           SERVER
  ├─ POST /api/auth/login ──────▶ AuthController.authenticateUser()
  │  {email, password}           │  ├─ AccountLockService.isLocked()? → 423
  │                              │  ├─ AuthenticationManager.authenticate()
  │                              │  │  ├─ loadUserByUsername(email) → DB
  │                              │  │  ├─ isEnabled()? → 401 if false
  │                              │  │  └─ BCrypt password match
  │                              │  ├─ [BadCredentials] loginFailed() → inc failedAttempts
  │                              │  ├─ loginSucceeded() → reset failedAttempts
  │                              │  ├─ JwtService.generateTokenLogin()
  │                              │  │  → HS512 signed, JTI, roles+userId claims, 15min
  │                              │  └─ RefreshTokenService.createRefreshToken() (7d, DB)
  │ ◀─ 200 OK ──────────────────┤
  │  {id, token, refreshToken,   │
  │   email, username, roles}    │
  └─ Store tokens locally ──────┘
```

### Request Authorization Flow

```
CLIENT                           SERVER
  │                                │
  ├─ GET /api/protected ──────────▶ JwtAuthenticationFilter
  │  Authorization: Bearer <token>  │
  │                                 ├─ Extract Authorization header
  │                                 ├─ Parse Bearer token
  │                                 │
  │                                 ├─ JwtService.validateJwtToken()
  │                                 │  ├─ Parse JWT signature
  │                                 │  ├─ Verify HS512 with SECRET_KEY
  │                                 │  └─ Check expiration
  │                                 │     (no DB call for validation)
  │                                 │
  │                                 ├─ JwtService.getEmailFromJwtToken()
  │                                 │  └─ Extract email from claims
  │                                 │
  │                                 ├─ UserServiceImpl.loadUserByUsername(email)
  │                                 │  └─ Load User + Roles from DB by email
  │                                 │
  │                                 ├─ Build UserPrinciple
  │                                 ├─ Create SecurityContext
  │                                 └─ Continue to Handler
  │                                    │
  │                                    ├─ Check @PreAuthorize annotations
  │                                    ├─ Verify user has required role
  │                                    │
  │                                    └─ Execute endpoint
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  {protected resource data}      │
  │
  └─────────────────────────────────┘
```

### Registration Flow

```
CLIENT                              SERVER
  │                                  │
  ├─ POST /api/auth/register ───────▶ AuthController.registerUser()
  │  {username, email, password,     │  ├─ existsByEmail() → 400 if duplicate
  │   fullName, roles}               │  ├─ BCrypt encode password
  │                                  │  ├─ Save User (active=false)
  │                                  │  └─ ActivationService.createActivationToken()
  │                                  │     └─ Email {activationBaseUrl}?token={uuid}
  │ ◀─ 200 OK ──────────────────────┤
  │  "User registered successfully!" │
  └─────────────────────────────────┘
```

### Email Activation Flow (Event-Driven)

```
CLIENT                    SERVER (auth-service)              Kafka              notification-service
  │                                │                           │                        │
  ├─ POST /api/auth/register ─────▶│ AuthController            │                        │
  │  {email, ...}                  │ ├─ Save user (active=false)                        │
  │                                │ └─ ActivationService      │                        │
  │                                │    ├─ Create token        │                        │
  │                                │    └─ publishEvent()      │                        │
  │                                │       KafkaTemplate       │                        │
  │ ◀─ 200 OK ─────────────────────│       publish to          │                        │
  │                                │       notification-events ├──────────────────────▶│
  │                                │                           │ NotificationRequested│
  │                                │                           │ Event(email, type)  │
  │                                │                           │                     │
  │                                │                           │  ├─ Consume event  │
  │                                │                           │  ├─ Build template │
  │                                │                           │  ├─ Send SMTP      │
  │                                │                           │  └─ Log status     │
  │                                │                           │                     │
  ├─ Open email, click link ──────────────────────────────────┼────────────────────┤
  │  /api/auth/activate?token=...  │ ActivationService         │                        │
  │                                │ ├─ Verify token           │                        │
  │                                │ ├─ Set active=true        │                        │
  │ ◀─ 200 OK ─────────────────────│ └─ Mark token used        │                        │
  └─ Account activated ───────────┘                           │                        │
```

### Token Refresh Flow

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/refresh-token ▶ AuthController.refreshToken()
  │  {refreshToken}                │
  │                                ├─ RefreshTokenService.findByToken()
  │                                │  └─ Database lookup
  │                                │
  │                                ├─ RefreshTokenService.verifyExpiration()
  │                                │  ├─ Check if expired
  │                                │  └─ Return RefreshToken if valid
  │                                │
  │                                ├─ JwtService.generateTokenFromEmail()
  │                                │  ├─ Extract email from user
  │                                │  ├─ Build JWT with new JTI
  │                                │  └─ Return new access token
  │                                │
  │                                ├─ RefreshTokenService.createRefreshToken()
  │                                │  ├─ Rotate: delete old, create new
  │                                │  └─ Save new RefreshToken
  │                                │
  │                                └─ Return TokenRefreshResponseDto
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  {accessToken, refreshToken}    │
  │                                 │
  └─ Update both tokens locally ───┘
```

### Password Reset Flow (Event-Driven)

```
CLIENT                    SERVER (auth-service)              Kafka              notification-service
  │                                │                           │                        │
  ├─ POST /api/auth/forgot-───────▶│ PasswordResetService      │                        │
  │    password {email}            │ ├─ Create 24h reset token │                        │
  │                                │ └─ publishEvent()         │                        │
  │                                │    KafkaTemplate ────────┼───────────────────────▶│
  │                                │    (password-reset type) │ NotificationRequested  │
  │ ◀─ 200 OK (generic) ──────────┤                           │ Event(email, link)    │
  │  "Check email if exists"       │                           │                        │
  │                                │                           │  ├─ Consume         │
  │                                │                           │  ├─ Send email      │
  │                                │                           │  └─ Log             │
  │                                │                           │                        │
  ├─ Open email, click link ──────────────────────────────────┼────────────────────┤
  │  /reset?token=...             │                           │                        │
  │                                │                           │                        │
  ├─ POST /api/auth/reset-password ▶ PasswordResetService      │                        │
  │  {token, newPassword}          │ ├─ Validate token        │                        │
  │                                │ ├─ BCrypt encode        │                        │
  │                                │ ├─ Update password      │                        │
  │ ◀─ 200 OK ────────────────────┤ └─ Delete token         │                        │
  └─ User can now login ──────────┘                           │                        │
```

### Logout Flow

```
CLIENT                           SERVER
  │                                │
  ├─ POST /api/auth/logout ──────▶ AuthController.logout()
  │  Authorization: Bearer <token> │
  │                                ├─ Extract Authorization header
  │                                ├─ Parse JWT token
  │                                │
  │                                ├─ JwtService.getJtiFromToken()
  │                                │  └─ Extract JTI claim
  │                                │
  │                                ├─ BlacklistedTokenService.blacklistToken()
  │                                │  ├─ RedisService.set(key, "1", ttl)
  │                                │  │  └─ Write to Redis: blacklist:{jti}=1
  │                                │  ├─ Set TTL = token expiration epoch
  │                                │  └─ On Redis error: fail-closed (reject token)
  │                                │
  │                                ├─ JwtService.getEmailFromJwtToken()
  │                                │  └─ Extract email
  │                                │
  │                                ├─ RefreshTokenService.deleteByUserId()
  │                                │  └─ Delete user's refresh token
  │                                │
  │                                └─ Return success
  │
  │ ◀─ 200 OK ─────────────────────┤
  │  "Logged out successfully"      │
  │                                 │
  └─ Clear tokens locally ────────┘

  (Redis auto-expires blacklist:{jti} when TTL elapses)
```

## Data Model

### Entity Relationships

```
┌──────────────────────┐         ┌──────────────────────┐
│       users          │         │       roles          │
├──────────────────────┤         ├──────────────────────┤
│ id (PK, BIGSERIAL)   │         │ id (PK, BIGSERIAL)   │
│ username (VARCHAR)        │         │ name (VARCHAR)       │
│ email (UNIQUE)            │─────┬──▶│ └─ "ROLE_USER"       │
│ password (VARCHAR)        │     │   │ └─ "ROLE_PM"         │
│ full_name (VARCHAR)       │ M:M │   │ └─ "ROLE_ADMIN"      │
│ active (BOOLEAN)          │     │   └──────────────────────┘
│ failed_attempts (INT)     │     │
│ lock_time (TIMESTAMP, NULL│     │
│ ◀─────────────────────┤     │
└─────────┬────────────┘     │
          │                  │ FK
          │ FK               │
          │ ┌────────────────┘
          │ │
          ▼ ▼
      (through user_roles)

┌──────────────────────┐     ┌──────────────────────────┐
│      user_roles      │     │   refresh_tokens         │
├──────────────────────┤     ├──────────────────────────┤
│ user_id (FK)         │     │ id (PK, BIGSERIAL)       │
│ role_id (FK)         │     │ token (VARCHAR, UNIQUE)  │
│ (PK: composite)      │     │ expiry_date (TIMESTAMP)  │
└──────────────────────┘     │ user_id (FK to users)    │
                             └──────────────────────────┘

┌───────────────────────────┐     ┌───────────────────────────┐
│ password_reset_tokens     │     │ activation_tokens         │
├───────────────────────────┤     ├───────────────────────────┤
│ id (PK, BIGSERIAL)        │     │ id (PK, BIGSERIAL)        │
│ token (VARCHAR, UNIQUE)   │     │ token (VARCHAR, UNIQUE)   │
│ expiry_date (TIMESTAMP)   │     │ expiry_date (TIMESTAMP)   │
│ user_id (FK to users)     │     │ user_id (FK to users)     │
└───────────────────────────┘     │ used (BOOLEAN, default F) │
                                  └───────────────────────────┘

REDIS (Key-Value Store)
├──────────────────────────────────┐
│ blacklist:{jti} (key)            │
│ └─ value: 1 (presence check)     │
│ └─ TTL: token expiration epoch   │
│ └─ Auto-expires when TTL elapsed │
└──────────────────────────────────┘
```

### Security Context Representation

After successful JWT validation, SecurityContext principal = `UserPrinciple`:
- `id` (Long), `username` (email), `fullName`, `authorities` (from Role names)
- `isAccountNonLocked()` checks `User.lockTime` against `lockDurationMs`
- `isEnabled()` returns `user.active` (false until email activation)
- No session stored (STATELESS policy)

## JWT Token Structure

### HS512 Token Anatomy

```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJqb2huIiwiaWF0IjoxNjM4MzYwMDAwLCJleHAiOjE2MzgzNjAwMDB9.signature...
┌─────────────────────┬─────────────────────────────────────────────┬──────┐
│      HEADER         │               PAYLOAD                        │ SIGN │
└─────────────────────┴─────────────────────────────────────────────┴──────┘

HEADER (Base64URL-decoded):
{
  "alg": "HS512",
  "typ": "JWT"
}

PAYLOAD (Base64URL-decoded):
{
  "sub": "john@example.com",        // email (used as subject)
  "jti": "uuid-string",             // JWT ID (unique identifier)
  "iat": 1638360000,                // issued at (seconds)
  "exp": 1638360900,                // expiration (15min later)
  "roles": ["ROLE_USER", "ROLE_PM"], // roles embedded for downstream services
  "userId": 1                        // user ID embedded for downstream services
}

SIGNATURE:
HMACSHA512(
  BASE64URL(HEADER) + "." + BASE64URL(PAYLOAD),
  "bezKoderSecretKey"
)
```

**Access Token Lifecycle:** 15-min expiration + JTI. Contains `roles` and `userId` claims for downstream service use. Validated via HS512 signature + Redis blacklist check.

**Refresh Token Lifecycle:** 7-day expiration, stored in DB. Rotated on each use (old deleted, new created). Send to `/api/auth/refresh-token` when access token expires.

**Token Revocation:** On logout, JTI stored in Redis with auto-TTL = token expiry epoch. Redis auto-expires entry; no cleanup job needed.

## Component Interactions

### Spring Security Filter Chain (auth-service)

Key filter order in auth-service SecurityConfig:
1. `JwtAuthenticationFilter` (custom, before UsernamePasswordAuthenticationFilter)
   - Extracts Bearer token, validates via JwtService, sets SecurityContext
2. `CustomAccessDeniedHandler` — returns 403 JSON on role check failure
3. CSRF: disabled; Sessions: STATELESS; CORS: enabled
4. Permits: `/api/auth/**`; All others require authenticated user

## Deployment Architecture

### Docker Compose Setup

```
┌─────────────────────────────────────────────────────────────────┐
│                        DOCKER HOST                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                   │
│  ┌─────────┐ ┌────────┐ ┌─────────┐ ┌────────┐ ┌────────────┐  │
│  │postgres │ │redis   │ │kafka    │ │eureka  │ │config-     │  │
│  │(:5432)  │ │(:6379) │ │(:9092)  │ │(:8761) │ │server      │  │
│  │postgres:│ │redis:  │ │confluen │ │        │ │(:8888)     │  │
│  │16       │ │latest  │ │t-kafka  │ │        │ │            │  │
│  └────┬────┘ └──┬─────┘ └────┬────┘ └───┬────┘ └──────┬─────┘  │
│       │        │             │ registry  │ fetch      │         │
│       │        │             │           ▼            ▼         │
│       │        │             │      ┌──────────────────────┐    │
│       │        │             │      │ api-gateway (:8080)  │    │
│       │        │             │      │ Single entry point   │    │
│       │        │             │      └────────┬─────────────┘    │
│       │        │             │               │ routes           │
│       │        │             │      ┌────────▼────────────────┐ │
│       └───────┼─────────────┼──────│  auth-service (:8081)  │ │
│               │             │      │  Publishes to Kafka    │ │
│               │             │      │  depends_on: postgres, │ │
│               │             │      │  redis, kafka, eureka  │ │
│               │             │      └───────────────────────┤ │
│               │             │                              │   │
│               │             │      ┌──────────────────────┬┘   │
│               │             └─────►│notification-service   │    │
│               │                    │  (:8085)              │    │
│               │                    │  Consumes Kafka events│    │
│               │                    │  Redis dedup (24h TTL)│    │
│               │                    │  Sends emails via SMTP│    │
│               └───────────────────►│  depends_on: kafka,   │    │
│                                    │  redis, eureka        │    │
│                                    └───────────────────────┘    │
│                                                                   │
│                      Network Bridge (my-net)                     │
└─────────────────────────────────────────────────────────────────┘
```

### Service Port Reference

| Service | Port | Notes |
|---------|------|-------|
| api-gateway | 8080 | Single external entry point |
| auth-service | 8081 | JWT auth + token validation |
| movie-service | 8082 | Movie catalog (business service) |
| booking-service | 8083 | Ticket booking (business service) |
| payment-service | 8084 | Payment processing (business service) |
| notification-service | 8085 | Kafka consumer; sends emails via SMTP |
| eureka-server | 8761 | Service registry dashboard |
| config-server | 8888 | Serves shared JWT secret |
| PostgreSQL | 5432 | auth-service only |
| Redis | 6379 | auth-service (token blacklist), notification-service (dedup) |
| Kafka | 9092 | Message broker (notification-events topic) |
| Prometheus | 9090 | Metrics collection (internal + host) |
| Grafana | 3000 | Dashboards (admin/admin) |

### Runtime Environment (auth-service)

```
┌────────────────────────────────────────┐
│  auth-service Container                │
├────────────────────────────────────────┤
│  Eclipse Temurin JDK 21 (Alpine Linux) │
│  Spring Boot 3.4.3 / Tomcat (:8081)    │
│  Registers with Eureka (:8761)         │
│  Fetches config from Config Server     │
│  ├─ JWT Secret (shared via config)     │
│  ├─ JWT Expiration: 900000ms (15min)   │
│  ├─ Database: PostgreSQL 16            │
│  └─ Redis: token blacklist             │
└────────────────────────────────────────┘
```

### Runtime Environment (notification-service)

```
┌────────────────────────────────────────┐
│ notification-service Container         │
├────────────────────────────────────────┤
│ Eclipse Temurin JDK 21 (Alpine Linux) │
│ Spring Boot 3.4.3 / Tomcat (:8085)    │
│ Registers with Eureka (:8761)         │
│ Fetches config from Config Server     │
│ ├─ Kafka bootstrap-servers (9092)     │
│ ├─ Redis host (6379, dedup cache)     │
│ └─ SMTP mail properties (Gmail)       │
└────────────────────────────────────────┘
```

### Event-Driven Notification Processing

**Kafka Consumer Flow:**
1. NotificationEventListener consumes from `notification-events` topic (groupId: notification-service)
2. NotificationDeduplicationService checks Redis for prior processing:
   - Key pattern: `notification:processed:{eventId}`
   - SETNX (atomic set-if-not-exists) with 24h TTL
   - Returns true if event newly marked (proceed), false if already processed (skip)
3. On duplicate: logs and returns (Kafka auto-commits offset)
4. On new event: EmailSenderService sends via SMTP; exception throws to trigger DLT retry

**Fail-Open Design:**
- Redis unavailable → proceed with email send + log warning
- Does NOT block notifications on Redis outage
- Conservative choice: prefer email duplicate over missing email

**Key Redis Pattern:**
```
notification:processed:event-uuid-123 = "1" (value irrelevant)
TTL: 24 hours (auto-expires entry)
```

## Security Architecture

### Authentication Mechanisms

| Mechanism | Implementation | Purpose |
|-----------|-----------------|---------|
| Password Encoding | BCryptPasswordEncoder | Secure password storage |
| JWT Generation | JJWT 0.9.0 HS512 | Token-based auth |
| Token Validation | JJWT parser | Signature & expiration verification |
| Authorization | Spring Security @PreAuthorize | Role-based access control |
| Session Management | STATELESS | No server-side session storage |

### Security Boundaries

```
┌──────────────────────────────────────────────────────────┐
│  PUBLIC ZONE                                              │
│  ┌──────────────────────────────────────────────────┐   │
│  │ POST /api/auth/login        (no auth required)   │   │
│  │  └─ Returns 401 if account not activated         │   │
│  │ POST /api/auth/register     (no auth required)   │   │
│  │ GET  /api/auth/activate     (token param only)   │   │
│  │ POST /api/auth/resend-activation (no auth req)   │   │
│  │ POST /api/auth/forgot-password   (no auth req)   │   │
│  │ POST /api/auth/reset-password    (token only)    │   │
│  │ POST /api/auth/refresh-token     (refresh token) │   │
│  └──────────────────────────────────────────────────┘   │
└────────────────────┬─────────────────────────────────────┘
                     │ Client obtains access + refresh tokens
                     ▼
┌──────────────────────────────────────────────────────────┐
│  PROTECTED ZONE                                           │
│  ┌───────────────────────────────────────────────────┐  │
│  │ All other endpoints require:                      │  │
│  │ 1. Authorization: Bearer <accessToken> header     │  │
│  │ 2. Valid token signature (HS512)                  │  │
│  │ 3. Token not expired (15 min)                     │  │
│  │ 4. JTI not in blacklist (logout check)            │  │
│  │ 5. @PreAuthorize role checks                      │  │
│  │                                                   │  │
│  │ POST /api/auth/logout                             │  │
│  │ ├─ Requires: Bearer accessToken                   │  │
│  │ ├─ Blacklists: JTI of current token               │  │
│  │ └─ Deletes: user's refresh token                  │  │
│  └───────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
```

### Security Improvements Implemented

1. **Token Refresh:** Refresh tokens with rotation (7-day expiration, new token on refresh)
2. **Token Revocation:** JTI-based blacklisting with scheduled cleanup
3. **Password Reset:** Email-driven reset flow with 24-hour token expiration
4. **Email Validation:** Required and unique email on registration
5. **Access Token Expiration:** Shortened to 15 minutes for reduced exposure
6. **Email Activation:** New accounts inactive until email-verified; login blocked for inactive accounts
7. **Account Lockout:** Auto-lock after N failed login attempts (default 5); auto-unlock after configurable duration (default 15 min); HTTP 423 returned with remaining lock time

### Potential Security Improvements (Future)

- Rate limiting on /api/auth/login (volumetric attack defense)
- Audit logging (IP, timestamp, success/failure)
- HTTPS enforcement in production
- RS256 asymmetric keys (better for multi-service trust)
- Two-factor authentication
- Secret rotation mechanism

## Scaling & Performance

- **Stateless JWT:** no session affinity; Eureka enables multi-instance auth-service
- **Shared secret via Config Server:** all instances receive same jwtSecret
- **JWT contains roles + userId:** downstream services avoid DB lookups on every request

| Operation | Approx Latency |
|-----------|---------------|
| Login (auth + token gen) | ~100-200ms |
| Token validation (gateway) | ~5-15ms |
| validate-token endpoint | ~5ms (Redis only, no DB) |
| Logout (blacklist) | ~2-5ms |

## Monitoring & Observability

### Metrics Stack

```
┌─────────────────────────────────────────────────────────┐
│  All Services (:808x + :9092)  /actuator/prometheus     │
│  Micrometer metrics tagged: application=<service-name>  │
└─────────────┬───────────────────────────────────────────┘
              │ scrape every 15s
              ▼
┌─────────────────────────────────────────────────────────┐
│  Prometheus (:9090)                                     │
│  monitoring/prometheus/prometheus.yml                   │
│  9 scrape jobs: prometheus + 8 services (including      │
│  notification-service)                                  │
└─────────────┬───────────────────────────────────────────┘
              │ datasource
              ▼
┌─────────────────────────────────────────────────────────┐
│  Grafana (:3000)  admin/admin                           │
│  Auto-provisioned datasource + 2 dashboards             │
│  monitoring/grafana/provisioning/                       │
│  ├─ datasources/datasources.yml                         │
│  ├─ dashboards/dashboards.yml  (provider config)        │
│  └─ dashboards/json/                                    │
│     ├─ jvm-micrometer.json                              │
│     └─ spring-boot-http-overview.json                   │
└─────────────────────────────────────────────────────────┘
```

**Network:** Prometheus and Grafana run inside `my-net` Docker network. Actuator endpoints are NOT routed through API Gateway — internal access only.

### Dashboards

| Dashboard | Metrics Covered |
|-----------|----------------|
| JVM Micrometer | Memory, GC pause, thread count, CPU usage, file descriptors per service |
| Spring Boot HTTP Overview | Request rate, error rate, p99 latency, HikariCP pool, business counters |

### Business Metrics (Custom Counters)

| Service | Counter |
|---------|---------|
| auth-service | auth.login, auth.register, auth.logout |
| booking-service | booking.created, booking.confirmed, booking.cancelled |
| payment-service | payment.initiated, payment.completed, payment.failed |

### Security Considerations

- `/actuator/**` permitted in auth-service and movie-service `SecurityConfig`
- `/actuator/prometheus` added to `jwt.auth.publicPaths` for movie/booking/payment services (JWT starter)
- Monitoring ports (:9090, :3000) exposed on Docker host; not behind API Gateway

### Log Levels

| Component | Log Level |
|-----------|-----------|
| JwtService | DEBUG |
| AuthController | INFO |
| Hibernate | DEBUG |
| com.namnd.cinema | DEBUG |

## Technology Stack Summary

| Layer | Technology | Version | Role |
|-------|-----------|---------|------|
| Framework | Spring Boot | 3.4.3 | Application container |
| Cloud | Spring Cloud | 2024.0.1 | Service discovery, config, gateway |
| Security | Spring Security | 6.x (via Boot) | Authentication/Authorization |
| Service Registry | Netflix Eureka | via Cloud | Service discovery |
| Config | Spring Cloud Config | via Cloud | Shared JWT secret distribution |
| Gateway | Spring Cloud Gateway MVC | via Cloud | Single entry point, routing |
| ORM | Spring Data JPA | via Boot | Object-relational mapping |
| JWT | JJWT | 0.12.6 | Token generation/validation |
| Password Hash | BCrypt | via Spring | Secure password encoding |
| Database | PostgreSQL | 16 | Data persistence (users, roles, refresh/reset tokens) |
| Cache/Blacklist | Redis | 7.x | Token blacklist (JTI) with auto-TTL |
| Message Broker | Apache Kafka | via Boot | Event streaming for notifications |
| Email Service | Spring Mail | via Boot | SMTP email delivery |
| Metrics | Micrometer + Prometheus | via Boot Actuator | Metrics scraping |
| Dashboards | Grafana | latest | Metrics visualization |
| Container | Docker | latest | Deployment container |
| Runtime | Eclipse Temurin | 21 (Alpine) | Java runtime |

## Dependency Graph (auth-service)

```
auth-service
├─ Spring Boot 3.4.3
│  ├─ Spring Security 6.x → JwtAuthenticationFilter, SecurityConfig
│  ├─ Spring Data JPA → Hibernate → PostgreSQL Driver (HikariCP pool)
│  ├─ Spring Data Redis → Lettuce → Redis (blacklist store)
│  ├─ Spring Kafka → Kafka (notification events)
│  ├─ Spring Web (Embedded Tomcat, :8081)
│  └─ kafka-events (shared domain events)
├─ Spring Cloud 2024.0.1
│  ├─ Eureka Client → registers as "auth-service"
│  └─ Config Client → fetches shared JWT secret from config-server
├─ JJWT 0.12.6 (api + impl + jackson)
└─ Lombok
```

## Architecture Decisions Rationale

| Decision | Chosen | Rationale | Trade-off |
|----------|--------|-----------|-----------|
| Stateless vs Sessions | Stateless JWT | Microservices-ready, no server state | Larger token, can't invalidate early without blacklist |
| HS512 vs RS256 | HS512 | Simpler operations, all servers share secret | Less secure for distributed trust |
| Eager vs Lazy Roles | Eager | Roles needed in SecurityContext immediately | Always loads roles even if unused |
| Manual vs Auto Schema | Manual | Version control, database as source of truth | Extra maintenance burden |
| Single DB vs Sharding | Single | Simpler for now, YAGNI principle | Scalability ceiling at DB level |
| Blacklist Storage | Redis | Fast O(1) lookup, auto-TTL eliminates cleanup jobs | New infrastructure dependency |
| Blacklist Error Handling | Fail-Closed | Conservative security: reject token if Redis unavailable | May block legitimate requests during outage |

## Future Architecture Evolution

### Phase 2: Microservices-Ready (IN PROGRESS)
- ✓ Converted to 8-module Maven multi-module project (added notification-service, kafka-events)
- ✓ Spring Cloud Eureka (service registry)
- ✓ Spring Cloud Config Server (shared JWT secret)
- ✓ Spring Cloud Gateway MVC (single entry point, port 8080)
- ✓ JWT validation starter library (jwt-auth-spring-boot-starter)
- ✓ POST /api/auth/validate-token (microservice token validation)
- ✓ GET /api/users/me (user profile for downstream services)
- ✓ JWT tokens include roles + userId claims
- ✓ OpenAPI/Swagger documentation (SpringDoc 2.8.4)
- ✓ Event-driven notification service (Kafka + SMTP; replaces direct email sending)
- ✓ kafka-events module (shared domain event records)
- ✓ Auth-service publishes NotificationRequestedEvent for password reset & activation
- [ ] Rate limiting middleware
- [ ] Audit logging service

### Phase 3: Enterprise Features
- Multi-tenancy support
- Advanced role model (permissions, resource-based)
- OAuth2/SAML integration
- Distributed tracing (Jaeger, Zipkin)

### Phase 4: Cloud-Native
- Kubernetes deployment manifests
- ConfigMap for secrets management
- Health checks & readiness probes
- ✓ Metrics export (Prometheus + Grafana)
- Centralized logging (ELK/Splunk)
