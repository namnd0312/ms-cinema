# Project Overview & Product Development Requirements

**Project:** MS Cinema
**Version:** 0.0.1-SNAPSHOT
**Group:** com.namnd
**Status:** Active Development — Microservice Integration Phase
**Last Updated:** March 2026

## Executive Summary

MS Cinema is an **11-module Spring Cloud microservices platform** for cinema ticket booking with event-driven architecture, JWT authentication, Stripe payments, comprehensive audit logging, and observability. The system consists of infrastructure services (Eureka, Config Server, API Gateway), 6 business services, 2 shared libraries, and Angular frontend.

**Key Characteristics:**
- Single external entry point: API Gateway (port 8080)
- **Auth-service** (port 8081): JWT auth lifecycle, email activation, account lockout (5 attempts/15min), token rotation, @Auditable integration
- **Movie-service** (port 8082): Movies, theaters, showtimes; auto-generates seat grids (A-Z rows); star ratings (1-5), paginated comments with soft-delete, comment reactions (like/dislike), @Auditable on CRUD
- **Booking-service** (port 8083): Seat reservation with Redis locking (5-min TTL), lifecycle states (PENDING→CONFIRMED/CANCELLED/EXPIRED), @Auditable on operations
- **Payment-service** (port 8084): Stripe integration, idempotent payment intents, webhook verification, @Auditable on payments
- **Notification-service** (port 8085): Kafka consumer, SMTP email delivery, Redis dedup (24h TTL), real-time SSE notifications
- **Audit-service** (port 8086): Centralized audit logging, Kafka consumer for audit-events, admin API with filtering, PostgreSQL immutable audit logs, 90-day retention
- **kafka-events module:** Shared domain events (PaymentCompletedEvent, BookingCreatedEvent, AuditEvent), @Auditable annotation, AOP aspect, JPA listeners
- **jwt-auth-autoconfigure:** Reusable JWT validator for all services (JJWT 0.12.6, HS512)
- Spring Cloud Eureka for service discovery, Config Server for centralized configuration
- **Kafka topics:** payment-events, movie-events, notification-events, notification.in_app, audit-events (3 retries, exponential backoff, DLT, 90-day audit retention)
- Redis for token blacklist, booking locks, notification dedup
- PostgreSQL per-service (auth→testdb, movie→moviedb, booking→bookingdb, payment→paymentdb, notification→notificationdb, audit→auditdb)
- Prometheus (9090) + Grafana (3000) + Loki 3.0 (3100) + Zipkin (9411) observability stack

## Functional Requirements

### Authentication (FR-001)
- **User Login:** Accept email/password, validate credentials, return JWT tokens
  - Accept JSON payload with email, password
  - Authenticate via Spring AuthenticationManager
  - Generate HS512-signed access token (15-min expiration) with unique JTI
  - Generate refresh token (7-day expiration)
  - Return both tokens + user metadata in JwtResponseDto

- **User Registration:** Accept registration data, create user with roles
  - Accept JSON with username, email (required), password, fullName, roles array
  - Validate email uniqueness and required (username uniqueness not enforced)
  - Encode password via BCrypt
  - Create roles if new, assign existing roles by ID
  - Persist User entity with role associations

- **Token Refresh:** Accept refresh token, return new token pair
  - Accept JSON with refreshToken
  - Validate refresh token exists and not expired
  - Generate new access token with new JTI
  - Rotate refresh token (delete old, create new)
  - Return new token pair in TokenRefreshResponseDto

- **Password Reset:** Email-driven password reset flow via Kafka notifications
  - Forgot Password: Accept email, generate 24-hour reset token, publish Kafka event
  - Reset Password: Accept reset token + new password, validate token against 3 most recent password hashes, update password
  - notification-service consumes event, sends SMTP email
  - Security: Returns generic message regardless of email existence

- **Change Password:** Allow authenticated users to change password with validation
  - POST /api/auth/change-password (requires Bearer JWT token)
  - Validate current password matches stored hash
  - Verify new password not in 3 most recent password history entries
  - Update password and record in password history table
  - Return success/failure with descriptive error messages

