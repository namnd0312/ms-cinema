# Codebase Summary

**Project:** ms-cinema
**Generated:** March 2026
**Architecture:** 11-module Maven microservices (Spring Cloud)
**Java Version:** 21 LTS
**Spring Boot:** 3.4.3
**Spring Cloud:** 2024.0.1

## 11 Maven Modules Overview

```
ms-cinema/ (root pom: packaging=pom)
├── Infrastructure (3 modules)
│   ├── eureka-server (:8761) - Service registry
│   ├── config-server (:8888) - Centralized config
│   └── api-gateway (:8080) - Single entry point
├── Business Services (5 modules)
│   ├── auth-service (:8081) - JWT auth, user management
│   ├── movie-service (:8082) - Movies, theaters, showtimes
│   ├── booking-service (:8083) - Seat reservation, Feign → movie-service
│   ├── payment-service (:8084) - Stripe payments, webhooks
│   └── notification-service (:8085) - Kafka consumer, email (SMTP)
├── Shared Libraries (2 modules)
│   ├── kafka-events - Event domain models
│   └── jwt-auth-spring-boot-starter - Reusable JWT validator
├── Frontend (1 module)
│   └── cinema-frontend (:4200→80) - Angular 18
└── Infrastructure Config
    ├── docker-compose.yml - PostgreSQL, Kafka, Redis, Prometheus, Grafana, Loki
    ├── monitoring/ - Prometheus.yml, Grafana dashboards, Loki config
    └── docs/ - Documentation files
```

## auth-service (Port 8081)

**Key Features:** JWT auth, email activation, account lockout, token rotation, Kafka event publishing

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
│   ├── AuthController.java (~230 lines) - login, register, activate, forgot-password, reset-password, refresh-token, logout
│   ├── TokenValidationController.java - validate-token (for microservices), /api/users/me
│   └── TestController.java - Health check
├── model/ - User, Role, RefreshToken, PasswordResetToken, ActivationToken
├── dto/ - LoginRequestDto, JwtResponseDto, RegisterDto, ForgotPasswordDto, ResetPasswordDto, RefreshTokenRequestDto, TokenRefreshResponseDto, ValidateTokenRequestDto/ResponseDto, UserInfoResponseDto
├── service/ - JwtService, UserService, RoleService, RefreshTokenService, PasswordResetService, EmailService, ActivationService, BlacklistedTokenService, AccountLockService, RedisService
├── repository/ - UserRepository, RoleRepository, RefreshTokenRepository, PasswordResetTokenRepository, ActivationTokenRepository
└── resources/
    ├── application.yml - port 8081, config-server import, eureka registration
    └── schema.sql - Users, roles, tokens tables (7 tables)
