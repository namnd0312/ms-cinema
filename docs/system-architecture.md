# System Architecture

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Java:** 21 LTS | **Spring Boot:** 3.4.3 | **Spring Cloud:** 2024.0.1

## High-Level Overview

MS Cinema is an 11-module Spring Cloud microservices platform for cinema ticket booking:

```
                        CLIENT (Web/Mobile)
                              │ HTTP:8080
                    ┌─────────▼──────────┐
                    │   api-gateway      │
                    │   (:8080, gateway) │
                    └────────┬───────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│ eureka-srv   │    │config-server │    │auth-service  │
│  (:8761)     │    │  (:8888)     │    │  (:8081)     │
└──────────────┘    └──────────────┘    └──────────────┘
                                              │
                ┌─────────────────────────────┼──────────────────┐
                ▼                             ▼                  ▼
           ┌─────────────┐        ┌──────────────┐    ┌──────────────┐
           │movie-service│        │booking-svc   │    │payment-svc   │
           │  (:8082)    │        │  (:8083)     │    │  (:8084)     │
           └─────────────┘        └──────────────┘    └──────────────┘
                                        │
                                        ▼
                                  ┌─────────────┐
                                  │notification │
                                  │  (:8085)    │
                                  └─────────────┘

Infrastructure:
- PostgreSQL (auth→testdb, movie→moviedb, booking→bookingdb, payment→paymentdb, audit→auditdb, notification→notificationdb)
- Redis (:6379) - token blacklist, locks, dedup
- Kafka (:9092) - event streaming (5 topics: movie-events, payment-events, notification-events, notification.in_app, audit-events)
- Prometheus (:9090) + Grafana (:3000) + Loki (:3100) - monitoring
- Zipkin (:9411) - distributed tracing
- Kafdrop (:9000) - Kafka topic browser
```

## Module Architecture

### Infrastructure Services (3 modules)

**eureka-server (:8761)** - Service discovery registry
- Netflix Eureka (self-register disabled, client discovery)
- All services register heartbeat every 10s
- Lease timeout: 30s

**config-server (:8888)** - Centralized configuration
- Loads from classpath:/config-repo/ (native profile)
- Provides shared JWT secret to all services
- Per-service override configs supported

**api-gateway (:8080)** - Single entry point
- Spring Cloud Gateway MVC (servlet-based, not WebFlux)
- Routes requests to downstream services via Eureka load balancing
- Routes:
  - `/api/auth/**` → auth-service
  - `/api/users/**` → auth-service
  - `/oauth2/authorization/**` → auth-service (OAuth2 authorization endpoint)
  - `/login/oauth2/code/**` → auth-service (OAuth2 callback endpoint)
  - `/api/movies/**` → movie-service (CRUD, ratings, comments, reactions)
  - `/api/showtimes/**` → movie-service
  - `/api/theaters/**` → movie-service
  - `/api/bookings/**` → booking-service
  - `/api/payments/**` → payment-service
  - `/api/notifications/**` → notification-service (SSE stream, REST CRUD, broadcast)
  - `/api/notifications/stream` → notification-service (SSE endpoint, **ContentCachingResponseWrapper skipped to prevent thread exhaustion**)
  - `/api/audit/**` → audit-service (admin-only audit log API, requires ADMIN role)
  - `/ws/**` → Nginx proxy directly to booking-service (WebSocket STOMP endpoint, **NEW March 22, 2026**, bypasses gateway)
- Aggregates OpenAPI documentation: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- HttpLoggingFilter: Logs requests with X-Correlation-ID, skips response caching for SSE paths
- Actuator endpoints (internal only, not exposed via gateway)

### Business Services (6 modules)

**auth-service (:8081)** - Authentication & user management
- Controllers: AuthController, TokenValidationController
- Services: JwtService, UserService, ActivationService, PasswordResetService, BlacklistedTokenService, AccountLockService, PasswordHistoryService, RedisService, OAuth2UserLinkingService
- Security: Spring Security 6.x with @EnableMethodSecurity, OAuth2 client, SecurityFilterChain pattern
- JWT: JJWT 0.12.6 HS512 (15-min access token, 7-day refresh, roles+userId claims)
- Token Blacklist: Redis with auto-TTL (fail-closed on outage)
- Account Lockout: 5 failed attempts → 15-min auto-unlock
- Password History: Maintains last 3 password hashes per user, prevents reuse in password reset & change-password flows
- **Deferred Password Setup:** Users register without password (username, email, fullName only), set password via email activation link
  - Registration: POST /api/auth/register creates user with password=NULL, active=false, sends activation email
  - Activation Email: Links to frontend /auth/setup-password?token=uuid
  - New Endpoint: POST /api/auth/activate-with-password {token, password, confirmPassword} - validates token, hashes password, seeds password_history, sets active=true, marks token used (@Transactional)
  - Backward Compat: GET /api/auth/activate (old endpoint) still works for OAuth integration
  - Config: activationBaseUrl updated to frontend URL (application.yml, config-repo/auth-service.yml)
