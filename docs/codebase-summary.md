# Codebase Summary

**Project:** ms-cinema
**Generated:** March 2026 (post-microservice migration)
**Architecture:** 6-module Maven multi-module (Spring Cloud)
**Java Version:** 21 LTS
**Spring Boot:** 3.4.3
**Spring Cloud:** 2024.0.1

## Module Structure

```
ms-cinema/                  ← root pom (packaging: pom)
├── auth-service/                     ← core auth service (:8081)
├── jwt-auth-spring-boot-autoconfigure/ ← JWT starter auto-config
├── jwt-auth-spring-boot-starter/     ← thin starter wrapper
├── eureka-server/                    ← service registry (:8761)
├── config-server/                    ← shared config (:8888)
├── api-gateway/                      ← single entry point (:8080)
├── movie-service/                    ← movie catalog service (:8082)
├── booking-service/                  ← ticket booking service (:8083)
├── payment-service/                  ← payment processing service (:8084)
├── notification-service/             ← notification service (:8085)
├── kafka-events/                     ← Kafka event domain models
├── monitoring/                       ← Prometheus & Grafana config
│   ├── prometheus/
│   │   └── prometheus.yml            ← 8 scrape jobs (15s interval)
│   └── grafana/provisioning/
│       ├── datasources/datasources.yml   ← auto-provision Prometheus datasource
│       └── dashboards/
│           ├── dashboards.yml            ← dashboard provider config
│           └── json/
│               ├── jvm-micrometer.json           ← JVM metrics dashboard
│               └── spring-boot-http-overview.json ← HTTP + business metrics dashboard
├── docker-compose.yml
└── docs/
```

### auth-service (main module)

```
auth-service/src/main/java/com/namnd/cinema/
├── CinemaAuthApplication.java
├── config/
│   ├── security/SecurityConfig.java
│   ├── filter/JwtAuthenticationFilter.java
│   ├── custom/CustomAccesDeniedHandler.java
│   ├── OpenApiConfig.java                 ← NEW (SpringDoc)
│   ├── RedisConfig.java
│   └── RedisKeyPrefix.java
├── controller/
│   ├── AuthController.java (~230 lines, @Tag + @Operation)
│   ├── TokenValidationController.java (99 lines, @Tag + @Operation)  ← NEW
│   └── TestController.java
├── model/
│   ├── User.java, Role.java, UserPrinciple.java
│   ├── RefreshToken.java, PasswordResetToken.java
│   └── ActivationToken.java
├── dto/
│   ├── LoginRequestDto.java, JwtResponseDto.java, RegisterDto.java
│   ├── ForgotPasswordDto.java, ResetPasswordDto.java
│   ├── RefreshTokenRequestDto.java, TokenRefreshResponseDto.java
│   ├── ValidateTokenRequestDto.java  ← NEW
│   ├── ValidateTokenResponseDto.java ← NEW
│   ├── UserInfoResponseDto.java      ← NEW
│   └── mapper/RegisterDtoMapper.java
├── service/
│   ├── JwtService.java (147 lines)   ← UPDATED (roles+userId claims)
│   ├── UserService.java, RoleService.java, RefreshTokenService.java
│   ├── PasswordResetService.java, EmailService.java
│   ├── ActivationService.java, BlacklistedTokenService.java
│   ├── AccountLockService.java, RedisService.java
│   └── impl/ (all above impls)
└── repository/
    ├── UserRepository.java, RoleRepository.java
    ├── RefreshTokenRepository.java, PasswordResetTokenRepository.java
    └── ActivationTokenRepository.java
```

### jwt-auth-spring-boot-autoconfigure

```
src/main/java/com/namnd/jwt/autoconfigure/
├── JwtAutoConfiguration.java   ← @AutoConfiguration, conditional beans
├── JwtAuthProperties.java      ← @ConfigurationProperties prefix=jwt.auth
├── JwtTokenValidator.java      ← validates HS512 signature
├── JwtAuthenticationFilter.java← sets SecurityContext for downstream services
└── JwtAuthenticatedUser.java   ← principal model
```

### Infrastructure Modules