- **Logout:** Blacklist token and delete refresh token
  - Accept Authorization header with access token
  - Extract and blacklist JTI with expiration date
  - Delete user's refresh token from database
  - Scheduled cleanup: hourly job removes expired blacklist entries

### Authorization (FR-002)
- **Token Validation:** Validate JWT on protected requests
  - Extract Bearer token from Authorization header
  - Parse & verify HS512 signature
  - Check token expiration
  - Load user from database via SecurityContext

- **Role-Based Access:** Enforce roles via method-level security
  - Support @PreAuthorize("hasRole('ROLE_ADMIN')")
  - Support @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_PM')")
  - Return 403 Forbidden on insufficient role

### User Management (FR-003)
- **User Entity:** Store user credentials & profile
  - Username (not unique - duplicates allowed)
  - Email (unique - used as login identifier)
  - Password (BCrypt hashed)
  - Full name
  - Set of assigned roles

- **Role Entity:** Define permission roles
  - Role name (ROLE_USER, ROLE_PM, ROLE_ADMIN)
  - Many-to-many relationship with User

### Notification Management (FR-004)
- **Event-Driven Email:** Kafka-based async notifications
  - Publish NotificationRequestedEvent on account activation, password reset
  - notification-service (port 8085) consumes events from Kafka
  - Sends HTML-formatted emails via SMTP (no direct email sending in auth-service)
  - Decouples auth-service from email delivery concerns

### Real-Time In-App Notifications (FR-006)
- **Server-Sent Events (SSE):** Real-time notification streaming to authenticated clients
  - GET /api/notifications/stream (SSE endpoint, JWT auth via query parameter)
  - 30-second heartbeat to prevent connection timeout
  - Unique Kafka consumer group per instance for broadcast pattern
  - Exponential backoff reconnect strategy on client disconnect (1s→30s max)
- **Persistence:** PostgreSQL notificationdb with notifications table
  - Track userId, title, message, notificationType, isRead, createdAt
  - Support for paginated retrieval and mark-as-read operations
- **REST API for Notifications:**
  - GET /api/notifications (paginated list, ordered DESC by createdAt)
  - PATCH /api/notifications/{id}/read (mark individual notification as read)
  - PATCH /api/notifications/read-all (bulk mark all as read)
  - GET /api/notifications/unread-count (unread badge count)
  - POST /api/notifications/broadcast (admin-only test broadcast)
- **Event Types:** PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM
- **Frontend Integration:** Notification bell component with badge, snackbar alerts, notification list page

### Movie Ratings & Comments (FR-005)
- **Star Ratings:** 1-5 point scale per movie, upsert per user
  - POST /api/movies/{movieId}/ratings (authenticated, upsert)
  - GET /api/movies/{movieId}/ratings (public, returns avg/count/userRating)
- **Comments:** Flat structure with soft-delete (status ACTIVE/DELETED)
  - POST /api/movies/{movieId}/comments (authenticated)
  - GET /api/movies/{movieId}/comments?page=0&size=20 (public, paginated)
  - PUT /api/comments/{commentId} (owner only)
  - DELETE /api/comments/{commentId} (owner or admin, soft-delete)
- **Comment Reactions:** Per-user like/dislike toggle
  - POST /api/comments/{commentId}/reactions (authenticated, toggle)
  - DELETE /api/comments/{commentId}/reactions (remove reaction)

## Non-Functional Requirements

### Security (NFR-001)
- **Password Encoding:** BCrypt with Spring Security encoder
- **JWT Signing:** HMAC SHA-512 (HS512) algorithm
- **Access Token Expiration:** 15 minutes (900000 ms, configurable)
- **Refresh Token Expiration:** 7 days (604800000 ms, configurable)
- **Token Rotation:** Refresh token replaced on each use
- **Token Revocation:** JTI-based blacklisting for logout
- **Email Validation:** Required unique email on registration
- **Password Reset:** 24-hour expiration tokens via secure email
- **Session Management:** Stateless (SessionCreationPolicy.STATELESS)
- **Scheduled Cleanup:** Hourly job cleans expired blacklist entries
- **CSRF Protection:** Disabled for JWT API (appropriate)
- **CORS:** Enabled for all origins (configurable)

