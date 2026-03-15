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
- PostgreSQL (auth→testdb, movie→moviedb, booking→bookingdb, payment→paymentdb)
- Redis (:6379) - token blacklist, locks, dedup
- Kafka (:9092) - event streaming (3 topics: movie-events, payment-events, notification-events)
- Prometheus (:9090) + Grafana (:3000) + Loki (:3100) - monitoring
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
  - `/api/movies/**` → movie-service (CRUD, ratings, comments, reactions)
  - `/api/showtimes/**` → movie-service
  - `/api/theaters/**` → movie-service
  - `/api/bookings/**` → booking-service
  - `/api/payments/**` → payment-service
  - `/api/notifications/**` → notification-service (SSE stream, REST CRUD, broadcast)
  - `/api/notifications/stream` → notification-service (SSE endpoint, **ContentCachingResponseWrapper skipped to prevent thread exhaustion**)
- Aggregates OpenAPI documentation: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- HttpLoggingFilter: Logs requests with X-Correlation-ID, skips response caching for SSE paths
- Actuator endpoints (internal only, not exposed via gateway)

### Business Services (5 modules)

**auth-service (:8081)** - Authentication & user management
- Controllers: AuthController, TokenValidationController
- Services: JwtService, UserService, ActivationService, PasswordResetService, BlacklistedTokenService, AccountLockService, PasswordHistoryService, RedisService
- Security: Spring Security 6.x with @EnableMethodSecurity, SecurityFilterChain pattern
- JWT: JJWT 0.12.6 HS512 (15-min access token, 7-day refresh, roles+userId claims)
- Token Blacklist: Redis with auto-TTL (fail-closed on outage)
- Account Lockout: 5 failed attempts → 15-min auto-unlock
- Password History: Maintains last 3 password hashes per user, prevents reuse in password reset & change-password flows
- Email Events: Publishes NotificationRequestedEvent to Kafka (no direct SMTP)
- Endpoints: /api/auth/login, /register, /refresh-token, /logout, /forgot-password, /reset-password, /change-password (auth required)
- Database: testdb (8 tables: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history)

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

### Shared Libraries (2 modules)

**jwt-auth-spring-boot-starter** - Reusable JWT validator for downstream services
- JwtAutoConfiguration: Conditional beans via @ConditionalOnProperty(jwt.auth.enabled=true)
- JwtAuthProperties: Configuration properties (prefix=jwt.auth)
- JwtTokenValidator: Validates HS512 signature, expiration
- JwtAuthenticationFilter: Extracts token, validates, sets SecurityContext
- Usage: Downstream services add this dependency, configure jwt.auth.secret from config-server, auto-enable via SecurityFilterChain

**kafka-events** - Shared domain event models
- EventEnvelope<T>: Wrapper (eventId UUID, eventType, source service, correlationId, timestamp, payload)
- Event Classes: PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent, MovieCreatedEvent, ShowtimeCreatedEvent, NotificationRequestedEvent
- Topics: movie-events, payment-events, notification-events (configured in docker-compose.yml)

### Frontend (1 module)

**cinema-frontend (Angular 18)** - Web UI
- Port: 4200 (dev) → 80 (prod via Nginx)
- Stack: TypeScript 5.5, Material 18, Stripe.js 8.9, RxJS
- Lazy-loaded routes: /auth, /movies, /booking, /payment, /profile (includes /profile/change-password), /admin, /notifications
- Components: ChangePasswordComponent (reactive form with current/new/confirm fields, visibility toggles, validation)
- API proxy: Configured to route /api/* to http://api-gateway:8080
- Nginx SPA fallback for client-side routing
- Password change integration: "Change Password" button on ProfileComponent

## Data Flow Patterns

### Authentication Flow
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

CLIENT: [Subsequent requests]
        Authorization: Bearer {accessToken}
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

## Data Persistence

**Per-Service Databases (PostgreSQL 16):**
- auth-service: testdb (8 tables: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens)
- movie-service: moviedb (7 tables: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 tables: bookings, booking_seats)
- payment-service: paymentdb (1 table: payments)
- notification-service: notificationdb (1 table: notifications with columns userId, title, message, notificationType, isRead, createdAt; indexed (userId, createdAt DESC))

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

**Loki (:3100)**
- Log aggregation, 7-day retention
- Labels: job, instance, application (service name)

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
| Micrometer | (via Boot) | Metrics |
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