| Module | Main Class | Port | Key Dep |
|--------|-----------|------|---------|
| eureka-server | EurekaServerApplication | 8761 | spring-cloud-starter-netflix-eureka-server |
| config-server | ConfigServerApplication | 8888 | spring-cloud-config-server |
| api-gateway | ApiGatewayApplication | 8080 | spring-cloud-starter-gateway-mvc, springdoc-openapi-starter-webmvc-ui |
| movie-service | MovieServiceApplication | 8082 | springdoc-openapi-starter-webmvc-ui, OpenApiConfig.java |
| booking-service | BookingServiceApplication | 8083 | springdoc-openapi-starter-webmvc-ui, OpenApiConfig.java |
| payment-service | PaymentServiceApplication | 8084 | springdoc-openapi-starter-webmvc-ui, OpenApiConfig.java |
| notification-service | NotificationServiceApplication | 8085 | spring-kafka, spring-boot-starter-mail |
| kafka-events | — | — | domain event records (NotificationRequestedEvent) |
| prometheus | (Docker image) | 9090 | monitoring/prometheus/prometheus.yml |
| grafana | (Docker image) | 3000 | monitoring/grafana/provisioning/ |

## Core Components

### 1. Application Entry Point

**CinemaAuthApplication.java** (35 lines)
- `@SpringBootApplication` main entry point
- Scans packages under com.namnd.cinema
- Runs on port 8080 via application.yml
- Note: ServletInitializer.java removed (JAR-only packaging post-migration)

### 2. Configuration Classes

**RedisConfig.java** (24 lines)
- @Configuration class
- Provides RedisTemplate<String, Object> bean
- Configures serializers: StringRedisSerializer for keys, Jackson2JsonRedisSerializer for values
- Auto-wired by Spring into RedisServiceImpl

**SecurityConfig.java** (updated for Spring Security 6.x)
- Annotations: @Configuration, @EnableWebSecurity, @EnableMethodSecurity
- **Method-Based Configuration** (replaces WebSecurityConfigurerAdapter pattern):
  - Returns SecurityFilterChain bean (new Spring Security 6.x pattern)
  - Uses HttpSecurity DSL (lambda configuration)
- **Beans:**
  - `jwtAuthenticationFilter()` - Creates JWT filter
  - `authenticationManager(AuthenticationConfiguration)` - Exposes AuthenticationManager
  - `customAccesDeniedHandler()` - Custom 403 handler
  - `passwordEncoder()` - BCryptPasswordEncoder bean
