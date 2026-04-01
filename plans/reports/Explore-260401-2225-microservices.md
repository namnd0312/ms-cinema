# Microservices Exploration Report
**Date:** 2026-04-01 | **Location:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema`

---

## 1. AUTH-SERVICE (8081)

### Directory Structure
- **Total Java Files:** 66 (Main: 64, Test: 1)
- **Main Path:** `/auth-service/src/main/java/com/namnd/cinema/`
- **Key Directories:**
  - `controller/` - REST endpoints
  - `service/` + `impl/` - Business logic
  - `model/` - JPA entities
  - `dto/` - Data transfer objects
  - `repository/` - Spring Data repositories
  - `config/` - Security, Redis, OpenAPI, filters
  - `test/` - Integration tests

### All Java Classes with Descriptions

#### Controllers (1)
- **AuthController** - Main auth REST API (login, register, activate, password reset, token refresh, logout, change password)

#### Services & Interfaces (13)
- **JwtService** - JWT token generation and validation
- **UserService** - User CRUD operations
- **RoleService** - Role management
- **ActivationService** - Email activation token lifecycle (interface)
- **ActivationServiceImpl** - Implements account activation with/without password setup, 24-hour token TTL
- **PasswordResetService** - Password reset flow via email tokens
- **PasswordResetServiceImpl** - Implements password reset with history tracking
- **PasswordHistoryService** - Prevents password reuse (tracks last 3 passwords)
- **PasswordHistoryServiceImpl** - Persists encoded passwords to history table
- **RefreshTokenService** - Refresh token rotation and validation
- **RefreshTokenServiceImpl** - Atomic refresh token rotation (verify + delete + create in transaction)
- **BlacklistedTokenService** - Prevents token reuse after logout
- **BlacklistedTokenServiceImpl** - Redis-backed token blacklist with JTI (JWT ID)
- **EmailService** - Email sending (interface)
- **EmailServiceImpl** - Sends activation and password reset emails via SMTP
- **AccountLockService** - Prevents brute-force attacks (configurable max attempts, lock duration)
- **AccountLockServiceImpl** - Tracks failed login attempts, locks account after 5 failures for 15 minutes
- **RedisService** - Redis operations (interface)
- **RedisServiceImpl** - Generic Redis get/set/delete operations
- **OAuth2UserLinkingService** - Links OAuth2 providers to user accounts
- **OAuth2UserLinkingServiceImpl** - Manages Google OAuth2 provider linkage

#### Models (8)
- **User** - Core user entity (id, username, password, email, active flag, failed attempts, lock time, roles)
- **Role** - Authority/role entity for RBAC
- **ActivationToken** - Email activation token (24-hour TTL, single-use)
- **RefreshToken** - JWT refresh token with expiry tracking
- **PasswordResetToken** - Password reset token (short-lived)
- **PasswordHistory** - Audit of encoded passwords per user (prevents reuse)
- **UserOAuthProvider** - OAuth2 provider linkage (Google, GitHub support) with unique constraints
- **UserPrinciple** - Spring Security UserDetails implementation

#### DTOs (9)
- **SetupPasswordDto** - NEW: Payload for `/activate-with-password` (token, password, confirmPassword) **[RECENT ADDITION]**
- **LoginRequestDto** - Login credentials (email, password)
- **RegisterDto** - Registration payload (email, username, fullName, password, roles)
- **JwtResponseDto** - JWT response (accessToken, refreshToken, user details, roles)
- **TokenRefreshResponseDto** - Token rotation response (newAccessToken, newRefreshToken)
- **RefreshTokenRequestDto** - Refresh token request (refreshToken)
- **ResetPasswordDto** - Password reset request (token, newPassword)
- **ForgotPasswordDto** - Password reset initiation (email)
- **ChangePasswordDto** - Change password for authenticated user (currentPassword, newPassword, confirmPassword)

#### Repositories (5)
- **UserRepository** - Find by email, existence checks, custom queries
- **RoleRepository** - Find by name
- **ActivationTokenRepository** - Find by token, delete unused per user, check existence
- **RefreshTokenRepository** - Find by token and user, delete by user
- **PasswordResetTokenRepository** - Find by token, custom queries
- **PasswordHistoryRepository** - Query recent passwords per user
- **UserOAuthProviderRepository** - Find provider by name and provider user ID

#### Configuration (8)
- **SecurityConfig** - Spring Security setup (JWT auth, CORS, OAuth2 Google)
- **RedisConfig** - Redis StringRedisTemplate bean
- **JwtAuthenticationFilter** - JWT extraction and validation from Authorization header
- **OpenApiConfig** - Swagger/OpenAPI documentation setup (Bearer auth scheme)
- **HttpLoggingConfig** - HTTP request/response logging configuration
- **MetricsConfig** - Micrometer metrics for auth events (login success/failure, register, token refresh, logout)
- **CustomAccesDeniedHandler** - Custom 403 Forbidden response handler
- **RedisKeyPrefix** - Constants for Redis key naming conventions

#### Token Validation Controller (1)
- **TokenValidationController** - Internal endpoint for validating JWT tokens (used by gateway)

#### Other (2)
- **CinemaAuthApplication** - Spring Boot entry point
- **CinemaAuthApplicationTests** - Minimal test context loader

#### Mappers (1)
- **RegisterDtoMapper** - Maps RegisterDto to User entity

#### HTTP Filters (1)
- **HttpLoggingFilter** - Logs HTTP request/response details to structured logger

### Key Configurations (application.yml)
```yaml
server.port: 8081
spring.datasource: PostgreSQL (testdb)
spring.data.redis: Redis for token caching
spring.security.oauth2.client: Google OAuth2 registration
namnd.app:
  jwtSecret: (configured)
  jwtExpiration: 900000ms (15 min)
  jwtRefreshExpiration: 604800000ms (7 days)
  passwordResetBaseUrl: http://localhost:4200/auth/reset-password
  activationBaseUrl: http://localhost:4200/auth/setup-password  <-- NEW: Setup password URL
  maxFailedAttempts: 5
  lockDurationMs: 900000ms (15 min)
  oauth2CallbackUrl: http://localhost:4200/auth/oauth2/callback