- OAuth2 Login: Google OAuth2 integration via Spring Security (email_verified auto-link)
  - UserOAuthProvider entity: Stores provider linkage (provider_name, provider_user_id, linkedAt)
  - OAuth2AuthenticationSuccessHandler: Generates JWT + refresh token, redirects to frontend with query params
  - OAuth2UserLinkingService: Find/create user, auto-link by email if verified, handle race conditions
  - OAuth users: password=NULL until user initiates password reset
- Email Events: Publishes NotificationRequestedEvent to Kafka (no direct SMTP)
- Endpoints: /api/auth/login, /register, /activate, /activate-with-password (NEW), /refresh-token, /logout, /forgot-password, /reset-password, /change-password (auth required), /oauth2/authorization/**, /login/oauth2/code/**
- Database: testdb (9 tables: users [password nullable], roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history, user_oauth_providers)

**movie-service (:8082)** - Movie catalog, showtimes, ratings, comments, reactions
- Controllers: MovieController, TheaterController, ShowtimeController, MovieRatingController, MovieCommentController, CommentReactionController
- Models: Movie, Theater, Seat, Showtime, MovieRating, MovieComment, CommentReaction
- Features:
  - Auto-generates seat grids (A-Z rows) on theater creation
  - Star ratings (1-5): Upsert per user, summary with avg/count/user's rating
  - Flat comments: Paginated (20/page), soft-delete via status column (ACTIVE/DELETED), owner/admin can update
  - Comment reactions: Per-user toggle (like/dislike), one per user per comment
  - MovieDto enhanced: Includes averageRating, totalRatings, commentCount fields
- Events Published: MovieCreatedEvent, ShowtimeCreatedEvent → movie-events topic
- Database: moviedb (7 tables: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- Security: /api/comments/** permitAll for GET (public), POST/PUT/DELETE require auth

**booking-service (:8083)** - Seat reservation & booking lifecycle
- Controllers: BookingController
- Models: Booking (PENDING→CONFIRMED/CANCELLED/EXPIRED), BookingSeat
- Feign Client: Calls movie-service for showtime/seat details
- Kafka Listeners: Consumes PaymentCompletedEvent (CONFIRMED), PaymentFailedEvent (CANCELLED)
- Redis Locks: Seat:lock:{showtimeId}:{seatId} with 5-min TTL
- Scheduler: BookingExpiryScheduler (60s check) transitions expired PENDING bookings
- **WebSocket Configuration (NEW March 22, 2026):**
  - WebSocketConfig.java: Spring WebSocket + STOMP, in-memory broker (app:/topic/showtime/*)
  - SeatStatusMessage.java: DTO (showtimeId, seatId, status, userId, action: LOCK/RESERVE/CANCEL)
  - SeatWebSocketPublisher.java: Broadcasts SeatStatusMessage to /topic/showtime/{showtimeId}/seats endpoint
  - Modified BookingServiceImpl: Calls publishSeatStatusChange() on lock/reserve/cancel
  - Modified BookingExpiryScheduler: Publishes CANCEL when booking expires
  - **nginx Proxy:** /ws/* endpoint routes directly to booking-service:8083 with WebSocket upgrade headers (Connection: Upgrade, Upgrade: websocket)
  - **Frontend Connection:** /ws endpoint connects via nginx to booking-service (bypasses api-gateway for WebSocket latency optimization)
- Database: bookingdb (2 tables: bookings, booking_seats)

**payment-service (:8084)** - Stripe payment processing
- Controllers: PaymentController
- Models: Payment, PaymentIntent
- Stripe Integration:
  - PaymentIntent creation with idempotency key (pay-{bookingId})
  - Webhook endpoint: POST /api/payments/webhook
  - Signature verification (stripeSig header)
  - stripeEventId dedup (prevent replay attacks)
- Kafka Events: Publishes PaymentCompletedEvent/PaymentFailedEvent after DB commit (TransactionalEventListener)
- Database: paymentdb (1 table: payments)

**notification-service (:8085)** - Email notifications + Real-time in-app SSE notifications
- **Kafka Listeners:**
  - notification-events topic: NotificationRequestedEvent → EmailSenderService → SMTP delivery
  - notification.in_app topic: InAppNotificationEvent → SseEmitterRegistryService → broadcast to all SSE clients
  - Consumer group: `notification-service-{instanceId}` (unique per instance for broadcast pattern)
- **REST Controllers:**
  - `NotificationSseController`:
    - GET /api/notifications/stream (SSE endpoint, JWT via query param: ?token=JWT)
    - Returns: event: InAppNotificationEvent, :heartbeat (comment), Connection: keep-alive, 30s interval
  - `NotificationRestController`:
    - GET /api/notifications (paginated, createdAt DESC, auth required)
    - PATCH /api/notifications/{id}/read (mark single, auth required)
    - PATCH /api/notifications/read-all (bulk mark all, auth required)
    - GET /api/notifications/unread-count (returns {count: N}, auth required)
    - POST /api/notifications/broadcast (admin-only test broadcast)
- **Models:** Notification JPA entity (id, userId, title, message, notificationType, isRead, createdAt)
- **Services:**
  - SseEmitterRegistryService: ConcurrentHashMap-based registry, heartbeat every 30s, atomic operations
  - InAppNotificationServiceImpl: CRUD, mark-as-read, broadcast, emitter management
  - EmailSenderService: SMTP (Gmail smtp.gmail.com:587, credentials MAIL_USERNAME/MAIL_PASSWORD)
  - NotificationDeduplicationService: Redis key notification:processed:{eventId}, 24h TTL
  - NotificationPublisherService: Publishes InAppNotificationEvent to notification.in_app topic
- **SSE Configuration:**
  - Heartbeat: Comment-only SSE events (:heartbeat) every 30s (prevent timeout, minimal overhead)
  - Emitter timeout: 30 minutes (configurable, client auto-reconnect on disconnect)
  - Client reconnect: Exponential backoff 1s→30s max, 5 attempts max
- **Database (notificationdb):** notifications table (id PK, userId FK, title, message, notificationType ENUM, isRead bool, createdAt timestamp)
  - Index: (userId, createdAt DESC) for efficient pagination
- **Error Handling:**
  - Kafka: 3 retries, exponential backoff (1s→2s→4s capped 10s), DLT for failures
  - Race condition fix: Atomic computeIfPresent in removeEmitter prevents concurrent mod issues
  - Broadcast optimization: findDistinctUserIds instead of findAll to avoid OOM on large datasets
- **Fail-Open:** Redis unavailable doesn't block email send or SSE emit; dedup is optional

**audit-service (:8086)** - Centralized audit logging
- **Purpose:** Consumes audit events from business services, persists immutable audit log, exposes admin API with filtering
- **Kafka Listener:**
  - Topic: audit-events (3 partitions, 90-day retention, EventEnvelope<AuditEvent>)
  - Consumer group: audit-service
  - Dedup: eventId UNIQUE constraint prevents duplicates on Kafka retries
  - Processing: ObjectMapper.convertValue() for generic payload deserialization
- **REST API:**
  - GET /api/audit/logs (paginated, sorted createdAt DESC, max 100 per page)
    - Filter params: userId, action [ENUM: LOGIN/LOGOUT/REGISTER/CHANGE_PASSWORD/CREATE_MOVIE/UPDATE_MOVIE/DELETE_MOVIE/CREATE_SHOWTIME/UPDATE_SHOWTIME/RESERVE_BOOKING/CANCEL_BOOKING/CREATE_PAYMENT], entityType, startDate, endDate
  - GET /api/audit/logs/{id} (retrieve single audit log entry)
  - Requires @PreAuthorize("hasRole('ADMIN')")
- **Models:** AuditLog JPA entity (id, eventId UNIQUE, userId, userIp, action ENUM, entityType, entityId, beforeState TEXT, afterState TEXT, sourceService, traceId, requestPath, createdAt)
  - afterState: JSON serialized state post-change (v1 implementation)
  - beforeState: NULL in v1 (reserved for Envers integration in v2)
  - LOGIN afterState skipped to prevent JWT token leakage
- **Repositories:** AuditLogRepository with Specification pattern for dynamic filtering
- **Database (auditdb):** audit_logs table
  - Indexes: (user_id), (action), (entity_type), (created_at) for efficient filtering
  - 90-day retention policy (configurable via audit.retention-days)
- **Error Handling:**
  - Kafka: 3 retries, exponential backoff (1s→2s→4s capped 10s), DLT (audit-events.DLT) for failures
  - Dedup: eventId UNIQUE constraint + DataIntegrityViolationException catch prevents duplicate inserts

### Shared Libraries (2 modules)

**jwt-auth-autoconfigure** - Reusable JWT validator for downstream services
- JwtAutoConfiguration: Conditional beans via @ConditionalOnProperty(jwt.auth.enabled=true)
- JwtAuthProperties: Configuration properties (prefix=jwt.auth)
- JwtTokenValidator: Validates HS512 signature, expiration
- JwtAuthenticationFilter: Extracts token, validates, sets SecurityContext
- Usage: Downstream services add this dependency, configure jwt.auth.secret from config-server, auto-enable via SecurityFilterChain

**kafka-events** - Shared domain event models & audit infrastructure
- EventEnvelope<T>: Wrapper (eventId UUID, eventType, source service, correlationId, timestamp, payload)
- Event Classes: PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent, MovieCreatedEvent, ShowtimeCreatedEvent, NotificationRequestedEvent, AuditEvent
- AuditAction Enum: LOGIN, LOGOUT, REGISTER, CHANGE_PASSWORD, CREATE_MOVIE, UPDATE_MOVIE, DELETE_MOVIE, CREATE_SHOWTIME, UPDATE_SHOWTIME, RESERVE_BOOKING, CANCEL_BOOKING, CREATE_PAYMENT
- Audit Infrastructure: @Auditable annotation, AuditAspect (AOP), AuditEntityListener (JPA), AuditEventPublisher, AuditAfterCommitListener (@TransactionalEventListener)
- Auto-Configuration: AuditAutoConfiguration (@ConditionalOnClass(KafkaTemplate)), AuditBeanProvider, AuditHttpContext
- Optional dependencies: spring-boot-starter-aop, spring-kafka, spring-boot-starter-data-jpa, spring-boot-starter-security, micrometer-tracing-core
- Topics: movie-events, payment-events, notification-events, notification.in_app, audit-events (all configured in docker-compose.yml)

### Frontend (1 module)

**cinema-frontend (Angular 18)** - Web UI
- Port: 4200 (dev) → 80 (prod via Nginx)
- Stack: TypeScript 5.5, Material 18, Stripe.js 8.9, RxJS
- **WebSocket Packages (NEW March 22, 2026):** @stomp/stompjs, sockjs-client
- Lazy-loaded routes: /auth, /movies, /booking, /payment, /profile (includes /profile/change-password), /admin, /notifications
- **Seat Grid Components (FR-3.1 NEW):**
  - seat-grid.component.ts: Color-coded seats (STANDARD=green, PREMIUM=blue, VIP=amber), row A-Z labels, legend
  - seat-selection.component.ts: Booking workflow + timer countdown
  - seat-suggestion-panel.component.ts: Displays recommended adjacent seat groups
  - seat-websocket.service.ts: STOMP/SockJS client connecting to /ws on booking-service (via nginx proxy, bypassing api-gateway)
  - seat-suggestion.service.ts: O(n*m) client-side seat matching algorithm
  - Utilities: seat-grid-layout.utils.ts, seat-grid-keyboard-navigation.utils.ts, seat-selection-timer.utils.ts
  - **March 22 Fixes:** Global sockjs-client polyfill added, Instant→String serialization fixed, nginx proxy with conditional Connection header for WebSocket upgrade
- **Accessibility:** WCAG 2.1 AA (ARIA grid role, keyboard nav, color+icons, focus styles)
- Components: ChangePasswordComponent (reactive form with current/new/confirm fields, visibility toggles, validation)
- API proxy: Configured to route /api/* to http://api-gateway:8080
- WebSocket proxy: /ws/* routes directly to booking-service via nginx (for low-latency WebSocket connections)
- Nginx SPA fallback for client-side routing
- Password change integration: "Change Password" button on ProfileComponent
- OAuth2 Callback: OAuth2CallbackComponent handles Google login callback, extracts JWT tokens from query params

## Data Flow Patterns

### Authentication Flow

#### User Registration (Deferred Password Setup - NEW March 27, 2026)
```
CLIENT: POST /api/auth/register
        └─► api-gateway (routes to auth-service)
            └─► auth-service AuthController.register()
                ├─ Validate email unique, required
                ├─ Create User: username, email, fullName, password=NULL, active=false, ROLE_USER
                ├─ Create ActivationToken (24-hour expiration, UUID-based)
                ├─ EmailService.sendActivationEmail()
                │  └─ Publish NotificationRequestedEvent to Kafka notification-events topic
                │     └─ Email body includes: {activationBaseUrl}/auth/setup-password?token={uuid}
                └─ 200 OK "User registered successfully! Check your email to set up your password."

FRONTEND: User clicks activation link in email
          └─► http://localhost:4200/auth/setup-password?token=uuid
              └─► SetupPasswordComponent extracts token, shows password form

CLIENT: POST /api/auth/activate-with-password
        └─► api-gateway (routes to auth-service)
            └─► auth-service AuthController.activateWithPassword()
                ├─ Validate token (exists, not expired, not used)
                ├─ Validate password + confirmPassword match
                ├─ @Transactional:
                │  ├─ Hash password via BCrypt
                │  ├─ Update User: password=hash, active=true
                │  ├─ PasswordHistoryService.savePasswordToHistory() → seeds entry
                │  ├─ ActivationToken.markAsUsed() → prevent reuse
                │  └─ Commit
                └─ 200 OK "Account activated successfully! You can now log in."

BACKWARD COMPATIBILITY: Old GET /api/auth/activate?token=uuid still works
                        └─ Auto-activates without password (for OAuth users)
```

#### Traditional Login (Email + Password)
```
CLIENT: POST /api/auth/login
        └─► api-gateway (routes to auth-service)
            └─► auth-service AuthController
                ├─ AccountLockService.isLocked() → 423 if locked
                ├─ AuthenticationManager.authenticate() → BCrypt password match
                │  └─ UserServiceImpl.loadUserByUsername(email) → DB
                ├─ [BadCredentials] → AccountLockService.loginFailed() (inc counter)
                ├─ [Success] → AccountLockService.loginSucceeded() (reset counter)
                ├─ JwtService.generateTokenLogin(auth) → HS512 signed, JTI, roles+userId, 15min
                ├─ RefreshTokenService.createRefreshToken() → 7-day token, DB
                └─ 200 OK JwtResponseDto(token, refreshToken, id, email, username, roles)
```

#### OAuth2 Login (Google)
```
CLIENT: Click "Sign in with Google" button
        └─► GET /oauth2/authorization/google
            └─► api-gateway → auth-service (Spring Security OAuth2)
                ├─ Redirect to Google consent screen
                └─ User grants permission → Google redirects to callback

CLIENT: [OAuth2 callback with authorization code]
        └─► GET /login/oauth2/code/google?code=...&state=...
            └─► api-gateway → auth-service
                └─► OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
                    ├─ Extract OAuth2User attributes (sub, email, name, email_verified)
                    ├─ OAuth2UserLinkingService.processOAuth2User()
                    │  ├─ [1] Check existing provider link (sub) → return user
                    │  ├─ [2] Check email match + email_verified=true → auto-link
                    │  ├─ [3] Create new user (password=NULL, active=true, ROLE_USER)
                    │  └─ Create UserOAuthProvider record
                    ├─ JwtService.generateTokenFromEmail() → HS512, 15min
                    ├─ RefreshTokenService.createRefreshToken() → 7-day, DB
                    └─ Redirect to frontend: /oauth2-callback?token={jwt}&refreshToken={refreshToken}

FRONTEND: OAuth2CallbackComponent
         └─► Extract token + refreshToken from URL
             ├─ Clear tokens from browser history (security)
             ├─ AuthService.handleOAuth2Callback() → store in localStorage
             └─ Navigate to /movies
```

#### Subsequent Authenticated Requests
```
CLIENT: GET /api/movies, Authorization: Bearer {accessToken}
        └─► api-gateway/auth-service
            └─► JwtAuthenticationFilter.doFilterInternal()
                ├─ Extract Bearer token
                ├─ JwtService.validateJwtToken() → signature+expiry+blacklist check
                ├─ Load UserDetails from SecurityContext (roles, userId embedded in token)
                └─ Pass to endpoint handler
```

### Event-Driven Flow (Booking + Payment)
```
USER: Reserve seats → booking-service BookingController.reserve()
      ├─ BookingService.reserveSeats()
      │  ├─ Get showtime/seats from movie-service (Feign)
      │  ├─ Acquire Redis locks (seat:lock:{showtimeId}:{seatId}, 5min TTL)
      │  ├─ Create Booking (PENDING), BookingSeat records
      │  ├─ Publish BookingCreatedEvent → Kafka
      │  └─ Return booking confirmation
      └─ 200 OK

USER: Pay via Stripe → payment-service PaymentController.createPaymentIntent()
      ├─ PaymentService.createPaymentIntent()
      │  ├─ Stripe API: PaymentIntent create (idempotency key: pay-{bookingId})
      │  ├─ Save Payment record (DB, INITIATED status)
      │  └─ Return clientSecret for frontend Stripe.js
      │
      └─ Client submits Stripe form
         └─ Stripe POST {baseUrl}/webhook → payment-service
            ├─ PaymentWebhookService.handleWebhookEvent()
            │  ├─ Verify signature (stripeSig header)
            │  ├─ Check stripeEventId (dedup, prevent replay)
            │  ├─ Update Payment (COMPLETED status)
            │  ├─ Publish PaymentCompletedEvent → Kafka payment-events
            │  └─ DB commit trigger TransactionalEventListener
            │
            └─ booking-service Kafka Listener
               ├─ Consumes PaymentCompletedEvent
               ├─ Transition Booking → CONFIRMED
               ├─ Release Redis locks (success → keep reservations)
               └─ Send email via notification-events

FAIL CASE: Payment failed
      └─ payment-service publishes PaymentFailedEvent
         └─ booking-service listener
            ├─ Transition Booking → CANCELLED
            ├─ Release Redis locks (failure → free seats)
            └─ Send failure notification
```

### Real-Time Seat Availability Flow (WebSocket STOMP - NEW March 22, 2026 - FR-3.1 COMPLETE)
```
cinema-frontend: Seat Selection Page Load (FR-3.1 Implementation)
      ├─ seat-websocket.service.ts: Connect to /ws via SockJS+STOMP (nginx proxy to booking-service:8083)
      │  └─ JWT validation during WebSocket handshake (SpringSecurity-integrated)
      │
      ├─ Subscribe to /topic/showtime/{showtimeId}/seats
      │  └─ Listen for SeatStatusMessage events (showtimeId, seatId, status, userId, action)
      │
      └─ User selects seat → BookingController.reserve()
         └─ booking-service BookingService.reserveSeats()
            ├─ Acquire Redis lock (seat:lock:{showtimeId}:{seatId}, 5min TTL)
            ├─ Create Booking (PENDING), BookingSeat (LOCKED)
            ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP endpoint
            │  └─ /topic/showtime/{showtimeId}/seats message (action: LOCK, userId, seatId, status)
            └─ All connected clients receive LOCK update, seat UI marked unavailable

USER: Payment completes → payment-service webhook
      ├─ PaymentWebhookService.handlePaymentCompleted()
      ├─ booking-service PaymentCompletedEvent listener
      │  ├─ Transition Booking → CONFIRMED
      │  ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP
      │  │  └─ /topic/showtime/{showtimeId}/seats (action: RESERVE, userId, seatId, status)
      │  └─ All clients: seat marked RESERVED (gray color, permanent)
      │
      └─ Timeout Case: Booking expires (PENDING past expiresAt)
         ├─ BookingExpiryScheduler (60s check) detects expiry
         ├─ Transition Booking → EXPIRED, release Redis lock
         ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP
         │  └─ /topic/showtime/{showtimeId}/seats (action: CANCEL, userId, seatId, status)
         └─ All clients: seat color resets to AVAILABLE (green/blue/amber)

FRONTEND: seat-websocket.service.ts event handlers
      ├─ onSeatStatusChange(message) processes incoming SeatStatusMessage
      │  ├─ LOCK: Update seat-grid component (user color, mark busy)
      │  ├─ RESERVE: Dim seat, show permanent booked indicator
      │  ├─ CANCEL: Reset to available color (green/blue/amber)
      │  └─ Update internal state, re-render seat grid
      │
      ├─ Reconnect Logic: Auto-reconnect with exponential backoff
      │  └─ Disconnect detected → retry 1s, 2s, 4s, 8s, 16s, 30s max
      │
      ├─ Nginx Routing (NEW March 22, 2026):**
      │  ├─ /ws/* directly routed to booking-service:8083 (bypasses api-gateway)
      │  ├─ WebSocket upgrade headers: Connection: Upgrade, Upgrade: websocket
      │  └─ <100ms latency (vs. 2-3s polling; 100x faster)
      │
      └─ Adjacent Seat Suggestion Panel (if incomplete selection)
         └─ seat-suggestion.service.ts: findBestAdjacentGroup()
            ├─ O(n*m) client-side algorithm on available seats
            ├─ Metrics: proximity (Euclidean distance), row alignment, type uniformity
            ├─ Score seats: same row preferred, PREMIUM/VIP type match, closest distance
            └─ seat-suggestion-panel.component.ts shows top recommendation + accept/dismiss buttons
```

### Notification Flow (Email + Real-Time SSE)
```
==== EMAIL NOTIFICATIONS (Synchronous) ====
auth-service: User registers
      └─► ActivationService.createActivationToken()
          ├─ Generate UUID token (24h expiry)
          ├─ Publish NotificationRequestedEvent(email, subject, body, type=ACTIVATION)
          │  └─ KafkaTemplate.send("notification-events", event)
          └─ Store ActivationToken in DB

notification-service: Kafka Consumer (notification-events topic)
      ├─ KafkaListener.handleNotificationEvent(event)
      │  ├─ NotificationDeduplicationService: Check Redis (notification:processed:{eventId})
      │  ├─ [Duplicate] Skip → prevent resend
      │  ├─ [New] EmailSenderService.sendEmail()
      │  │  └─ Spring Mail JavaMailSender → Gmail SMTP
      │  ├─ Store Notification record (DB, SENT status)
      │  ├─ Set Redis dedup key (24h TTL)
      │  └─ Commit Kafka offset
      │
      └─ Error Handling: If exception
         └─ Kafka error handler
            ├─ 1st retry: 1s delay
            ├─ 2nd retry: 2s delay
            ├─ 3rd retry: 4s delay
            └─ Failure: Send to DLT (notification-events.DLT)

==== IN-APP NOTIFICATIONS (Real-Time SSE) ====
payment-service: Payment completes (webhook)
      ├─ PaymentWebhookService.handlePaymentCompleted()
      │  ├─ Update Payment status → COMPLETED
      │  └─ TransactionalEventListener: Publish PaymentCompletedEvent
      │
      └─ booking-service: Kafka Listener (payment-events)
         ├─ Consumes PaymentCompletedEvent
         ├─ Transition Booking → CONFIRMED
         ├─ NotificationPublisherService.publishInAppNotification()
         │  └─ KafkaTemplate.send("notification.in_app", InAppNotificationEvent)
         └─ Store Notification record (DB, isRead=false)

notification-service: Kafka Consumer (notification.in_app topic)
      ├─ Unique consumer group per instance (notification-service-{instanceId})
      │  └─ Ensures all connected SSE clients receive broadcast
      │
      ├─ KafkaListener.handleInAppNotification(event)
      │  ├─ Store Notification record (DB, DELIVERED status)
      │  ├─ For each userId in event:
      │  │  └─ SseEmitterService.sendToUser(userId, notificationEvent)
      │  │     └─ Emit SSE event to all connected clients for this userId
      │  └─ Commit Kafka offset
      │
      └─ Error Handling: Graceful degrade (Redis outage doesn't block SSE emit)

cinema-frontend: Real-Time SSE Connection
      ├─ NotificationSseService.connect(jwt)
      │  ├─ EventSource to /api/notifications/stream?token={jwt}
      │  ├─ Heartbeat every 30s (keep connection alive)
      │  └─ Exponential backoff reconnect on disconnect
      │
      ├─ Listen: InAppNotificationEvent (SSE message type)
      │  ├─ NotificationBellComponent.onNotification()
      │  │  ├─ Increment unread badge count
      │  │  └─ Show snackbar toast
      │  └─ NotificationListComponent: Auto-refresh if open
      │
      ├─ Listen: Heartbeat (SSE comment-only, no processing)
      │  └─ Reset connection timeout, keep-alive indicator
      │
      └─ User Actions:
         ├─ Click bell → open NotificationListComponent
         ├─ GET /api/notifications (paginated, ordered by createdAt DESC)
         ├─ Click notification → PATCH /api/notifications/{id}/read (mark-as-read)
         └─ Badge updates: GET /api/notifications/unread-count
```

### Audit Logging Flow (@Auditable AOP + @TransactionalEventListener)
```
SERVICE: @Auditable-annotated method (auth/movie/booking/payment)
      ├─ AuditAspect intercepts before method execution
      ├─ Extract userId from JwtAuthenticatedUser principal (reflection-based email())
      ├─ Extract userIp from AuditHttpContext (request IP)
      ├─ Extract traceId from Micrometer tracing context
      ├─ Execute business logic (create/update/delete entity)
      ├─ Capture afterState: ObjectMapper to JSON
      ├─ Build AuditEvent: userId, action [ENUM], entityType, entityId, afterState, sourceService, traceId
      ├─ Store AuditSpringEvent in ApplicationEventPublisher
      └─ Return business result

SPRING TRANSACTION COMMIT (after-commit pattern):
      └─► @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)
          ├─ AuditAfterCommitListener.onAuditSpringEvent()
          ├─ AuditEventPublisher.publishAuditEvent()
          └─ KafkaTemplate.send("audit-events", EventEnvelope<AuditEvent>)

audit-service: Kafka Consumer (audit-events topic)
      ├─ KafkaListener.handleAuditEvent(EventEnvelope<AuditEvent>)
      │  ├─ ObjectMapper.convertValue() payload to AuditEvent
      │  ├─ Build AuditLog entity
      │  ├─ eventId dedup: UNIQUE constraint prevents duplicates
      │  ├─ Save to auditdb.audit_logs
      │  └─ Commit Kafka offset
      │
      └─ Error Handling: If exception (e.g., DataIntegrityViolationException on duplicate)
         └─ Kafka error handler
            ├─ Catch DataIntegrityViolationException → log (duplicate, skip)
            ├─ 1st retry: 1s delay
            ├─ 2nd retry: 2s delay
            ├─ 3rd retry: 4s delay
            └─ Failure: Send to DLT (audit-events.DLT)

ADMIN: Query audit logs
      └─► GET /api/audit/logs?userId={id}&action={action}&entityType={type}&startDate={date}&endDate={date}
          └─► audit-service AdminAuditLogController
              ├─ Build Specification from filter params
              ├─ Query auditdb with indexes (user_id, action, entity_type, created_at)
              ├─ Pagination: 20/page (max 100)
              └─ Return Page<AuditLogResponse> (sorted createdAt DESC)
```

### Audit Logging Flow (@Auditable AOP)
```
SERVICE: @Auditable-annotated method (auth, movie, booking, payment)
      ├─ Intercept before method execution (AOP aspect)
      ├─ Capture context (userId from JWT, action type, entityType, entityId)
      ├─ Execute business logic
      ├─ Capture afterState (entity post-change)
      ├─ AuditEventPublisher.publish(AuditEvent)
      │  └─ KafkaTemplate.send("audit-events", AuditEvent)
      └─ Return result

audit-service: Kafka Consumer (audit-events topic)
      ├─ KafkaListener.handleAuditEvent(event)
      │  ├─ Build AuditLog entity from AuditEvent
      │  ├─ eventId dedup check (UNIQUE constraint prevents duplicates)
      │  ├─ Save to auditdb.audit_logs
      │  └─ Commit Kafka offset
      │
      └─ Error Handling: If exception
         └─ Kafka error handler
            ├─ 1st retry: 1s delay
            ├─ 2nd retry: 2s delay
            ├─ 3rd retry: 4s delay
            └─ Failure: Send to DLT (audit-events.DLT)

ADMIN: Query audit logs
      └─► GET /api/audit/logs?userId={id}&action={action}&entityType={type}
          └─► audit-service AdminAuditLogController
              ├─ Build Specification from filter params
              ├─ Query auditdb with indexes (user_id, action, entity_type, created_at)
              └─ Return Page<AuditLogResponse> (sorted createdAt DESC, max 100/page)
```

## Data Persistence

**Per-Service Databases (PostgreSQL 16):**
- auth-service: testdb (9 tables: users [with @JsonIgnore on password], roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history, user_oauth_providers)
- movie-service: moviedb (7 tables: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 tables: bookings, booking_seats)
- payment-service: paymentdb (1 table: payments)
- notification-service: notificationdb (1 table: notifications with columns userId, eventId, title, message, notificationType ENUM, isRead, createdAt; indexed (userId, createdAt DESC))
- audit-service: auditdb (1 table: audit_logs with id PK, eventId UNIQUE, userId, userIp, action ENUM, entityType, entityId, beforeState TEXT, afterState TEXT, sourceService, traceId, requestPath, createdAt; indexed userId, action, entityType, createdAt; 90-day retention)

**Shared Resources:**
- PostgreSQL cluster (same instance, different databases)
- Redis: token blacklist, seat locks, notification dedup
- Kafka: event topics (replication factor 3, partitions 1)

## Security Model

**Authentication:** JWT (JJWT 0.12.6) HS512 symmetric signing
- Access token: 15 minutes (900000 ms)
- Refresh token: 7 days (604800000 ms), rotated on each use
- Token claims: sub (email), roles, userId, iat, exp, jti (unique ID)

**Authorization:** Spring Security @PreAuthorize method-level
- Example: `@PreAuthorize("hasRole('ROLE_ADMIN')")`

**Token Revocation:** JTI-based Redis blacklist
- On logout: Extract JTI, store in Redis with expiry = token exp time
- On request: JwtAuthenticationFilter checks blacklist (fail-closed)

**Account Lockout:** After 5 failed login attempts
- Counter stored in User.failedAttempts
- Lock time stored in User.lockTime
- Auto-unlock after 15 minutes (configurable namnd.app.lockDurationMs)

**Password Encoding:** BCrypt (Spring Security PasswordEncoder)

## Observability

**Prometheus (:9090)**
- Scrape interval: 15 seconds
- Retention: 7 days
- Scrape targets: /actuator/prometheus on all 8 services

**Grafana (:3000)**
- 2 prebuilt dashboards:
  - JVM Micrometer (memory, GC, threads, CPU)
  - Spring Boot HTTP Overview (req rate, latency, errors, DB pool, business counters)
- Business Counters: auth.login/logout/register, booking.created/confirmed/cancelled, payment.initiated/completed/failed
- Zipkin datasource provisioned for trace visualization

**Loki (:3100)**
- Log aggregation, 7-day retention
- Labels: job, instance, application (service name)
- Auto-injects traceId/spanId via MDC + LogstashEncoder (visible in log queries)

**Zipkin (:9411)**
- Distributed tracing via Micrometer Tracing (OpenTelemetry bridge)
- Endpoint: http://zipkin:9411/api/v2/spans
- Sampling: 100% (configurable via TRACING_SAMPLING_PROBABILITY env var)
- Traces all service-to-service requests, Kafka events, database calls
- traceId/spanId auto-injected into logs via MDC for correlation
- No code changes required: auto-configured by Spring Boot 3.4.3
- Docker image: openzipkin/zipkin:3.4 (pinned version)

**Kafdrop (:9000)**
- Kafka topic browser UI for local development and debugging
- Inspect topics, view messages, monitor consumer groups
- Docker image: obsidiandynamics/kafdrop:4.0.2

## Technology Stack Summary

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 21 LTS | Runtime |
| Spring Boot | 3.4.3 | Framework |
| Spring Cloud | 2024.0.1 | Microservices (Eureka, Config, Gateway) |
| Spring Security | 6.x | Authentication/Authorization |
| JJWT | 0.12.6 | JWT handling |
| Spring Kafka | (via Boot) | Event streaming |
| Spring Mail | (via Boot) | SMTP integration |
| Spring Data JPA | (via Boot) | ORM |
| PostgreSQL | 16 | Relational DB |
| Redis | 7 | Caching, locks, dedup |
| Kafka | 3.7 KRaft | Message broker |
| Stripe SDK | latest | Payment processing |
| Micrometer Tracing | (via Boot) | OpenTelemetry bridge for distributed tracing |
| Zipkin | latest | Trace aggregation & visualization |
| SpringDoc OpenAPI | 2.8.4 | API documentation |
| Lombok | 1.18.x | Boilerplate reduction |

## Deployment

**Docker Compose Stack:**
```bash
docker-compose up --build
```

Starts:
- PostgreSQL (5432)
- Kafka (9092, 3 brokers)
- Redis (6379)
- All 8 services + Prometheus + Grafana + Loki

**Production Considerations:**
- Database: Migrate from per-service to shared schema with row-level security
- Secrets: Use external vault (HashiCorp Vault, AWS Secrets Manager)
- Replicas: Scale services via Kubernetes, service discovery via Eureka
- Load Balancing: API Gateway can run multiple instances behind external LB
- Monitoring: Add Prometheus alerting rules, PagerDuty integration
- Logging: Collect logs to Loki, query via Grafana