```

**Key Services:**
- **JwtService** (147 lines): HS512 signing, validates signature+expiration+blacklist, embeds roles+userId claims
- **EmailServiceImpl** (35 lines): Publishes NotificationRequestedEvent to Kafka topic "notification-events" (no direct SMTP)
- **ActivationServiceImpl** (~90 lines): 24-hour email activation tokens (UUID-based)
- **PasswordResetServiceImpl** (~80 lines): 24-hour password reset tokens
- **BlacklistedTokenServiceImpl** (~50 lines): Redis JTI blacklist with auto-TTL (fail-closed)
- **AccountLockServiceImpl** (~60 lines): 5-attempt lockout, auto-unlock after 15 minutes

**API Endpoints:** See README.md API Reference section

**Database Schema:**
- users (id, username, email UNIQUE, password, fullName, active, failedAttempts, lockTime)
- roles (id, name)
- user_roles (user_id FK, role_id FK)
- refresh_tokens (id, token UNIQUE, expiryDate, user_id FK)
- password_reset_tokens (id, token UNIQUE, expiryDate, user_id FK)
- activation_tokens (id, token UNIQUE, expiryDate, user_id FK, used)
- blacklisted_tokens (id, jti UNIQUE, expiry_date)

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

**Repositories (Custom Queries):**
- MovieRatingRepository - findAverageRatingByMovieId(), countByMovieId()
- MovieCommentRepository - findByMovieIdAndStatusActive (custom @Query)
- CommentReactionRepository - countLikesByCommentId(), countDislikesByCommentId()

**Kafka Events Published:** MovieCreatedEvent, ShowtimeCreatedEvent → topic: movie-events

**Security:** /api/comments/** added to permitAll in SecurityConfig for GET (public comments)

## booking-service (Port 8083)

**Key Features:** Seat reservation with Redis locking (5-min TTL), booking lifecycle (PENDING→CONFIRMED/CANCELLED/EXPIRED), Feign client to movie-service, consumes PaymentCompletedEvent/PaymentFailedEvent, BookingExpiryScheduler (60s check), transactional event listener

**Controllers:**
- BookingController - reserve, getBooking, getUserBookings, confirmBooking, cancelBooking, getBookedSeats

**Models:**
- Booking (id, showtimeRef, userRef, status ENUM, createdAt, expiresAt)
- BookingSeat (id, bookingRef, seatRef, status ENUM)

**Services:**
- BookingService - Manages lifecycle, Redis locking, event handling
- SeatReservationService - Acquires locks (key pattern: seat:lock:{showtimeId}:{seatId})

**Feign Clients:**
- MovieServiceClient - Fetch showtime & seat details

**Kafka Event Handlers:**
- Consumes PaymentCompletedEvent → transitions booking CONFIRMED
- Consumes PaymentFailedEvent → transitions booking CANCELLED, releases locks
- Publishes BookingCreatedEvent → notification-events

**Schedulers:**
- BookingExpiryScheduler (60s): Finds PENDING bookings past expiresAt, transitions EXPIRED, releases Redis locks

**Redis Lock Key Pattern:** `seat:lock:{showtimeId}:{seatId}`
**Lock TTL:** 5 minutes (configurable)

## payment-service (Port 8084)

**Key Features:** Stripe integration with idempotency key (pay-{bookingId}), webhook processing with signature verification, refund (ADMIN-only), publishes PaymentCompletedEvent/PaymentFailedEvent, TransactionalEventListener for after-commit Kafka publish

**Controllers:**
- PaymentController - create-intent, confirm, getPayment, getUserPayments, refund (ADMIN), webhook (POST)

**Models:**
- Payment (id, bookingRef, amount, currency, status ENUM, stripePaymentIntentId, createdAt)

**Services:**
- PaymentService - Stripe PaymentIntent creation (idempotency key: pay-{bookingId}), webhook handling
- StripeWebhookService - Signature verification (stripeSig header), stripeEventId dedup (prevent replay)

**Stripe Integration:**
- Environment: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET
- PaymentIntent creation with idempotency key (pay-{bookingId})
- Webhook: POST /api/payments/webhook (signature verification, event dedup)
- Publishes PaymentCompletedEvent/PaymentFailedEvent after DB commit (TransactionalEventListener)

**Kafka Events Published:** PaymentCompletedEvent, PaymentFailedEvent → topic: payment-events

## notification-service (Port 8085)

**Key Features:** Kafka consumer (notification-events topic), SMTP email delivery via Spring Mail, Redis deduplication (24h TTL), fail-open on Redis

**Kafka Consumer:**
- Listens to notification-events topic
- Consumes NotificationRequestedEvent

**Models:**
- Notification (id, eventId UNIQUE, recipientEmail, subject, body, status ENUM, createdAt)

**Services:**
- EmailSenderService - SMTP delivery via Spring Mail (jakarta.mail)
- NotificationDeduplicationService - Redis key pattern: notification:processed:{eventId}, TTL 24h
- KafkaNotificationListener - Event handler with error handling (retry policy)

**Configuration:**
- spring.mail.host: smtp.gmail.com
- spring.mail.port: 587
- MAIL_USERNAME, MAIL_PASSWORD (Gmail app-specific password)

**Error Handling:**
- Kafka: 3 retries, exponential backoff (1s→2s→4s, capped 10s), DLT for failures
- Redis fail-open: sends email even if Redis outage (may duplicate)

## kafka-events (Shared Library)

**Purpose:** Shared event domain models for all services

**Event Classes:**
- PaymentCompletedEvent (bookingId, amount, status)
- PaymentFailedEvent (bookingId, reason)
- BookingCreatedEvent (bookingId, userId, showtimeId)
- MovieCreatedEvent (movieId, title, genre)
- ShowtimeCreatedEvent (showtimeId, movieId, theaterId, startTime)
- NotificationRequestedEvent (recipientEmail, subject, body, eventType)

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

**Kafka Topics Configuration:**
- movie-events (partition 1, replication 3)
- payment-events (partition 1, replication 3)
- notification-events (partition 1, replication 3)

**Error Handling (Docker Compose Kafka Config):**
```
num.network.threads: 8
offsets.topic.replication.factor: 3
transaction.state.log.replication.factor: 3
spring.kafka.producer.retries: 3
spring.kafka.producer.properties.linger.ms: 10
spring.kafka.consumer.max.poll.records: 100
spring.kafka.listener.error-handler: org.springframework.kafka.listener.DefaultErrorHandler (3 retries, exponential backoff 1s-4s-10s)
```

## jwt-auth-spring-boot-starter (Shared Library)

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
- Add jwt-auth-spring-boot-starter as dependency
- Configure jwt.auth.secret in application.yml (from config-server)
- Annotate controller methods with @PreAuthorize("hasRole('ROLE_USER')")
- JwtAuthenticationFilter auto-wired via auto-config

## api-gateway (Port 8080)

**Purpose:** Single entry point, request routing, OpenAPI aggregation

**Routes:**
```
/api/auth/** → lb://auth-service
/api/users/** → lb://auth-service
/api/movies/** → lb://movie-service
/api/showtimes/** → lb://movie-service
/api/theaters/** → lb://movie-service
/api/bookings/** → lb://booking-service
/api/payments/** → lb://payment-service
/actuator/** → REJECT (internal only)
```

**Features:**
- Spring Cloud Gateway MVC (servlet-based)
- ServiceInstanceListSupplier for Eureka load balancing
- HttpLoggingFilter - Logs request/response with X-Correlation-ID header
- OpenAPI endpoint aggregation: /v3/api-docs (combines all service OpenAPIs)
- Swagger UI: /swagger-ui.html

**Configuration:**
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service-routes
          predicates:
            - Path=/api/auth/**,/api/users/**
          uri: lb://auth-service
        # ... more routes
```

## eureka-server (Port 8761)

**Purpose:** Service discovery registry

**Configuration:**
- eureka.server.enable-self-preservation: false
- eureka.client.register-with-eureka: false
- eureka.client.fetch-registry: false
- Heartbeat interval: 10s (client → server)
- Lease timeout: 30s

## config-server (Port 8888)

**Purpose:** Centralized configuration management

**Configuration:**
- Loads from classpath:/config-repo/ (native profile)
- Alternative: Git repository support

**Shared Config (config-repo/application.yml):**
```yaml
namnd.app.jwtSecret: ${JWT_SECRET}
jwt.auth.secret: ${JWT_SECRET}
```

**Per-Service Configs:**
- auth-service/application.yml (auth-specific)
- movie-service/application.yml
- booking-service/application.yml
- payment-service/application.yml
- notification-service/application.yml

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
- **AdminNavComponent:** Tab-based navigation (Movies, Theaters, Showtimes, Payments)
- **MovieManagementComponent:** List movies in MatTable with edit/delete actions
- **MovieFormDialogComponent:** Modal form for create/edit movie
- **TheaterManagementComponent:** List theaters in MatTable with edit/delete actions
- **TheaterFormDialogComponent:** Modal form for create/edit theater
- **ShowtimeManagementComponent:** List showtimes in MatTable with edit/delete actions
- **ShowtimeFormDialogComponent:** Modal form for create/edit showtime
- **PaymentManagementComponent:** List all payments in MatTable (admin-only view)

**Lazy-Loaded Routes:**
- /auth (login, register, password reset)
- /movies (browse, details)
- /booking (seat selection, confirmation)
- /payment (Stripe checkout)
- /profile (user info, bookings)
- /admin (admin dashboard with tabs)

**API Proxy:** Configured to route /api/* to http://api-gateway:8080

**Nginx Config (Prod):** SPA fallback for client-side routing

## Observability Stack

**Prometheus (Port 9090)**
- Scrape interval: 15 seconds
- Retention: 7 days
- Scrape targets (all 8 services): /actuator/prometheus endpoint
- Metrics: JVM (memory, GC, threads), HTTP (req rate, latency, errors), custom business counters

**Grafana (Port 3000)**
- Auto-provisioned datasources: Prometheus, Loki
- 2 prebuilt dashboards:
  - JVM Micrometer (memory, GC, threads, CPU usage)
  - Spring Boot HTTP Overview (request rate, error rate, latency, database pool, business counters)
- Custom business metrics: auth.login.success/failure, booking.created/confirmed/cancelled, payment.initiated/completed/failed

**Loki (Port 3100)**
- 7-day retention
- Log labels: job, instance, application (service name)
- Log discovery via Grafana

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
- Spring Boot: 3.4.3
- Spring Cloud: 2024.0.1
- Spring Kafka: (via Boot)
- JJWT: 0.12.6
- Stripe Java SDK: latest
- SpringDoc OpenAPI: 2.8.4
- PostgreSQL Driver: 42.x
- Lombok: 1.18.x (BOM-managed)

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
| Configuration Sharing | all services ← config-server (JWT secret) | Spring Cloud Config |
| Service Discovery | all services ← eureka-server (dynamic routing) | Eureka |
| Token Validation | downstream services validate JWT | jwt-auth-spring-boot-starter |

## Data Isolation

**Per-Service PostgreSQL Databases:**
- auth-service: testdb (7 tables: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens)
- movie-service: moviedb (7 tables: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 tables: bookings, booking_seats)
- payment-service: paymentdb (1 table: payments)

**Shared Resources:**
- PostgreSQL cluster (all databases on same instance)
- Redis (all services share token blacklist, locks, notifications)
- Kafka (all topics, configurable partitions/replicas)
