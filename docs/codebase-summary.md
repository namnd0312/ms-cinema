# Codebase Summary

**Project:** ms-cinema
**Generated:** April 2026
**Architecture:** 6 Business Services + 2 Shared Libraries + Angular Frontend
**Java Version:** 21 LTS
**Spring Boot:** 3.4.3
**Spring Cloud:** 2024.0.1 (Service Discovery via K8s DNS / Static URIs)

## Maven Modules Overview

```
ms-cinema/ (root pom: packaging=pom)
├── Business Services (6 modules)
│   ├── auth-service (:8081) - JWT auth, user management, @Auditable integration, OAuth2
│   ├── movie-service (:8082) - Movies, theaters, showtimes, ratings, comments, @Auditable on CRUD
│   ├── booking-service (:8083) - Seat reservation, Feign → movie-service, WebSocket STOMP, @Auditable
│   ├── payment-service (:8084) - Stripe payments, webhooks, Spring Batch reconciliation, @Auditable
│   ├── notification-service (:8085) - Kafka consumer, email (SMTP), SSE real-time streams
│   └── audit-service (:8086) - Kafka consumer for audit-events, admin API with filtering
├── Shared Libraries (2 modules)
│   ├── kafka-events - Event domain models, AuditEvent record, AuditAction enum, audit infrastructure
│   └── jwt-auth-autoconfigure - Reusable JWT validator (HS512, auto-configuration)
├── Frontend (1 module)
│   └── cinema-frontend (:4200→80) - Angular 18, Material, Stripe.js, WebSocket STOMP client
└── Infrastructure & Deployment
    ├── docker-compose.yml - PostgreSQL (6 DBs), Kafka KRaft, Redis, monitoring stack (local dev)
    ├── k8s/ - Kubernetes manifests for Minikube/OrbStack deployment (ConfigMaps, Secrets, Services, Ingress)
    ├── monitoring/ - Prometheus.yml, Grafana dashboards, Loki config
    └── docs/ - Project documentation
```

## auth-service (Port 8081)

**Key Features:** JWT auth, deferred password setup on activation, email activation, account lockout, token rotation, Kafka event publishing

```
src/main/java/com/namnd/cinema/
├── CinemaAuthApplication.java
├── config/
│   ├── SecurityConfig.java - Spring Security 6.x, @EnableMethodSecurity
│   ├── JwtAuthenticationFilter.java - Extract & validate JWT on each request
│   ├── CustomAccesDeniedHandler.java - 403 error responses
│   ├── OpenApiConfig.java - SpringDoc (Swagger UI)
│   ├── RedisConfig.java - Token blacklist template
│   └── RedisKeyPrefix.java - Constants (blacklist:, lock:)
├── controller/
│   ├── AuthController.java (~230 lines) - login, register, activate, activate-with-password, forgot-password, reset-password, refresh-token, logout
│   ├── TokenValidationController.java - validate-token (for microservices), /api/users/me
│   └── TestController.java - Health check
├── model/ - User, Role, RefreshToken, PasswordResetToken, ActivationToken
├── dto/ - LoginRequestDto, JwtResponseDto, RegisterDto, SetupPasswordDto, ForgotPasswordDto, ResetPasswordDto, RefreshTokenRequestDto, TokenRefreshResponseDto, ValidateTokenRequestDto/ResponseDto, UserInfoResponseDto
├── service/ - JwtService, UserService, RoleService, RefreshTokenService, PasswordResetService, EmailService, ActivationService, BlacklistedTokenService, AccountLockService, RedisService
├── repository/ - UserRepository, RoleRepository, RefreshTokenRepository, PasswordResetTokenRepository, ActivationTokenRepository
└── resources/
    ├── application.yml - port 8081, k8s profile, static service URIs
    └── schema.sql - Users, roles, tokens tables (7 tables)
```

**Key Services:**
- **JwtService** (147 lines): HS512 signing, validates signature+expiration+blacklist, embeds roles+userId claims
- **EmailServiceImpl** (35 lines): Publishes NotificationRequestedEvent to Kafka topic "notification-events" (no direct SMTP)
- **ActivationServiceImpl** (~90 lines): 24-hour email activation tokens (UUID-based), new activateWithPassword() method for deferred password setup
- **PasswordResetServiceImpl** (~80 lines): 24-hour password reset tokens
- **BlacklistedTokenServiceImpl** (~50 lines): Redis JTI blacklist with auto-TTL (fail-closed)
- **AccountLockServiceImpl** (~60 lines): 5-attempt lockout, auto-unlock after 15 minutes
- **OAuth2UserLinkingService** (~100 lines): Finds/creates users from OAuth2 provider data, auto-links by email if verified, handles race conditions

**Audit Integration:**
- AuthController: @Auditable on login, register, logout, change-password methods
- Audit actions: LOGIN, REGISTER, LOGOUT, CHANGE_PASSWORD
- UserService: @Auditable marks user modification operations

**API Endpoints:** See README.md API Reference section