- **Security Configuration:**
  - Permits: /api/auth/** (public endpoints)
  - Requires auth: all other endpoints
  - CSRF disabled (appropriate for JWT API)
  - Adds JwtAuthenticationFilter before UsernamePasswordAuthenticationFilter
  - Session policy: STATELESS
  - CORS enabled
  - Exception handling via CustomAccesDeniedHandler
- **Key Change:** @EnableGlobalMethodSecurity(prePostEnabled=true) → @EnableMethodSecurity

**RedisKeyPrefix.java** (15 lines)
- @final utility class with constants
- BLACKLIST = "blacklist:" (JWT token blacklist prefix)
- LOCK = "lock:" (distributed lock prefix)
- Prevents Redis key collisions across features

**JwtAuthenticationFilter.java** (~60 lines)
- Extends OncePerRequestFilter
- Implements filter per request
- **doFilterInternal():**
  - Extracts "Authorization" header
  - Strips "Bearer " prefix
  - Calls JwtService.validateJwtToken()
  - On valid token: loads UserDetails via UserService, sets SecurityContext
  - On invalid: clears SecurityContext, continues filter chain
  - Catches exceptions, logs errors

**CustomAccesDeniedHandler.java** (~30 lines)
- Implements AccessDeniedHandler
- **handle():** Returns 403 with JSON error on access denied
- Prevents Spring default redirect behavior

### 3. REST Controllers

**AuthController.java** (~230 lines) — route: `/api/auth`

| Endpoint | Method | Auth | Notes |
|----------|--------|------|-------|
| /login | POST | none | Returns JwtResponseDto with id/token/refreshToken/email/username/roles |
| /register | POST | none | Saves user (active=false), sends activation email |
| /activate | GET | token param | Sets user.active=true |
| /resend-activation | POST | none | Resends activation email |
| /forgot-password | POST | none | Generates 24h reset token, sends email |
| /reset-password | POST | token | BCrypt-encodes new password |
| /refresh-token | POST | refresh token | Rotates refresh token, returns new pair |
| /logout | POST | Bearer | Blacklists JTI in Redis, deletes refresh token |

**TokenValidationController.java** (99 lines) — NEW for microservice integration

| Endpoint | Method | Auth | Notes |
|----------|--------|------|-------|
| /api/auth/validate-token | POST | none | Validates JWT sig+expiry+blacklist; returns valid/userId/email/roles |
| /api/users/me | GET | Bearer | Loads fresh user profile from DB for authenticated caller |

### 4. Data Models

**User.java** (~45 lines)
- @Entity, @Data (Lombok)
- **Table:** users
- **Columns:**
  - id (BIGSERIAL, GenerationType.IDENTITY)
  - username (String, not unique - duplicates allowed)
  - email (String, unique)
  - password (String, BCrypt-encoded)
  - fullName (String)
  - active (boolean, default false - set true after email activation)
  - failedAttempts (int, default 0 - incremented on bad credentials)
  - lockTime (Date, nullable - set when account locked, cleared on unlock)
  - roles (Set<Role>, ManyToMany eager, JoinTable user_roles)

**RefreshToken.java** (~30 lines)
- @Entity, @Data (Lombok)
- **Table:** refresh_tokens
- **Columns:**
  - id (BIGSERIAL)
  - token (String, unique)
  - expiryDate (LocalDateTime)
  - user (User, ManyToOne)
- **Methods:**
  - isExpired() - checks if token past expiration

**PasswordResetToken.java** (~30 lines)
- @Entity, @Data (Lombok)
- **Table:** password_reset_tokens
- **Columns:**
  - id (BIGSERIAL)
  - token (String, unique)
  - expiryDate (LocalDateTime)
  - user (User, ManyToOne)
- **Methods:**
  - isExpired() - checks token validity

**ActivationToken.java** (~35 lines)
- @Entity, @Data (Lombok)
- **Table:** activation_tokens
- **Columns:**
  - id (BIGSERIAL)
  - token (String, unique)
  - expiryDate (LocalDateTime)
  - user (User, ManyToOne)
  - used (boolean, default false)
- **Purpose:** Tracks email activation links; single-use, expires after 24h

**Role.java** (~20 lines)
- @Entity, @Data (Lombok)
- **Table:** roles
- **Columns:**
  - id (BIGSERIAL, GenerationType.IDENTITY)
  - name (String, e.g., "ROLE_USER", "ROLE_PM", "ROLE_ADMIN")
- **Relationships:** Many-to-many with User

**UserPrinciple.java** (~80 lines)
- Implements UserDetails (Spring Security interface)
- Wraps User entity for Spring Security
- **Fields:**
  - id, username, password, fullName, roles (from User)
  - authorities (derived from roles)
- **Methods:**
  - getAuthorities() - Returns GrantedAuthority collection from Role names
  - getUsername(), getPassword() - Simple getters
  - isAccountNonExpired(), isCredentialsNonExpired() - Return true
  - isAccountNonLocked() - Returns actual lock state (false when User.lockTime set and within lockDurationMs)
  - isEnabled() - Returns user.active (false until email activation completes)

### 5. Data Transfer Objects

**JwtResponseDto.java** (~45 lines)
- **Fields:** id (Long), token (String), refreshToken (String), email (String), username (String), name (String), roles (Collection<? extends GrantedAuthority>)
- **Purpose:** Response payload for login endpoint

**LoginRequestDto.java** (~15 lines)
- **Fields:** email (String), password (String)
- **Purpose:** Request payload for login endpoint (replaces raw User entity)

**RegisterDto.java** (~35 lines)
- **Fields:** username (String), email (String), password (String), fullName (String), roles (Set<Role>)
- **Purpose:** Request payload for register endpoint (email now required)

**ForgotPasswordDto.java** (~15 lines)
- **Fields:** email (String)
- **Purpose:** Request payload for password reset initiation

**ResetPasswordDto.java** (~20 lines)
- **Fields:** token (String), newPassword (String)
- **Purpose:** Request payload for password reset completion

**RefreshTokenRequestDto.java** (~15 lines)
- **Fields:** refreshToken (String)
- **Purpose:** Request payload for token refresh

**TokenRefreshResponseDto.java** (~20 lines)
- **Fields:** accessToken (String), refreshToken (String)
- **Purpose:** Response payload for token refresh endpoint

**RegisterDtoMapper.java** (~40 lines)
- Maps RegisterDto → User entity
- Encodes password via PasswordEncoder (BCrypt)
- Copies username, email, password (encoded), fullName, roles

### 6. Services

**JwtService.java** (147 lines) — UPDATED
- Injected: `${namnd.app.jwtSecret}`, `${namnd.app.jwtExpiration}`
- **Methods:**
  - `generateTokenLogin(Authentication)` — HS512 signed, JTI, includes `roles` + `userId` claims
  - `generateTokenFromEmail(String)` — legacy, no roles/userId (backward compat)
  - `generateTokenFromEmail(String, Long, List<String>)` — with roles+userId for refresh flow
  - `validateJwtToken(String)` — signature + expiration check
  - `getEmailFromJwtToken(String)`, `getJtiFromToken(String)`, `getExpirationFromToken(String)`
  - `getRolesFromToken(String)` — NEW, extracts roles claim
  - `getUserIdFromToken(String)` — NEW, extracts userId claim
- JWT sub = email; roles+userId embedded for downstream service consumption via validate-token

**RefreshTokenService.java** (interface)
- **Methods:**
  - createRefreshToken(Long userId) - Creates new 7-day token
  - findByToken(String) - Optional lookup
  - verifyExpiration(RefreshToken) - Validates & returns token
  - deleteByUserId(Long) - Deletes user's refresh token

**RefreshTokenServiceImpl.java** (~60 lines)
- @Service, injected RefreshTokenRepository, UserRepository
- Implements RefreshTokenService
- Token rotation on refresh (creates new token, old deleted)

**PasswordResetService.java** (interface)
- **Methods:**
  - createPasswordResetToken(String email) - Creates token, sends email
  - resetPassword(String token, String newPassword) - Validates & updates

**PasswordResetServiceImpl.java** (~80 lines)
- @Service, injected repositories, UserService, EmailService
- Generates 24-hour reset tokens
- Sends reset links via email

**EmailService.java** (interface)
- **Methods:**
  - sendPasswordResetEmail(String email, String resetLink) - Publishes Kafka event
  - sendActivationEmail(String email, String activationLink) - Publishes Kafka event

**EmailServiceImpl.java** (~35 lines)
- @Service, injected KafkaTemplate<String, Object>
- Publishes NotificationRequestedEvent to Kafka topic "notification-events"
- Removed JavaMailSender dependency; actual email sending delegated to notification-service

**ActivationService.java** (interface)
- **Methods:**
  - createActivationToken(User user) - Creates token, sends activation email
  - activateAccount(String token) - Validates token, sets user.active=true
  - resendActivation(String email) - Generates new token if account not yet active

**ActivationServiceImpl.java** (~90 lines)
- @Service, injected ActivationTokenRepository, UserService, EmailService
- Generates 24-hour activation tokens (UUID-based)
- Sends activation links via email ({activationBaseUrl}?token={token})
- Marks token as used after successful activation

**BlacklistedTokenService.java** (interface)
- **Methods:**
  - blacklistToken(String jti, Date expiry) - Adds to Redis blacklist
  - isTokenBlacklisted(String jti) - Checks Redis membership

**BlacklistedTokenServiceImpl.java** (~50 lines)
- @Service, injected RedisService
- Uses RedisKeyPrefix.BLACKLIST constant + JTI
- Sets auto-TTL based on token expiry date via RedisService.set()
- Fail-closed error handling: returns true on Redis outage (reject token)

**RedisService.java** (interface, ~46 lines)
- Shared utility for all Redis operations
- **Key-Value ops:** set, get, delete, hasKey, expire, getExpire
- **Hash ops:** hSet, hGet, hGetAll, hDelete, hHasKey
- **List ops:** lPush, rPush, lRange, lLen
- **Set ops:** sAdd, sMembers, sIsMember, sRemove
- **Pub/Sub:** publish(channel, message)
- **Distributed Lock:** tryLock, unlock

**RedisServiceImpl.java** (~276 lines)
- @Service, implements RedisService
- Injected: StringRedisTemplate, RedisTemplate<String, Object>
- Try-catch error handling on every method (fail-safe returns)
- Uses Jackson2JsonRedisSerializer for JSON serialization

**AccountLockService.java** (interface)
- **Methods:**
  - loginFailed(String email) - Increments failedAttempts; locks account when maxFailedAttempts reached
  - loginSucceeded(String email) - Resets failedAttempts to 0 and clears lockTime
  - isLocked(User user) - Returns true if lockTime set and lock period not yet elapsed (auto-unlock check)
  - getLockTimeRemaining(User user) - Returns remaining lock duration in minutes

**AccountLockServiceImpl.java** (~60 lines)
- @Service, injected UserRepository, @Value maxFailedAttempts, @Value lockDurationMs
- loginFailed(): finds user by email, increments counter, sets lockTime when counter >= max
- loginSucceeded(): resets failedAttempts=0 and lockTime=null, saves user
- isLocked(): compares System.currentTimeMillis() against lockTime+lockDurationMs; auto-unlocks expired locks

**UserService.java** (interface, ~20 lines)
- Extends UserDetailsService (Spring Security)
- **Methods:**
  - save(User)
  - findByEmail(String) - Returns Optional<User>
  - existsByEmail(String) - Returns boolean

**UserServiceImpl.java** (~50 lines)
- @Service, implements UserService
- Injected: UserRepository, PasswordEncoder
- **Methods:**
  - save(User) - Delegates to repo.save()
  - findByEmail(String) - Calls repo.findByEmail()
  - existsByEmail(String) - Calls repo.existsByEmail()
  - loadUserByUsername(String) (from UserDetailsService)
    - Queries by email (parameter is email, not username)
    - Returns UserPrinciple wrapping user

**RoleService.java** (interface, ~15 lines)
- **Methods:**
  - save(Role)
  - findByName(String) - Returns Role or null
  - flush()

**RoleServiceImpl.java** (~30 lines)
- @Service, implements RoleService
- Injected: RoleRepository
- Delegates all methods to RoleRepository

### 7. Repositories

**UserRepository.java** (interface)
- Extends JpaRepository<User, Long>
- **Methods:**
  - Optional<User> findByUsername(String) - exists but not used for login
  - boolean existsByUsername(String) - exists but not used for registration check
  - Optional<User> findByEmail(String) - primary lookup (login, password reset)
  - boolean existsByEmail(String) - used for registration uniqueness check

**RoleRepository.java** (interface)
- Extends JpaRepository<Role, Long>
- **Methods:**
  - Role findByName(String)

**RefreshTokenRepository.java** (interface)
- Extends JpaRepository<RefreshToken, Long>
- **Methods:**
  - Optional<RefreshToken> findByToken(String)
  - void deleteByUserId(Long userId)

**PasswordResetTokenRepository.java** (interface)
- Extends JpaRepository<PasswordResetToken, Long>
- **Methods:**
  - Optional<PasswordResetToken> findByToken(String)
  - void deleteByUserId(Long userId)

**ActivationTokenRepository.java** (interface)
- Extends JpaRepository<ActivationToken, Long>
- **Methods:**
  - Optional<ActivationToken> findByToken(String)
  - void deleteByUserId(Long userId)

## Configuration Files

**Root pom.xml** — packaging: pom, 6 modules, Spring Cloud BOM (2024.0.1), JJWT 0.12.6

**auth-service/application.yml** (key values)
- `server.port: ${SERVER_PORT:8081}` (changed from 8080)
- `spring.config.import: optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888`
- `eureka.client.service-url.defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/`
- `namnd.app.jwtSecret: ${JWT_SECRET:...}` — overridden by Config Server in production
- `namnd.app.jwtExpiration: 900000` / `jwtRefreshExpiration: 604800000`

**config-repo/application.yml** — shared: `namnd.app.jwtSecret` + `jwt.auth.secret`

**api-gateway/application.yml** — port 8080, routes `/api/auth/**` and `/api/users/**` to `lb://auth-service`

**eureka-server/application.yml** — port 8761, self-register: false

**docker-compose.yml** — now includes eureka-server, config-server, api-gateway; auth-service on 8081

## Key Design Patterns

| Pattern | Location | Purpose |
|---------|----------|---------|
| Service Locator | SecurityConfig | Wires AuthenticationManager, PasswordEncoder |
| Data Mapper | RegisterDtoMapper | DTO → Entity conversion |
| Adapter | UserPrinciple | Adapts User to UserDetails |
| Filter Chain | JwtAuthenticationFilter | JWT extraction & validation |
| Repository | UserRepository, RoleRepository | Data access abstraction |
| Dependency Injection | Throughout (@Autowired, @Inject) | Loose coupling |

## Code Metrics

| Metric | Value |
|--------|-------|
| Maven Modules | 9 (auth-service, autoconfigure, starter, eureka, config, gateway, movie, booking, payment) |
| auth-service Java Classes | ~42 (added TokenValidationController + 3 DTOs) |
| New Controllers | TokenValidationController (validate-token + users/me) |
| New DTOs | ValidateTokenRequestDto, ValidateTokenResponseDto, UserInfoResponseDto |
| New JwtService methods | getRolesFromToken(), getUserIdFromToken(), generateTokenFromEmail(String,Long,List) |
| Starter lib classes | 5 (JwtAutoConfiguration, JwtAuthProperties, JwtTokenValidator, JwtAuthenticationFilter, JwtAuthenticatedUser) |
| Test Coverage | 1 smoke test (auth-service CinemaAuthApplicationTests) |
| Scheduled Tasks | 0 (Redis auto-TTL) |

## External Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| Spring Boot | 3.4.3 | Jakarta EE, virtual threads ready |
| Spring Cloud | 2024.0.1 | Eureka, Config, Gateway |
| Spring Security | 6.x (via Boot) | SecurityFilterChain pattern |
| Spring Kafka | via Boot | message broker for event-driven flow |
| Spring Mail | via Boot | SMTP email delivery (notification-service) |
| JJWT | 0.12.6 | 3 split artifacts (api, impl, jackson) |
| SpringDoc OpenAPI Starter | 2.8.4 | Swagger UI + OpenAPI 3.0 docs |
| Micrometer | via Spring Boot Actuator | Metrics export per service |
| PostgreSQL Driver | ~42.x | auth-service only |
| Redis | via Spring Data Redis | auth-service only (blacklist) |
| Lombok | BOM-managed | JDK 21 compatible |

## Build

```bash
# Build all modules from root
mvn clean install

# Build individual module
cd auth-service && mvn clean package

# Docker (auth-service)
docker build -t auth-service ./auth-service
```

## Integration Points

| System | Consumer | Type |
|--------|----------|------|
| PostgreSQL (:5432) | auth-service | JDBC/JPA |
| Redis (:6379) | auth-service | token blacklist |
| Kafka (notification-events topic) | notification-service | event streaming |
| SMTP (Gmail) | notification-service | email sending |
| Eureka (:8761) | auth-service, api-gateway, business services | service registry |
| Config Server (:8888) | auth-service, api-gateway, business services | shared JWT secret |
| api-gateway (:8080) | clients | routing to downstream services |
| Prometheus (:9090) | grafana | metrics datasource |
| /actuator/prometheus | prometheus | scraped from all 8 services every 15s |

## Future Expansion Points

1. **OAuth2/Social Login** — Google, GitHub
2. **Advanced Roles** — permissions model
3. **Audit Logging** — login/reset/logout events with IP
4. **Rate Limiting** — login/forgot-password endpoints
5. ✓ **OpenAPI/Swagger** — auto-generated API docs (DONE: SpringDoc 2.8.4)
6. ✓ **Event-Driven Email** — Kafka-based notification service (DONE)
7. **Alerting Rules** — Prometheus alertmanager for SLA breach notifications
8. **Centralized Logging** — ELK/Loki stack integration
9. **Notification Templates** — Customizable email templates in notification-service