### Performance (NFR-002)
- **Database Queries:** Optimized via Spring Data JPA
- **JWT Processing:** In-memory token validation (no database lookup on validate)
- **Eager Loading:** User roles fetched eagerly to minimize queries
- **Connection Pooling:** Standard Spring Boot datasource (HikariCP)

### Scalability (NFR-003)
- **Stateless Architecture:** No session affinity required
- **Service Discovery:** Eureka enables load-balanced multi-instance auth-service
- **Shared Config:** JWT secret distributed via Config Server (all instances consistent)
- **JWT claims:** roles + userId embedded, downstream services avoid DB lookups
- **Gateway routing:** `lb://auth-service` uses Eureka for load balancing

### Availability (NFR-004)
- **Database Dependency:** PostgreSQL required for startup
- **Graceful Degradation:** Token validation fails if signature key corrupted
- **Container Restart:** docker-compose restart policy: unless-stopped

### Maintainability (NFR-005)
- **Code Organization:** Package structure by layer (controller, service, model, repository)
- **Logging:** DEBUG level for app package, SQL query logging
- **Configuration:** YAML-based with environment variable overrides
- **Dependencies:** Minimal, well-maintained (Spring Boot 3.4.3, JJWT 0.12.6)

### Observability (NFR-006)
- **Metrics Collection:** Micrometer auto-instrumentation on all services; exported at `/actuator/prometheus`
- **Scrape Interval:** 15 seconds (Prometheus `global.scrape_interval`)
- **Service Tagging:** All metrics tagged with `application=${spring.application.name}`
- **Dashboards:** JVM Micrometer dashboard (memory, GC, threads, CPU); Spring Boot HTTP Overview (req rate, error rate, latency, HikariCP, business counters)
- **Business Counters:** auth.login/register/logout, booking.created/confirmed/cancelled, payment.initiated/completed/failed
- **Actuator Security:** `/actuator/**` permitted within Docker `my-net` only; not exposed via API Gateway

## Technical Constraints

| Constraint | Specification | Rationale |
|-----------|---------------|-----------|
| Java Version | 21 | Latest LTS, modern features (records, sealed classes, pattern matching) |
| Spring Boot | 3.4.3 | Latest LTS, Java 21 support, virtual threads ready |
| Spring Security | 6.x | New SecurityFilterChain pattern, @EnableMethodSecurity |
| Database | PostgreSQL 16 | Advanced features, JSON/JSONB, improved performance |
| JWT Library | JJWT 0.12.6 (3 artifacts) | Modular design, modern API, async support |
| Message Broker | Apache Kafka | Event streaming; decouples auth from email delivery |
| Email Service | Spring Mail SMTP | async email delivery via notification-service |
| Packaging | JAR | Streamlined deployment, embedded Tomcat |
| Token Algorithm | HS512 | Deterministic, fast, symmetric |
| Session Policy | STATELESS | Matches JWT stateless design |

## Success Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Login Response Time | < 200ms | Spring Security authentication + JWT generation |
| Token Validation | < 50ms | Token parsing + signature verification |
| Database Availability | 99.5% uptime | PostgreSQL monitored via container |
| Code Coverage | > 70% (unit tests) | Maven jacoco plugin |
| Security Compliance | OWASP Top 10 covered | BCrypt, HS512, CSRF disabled, CORS controlled |

## API Contracts

### Login Endpoint
**POST /api/auth/login**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

Response (200 OK):
```json
{
  "id": 1,
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
  "email": "john@example.com",
  "username": "john",
  "name": "John Doe",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Error (401 Unauthorized):
- Invalid credentials, email not found, or password mismatch

### Register Endpoint
**POST /api/auth/register**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "username": "jane",
  "email": "jane@example.com",
  "password": "secure123",
  "fullName": "Jane Doe",
  "roles": [
    {"name": "ROLE_USER"}
  ]
}
```

Response (200 OK):
```
"User registered successfully!"
```

Error (400 Bad Request):
- Email already in use, or email required