```

### Recent Additions & Key Features

#### Deferred Password to Activation Flow
- **SetupPasswordDto** - New DTO supporting deferred password setup at activation time
- **ActivationServiceImpl.activateWithPassword(token, password)** - NEW method: Sets password during account activation (not at registration)
- **AuthController.activateWithPassword(@RequestBody SetupPasswordDto)** - NEW endpoint: POST `/api/auth/activate-with-password`
  - Validates token expiry/usage
  - Validates password (min 6 chars, matches confirm)
  - Encodes password, activates account, saves to password history (all transactional)
  - Returns 200 on success, 400 on validation failure

#### Password History System
- **PasswordHistoryService** - Interface for password reuse prevention
- **PasswordHistoryServiceImpl** - Keeps last 3 passwords, prevents reuse
- **PasswordHistory** entity - JPA table with encrypted hashes
- **AuthController.changePassword()** - Checks history before allowing new password

#### OAuth2 Google Authentication
- **UserOAuthProvider** - Entity with unique constraints on (provider_name, provider_user_id) and (user_id, provider_name)
- **OAuth2UserLinkingServiceImpl** - Links/unlinks Google accounts to existing users
- **OAuth2AuthenticationSuccessHandler** - Custom handler after successful OAuth2 login
- **SecurityConfig** - Configures Google OAuth2 client registration
- **AuthController.authenticateUser()** - Prevents password-based login for OAuth-only accounts

#### Email Activation
- **ActivationServiceImpl** - Creates 24-hour activation tokens
- **EmailServiceImpl** - Sends activation emails via SMTP
- **AuthController.activateAccount()** - Simple activation: GET `/api/auth/activate?token=...`
- **AuthController.resendActivation()** - Resend activation email (prevents email enumeration)

#### Account Locking & Brute-Force Protection
- **AccountLockServiceImpl** - Tracks failed attempts per user
- **AuthController.authenticateUser()** - Auto-unlocks after 15 min, checks lock before auth attempt
- **Default:** 5 failed attempts → 15 min lock

### Test Files
- **CinemaAuthApplicationTests.java** - Basic Spring Boot context test

---

## 2. BOOKING-SERVICE (8083)

### Directory Structure
- **Total Java Files:** 35 (Main: 34, Test: 1)
- **Main Path:** `/booking-service/src/main/java/com/namnd/bookingservice/`
- **Key Directories:**
  - `controller/` - Booking REST endpoints
  - `service/` + `impl/` - Booking business logic, seat locking, expiry scheduler
  - `model/` - JPA entities (Booking, BookingSeat, BookingStatus enum)
  - `dto/` - Data transfer objects
  - `repository/` - Spring Data repositories
  - `listener/` - Kafka consumer for payment events
  - `websocket/` - WebSocket STOMP publisher for real-time seat updates
  - `config/` - Security, Redis, Kafka, WebSocket, OpenAPI
  - `client/` - Feign client for movie-service
  - `exception/` - Custom exceptions
  - `test/` - Integration tests with EmbeddedKafka

### All Java Classes with Descriptions

#### Controllers (1)
- **BookingController** - REST API (reserve, get, list, confirm, cancel bookings; get booked seats per showtime)

#### Services & Interfaces (5)
- **BookingService** - Business logic interface (reserve, confirm, cancel, get, list)
- **BookingServiceImpl** - Implements booking lifecycle with seat locking, price calculation, Kafka audit events
- **SeatLockService** - Distributed lock interface using Redis (5-min TTL, atomic all-or-nothing)
- **SeatLockServiceImpl** - Redis SET NX EX pattern, sorted seat processing to prevent deadlocks, rollback on partial failure
- **NotificationPublisherService** - Kafka publisher for booking notifications (not implemented in this service, just interface)

#### Models (3)
- **Booking** - Aggregate root: PENDING → CONFIRMED | CANCELLED | EXPIRED; tracks userId, showtimeId, totalAmount, timestamps
- **BookingSeat** - Detail entity: Links booking to seat with price, label, type (STANDARD, VIP, etc.)
- **BookingStatus** - Enum: PENDING, CONFIRMED, CANCELLED, EXPIRED

#### DTOs (5)
- **BookingRequestDto** - Request payload (showtimeId, List<seatIds>)
- **BookingResponseDto** - Response payload (id, userId, showtimeId, status, totalAmount, seats, timestamps)
- **BookingSeatDto** - Nested seat info (seatId, label, type, price)
- **SeatInfoDto** - Movie-service DTO (id, label, type, available, price)
- **ShowtimeInfoDto** - Movie-service DTO (id, movieId, basePrice, datetime, hall)
- **SeatStatusMessage** - WebSocket message (List<seatIds>, status string)

#### Repositories (2)
- **BookingRepository** - Find by id, userId, showtimeId; custom queries for user bookings, pending with expiry
- **BookingSeatRepository** - Find booked seat IDs per showtime (for public API showing occupied seats)

#### Listeners (1)
- **PaymentEventListener** - Kafka consumer for `payment-events` topic
  - Handles `PaymentCompletedEvent`: Confirms booking, publishes notification
  - Handles `PaymentFailedEvent`: Cancels booking, releases seat locks, publishes notification
  - **Idempotency:** Skips events if booking already in terminal state (CONFIRMED/CANCELLED)
  - **Exceptions:** Propagate to trigger DLT (Dead Letter Topic) retry via DefaultErrorHandler

#### WebSocket & Real-Time Updates (1)
- **SeatWebSocketPublisher** - STOMP topic publisher
  - Topic: `/topic/showtime/{showtimeId}/seats`
  - Publishes SeatStatusMessage on lock/unlock (public endpoint, no auth required for viewing available seats)

#### Schedulers (1)
- **BookingExpiryScheduler** - Scheduled task to expire PENDING bookings after reservation window
  - Cron-based or fixed-delay cleanup

#### Feign Client (1)
- **MovieServiceClient** - Remote call to movie-service
  - `getShowtime(showtimeId)` - Fetch showtime details (base price, datetime)
  - `getSeatsForShowtime(showtimeId)` - Fetch all seats for validation

#### Configuration (8)
- **SecurityConfig** - JWT auth filter, security rules
- **WebSocketConfig** - STOMP broker setup, topic/queue endpoints
- **RedisConfig** - StringRedisTemplate for seat locking
- **KafkaConsumerConfig** - EventEnvelope deserialization, payment-events listener config, DLT setup
- **KafkaProducerConfig** - Event publishing configuration
- **FeignJwtInterceptor** - JWT token injection into Feign requests to movie-service
- **OpenApiConfig** - Swagger/OpenAPI documentation
- **HttpLoggingConfig** - HTTP logging

#### Exception Handlers & Custom Exceptions (3)
- **GlobalExceptionHandler** - Centralized exception mapping to HTTP responses
- **SeatAlreadyLockedException** - Thrown when seat lock acquisition fails
- **BookingNotFoundException** - Thrown when booking not found (triggers Kafka DLT on payment event)

#### HTTP Filters (1)
- **HttpLoggingFilter** - Structured HTTP logging

#### Metrics & Other (1)
- **MetricsConfig** - Counter beans: bookingCreatedCounter, bookingConfirmedCounter, bookingCancelledCounter

#### Entry Point (1)
- **BookingServiceApplication** - Spring Boot entry point

### Key Configurations (application.yml)
```yaml
server.port: 8083
spring.datasource: PostgreSQL (bookingdb)
spring.data.redis: Redis for seat locks
spring.kafka:
  consumer.group-id: booking-service
  topics: payment-events