**Database Schema:**
- users (id, username, email UNIQUE, password [nullable - set during activation or for OAuth-only users], fullName, active, failedAttempts, lockTime; @JsonIgnore added to password field for API security)
- roles (id, name)
- user_roles (user_id FK, role_id FK)
- refresh_tokens (id, token UNIQUE, expiryDate, user_id FK)
- password_reset_tokens (id, token UNIQUE, expiryDate, user_id FK)
- activation_tokens (id, token UNIQUE, expiryDate, user_id FK, used)
- password_history (id, user_id FK, password_hash, created_at)
- blacklisted_tokens (id, jti UNIQUE, expiry_date)
- user_oauth_providers (id, user_id FK, provider_name, provider_user_id, provider_email, linked_at; UC: (provider_name+provider_user_id, user_id+provider_name))

**Password History Feature:**
- PasswordHistory entity: Stores up to 3 previous password hashes per user for reuse prevention
- PasswordHistoryService: Manages history CRUD, validates new password against recent entries
- POST /api/auth/change-password: Endpoint for authenticated password changes (blocked for OAuth-only users)
- Password reset (POST /api/auth/reset-password): Validates new password against 3 most recent hashes
- Activation flow (POST /api/auth/activate-with-password): Seeds initial password to history table when user sets password
- Registration flow: No longer seeds password history (deferred until activation)

**OAuth2 Integration:**
- UserOAuthProvider entity: Stores provider linkage (provider_name, provider_user_id, providerEmail, linkedAt)
- UserOAuthProviderRepository: findByProviderNameAndProviderUserId(), existsByUserIdAndProviderName()
- OAuth2AuthenticationSuccessHandler: Generates JWT+refresh token, redirects to frontend with tokens as query params
- OAuth2UserLinkingService: Lookup order (1) existing provider link (2) email match if verified (3) create new
- Unique constraints prevent duplicate links per provider and per user
- Concurrent login race condition: Handled via DataIntegrityViolationException catch during provider link creation
- Auto-create OAuth-only users: password=NULL, active=true, default ROLE_USER assignment
- **March 16 Fix:** LazyInitializationException on user.getRoles() resolved by force-initializing roles within @Transactional context in OAuth2UserLinkingService

## movie-service (Port 8082)

**Key Features:** Movie CRUD, theater CRUD (auto-generates seat grids A-Z rows), showtime management, publishes MovieCreatedEvent/ShowtimeCreatedEvent