### Forgot Password Endpoint
**POST /api/auth/forgot-password**
- Consumes: application/json
- Produces: application/json
- Auth: None (public)

Request:
```json
{
  "email": "jane@example.com"
}
```

Response (200 OK):
```
"If the email exists, a password reset link has been sent."
```

### Reset Password Endpoint
**POST /api/auth/reset-password**
- Consumes: application/json
- Produces: application/json
- Auth: None (token-based)

Request:
```json
{
  "token": "reset-token-from-email",
  "newPassword": "newSecure123"
}
```

Response (200 OK):
```
"Password reset successful."
```

Error (400 Bad Request):
- Invalid, expired, or already-used reset token

### Refresh Token Endpoint
**POST /api/auth/refresh-token**
- Consumes: application/json
- Produces: application/json
- Auth: None (refresh token-based)

Request:
```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Response (200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Error (400 Bad Request):
- Invalid or expired refresh token

### Logout Endpoint
**POST /api/auth/logout**
- Consumes: (none)
- Produces: application/json
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Response (200 OK):
```
"Logged out successfully."
```

Error (400 Bad Request):
- No token provided
- Invalid token

### Validate Token Endpoint (NEW — microservice use)
**POST /api/auth/validate-token**
- Consumes: application/json
- Auth: None (called by other services)

Request:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Response (200 OK — valid token):
```json
{
  "valid": true,
  "userId": 1,
  "email": "john@example.com",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Response (200 OK — invalid/expired/blacklisted):
```json
{
  "valid": false
}
```

### Get Current User Endpoint (NEW)
**GET /api/users/me**
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Response (200 OK):
```json
{
  "id": 1,
  "email": "john@example.com",
  "username": "john",
  "fullName": "John Doe",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Error (401 Unauthorized): missing/invalid/expired token

### Change Password Endpoint
**POST /api/auth/change-password**
- Consumes: application/json
- Produces: application/json
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Request:
```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "confirmPassword": "newPassword456"
}
```

Response (200 OK):
```
"Password changed successfully."
```

Error (400 Bad Request):
- Current password incorrect
- New password matches one of 3 recent passwords (reuse not allowed)
- Passwords don't match

Error (401 Unauthorized):
- Missing/invalid/expired token

### Protected Endpoint Example
**GET /api/protected** (or any non-auth endpoint)
- Auth: JWT Bearer token required
- Header: `Authorization: Bearer <accessToken>`

Response (401 Unauthorized):
- Missing/invalid token
- Token expired (15 min) - use refresh endpoint
- Token blacklisted (logged out) - re-login required
- Invalid signature

Response (403 Forbidden):
- User lacks required role

## Architecture Decisions

### Decision: Stateless JWT vs Session-Based
**Chosen:** Stateless JWT with Refresh Tokens
- **Rationale:** Microservices-friendly, horizontal scaling, no server state for access tokens
- **Trade-off:** Larger token size vs reduced database load; refresh tokens stored in DB for revocation

### Decision: Symmetric (HS512) vs Asymmetric (RS256) Signing
**Chosen:** Symmetric HS512
- **Rationale:** Shared-secret deployment, simpler operations, faster validation
- **Trade-off:** All instances must protect secret vs distributed trust model

### Decision: Eager vs Lazy Role Loading
**Chosen:** Eager (FetchType.EAGER)
- **Rationale:** Roles required in SecurityContext, single query more efficient
- **Trade-off:** Always loads roles even if not needed vs N+1 queries

### Decision: Manual Schema vs Hibernate DDL
**Chosen:** Manual (ddl-auto: none, create-drop for dev)
- **Rationale:** Database as source of truth, version control flexibility
- **Trade-off:** Extra maintenance vs schema evolution control

### Decision: Token Revocation Strategy
**Chosen:** JTI-based Blacklist with Scheduled Cleanup
- **Rationale:** Efficient revocation without modifying JWT claims, scheduled cleanup prevents table bloat
- **Trade-off:** Database lookup on validation vs complete logout support

### Decision: Refresh Token Rotation
**Chosen:** Replace token on each refresh
- **Rationale:** Limits window of exposure if refresh token compromised
- **Trade-off:** Database updates on refresh vs reduced breach impact

### Decision: Password Reset Delivery
**Chosen:** Email-based with stateful tokens
- **Rationale:** Secure, auditable, familiar to users
- **Trade-off:** Requires SMTP config vs alternative delivery methods

## Roadmap

### Phase 1: Foundation (COMPLETE)
- ✓ Core authentication (login/register)
- ✓ JWT generation & validation with JTI
- ✓ Role-based authorization
- ✓ PostgreSQL persistence
- ✓ Docker containerization
- ✓ Basic testing

### Phase 2: Token Management (COMPLETE)
- ✓ Token refresh mechanism with rotation
- ✓ Password reset flow via email
- ✓ Logout with token blacklisting (Redis JTI, auto-TTL)
- ✓ Email verification (activation link)
- ✓ Account lockout after N failed attempts (auto-unlock)

### Phase 3: Microservice Integration (COMPLETE)
- ✓ 10-module Maven project: 5 business services, 3 infrastructure, 2 shared libs, 1 frontend
- ✓ Spring Cloud Eureka (service registry, :8761)
- ✓ Spring Cloud Config Server (shared JWT secret, :8888, classpath:/config-repo/)
- ✓ Spring Cloud Gateway MVC (single entry :8080, routes, OpenAPI aggregation, HttpLoggingFilter)
- ✓ JWT tokens include `roles` + `userId` claims for downstream use
- ✓ POST /api/auth/validate-token (microservice validation, no DB lookup)
- ✓ GET /api/users/me (authenticated user profile retrieval)
- ✓ jwt-auth-autoconfigure (JJWT 0.12.6, reusable JWT validator, @ConditionalOnProperty)
- ✓ **OpenAPI 3.0 documentation** (Swagger UI on all services, aggregated at gateway)
- ✓ notification-service (Kafka consumer, SMTP via Spring Mail, fail-open on Redis)
- ✓ kafka-events module (PaymentCompletedEvent, BookingCreatedEvent, MovieCreatedEvent, etc.)
- ✓ Auth-service publishes NotificationRequestedEvent (no direct email sending)
- ✓ Prometheus (15s scrape, 7-day retention) + Grafana (3000, 2 dashboards) + Loki 3.0 logs
- ✓ Movie-service auto-generates seat grids (A-Z rows) on theater creation
- ✓ Booking-service Redis locking (5-min TTL, key pattern: seat:lock:{showtimeId}:{seatId})
- ✓ Payment-service Stripe integration (idempotency key, webhook signature verification)
- ✓ Movie ratings & comments (FR-005): Star ratings, flat comments, soft-delete, reactions
- [ ] Rate limiting on login/forgot-password endpoints

### Phase 4: Security Hardening (Planned)
- [ ] Audit logging on sensitive actions (IP, timestamp)
- [ ] JWT claim validation (issuer, audience)
- [ ] Secret rotation mechanism
- [ ] IP whitelisting
- [ ] Token encryption at rest

### Phase 5: Operations (PARTIAL)
- ✓ Metrics collection (Micrometer/Prometheus) — all 8 services instrumented
- ✓ Grafana dashboards — JVM Micrometer + Spring Boot HTTP Overview
- ✓ Custom business counters — auth, booking, payment events
- ✓ Centralized logging (Loki 3.0 with 7-day retention)
- [ ] CI/CD pipeline (GitHub Actions) — planned
- [ ] Alerting rules (Prometheus alertmanager) — planned
- [ ] Kubernetes deployment manifests — planned
- [ ] Load testing & performance benchmarks — planned

## Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Spring Boot | 3.4.3 | Framework |
| Spring Cloud | 2024.0.1 | Eureka, Config, Gateway |
| Spring Security | 6.x (via Spring Boot) | Authentication/Authorization |
| Spring Data JPA | via Spring Boot | ORM |
| Spring Kafka | via Spring Boot | Message broker integration |
| Spring Mail | via Spring Boot | SMTP email delivery (notification-service) |
| Spring Boot Actuator | via Spring Boot | Metrics endpoint /actuator/prometheus |
| Micrometer | via Actuator | JVM + HTTP + custom business metrics |
| JJWT | 0.12.6 (api, impl, jackson) | JWT handling (HS512) |
| Spring Cloud | 2024.0.1 | Eureka, Config, Gateway, LoadBalancer |
| Stripe Java SDK | latest | Payment processing, webhooks |
| Feign Client | via Spring Cloud | Service-to-service HTTP calls |
| PostgreSQL Driver | latest | Database (auth-service) |
| Lombok | BOM-managed | Boilerplate reduction |
| BCrypt | via Spring Security | Password encoding |
| Jakarta EE | 10+ | Namespace (javax.* → jakarta.*) |

## Configuration Parameters

| Parameter | Default | Scope | Notes |
|-----------|---------|-------|-------|
| server.port (auth-service) | 8081 | Spring Boot | Changed from 8080 |
| server.port (api-gateway) | 8080 | Spring Boot | External entry point |
| server.port (eureka-server) | 8761 | Spring Boot | Service registry |
| server.port (config-server) | 8888 | Spring Boot | Shared config |
| namnd.app.jwtSecret | (Base64 key) | Custom | Shared via Config Server |
| namnd.app.jwtExpiration | 900000 | Custom (ms) | 15 min |
| namnd.app.jwtRefreshExpiration | 604800000 | Custom (ms) | 7 days |
| jwt.auth.secret | (from namnd.app.jwtSecret) | Starter lib | For downstream services |
| jwt.auth.enabled | true | Starter lib | Disable with false |
| jwt.auth.publicPaths | [] | Starter lib | Paths that skip auth |
| spring.datasource.url | jdbc:postgresql://localhost:5432/testdb | JPA | auth-service only |

## Acceptance Criteria

### User Story: Login Workflow
**Given** a user with valid credentials registered in the system
**When** user POSTs to /api/auth/login with email & password
**Then** API returns 200 OK with JWT token valid for 15 minutes

**And** token can be decoded to extract email (JWT sub claim)
**And** token signature verifies with configured jwtSecret
**And** subsequent requests with Authorization header are authenticated

### User Story: Registration Workflow
**Given** a unique email not in the system
**When** user POSTs to /api/auth/register with credentials & roles
**Then** user account created with BCrypt-encoded password
**And** user assigned to specified roles
**And** duplicate email attempt returns 400 Bad Request (duplicate username allowed)

### User Story: Role-Based Access
**Given** a user with ROLE_ADMIN role
**When** accessing endpoint protected by @PreAuthorize("hasRole('ROLE_ADMIN')")
**Then** request succeeds (200 OK)

**And** user without ROLE_ADMIN accessing same endpoint gets 403 Forbidden

## Implementation Notes

### Multi-Module Build Order
Config Server and Eureka must be running before auth-service and api-gateway start.
Config Server loads `config-repo/application.yml` (shared JWT secret) and per-service configs.

### JWT Starter Library Usage
Downstream services add `jwt-auth-autoconfigure` as a dependency, configure:
```yaml
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}  # received from Config Server
    publicPaths: ["/public/**"]
```
Auto-configuration wires `JwtAuthenticationFilter` and stateless `SecurityFilterChain`.

### Email Configuration (auth-service)
- Gmail SMTP (smtp.gmail.com:587)
- Env vars: MAIL_USERNAME, MAIL_PASSWORD (app-specific password)
- `activationBaseUrl` / `passwordResetBaseUrl` — configure for your frontend

### Token Blacklist
- Redis auto-TTL: no cleanup job needed
- Fail-closed: Redis outage → token rejected

## Open Questions

1. **Multi-tenancy:** Future support for multiple organizations?
2. **API Versioning:** How to version endpoints (v1/v2)?
3. **Rate Limiting:** Should login endpoint have rate limiting to prevent brute force?
4. **Email Delivery:** Use service like SendGrid/Mailgun instead of SMTP?
5. **Token Revocation TTL:** Should whitelist instead of blacklist for shorter-lived tokens?