jwt.auth.secret: (JWT secret from config server)
jwt.auth.public-paths:
  - /api/bookings/showtimes/*/booked-seats  <-- Public: Get booked seats
  - /ws/**  <-- WebSocket: Real-time seat updates
```

### Recent Additions & Key Features

#### Distributed Seat Locking with Redis
- **SeatLockServiceImpl** - Redis-based 5-minute locks
  - Atomic all-or-nothing: `lockSeats()` sorts seat IDs to prevent deadlocks, acquires all or rolls back partial
  - Pattern: `seat:lock:{showtimeId}:{seatId}` → value is userId (for audit)
  - TTL: 300 seconds (5 minutes)
  - Prevents overbooking in high-concurrency scenarios

#### Payment Event Consumption with Idempotency
- **PaymentEventListener.handlePaymentEvent(EventEnvelope)** - Kafka consumer
  - Uses `EventEnvelope` wrapper for generic payload deserialization
  - Discriminates on `eventType` field: "payment.completed" vs "payment.failed"
  - **Idempotency:** Checks booking status before confirming/cancelling (skips if already terminal)
  - **Exception handling:** Propagates exceptions to DLT after retries
  - **Notifications:** Publishes success/failure notifications to notification-service via Kafka

#### WebSocket Real-Time Seat Updates
- **SeatWebSocketPublisher.publishSeatUpdate()** - Broadcast seat status changes
  - Topic: `/topic/showtime/{showtimeId}/seats`
  - Client subscribes: `/user/queue/showtime/{showtimeId}/seats` (if authenticated) or topic (if public)
  - Triggered on lock/unlock during reserve/cancel operations

#### Booking Lifecycle with Expiry
- **Booking** entity: Status machine (PENDING → CONFIRMED | CANCELLED | EXPIRED)
- **BookingServiceImpl.reserve()** - Creates PENDING booking with 5-min expiry, locks seats, calculates total price
- **BookingServiceImpl.confirmBooking()** - Transitions to CONFIRMED on payment success
- **BookingServiceImpl.cancelBooking()** - Releases seat locks, transitions to CANCELLED
- **BookingExpiryScheduler** - Cleanup: Expires stale PENDING bookings

### Test Files
- **PaymentEventListenerIntegrationTest.java** - EmbeddedKafka integration tests
  - Tests happy path: Payment completed → Booking confirmed
  - Tests failure path: Payment failed → Booking cancelled
  - Tests idempotency: Duplicate payment events are skipped
  - Tests DLT routing: Non-existent bookings route to Dead Letter Topic after retries

---

## 3. AUDIT-SERVICE (8086)

### Directory Structure
- **Total Java Files:** 11 (Main: 11, Test: 0)
- **Main Path:** `/audit-service/src/main/java/com/namnd/auditservice/`
- **Key Directories:**
  - `controller/` - Admin REST API for audit log search
  - `consumer/` - Kafka consumer for audit events
  - `domain/` - JPA entities
  - `dto/` - Request/response DTOs
  - `repository/` - Spring Data repositories with JPA Specification
  - `specification/` - JPA Specification builders for advanced search
  - `mapper/` - Entity-to-DTO mapping
  - `config/` - Kafka configuration

### All Java Classes with Descriptions

#### Controllers (1)
- **AdminAuditLogController** - Admin-only REST API (requires ADMIN role)
  - GET `/api/audit/logs` - Search audit logs with pagination (max 100 per page)
  - GET `/api/audit/logs/{id}` - Get single audit log by ID
  - Uses JPA Specification for dynamic filtering

#### Kafka Consumer (1)
- **AuditEventConsumer** - Consumes from `audit-events` Kafka topic
  - **Idempotency:** Uses `eventId` as unique constraint; skips duplicate events
  - **Generic payload:** Receives `EventEnvelope<?>`, converts payload to `AuditEvent` via ObjectMapper
  - **Error handling:** Catches DataIntegrityViolationException on duplicate insertion
  - **Transactional:** Single @Transactional method for atomic persistence

#### Domain Entities (1)
- **AuditLog** - JPA entity for persisting audit events
  - Fields: eventId (unique), userId, userIp, action (enum), entityType, entityId, beforeState (text), afterState (text), sourceService, traceId, requestPath, createdAt
  - Indexes: user_id, action, entity_type, created_at (for query performance)

#### DTOs (2)
- **AuditLogSearchRequest** - Query parameters for filtering (userId, action, entityType, dateRange, etc.)
- **AuditLogResponse** - Response payload (id, eventId, userId, userIp, action, entityType, entityId, beforeState, afterState, sourceService, traceId, requestPath, createdAt)

#### Repositories (1)
- **AuditLogRepository** - Spring Data JPA with custom methods
  - `findAll(Specification, Pageable)` - Dynamic filtering via JPA Specification
  - `existsByEventId(eventId)` - Check for duplicate events (idempotency)

#### Specifications (1)
- **AuditLogSpecification** - JPA Specification builder for dynamic WHERE clauses
  - Filters by userId, action, entityType, dateRange, sourceService, etc.
  - Used by AdminAuditLogController for advanced search

#### Mapper (1)
- **AuditLogMapper** - Maps AuditLog entity to AuditLogResponse DTO
  - Also maps EventEnvelope<AuditEvent> to AuditLog entity (used by consumer)

#### Configuration (2)
- **KafkaConsumerConfig** - EventEnvelope deserialization, audit-events topic listener config
- **KafkaTopicConfig** - Topic creation/management (audit-events topic definition)

#### Entry Point (1)
- **AuditServiceApplication** - Spring Boot entry point

### Key Configurations (application.yml)
```yaml
server.port: 8086
spring.datasource: PostgreSQL (auditdb)
spring.kafka:
  consumer.group-id: audit-service
  topics: audit-events
jwt.auth.secret: (JWT secret from config server)
jwt.auth.public-paths: []  <-- All endpoints require JWT + ADMIN role
```

### Recent Additions & Key Features

#### Kafka Event Consumption with Idempotency
- **AuditEventConsumer** - Generic EventEnvelope consumption
  - Receives events from all services (auth, booking, movie, payment, etc.)
  - **Idempotency key:** eventId (unique constraint in DB)
  - **Generic deserialization:** ObjectMapper.convertValue(envelope.payload(), AuditEvent.class)
  - **Deduplication:** Checks `existsByEventId()` before processing; also catches DataIntegrityViolationException on constraint violation
  - **Logging:** Info on success, debug on duplicate, error on exceptions

#### PostgreSQL Persistence with Indexes
- **AuditLog** entity with 4 indexes for common queries
- **createdAt** index enables efficient time-range filtering
- **user_id, action, entity_type** indexes optimize filtering by role/context

#### Dynamic Audit Log Search
- **AdminAuditLogController** - JPA Specification-based search
  - Page size capped at 100 (MAX_PAGE_SIZE security check)
  - Default sort by createdAt DESC (most recent first)
  - Supports filtering on all AuditLog fields

#### State Change Tracking
- **beforeState** and **afterState** fields (text/JSON) - Full snapshots of entity state before/after action
- Useful for compliance audits, debugging, and rollback analysis

---

## Summary Comparison

| Aspect | Auth-Service | Booking-Service | Audit-Service |
|--------|--------------|-----------------|---------------|
| **Java Files** | 66 | 35 | 11 |
| **Main Purpose** | JWT auth, user management, OAuth2 | Seat reservations, locking | Event logging, audit trails |
| **Database** | PostgreSQL (testdb) | PostgreSQL (bookingdb) | PostgreSQL (auditdb) |
| **Redis** | Token caching | Seat locks (5-min TTL) | Not used |
| **Kafka** | Producer (auth events) | Consumer (payment events) → Producer | Consumer (audit events) |
| **WebSocket** | No | Yes (real-time seats) | No |
| **Security** | Bearer JWT, CORS, OAuth2 | Bearer JWT | Bearer JWT + ADMIN role |
| **Metrics** | 4 counters (login, register, etc.) | 3 counters (booking lifecycle) | None (default metrics) |
| **Tests** | 1 basic context test | 1 EmbeddedKafka integration | 0 |

---

## Recent Additions Across All Services

### Auth-Service
1. **SetupPasswordDto** - Deferred password setup DTO
2. **ActivationServiceImpl.activateWithPassword()** - NEW: Password setup at activation time
3. **AuthController.activateWithPassword()** - NEW: POST endpoint for deferred password activation
4. **OAuth2UserLinkingService** & **UserOAuthProvider** - Google OAuth2 provider linkage
5. **PasswordHistoryService** - Password reuse prevention (tracks last 3)
6. **AccountLockService** - Brute-force protection (5 failed attempts → 15 min lock)

### Booking-Service
1. **SeatLockServiceImpl** - Redis-based distributed locking (5-min TTL, atomic)
2. **SeatWebSocketPublisher** - Real-time seat status updates via STOMP
3. **PaymentEventListener** - Kafka consumer with idempotency checks
4. **BookingExpiryScheduler** - Automated cleanup of expired pending bookings
5. **FeignJwtInterceptor** - JWT injection into movie-service calls

### Audit-Service
1. Full implementation of Kafka-driven audit log persistence
2. JPA Specification-based dynamic search (no recent additions, stable)
3. Idempotency via eventId unique constraint

---

## Configuration Highlights

**Shared Patterns:**
- All services use PostgreSQL with Spring Boot Actuator (health, prometheus)
- All use Kafka with EventEnvelope<T> pattern for type-safe event handling
- All configured with Eureka service discovery
- All use structured logging via logback-logstash-encoder (JSON output to stdout)
- Distributed tracing via Zipkin (sampling probability configurable)

**Unique Patterns:**
- **Auth:** Redis for token blacklisting and session management; OAuth2 Google integration
- **Booking:** Redis for distributed seat locks; WebSocket for real-time updates
- **Audit:** JPA Specification for SQL generation; ADMIN role-based access control