**Controllers:**
- MovieController - GET all, GET by id, POST (ADMIN), PUT (ADMIN), DELETE (ADMIN)
- TheaterController - GET all, GET by id, POST (ADMIN, auto-generates seats), PUT (ADMIN), DELETE (ADMIN)
- ShowtimeController - GET all, GET by id, POST (ADMIN), PUT (ADMIN), DELETE (ADMIN)
- MovieRatingController - POST (create/update 1-5 rating, upsert), GET (summary with avg/count/user's rating)
- MovieCommentController - POST (create), GET (paginated list, 20/page), PUT (update, owner only), DELETE (soft-delete)
- CommentReactionController - POST (toggle like/dislike), DELETE (remove reaction)

**Models:**
- Movie (id, title, description, duration, genre, releaseDate; includes averageRating, totalRatings, commentCount in DTO)
- Theater (id, name, location, totalSeats, seats list LAZY)
- Seat (id, seatNumber [A-Z row format], theaterRef, available)
- Showtime (id, movieRef, theaterRef, startTime, endTime, price)
- MovieRating (id, movie_id, user_id, rating [1-5], created_at, updated_at; UNIQUE(movie_id, user_id))
- MovieComment (id, movie_id, user_id, content, status ENUM [ACTIVE/DELETED], created_at, updated_at)
- CommentReaction (id, comment_id, user_id, reaction_type ENUM [LIKE/DISLIKE], created_at; UNIQUE(comment_id, user_id))

**Services:**
- MovieService - CRUD + event publishing (MovieCreatedEvent); query-time aggregation in toDto()
- TheaterService - CRUD + auto-seat-grid generation
- ShowtimeService - CRUD + event publishing (ShowtimeCreatedEvent)
- MovieRatingService - Upsert rating, get summary (avg, count, user's rating)
- MovieCommentService - Create, list (paginated), update, soft-delete
- CommentReactionService - Toggle like/dislike, remove reaction

**Audit Integration:**
- MovieController: @Auditable on create, update, delete methods (admin-only)
- ShowtimeController: @Auditable on create, update methods (admin-only)
- Audit actions: CREATE_MOVIE, UPDATE_MOVIE, DELETE_MOVIE, CREATE_SHOWTIME, UPDATE_SHOWTIME

**Repositories (Custom Queries):**
- MovieRatingRepository - findAverageRatingByMovieId(), countByMovieId()
- MovieCommentRepository - findByMovieIdAndStatusActive (custom @Query)
- CommentReactionRepository - countLikesByCommentId(), countDislikesByCommentId()

**Kafka Events Published:** MovieCreatedEvent, ShowtimeCreatedEvent → topic: movie-events

**Security:** /api/comments/** added to permitAll in SecurityConfig for GET (public comments)

## booking-service (Port 8083)

**Key Features:** Seat reservation with Redis locking (5-min TTL), booking lifecycle (PENDING→CONFIRMED/CANCELLED/EXPIRED), Feign client to movie-service, consumes PaymentCompletedEvent/PaymentFailedEvent, BookingExpiryScheduler (60s check), transactional event listener, real-time WebSocket seat availability broadcasts

**Controllers:**
- BookingController - reserve, getBooking, getUserBookings, confirmBooking, cancelBooking, getBookedSeats

**Models:**
- Booking (id, showtimeRef, userRef, status ENUM, createdAt, expiresAt)
- BookingSeat (id, bookingRef, seatRef, status ENUM)
- SeatStatusMessage (showtimeId, seatId, status, userId, action: LOCK/RESERVE/CANCEL)

**Services:**
- BookingService - Manages lifecycle, Redis locking, event handling
- SeatReservationService - Acquires locks (key pattern: seat:lock:{showtimeId}:{seatId})
- SeatWebSocketPublisher - Broadcasts seat status changes to connected WebSocket clients (STOMP)

**Feign Clients:**
- MovieServiceClient - Fetch showtime & seat details

**Kafka Event Handlers:**
- Consumes PaymentCompletedEvent → transitions booking CONFIRMED, publishes InAppNotificationEvent
- Consumes PaymentFailedEvent → transitions booking CANCELLED, releases locks, publishes InAppNotificationEvent
- Publishes BookingCreatedEvent → notification-events
- Publishes InAppNotificationEvent → notification.in_app (payment confirm/fail events)

**Schedulers:**
- BookingExpiryScheduler (60s): Finds PENDING bookings past expiresAt, transitions EXPIRED, releases Redis locks

**Audit Integration:**
- BookingController: @Auditable on reserve, cancelBooking methods
- Audit actions: RESERVE_BOOKING, CANCEL_BOOKING

**Redis Lock Key Pattern:** `seat:lock:{showtimeId}:{seatId}`
**Lock TTL:** 5 minutes (configurable)

**WebSocket Configuration (NEW - March 22, 2026):**
- WebSocketConfig.java: Spring WebSocket configuration, STOMP endpoint /ws/booking, SockJS fallback, JWT validation during handshake
- Message broker: In-memory (app:/booking/seats/*)
- Authenticated via JWT during WebSocket handshake (SpringSecurity-integrated)
- SeatStatusMessage.java: DTO (showtimeId, seatId, status, userId, action: LOCK/RESERVE/CANCEL)
- SeatWebSocketPublisher.java: Service to broadcast seat status changes to connected clients via STOMP (/topic/showtime/{id}/seats)
- Event actions: LOCK (user acquiring seat), RESERVE (payment confirmed), CANCEL (booking expired/failed)
- Modified BookingServiceImpl: Calls SeatWebSocketPublisher.publishSeatStatusChange() on lock/reserve/cancel operations
- Modified BookingExpiryScheduler: Publishes CANCEL action when booking expires
- Frontend subscribes to /topic/showtime/{showtimeId}/seats for real-time updates (<100ms latency)

## payment-service (Port 8084)

**Key Features:** Stripe integration with idempotency key (pay-{bookingId}), webhook processing with signature verification, refund (ADMIN-only), publishes PaymentCompletedEvent/PaymentFailedEvent, TransactionalEventListener for after-commit Kafka publish, **Spring Batch reconciliation with scheduled daily jobs**

**Controllers:**
- PaymentController - create-intent, confirm, getPayment, getUserPayments, refund (ADMIN), webhook (POST)
- ReconciliationController (NEW, @PreAuthorize("hasRole('ADMIN')"), base: /api/payments/reconciliation)
  - POST /trigger - Trigger reconciliation for date range (validates max 31 days)
  - GET /runs - Paginated list of reconciliation runs
  - GET /runs/{runId} - Single run details with counts
  - GET /runs/{runId}/items - Paginated items with optional discrepancyType filter
  - GET /summary - Latest run stats (summary counts)
  - PUT /items/{itemId}/resolve - Mark item resolved with notes

**Models:**
- Payment (id, bookingRef, amount, currency, status ENUM, stripePaymentIntentId, createdAt)
- ReconciliationRun (startDate, endDate, status [RUNNING/COMPLETED/FAILED], counts for matched/mismatched/missing)
- ReconciliationItem (runId FK, stripePaymentIntentId, localPaymentId, discrepancyType ENUM, amounts, statuses, resolved, notes)
- DiscrepancyType enum: MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE
- ReconciliationStatus enum: RUNNING, COMPLETED, FAILED

**Batch Job (Spring Batch):**
- JobConfig: Job + Step beans (chunk size 100)
- LocalPaymentReader: ItemReader querying paymentdb by createdAt range
- ReconciliationProcessor: ItemProcessor calling PaymentIntent.retrieve() per payment, classifies discrepancy
- ReconciliationItemWriter: ItemWriter persisting ReconciliationItem chunks to paymentdb
- ReconciliationJobListener: afterJob calls Stripe list API for MISSING_LOCAL, finalizes run counts

**Services:**
- PaymentService - Stripe PaymentIntent creation (idempotency key: pay-{bookingId}), webhook handling
- StripeWebhookService - Signature verification (stripeSig header), stripeEventId dedup (prevent replay)
- ReconciliationService (interface) & ReconciliationServiceImpl
  - triggerReconciliation(startDate, endDate) - Validates date range ≤31 days, creates ReconciliationRun, launches batch job
  - getRuns(pageable) - Returns paginated runs sorted by createdAt DESC
  - getRunDetails(runId) - Returns single run with summary counts
  - getRunItems(runId, discrepancyType, pageable) - Paginated items with optional filter
  - getSummary() - Latest run stats
  - resolveItem(itemId, notes) - Mark item resolved with admin notes

**Scheduled Batch Processing:**
- Cron: Daily at 2 AM Asia/Saigon (configurable via reconciliation.cron property)
- EnableScheduling: Auto-triggered via @Scheduled on ReconciliationScheduler bean
- ConditionalOnProperty: reconciliation.auto-run=true enables auto-trigger
- Max date range: 31 days (configurable via reconciliation.max-date-range-days)

**Repositories:**
- ReconciliationRunRepository - paginated, ordered by createdAt DESC
- ReconciliationItemRepository - filtered by runId + discrepancyType, count queries
- PaymentRepository.findByDateRangeWithStripeId(startDate, endDate) - NEW method for batch reader

**Stripe Integration:**
- Environment: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET
- PaymentIntent creation with idempotency key (pay-{bookingId})
- Webhook: POST /api/payments/webhook (signature verification, event dedup)
- Batch reconciliation: PaymentIntent.retrieve() per payment, StripeClient.listPaymentIntents() for missing locals
- Publishes PaymentCompletedEvent/PaymentFailedEvent after DB commit (TransactionalEventListener)

**Kafka Events Published:** PaymentCompletedEvent, PaymentFailedEvent → topic: payment-events

**Audit Integration:**
- PaymentController: @Auditable on createPaymentIntent method
- Audit actions: CREATE_PAYMENT

**Database (paymentdb):**
- payments table: id, bookingRef, amount, currency, status, stripePaymentIntentId, createdAt
- reconciliation_runs table: id, startDate, endDate, status, matchedCount, mismatchedCount, missingLocalCount, missingStripeCount, totalChecked, createdAt
- reconciliation_items table: id, runId FK, stripePaymentIntentId, localPaymentId, discrepancyType ENUM, stripeAmount, localAmount, stripeStatus, localStatus, resolved, notes, createdAt
  - Indexes: (run_id), (discrepancy_type) for filtering

**Configuration (application.yml):**
- spring.batch.jdbc.initialize-schema=always (auto-create Spring Batch metadata tables)
- spring.batch.job.enabled=false (disable auto-run on startup)
- reconciliation.cron: 0 2 * * * (2 AM daily, Asia/Saigon timezone)
- reconciliation.auto-run: true (enable scheduled reconciliation)
- reconciliation.max-date-range-days: 31 (max 31 days per reconciliation)
- stripe.api.key: ${STRIPE_API_KEY:} (env var, no hardcoded test key)

## notification-service (Port 8085)

**Key Features:** Kafka consumer (notification-events + in-app SSE events), SMTP email delivery, Server-Sent Events (SSE) streaming, PostgreSQL persistence, REST API for CRUD + mark-as-read, JWT auth via query param for SSE

**Kafka Consumers:**
- Topic: notification-events (NotificationRequestedEvent) → EmailSenderService → SMTP delivery
- Topic: notification.in_app (InAppNotificationEvent) → SseEmitterService → broadcast to all connected SSE clients
- Consumer group: `notification-service-{instance-id}` (unique per instance for broadcast to all instances)

**JPA Entity:**
- Notification (id, userId, title, message, notificationType ENUM [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM], isRead, createdAt)

**REST Controllers:**
- `NotificationSseController` - GET /api/notifications/stream (SSE endpoint, JWT via query param ?token=JWT)
- `NotificationRestController`:
  - GET /api/notifications (paginated, ordered createdAt DESC)
  - PATCH /api/notifications/{id}/read (mark single as read)
  - PATCH /api/notifications/read-all (bulk mark all as read)
  - GET /api/notifications/unread-count (returns count for badge)
  - POST /api/notifications/broadcast (admin-only test broadcast)

**Services & Components:**
- `SseEmitterRegistryService` - ConcurrentHashMap emitter management, heartbeat every 30s
- `InAppNotificationServiceImpl` - Persist & emit notifications, mark-as-read, broadcast
- `InAppNotificationEventListener` (Kafka) - Consumes InAppNotificationEvent, broadcasts via SSE
- `NotificationPublisherService` - Called by booking-service to publish InAppNotificationEvent
- `EmailSenderService` - SMTP (Gmail, authenticated via MAIL_USERNAME/MAIL_PASSWORD)
- `NotificationDeduplicationService` - Redis: key pattern notification:processed:{eventId}, TTL 24h

**JPA Repository:**
- `NotificationRepository`:
  - findByUserIdOrderByCreatedAtDesc (paginated)
  - countByUserIdAndIsReadFalse (unread count)
  - markAllAsReadByUserId (bulk update)
  - findDistinctUserIds (for broadcast, avoids OOM)

**SSE Configuration:**
- Heartbeat: Comment-only events every 30s (keep-alive, no disconnect on timeout)
- Emitter timeout: 30 minutes (configurable)
- Unique consumer group per instance ensures all SSE clients receive broadcasts
- Graceful reconnect: Client implements exponential backoff (1s→30s max, 5 attempts)

**Database (notificationdb):**
- notifications table: id (PK), userId (FK), title, message, notificationType, isRead, createdAt
- Indexed: (userId, createdAt DESC) for efficient pagination

**Configuration:**
- spring.mail.host: smtp.gmail.com
- spring.mail.port: 587
- spring.mail.username: ${MAIL_USERNAME}
- spring.mail.password: ${MAIL_PASSWORD}

**Error Handling:**
- Kafka: 3 retries, exponential backoff (1s→2s→4s capped 10s), DLT for failures
- Redis fail-open: NotificationDeduplicationService is optional; proceeds if Redis unavailable
- SSE race condition fix: Atomic computeIfPresent in removeEmitter to prevent concurrent issues

## kafka-events (Shared Library)

**Purpose:** Shared event domain models for all services (business events + audit events)

**Enums:**
- `NotificationType` [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM]
- `AuditAction` [LOGIN, LOGOUT, REGISTER, CHANGE_PASSWORD, CREATE_MOVIE, UPDATE_MOVIE, DELETE_MOVIE, CREATE_SHOWTIME, UPDATE_SHOWTIME, RESERVE_BOOKING, CANCEL_BOOKING, CREATE_PAYMENT]

**Records (Java Records):**
- `PaymentCompletedEvent` (bookingId, amount, status)
- `PaymentFailedEvent` (bookingId, reason)
- `BookingCreatedEvent` (bookingId, userId, showtimeId)
- `MovieCreatedEvent` (movieId, title, genre)
- `ShowtimeCreatedEvent` (showtimeId, movieId, theaterId, startTime)
- `NotificationRequestedEvent` (recipientEmail, subject, body, eventType)
- `InAppNotificationEvent` (userId, title, message, notificationType: NotificationType)
- `AuditEvent` (userId, userIp, action: AuditAction, entityType, entityId, beforeState, afterState, sourceService, traceId, requestPath)

**EventEnvelope Wrapper:**
```java
EventEnvelope<T> {
  eventId: String (UUID)
  eventType: String (class name)
  source: String (service name)
  correlationId: String (trace ID)
  timestamp: Instant
  payload: T
}
```

**Audit Support Package (com.namnd.cinema.audit/):**
- `@Auditable` annotation: Method-level marker for audit logging
- `AuditAspect` (AOP): Intercepts @Auditable methods, captures userId/action/entityType
- `AuditEntityListener` (JPA): Post-persist/update/remove lifecycle hooks for entity audit
- `AuditEventPublisher`: Publishes AuditEvent to Kafka (calls KafkaTemplate.send)
- `AuditAfterCommitListener`: @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true) pattern
- `AuditAutoConfiguration`: @ConditionalOnClass(KafkaTemplate) enables audit beans
- `AuditBeanProvider`: Utility for retrieving userId from principal (email-based)
- `AuditHttpContext`: Thread-local storage for request context (userIp, requestPath)

**Optional Dependencies (kafka-events pom.xml):**
- spring-boot-starter-aop (for @Auditable aspect)
- spring-kafka (for KafkaTemplate)
- spring-boot-starter-data-jpa (for entity lifecycle)
- spring-boot-starter-security (for principal extraction)
- micrometer-tracing-core (for traceId access)

**Kafka Topics Configuration:**
- movie-events (3 partitions, replication 3, 7-day retention)
- payment-events (3 partitions, replication 3, 7-day retention)
- notification-events (3 partitions, replication 3, 7-day retention)
- notification.in_app (3 partitions, replication 3, 7-day retention, broadcast to all SSE instances)
- audit-events (3 partitions, replication 3, 90-day retention)
- audit-events.DLT (Dead Letter Topic for failed audit messages)

**Constants:**
- `AUDIT_EVENTS` = "audit-events" (topic name)

## jwt-auth-autoconfigure (Shared Library)

**Purpose:** Reusable JWT validator for downstream services (booking, payment, etc.)

**Components:**
- JwtAutoConfiguration - Spring Boot auto-config (conditional beans)
- JwtAuthProperties - @ConfigurationProperties(prefix="jwt.auth")
- JwtTokenValidator - Validates HS512 signature, expiration
- JwtAuthenticationFilter - Sets SecurityContext from JWT claims (roles, userId)
- JwtAuthenticatedUser - Principal model

**Configuration Properties:**
```yaml
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}
    publicPaths: ["/actuator/health"]
    enabled: true
```

**Activation:** @ConditionalOnProperty(name="jwt.auth.enabled", havingValue="true")

**Usage in Downstream Services:**
- Add jwt-auth-autoconfigure as dependency
- Configure jwt.auth.secret in application.yml (via JWT_SECRET environment variable)
- Annotate controller methods with @PreAuthorize("hasRole('ROLE_USER')")
- JwtAuthenticationFilter auto-wired via auto-config

## cinema-frontend (Angular 18)

**Port:** 4200 (dev) → 80 (production via Nginx)

**Stack:** TypeScript 5.5, Material 18, Stripe.js 8.9, RxJS

**Features:**
- Authentication flow (login, register, token refresh)
- Movie browser, showtime selection
- Seat grid interactive booking
- Stripe checkout integration
- User profile, booking history
- Admin CRUD dashboard (ROLE_ADMIN)
- **Movie ratings & comments:** Star rating display, comment list, reaction buttons
- **Models:** movie-rating.model.ts, movie-comment.model.ts (includes Page<T> interface for pagination)
- **Services:** MovieRatingService, MovieCommentService, TheaterService, ShowtimeAdminService
- **Components:** StarRatingComponent, CommentListComponent, CommentItemComponent
- **Auth interceptor:** Fixed to always attach token when available; PUBLIC_URLS only gates 401 refresh
- **MovieDetailComponent:** Integrated with star rating + comment list

**Admin Dashboard (New):**
- **AdminNavComponent:** Tab-based navigation (Movies, Theaters, Showtimes, Payments, Reconciliation)
- **MovieManagementComponent:** List movies in MatTable with edit/delete actions
- **MovieFormDialogComponent:** Modal form for create/edit movie
- **TheaterManagementComponent:** List theaters in MatTable with edit/delete actions
- **TheaterFormDialogComponent:** Modal form for create/edit theater
- **ShowtimeManagementComponent:** List showtimes in MatTable with edit/delete actions
- **ShowtimeFormDialogComponent:** Modal form for create/edit showtime
- **PaymentManagementComponent:** List all payments in MatTable (admin-only view)

**Stripe Reconciliation Dashboard (NEW - March 31, 2026):**
- **reconciliation-dashboard.component.ts:** Main component for reconciliation tab
  - Displays: Reconciliation run history in MatTable (startDate, endDate, status, matched/mismatched/missing counts)
  - Actions: Manual trigger button (opens date range picker dialog)
  - Filters: Click on row to view details (discrepancy breakdown)
  - CSV export: Download reconciliation items as CSV (discrepancyType, amounts, statuses, resolved)
- **reconciliation-detail.component.ts:** Detail view for single run
  - Shows ReconciliationItem table: stripePaymentIntentId, localPaymentId, discrepancyType, amounts, statuses, resolved flag
  - Optional filter: dropdown by discrepancyType (MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE)
  - Resolve action: Click "Resolve" button to mark item resolved with admin notes
- **Reconciliation API Service:**
  - triggerReconciliation(startDate, endDate) - POST /api/payments/reconciliation/trigger
  - getRuns(page) - GET /api/payments/reconciliation/runs
  - getRunDetails(runId) - GET /api/payments/reconciliation/runs/{runId}
  - getRunItems(runId, discrepancyType?, page?) - GET /api/payments/reconciliation/runs/{runId}/items
  - getSummary() - GET /api/payments/reconciliation/summary
  - resolveItem(itemId, notes) - PUT /api/payments/reconciliation/items/{itemId}/resolve

**Real-Time Notifications (New - March 14, 2026):**
- **notification.model.ts:** Interfaces (Notification, NotificationPage, UnreadCountResponse)
- **notification-sse.service.ts:** EventSource with exponential backoff (1s→30s max, 5 attempts), JWT auth via query param
- **notification-api.service.ts:** REST calls (GET list, PATCH mark-as-read, GET unread-count, POST broadcast)
- **notification-bell.component.ts:** Toolbar badge with matBadge, snackbar alerts, auto-increment on new notifications
- **notification-list.component.ts:** Mat-card list with dark theme, colored borders per notificationType, pagination
- **notifications.routes.ts:** Lazy route config
- **Toolbar integration:** notification-bell added to toolbar.component.ts for persistent access
- **App integration:** /notifications route added to app.routes.ts

**Seat Grid Display & Booking UI (NEW - March 22, 2026 - FR-3.1 COMPLETE):**
- **Services:**
  - seat-websocket.service.ts: STOMP/SockJS WebSocket client (connects to /ws on booking-service via nginx proxy, listens for LOCK/RESERVE/CANCEL events on /topic/showtime/{id}/seats)
  - seat-suggestion.service.ts: Client-side O(n*m) algorithm (proximity distance, row preference, type uniformity)
- **Components:**
  - seat-suggestion-panel.component.ts: UI panel with recommended seat groups + accept/dismiss actions
- **Utilities:**
  - seat-grid-layout.utils.ts: Screen curves with glow, aisle gaps (cols 6,13), VIP dividers, responsive 36/40/44px
  - seat-grid-keyboard-navigation.utils.ts: Arrow keys (up/down/left/right), roving tabindex, focus-visible
  - seat-selection-timer.utils.ts: Booking countdown timer with expiration logic
- **Modified Components:**
  - seat-grid.component.ts: Color-coded seats (STANDARD=green, PREMIUM=blue, VIP=amber), row A-Z labels, legend (type+price), Instant→String serialization fix for seat data
  - seat-selection.component.ts: WebSocket integration, suggestion panel UI, timer countdown, global polyfill for sockjs-client
- **Nginx Configuration:** nginx.conf configured to proxy /ws/ directly to booking-service:8083, includes WebSocket upgrade headers (Connection: Upgrade, Upgrade: websocket)
- **Dependencies:** @stomp/stompjs, sockjs-client (added to package.json with global import)
- **Accessibility:** WCAG 2.1 AA (ARIA role=grid, keyboard nav, color+icons, MatTooltip, aria-live)
- **API Data Mapping:** Fixed rowLabel/seatNumber (API) → rowNumber/columnNumber/price (frontend) in seat grid display

**Date/Time Utilities (NEW - April 1, 2026):**
- **date-format.util.ts:** Timezone-safe date/time formatting
  - `formatDate(date: Date, format?: string)` - Formats date in local timezone (YYYY-MM-DD HH:mm:ss)
  - `combineDatetime(dateStr: string, timeStr: string)` - Combines date + time strings into Date object (handles timezone conversion)
  - `parseTime(timeStr: string)` - Parses HH:mm time string into minutes for time picker logic
- **Integration:** Used in showtime-form-dialog and movie-form-dialog for timezone-safe date/time input
- **Problem Solved:** Prevents timezone offset issues in browser when submitting datetime forms

**Lazy-Loaded Routes:**
- /auth (login, register, setup-password, password reset, OAuth2 callback)
- /movies (browse, details)
- /booking (seat selection with grid, real-time updates, suggestions)
- /payment (Stripe checkout)
- /profile (user info, bookings, change password)
- /admin (admin dashboard with tabs, reconciliation tab NEW)
- /notifications (notification history, mark-as-read)

**Password Setup on Activation (Frontend - NEW):**
- Route: /auth/setup-password?token=uuid (public, pre-activation)
- Component: SetupPasswordComponent with reactive form
- Fields: password, confirmPassword
- Validation: Passwords match check, password strength validation
- Token extraction: Query param ?token=uuid
- Integration: Activation email links to this route
- Error handling: Display validation errors, token expiration, server response messages
- Success: Redirect to login after successful password setup

**Password Change Feature (Frontend):**
- Route: /profile/change-password (protected, requires authentication)
- Component: ChangePasswordComponent with reactive form
- Fields: currentPassword, newPassword, confirmPassword
- Validation: Passwords match check, current password verification
- Integration: "Change Password" button on profile page (ProfileComponent)
- Error handling: Display validation errors and server response messages
- Guard: Block access for OAuth-only users (password=NULL)

**OAuth2 Integration (Frontend):**
- Route: /oauth2-callback (standalone component, handles OAuth2 callback)
- Component: OAuth2CallbackComponent
  - Extracts token + refreshToken from URL query params
  - Clears tokens from browser history immediately (security: prevent leakage)
  - Calls AuthService.handleOAuth2Callback() to store tokens
  - Redirects to /movies on success, /auth/login on error (2-second delay)
  - Displays "Signing in..." spinner during processing
- AuthService update: handleOAuth2Callback() stores tokens identically to traditional login
- Login Component: "Sign in with Google" button redirects to /oauth2/authorization/google
- Error handling: Display error message if missing email or invalid callback

**API Proxy:** Configured to route /api/* directly to each backend service (via nginx.conf in docker-compose; K8s Ingress in Kubernetes)

**Nginx Config (Prod):** SPA fallback for client-side routing

## Observability Stack

**Prometheus (Port 9090)**
- Scrape interval: 15 seconds
- Retention: 7 days
- Scrape targets (all 8 services): /actuator/prometheus endpoint
- Metrics: JVM (memory, GC, threads), HTTP (req rate, latency, errors), custom business counters

**Grafana (Port 3000)**
- Auto-provisioned datasources: Prometheus, Loki, Tempo (with `tracesToLogsV2` + `tracesToMetrics`)
- 2 prebuilt dashboards:
  - JVM Micrometer (memory, GC, threads, CPU usage)
  - Spring Boot HTTP Overview (request rate, error rate, latency, database pool, business counters)
- Custom business metrics: auth.login.success/failure, booking.created/confirmed/cancelled, payment.initiated/completed/failed

**Loki (Port 3100)**
- 7-day retention
- Log labels: job, instance, application (service name)
- Log discovery via Grafana
- Auto-includes traceId and spanId from Micrometer Tracing MDC

**Tempo (Port 3200) + OTel Collector (Ports 4317/4318)**
- Distributed tracing pipeline: Spring Boot → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP → OTel Collector (contrib) → Grafana Tempo
- Centralized trace collection from all 6 business services
- Docker images pinned: `grafana/tempo:2.6.0`, `otel/opentelemetry-collector-contrib:0.115.1`
- App config: `management.otlp.tracing.endpoint: http://otel-collector:4318/v1/traces`
- Resource attributes: `service.name`, `deployment.environment`
- Sampling: 100% by default (via TRACING_SAMPLING_PROBABILITY env var)
- Tempo retention: 24h (compactor)
- Auto-traces: service-to-service (HTTP/Feign), Kafka producers/consumers (W3C `traceparent`), database operations
- Zero code changes: enabled by Spring Boot 3.4.3 auto-configuration

**Actuator Metrics (/actuator/prometheus):**
- Exposed on all services
- Security: Internal Docker network only (not via API Gateway)

## Configuration Files

**Root pom.xml:**
- Packaging: pom
- 11 modules
- Spring Cloud BOM: 2024.0.1
- JJWT: 0.12.6 (api, impl, jackson)

**Key Dependencies Versions:**
- Spring Boot: 3.4.3 (includes Micrometer Tracing, Spring Cloud Sleuth)
- Spring Cloud: 2024.0.1
- Spring Kafka: (via Boot)
- JJWT: 0.12.6
- Stripe Java SDK: latest
- SpringDoc OpenAPI: 2.8.4
- PostgreSQL Driver: 42.x
- Lombok: 1.18.x (BOM-managed)
- Micrometer Tracing: (auto-included via Spring Boot 3.4.3)
- OpenTelemetry SDK: (via Micrometer bridge)
- OpenTelemetry OTLP Exporter: 1.43.x (via Spring Boot auto-config)

## Build & Deployment

```bash
# Build all modules
mvn clean install

# Build specific module
mvn -pl auth-service clean package

# Docker Compose (all services + infrastructure)
docker-compose up --build

# Individual service Docker build
docker build -t auth-service ./auth-service
docker build -t movie-service ./movie-service
# ... etc
```

## Metrics & Monitoring

**Business Counters:**
- auth.login.success (Counter)
- auth.login.failure (Counter)
- auth.register (Counter)
- auth.logout (Counter)
- booking.created (Counter)
- booking.confirmed (Counter)
- booking.cancelled (Counter)
- payment.initiated (Counter)
- payment.completed (Counter)
- payment.failed (Counter)

**HTTP Metrics (Micrometer):**
- http.server.requests (timer)
- http.client.requests (timer, Feign)
- jdbc connection pool stats
- jvm.memory, jvm.gc, jvm.threads

## Cross-Service Communication Patterns

| Pattern | Example | Technology |
|---------|---------|-----------|
| Service-to-Service (Sync) | booking-service → movie-service (seat details) | Feign HTTP |
| Event-Driven (Async) | payment-service → booking-service (PaymentCompletedEvent) | Kafka |
| Configuration Sharing | all services use environment variables (JWT secret) | K8s ConfigMap / docker-compose env |
| Token Validation | downstream services validate JWT | jwt-auth-autoconfigure |

## Data Isolation

**Per-Service PostgreSQL Databases:**
- auth-service: testdb (7 tables: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens)
- movie-service: moviedb (7 tables: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 tables: bookings, booking_seats)
- payment-service: paymentdb (1 table: payments)
- notification-service: notificationdb (1 table: notifications with userId, eventId, notificationType, status, isRead fields)

## audit-service (Port 8086)

**Key Features:** Centralized audit logging via Kafka consumer, admin API with filtering, PostgreSQL persistence, idempotent event processing, 90-day retention

**Controllers:**
- AdminAuditLogController - GET /api/audit/logs (paginated, filtered), GET /api/audit/logs/{id}
  - Filters: userId, action [ENUM], entityType, startDate, endDate
  - Pagination: 20/page (max 100), sorted createdAt DESC
  - Requires @PreAuthorize("hasRole('ADMIN')")

**Models:**
- AuditLog JPA entity: id PK, eventId UNIQUE, userId, userIp, action [ENUM], entityType, entityId, beforeState, afterState, sourceService, traceId, requestPath, createdAt
  - afterState: JSON of entity state post-change
  - beforeState: NULL in v1 (reserved for Envers v2)
  - LOGIN action skips afterState to prevent JWT token leakage

**Services:**
- AuditEventConsumer (Kafka listener): Converts EventEnvelope<AuditEvent> to AuditLog via ObjectMapper.convertValue()
- AuditLogRepository: Specification pattern for dynamic filtering (userId, action, entityType, dateRange)

**Kafka Consumer:**
- Topic: audit-events (3 partitions, 90-day retention)
- Consumer group: audit-service
- Message format: EventEnvelope<AuditEvent>
- Dedup: eventId UNIQUE constraint prevents duplicates on Kafka retries
- Error handling: 3 retries (1s→2s→4s backoff), DLT (audit-events.DLT) for failures

**Database (auditdb):**
- audit_logs table (id PK, eventId UNIQUE, userId FK, userIp, action ENUM, entityType, entityId, beforeState TEXT, afterState TEXT, sourceService VARCHAR, traceId VARCHAR, requestPath TEXT, createdAt TIMESTAMP)
- Indexes: (user_id), (action), (entity_type), (created_at) for efficient filtering

**Integration Points:**
- All @Auditable-annotated methods (auth-service login/register/logout/change-password, movie-service CRUD, booking-service reserve/cancel, payment-service createPaymentIntent)
- After-commit pattern: Spring @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true) publishes to Kafka
- userId extraction: Reflection-based email() from JwtAuthenticatedUser principal

**Configuration:**
- application.yml: kafka.brokers, audit.retention-days=90
- Dockerfile: Maven build, Spring Boot jar, port 8086
- docker-compose.yml: Spring profile, Kafka topic auto-creation, auditdb creation (init-databases.sql)
