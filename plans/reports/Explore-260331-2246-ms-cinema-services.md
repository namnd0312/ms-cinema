# MS-Cinema Backend Services Exploration Report

## Executive Summary
The ms-cinema system is a Spring Boot microservices architecture with 9 modules covering authentication, movie catalog, booking, payments, notifications, auditing, and API gateway. The payment-service includes a newly added reconciliation module using Spring Batch for Stripe payment validation.

---

## Services Overview

| Service | Location | Files | Package | Key Features |
|---------|----------|-------|---------|--------------|
| auth-service | `/auth-service/src/main/java/com/namnd/cinema/` | 66 | `com.namnd.cinema` | User auth, JWT, OAuth2, token refresh, password reset |
| movie-service | `/movie-service/src/main/java/com/namnd/movieservice/` | 59 | `com.namnd.movieservice` | Movie catalog, ratings, comments, reactions |
| booking-service | `/booking-service/src/main/java/com/namnd/bookingservice/` | 35 | `com.namnd.bookingservice` | Seat reservations, locking, WebSocket updates |
| **payment-service** | `/payment-service/src/main/java/com/namnd/paymentservice/` | **46** | `com.namnd.paymentservice` | **Stripe integration, reconciliation (NEW)** |
| notification-service | `/notification-service/src/main/java/com/namnd/notification/` | 16 | `com.namnd.notification` | Email, in-app notifications, SSE streams |
| audit-service | `/audit-service/src/main/java/com/namnd/auditservice/` | 11 | `com.namnd.auditservice` | Audit logging, event consumption |
| api-gateway | `/api-gateway/src/main/java/com/namnd/apigateway/` | 5 | `com.namnd.apigateway` | Request routing via Eureka |
| kafka-events | `/kafka-events/src/main/java/com/namnd/kafka/events/` | 21 | `com.namnd.kafka.events` | Event definitions, audit autoconfiguration |
| jwt-auth-autoconfigure | `/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/` | 5 | `com.namnd.jwt.autoconfigure` | JWT validation, auto-configuration library |

---

## 1. AUTH-SERVICE (66 files)

**Root Package:** `com.namnd.cinema`

### Package Structure
```
com.namnd.cinema/
├── config/
│   ├── OpenApiConfig.java
│   ├── HttpLoggingConfig.java
│   ├── MetricsConfig.java
│   ├── GlobalExceptionHandler.java
│   ├── RedisConfig.java
│   ├── RedisKeyPrefix.java
│   ├── security/
│   │   ├── SecurityConfig.java
│   │   └── OAuth2AuthenticationSuccessHandler.java
│   └── filter/
│       ├── JwtAuthenticationFilter.java
│       └── HttpLoggingFilter.java
├── custom/
│   └── CustomAccessDeniedHandler.java
├── controller/
│   ├── AuthController.java
│   └── TokenValidationController.java
├── model/
│   ├── User.java
│   ├── Role.java
│   ├── RefreshToken.java
│   ├── PasswordResetToken.java
│   ├── ActivationToken.java
│   ├── PasswordHistory.java
│   ├── UserOAuthProvider.java
│   └── UserPrinciple.java
├── repository/
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   ├── RefreshTokenRepository.java
│   ├── PasswordResetTokenRepository.java
│   ├── ActivationTokenRepository.java
│   ├── PasswordHistoryRepository.java
│   └── UserOAuthProviderRepository.java
├── service/
│   ├── JwtService.java
│   ├── UserService.java
│   ├── RoleService.java
│   ├── RefreshTokenService.java
│   ├── AccountLockService.java
│   ├── BlacklistedTokenService.java
│   ├── EmailService.java
│   ├── PasswordResetService.java
│   ├── PasswordHistoryService.java
│   ├── RedisService.java
│   ├── ActivationService.java
│   ├── OAuth2UserLinkingService.java
│   └── impl/
│       ├── UserServiceImpl.java
│       ├── RoleServiceImpl.java
│       ├── RefreshTokenServiceImpl.java
│       ├── BlacklistedTokenServiceImpl.java
│       ├── AccountLockServiceImpl.java
│       ├── RedisServiceImpl.java
│       ├── PasswordResetServiceImpl.java
│       ├── PasswordHistoryServiceImpl.java
│       ├── EmailServiceImpl.java
│       ├── ActivationServiceImpl.java
│       └── OAuth2UserLinkingServiceImpl.java
├── dto/
│   ├── LoginRequestDto.java
│   ├── JwtResponseDto.java
│   ├── TokenRefreshResponseDto.java
│   ├── RefreshTokenRequestDto.java
│   ├── ValidateTokenRequestDto.java
│   ├── ValidateTokenResponseDto.java
│   ├── UserInfoResponseDto.java
│   ├── ForgotPasswordDto.java
│   ├── ResetPasswordDto.java
│   ├── ChangePasswordDto.java
│   ├── RegisterDto.java
│   ├── SetupPasswordDto.java
│   └── mapper/
│       └── RegisterDtoMapper.java
└── CinemaAuthApplication.java
```

### Key Classes & Purpose
- **User**: Core entity with roles, OAuth providers, password history
- **JwtService**: Token generation & validation
- **RefreshToken/PasswordResetToken**: Token lifecycle management
- **SecurityConfig**: Spring Security configuration with OAuth2 support
- **AccountLockService**: Failed login attempt tracking & account locking
- **OAuth2UserLinkingService**: Multi-provider account linking

---

## 2. MOVIE-SERVICE (59 files)

**Root Package:** `com.namnd.movieservice`

### Package Structure
```
com.namnd.movieservice/
├── config/
│   ├── OpenApiConfig.java
│   ├── HttpLoggingConfig.java
│   ├── HttpLoggingFilter.java
│   ├── GlobalExceptionHandler.java
│   ├── SecurityConfig.java
│   └── DevDataInitializer.java
├── controller/
│   ├── MovieController.java
│   ├── TheaterController.java
│   ├── ShowtimeController.java
│   ├── MovieRatingController.java
│   ├── MovieCommentController.java
│   └── CommentReactionController.java
├── model/
│   ├── Movie.java (with MovieStatus enum)
│   ├── Theater.java
│   ├── Showtime.java (with ShowtimeStatus enum)
│   ├── Seat.java (with SeatType enum)
│   ├── MovieRating.java
│   ├── MovieComment.java (with CommentStatus enum)
│   └── CommentReaction.java
├── repository/
│   ├── MovieRepository.java
│   ├── TheaterRepository.java
│   ├── ShowtimeRepository.java
│   ├── SeatRepository.java
│   ├── MovieRatingRepository.java
│   ├── MovieCommentRepository.java
│   └── CommentReactionRepository.java
├── service/
│   ├── MovieService.java
│   ├── TheaterService.java
│   ├── ShowtimeService.java
│   ├── MovieRatingService.java
│   ├── MovieCommentService.java
│   ├── CommentReactionService.java
│   └── impl/
│       ├── MovieServiceImpl.java
│       ├── TheaterServiceImpl.java
│       ├── ShowtimeServiceImpl.java
│       ├── MovieRatingServiceImpl.java
│       ├── MovieCommentServiceImpl.java
│       └── CommentReactionServiceImpl.java
├── dto/
│   ├── CreateMovieRequest.java
│   ├── MovieDto.java
│   ├── CreateTheaterRequest.java
│   ├── TheaterDto.java
│   ├── CreateShowtimeRequest.java
│   ├── ShowtimeDto.java
│   ├── SeatDto.java
│   ├── CreateRatingRequest.java
│   ├── MovieRatingDto.java
│   ├── MovieRatingSummaryDto.java
│   ├── CreateCommentRequest.java
│   ├── UpdateCommentRequest.java
│   ├── MovieCommentDto.java
│   ├── CommentReactionRequest.java
│   └── CommentReactionDto.java
├── event/
│   └── MovieEventPublisher.java
└── MovieServiceApplication.java
```

### Key Classes & Purpose
- **Movie**: Catalog entity with soft-delete via status enum
- **Showtime**: Show scheduling with theater & seat references
- **Seat**: Individual theater seats with type classification
- **MovieRating/MovieComment**: User engagement & feedback
- **CommentReaction**: Like/reaction tracking on comments
- **MovieEventPublisher**: Kafka event publishing for movie events

---

## 3. BOOKING-SERVICE (35 files)

**Root Package:** `com.namnd.bookingservice`

### Package Structure
```
com.namnd.bookingservice/
├── config/
│   ├── OpenApiConfig.java
│   ├── HttpLoggingConfig.java
│   ├── HttpLoggingFilter.java
│   ├── MetricsConfig.java
│   ├── RedisConfig.java
│   ├── FeignJwtInterceptor.java
│   ├── SecurityConfig.java
│   ├── KafkaProducerConfig.java
│   ├── KafkaConsumerConfig.java
│   ├── WebSocketConfig.java
│   └── filter/
│       └── HttpLoggingFilter.java
├── controller/
│   └── BookingController.java
├── model/
│   ├── Booking.java (with BookingStatus enum)
│   └── BookingSeat.java
├── repository/
│   ├── BookingRepository.java
│   └── BookingSeatRepository.java
├── service/
│   ├── BookingService.java
│   ├── SeatLockService.java
│   ├── NotificationPublisherService.java
│   └── impl/
│       ├── BookingServiceImpl.java
│       ├── SeatLockServiceImpl.java
│       └── BookingExpiryScheduler.java
├── exception/
│   ├── BookingNotFoundException.java
│   └── SeatAlreadyLockedException.java
├── listener/
│   └── PaymentEventListener.java (listens to payment service)
├── websocket/
│   └── SeatWebSocketPublisher.java
├── dto/
│   ├── BookingRequestDto.java
│   ├── BookingResponseDto.java
│   ├── BookingSeatDto.java
│   ├── SeatInfoDto.java
│   ├── ShowtimeInfoDto.java
│   └── SeatStatusMessage.java
├── client/
│   └── MovieServiceClient.java (Feign)
└── BookingServiceApplication.java
```

### Key Classes & Purpose
- **Booking**: Aggregate root (PENDING → CONFIRMED | CANCELLED | EXPIRED)
- **BookingSeat**: Seat-booking association
- **SeatLockService**: Distributed seat locking via Redis
- **PaymentEventListener**: Consumes payment confirmation events
- **SeatWebSocketPublisher**: Real-time seat availability via WebSocket
- **BookingExpiryScheduler**: Scheduled cleanup of expired reservations

---

## 4. PAYMENT-SERVICE (46 files) - **RECONCILIATION FOCUS**

**Root Package:** `com.namnd.paymentservice`

### Package Structure

```
com.namnd.paymentservice/
├── batch/  [NEW RECONCILIATION MODULE]
│   ├── LocalPaymentReader.java
│   │   └── Reads local Payment records by date range (ItemReader<Payment>)
│   ├── ReconciliationProcessor.java
│   │   └── Compares local Payment vs Stripe PaymentIntent (ItemProcessor<Payment, ReconciliationItem>)
│   ├── ReconciliationItemWriter.java
│   │   └── Persists ReconciliationItem results (ItemWriter<ReconciliationItem>)
│   └── ReconciliationJobListener.java
│       └── Job lifecycle listener for run status updates
├── config/
│   ├── OpenApiConfig.java
│   ├── HttpLoggingConfig.java
│   ├── MetricsConfig.java
│   ├── FeignJwtInterceptor.java
│   ├── StripeConfig.java
│   ├── SchedulingConfig.java
│   ├── KafkaConsumerConfig.java
│   ├── ReconciliationJobConfig.java  [NEW]
│   │   └── Spring Batch job configuration (100-item chunks)
│   ├── ReconciliationScheduler.java  [NEW]
│   │   └── Scheduled daily reconciliation (Asia/Saigon timezone)
│   └── filter/
│       └── HttpLoggingFilter.java
├── controller/
│   ├── PaymentController.java
│   ├── StripeWebhookController.java
│   └── ReconciliationController.java  [NEW]
│       └── Admin-only REST endpoints for recon management
├── model/
│   ├── Payment.java
│   ├── PaymentStatus.java (PENDING, COMPLETED, FAILED)
│   ├── ReconciliationRun.java  [NEW]
│   │   └── Tracks batch execution with aggregated counts
│   ├── ReconciliationItem.java  [NEW]
│   │   └── Individual comparison result with discrepancy classification
│   ├── DiscrepancyType.java  [NEW]
│   │   └── MATCHED, AMOUNT_MISMATCH, STATUS_MISMATCH, MISSING_STRIPE, MISSING_LOCAL
│   └── ReconciliationStatus.java  [NEW]
│       └── RUNNING, COMPLETED, FAILED
├── repository/
│   ├── PaymentRepository.java
│   ├── ReconciliationRunRepository.java  [NEW]
│   └── ReconciliationItemRepository.java  [NEW]
├── service/
│   ├── PaymentService.java
│   ├── ReconciliationService.java  [NEW]
│   │   └── Contract for recon operations: trigger, query, resolve
│   └── impl/
│       ├── PaymentServiceImpl.java
│       └── ReconciliationServiceImpl.java  [NEW]
│           └── Orchestrates batch job launch & result queries
├── dto/
│   ├── CreatePaymentIntentRequest.java
│   ├── PaymentIntentResponse.java
│   ├── PaymentHistoryResponse.java
│   ├── RefundRequest.java
│   ├── TriggerReconciliationRequest.java  [NEW]
│   ├── ReconciliationRunResponse.java  [NEW]
│   ├── ReconciliationItemResponse.java  [NEW]
│   ├── ReconciliationSummaryResponse.java  [NEW]
│   └── ResolveItemRequest.java  [NEW]
├── event/
│   ├── PaymentEventPublisher.java
│   ├── PaymentCompletedSpringEvent.java
│   ├── PaymentFailedSpringEvent.java
│   └── PaymentAfterCommitListener.java
├── exception/
│   ├── PaymentException.java
│   └── GlobalExceptionHandler.java
└── PaymentServiceApplication.java
```

### Recent Additions (Git: 25bad94)
**Commit:** `feat(payment): add Stripe reconciliation with Spring Batch`

#### New Files (14 reconciliation-related):
1. **batch/** (4 files)
   - LocalPaymentReader: Reads payments by date range
   - ReconciliationProcessor: Compares local vs Stripe using PaymentIntent.retrieve()
   - ReconciliationItemWriter: Persists comparison results
   - ReconciliationJobListener: Updates run status upon job completion

2. **config/** (2 files)
   - ReconciliationJobConfig: Spring Batch Job definition (100-item chunks)
   - ReconciliationScheduler: Daily scheduled reconciliation (Asia/Saigon TZ)

3. **controller/** (1 file)
   - ReconciliationController: Admin-only REST API (@PreAuthorize("hasRole('ADMIN')"))

4. **model/** (4 files)
   - ReconciliationRun: Batch execution tracking (id, dateRange, status, counts)
   - ReconciliationItem: Comparison result per payment
   - DiscrepancyType: Enum classification
   - ReconciliationStatus: Job lifecycle (RUNNING, COMPLETED, FAILED)

5. **repository/** (2 files)
   - ReconciliationRunRepository: Paginated queries
   - ReconciliationItemRepository: Filter by type & run

6. **service/** (1 file)
   - ReconciliationServiceImpl: Job launcher, date range validation, result mapping

7. **dto/** (5 files)
   - TriggerReconciliationRequest, RunResponse, ItemResponse, SummaryResponse, ResolveItemRequest

### Key Classes & Purpose
- **Payment**: JPA entity (bookingId, amount, Stripe PI ID, status)
- **ReconciliationRun**: Batch execution record (tracks matched/mismatched/missing counts)
- **ReconciliationItem**: Individual payment comparison (local amount vs Stripe amount, status mismatch)
- **DiscrepancyType**: Classification (MATCHED, AMOUNT_MISMATCH, STATUS_MISMATCH, MISSING_STRIPE)
- **ReconciliationProcessor**: Core logic using Stripe API (PaymentIntent.retrieve())
- **ReconciliationJobConfig**: Spring Batch pipeline (read 100 items → process → write)
- **ReconciliationScheduler**: @Scheduled daily trigger (previous day, 1-day range)
- **ReconciliationController**: Admin endpoints (/trigger, /runs, /runs/{id}/items, /summary, /items/{id}/resolve)

---

## 5. NOTIFICATION-SERVICE (16 files)

**Root Package:** `com.namnd.notification`

### Package Structure
```
com.namnd.notification/
├── config/
│   └── KafkaConsumerConfig.java
├── controller/
│   ├── NotificationRestController.java
│   └── NotificationSseController.java
├── model/
│   └── Notification.java
├── repository/
│   └── NotificationRepository.java
├── service/
│   ├── EmailSenderService.java
│   ├── InAppNotificationService.java
│   ├── SseEmitterRegistryService.java
│   ├── NotificationDeduplicationService.java
│   └── impl/
│       └── InAppNotificationServiceImpl.java
├── listener/
│   ├── NotificationEventListener.java
│   └── InAppNotificationEventListener.java
├── dto/
│   ├── NotificationResponseDto.java
│   ├── BroadcastRequestDto.java
│   └── UnreadCountResponseDto.java
└── NotificationServiceApplication.java
```

### Key Classes & Purpose
- **Notification**: In-app notification entity with read/unread status
- **EmailSenderService**: SMTP email dispatch
- **InAppNotificationService**: Database notification storage
- **SseEmitterRegistryService**: Real-time push via Server-Sent Events
- **NotificationDeduplicationService**: Idempotent event processing
- **NotificationEventListener**: Consumes Kafka events from other services

---

## 6. AUDIT-SERVICE (11 files)

**Root Package:** `com.namnd.auditservice`

### Package Structure
```
com.namnd.auditservice/
├── config/
│   ├── KafkaConsumerConfig.java
│   └── KafkaTopicConfig.java
├── controller/
│   └── AdminAuditLogController.java
├── domain/
│   └── AuditLog.java
├── repository/
│   └── AuditLogRepository.java
├── specification/
│   └── AuditLogSpecification.java
├── mapper/
│   └── AuditLogMapper.java
├── consumer/
│   └── AuditEventConsumer.java
├── dto/
│   ├── AuditLogSearchRequest.java
│   └── AuditLogResponse.java
└── AuditServiceApplication.java
```

### Key Classes & Purpose
- **AuditLog**: Immutable audit trail (action, user, service, timestamp)
- **AuditEventConsumer**: Consumes Kafka audit events
- **AuditLogSpecification**: JPA Criteria API for advanced filtering
- **AdminAuditLogController**: Admin query endpoints with pagination

---

## 7. API-GATEWAY (5 files)

**Root Package:** `com.namnd.apigateway`

### Package Structure
```
com.namnd.apigateway/
├── config/
│   ├── OpenApiConfig.java
│   ├── HttpLoggingConfig.java
│   └── GlobalExceptionHandler.java
├── config/filter/
│   └── HttpLoggingFilter.java
└── ApiGatewayApplication.java
```

### Key Features
- Spring Cloud Gateway (servlet-based, not WebFlux)
- Service discovery via Eureka
- Route mapping to downstream microservices
- HTTP logging & exception handling

---

## 8. KAFKA-EVENTS (21 files)

**Root Package:** `com.namnd.kafka.events`

### Package Structure
```
com.namnd.kafka.events/
├── domain/
│   ├── PaymentCompletedEvent.java
│   ├── PaymentFailedEvent.java
│   ├── BookingCreatedEvent.java
│   ├── MovieCreatedEvent.java
│   ├── ShowtimeCreatedEvent.java
│   ├── NotificationRequestedEvent.java
│   ├── NotificationType.java
│   ├── InAppNotificationEvent.java
│   ├── AuditEvent.java
│   └── AuditAction.java
├── envelope/
│   └── EventEnvelope.java (generic wrapper)
├── topic/
│   └── KafkaTopics.java (constants)
├── audit/  [AUDIT FRAMEWORK]
│   ├── Auditable.java (annotation)
│   ├── AuditSpringEvent.java
│   ├── AuditEventPublisher.java
│   ├── AuditHttpContext.java (request context)
│   ├── AuditBeanProvider.java
│   ├── AuditAutoConfiguration.java
│   ├── AuditAfterCommitListener.java (transactional)
│   ├── AuditAspect.java (method interception)
│   └── AuditEntityListener.java (JPA lifecycle)
└── audit/
    └── [Spring Event abstraction for audit trail]
```

### Key Classes & Purpose
- **Event Domain Classes**: Immutable event objects (PaymentCompletedEvent, BookingCreatedEvent, etc.)
- **EventEnvelope**: Generic wrapper with metadata, tracing ID, timestamp
- **Auditable**: Annotation for marking audited operations
- **AuditAutoConfiguration**: Conditional registration (activates when KafkaTemplate exists)
- **AuditAspect**: Method-level interception for audit logging
- **AuditEntityListener**: JPA listener for entity lifecycle auditing
- **AuditEventPublisher**: Publishes audit events to Kafka topic

---

## 9. JWT-AUTH-AUTOCONFIGURE (5 files)

**Root Package:** `com.namnd.jwt.autoconfigure`

### Package Structure
```
com.namnd.jwt.autoconfigure/
├── JwtAuthProperties.java
├── JwtAuthenticatedUser.java
├── JwtAuthenticationFilter.java
├── JwtTokenValidator.java
└── JwtAutoConfiguration.java
```

### Key Classes & Purpose
- **JwtAuthProperties**: Configuration properties binding
- **JwtTokenValidator**: Token parsing & validation logic
- **JwtAuthenticationFilter**: Servlet filter for JWT extraction & validation
- **JwtAutoConfiguration**: Spring Boot autoconfiguration bean registration
- **JwtAuthenticatedUser**: User principal object

---

## Cross-Service Communication Patterns

### Service Clients (Feign)
- **BookingService** → MovieServiceClient (fetch movie/theater/showtime data)
- **PaymentService** → Auth tokens via FeignJwtInterceptor

### Event-Driven (Kafka)
- **PaymentEventPublisher** → payment-completed, payment-failed topics
- **MovieEventPublisher** → movie-created, movie-updated topics
- **NotificationEventListener** → consumes PaymentCompletedEvent, BookingCreatedEvent
- **AuditEventConsumer** → consumes AuditEvent topic

### WebSocket
- **BookingService** → SeatWebSocketPublisher (real-time seat updates)

### REST
- **API Gateway** → Routes to downstream services via Eureka discovery

---

## Technical Stack Summary

| Category | Technology |
|----------|-----------|
| Framework | Spring Boot 3.x, Spring Cloud |
| ORM | JPA/Hibernate |
| Database | PostgreSQL (implied from repositories) |
| Messaging | Apache Kafka |
| Cache | Redis |
| Payment | Stripe API |
| Search/Filter | JPA Criteria API, Specifications |
| Batch Processing | Spring Batch |
| API Docs | OpenAPI 3.0 (Springdoc) |
| Security | Spring Security, JWT, OAuth2 |
| Service Discovery | Eureka |
| API Gateway | Spring Cloud Gateway (servlet) |
| Real-time | WebSocket, Server-Sent Events (SSE) |
| Monitoring | Micrometer (metrics), Zipkin (tracing) |

---

## Reconciliation Module Details

### Flow Diagram
```
[Admin REST] 
    ↓
[ReconciliationController::POST /trigger]
    ↓
[ReconciliationService::triggerReconciliation()]
    ├─ Validate date range (max 31 days)
    ├─ Create ReconciliationRun (RUNNING status)
    ├─ Launch Spring Batch Job with JobLauncher
    │
    └─→ [Spring Batch Job Pipeline]
            ├─ Step: reconcileLocalStep
            │   ├─ Reader: LocalPaymentReader
            │   │   └─ SELECT * FROM payments WHERE created_at BETWEEN ? AND ?
            │   │
            │   ├─ Processor: ReconciliationProcessor (chunk size: 100)
            │   │   ├─ For each Payment:
            │   │   │   ├─ Call Stripe: PaymentIntent.retrieve(stripeId)
            │   │   │   ├─ Compare amounts & status
            │   │   │   ├─ Classify discrepancy
            │   │   │   └─ Return ReconciliationItem
            │   │   │
            │   │   └─ Discrepancy Types:
            │   │       ├─ MATCHED (amounts & status align)
            │   │       ├─ AMOUNT_MISMATCH
            │   │       ├─ STATUS_MISMATCH
            │   │       ├─ MISSING_STRIPE (no Stripe record found)
            │   │       └─ MISSING_LOCAL (payment without local DB record)
            │   │
            │   └─ Writer: ReconciliationItemWriter
            │       └─ INSERT INTO reconciliation_items (...)
            │
            └─ Listener: ReconciliationJobListener
                └─ On completion:
                   ├─ Fetch aggregated counts
                   ├─ Update ReconciliationRun (COMPLETED/FAILED)
                   └─ Persist summary counts

[Query APIs]
    ├─ GET /reconciliation/runs → List all runs (paginated)
    ├─ GET /reconciliation/runs/{id} → Run details with summary
    ├─ GET /reconciliation/runs/{id}/items → Items with optional discrepancy filter
    ├─ GET /reconciliation/summary → Latest run summary
    └─ PUT /reconciliation/items/{id}/resolve → Mark as resolved with notes

[Scheduled Trigger]
    └─ ReconciliationScheduler::@Scheduled(cron = "${reconciliation.cron}")
        └─ Trigger daily for yesterday's date (Asia/Saigon timezone)
```

### Configuration Properties
- `reconciliation.max-date-range-days` (default: 31)
- `reconciliation.auto-run.enabled` (enables/disables scheduled job)
- `reconciliation.cron` (cron expression for scheduler)

---

## Notable Patterns & Best Practices

1. **Transactional Event Publishing**: Payment/audit events use @TransactionalEventListener for at-least-once guarantees
2. **Distributed Lock (Redis)**: SeatLockService prevents race conditions in seat booking
3. **Kafka Idempotency**: NotificationDeduplicationService uses Stripe event IDs for webhook deduplication
4. **Soft Delete**: Movie records use status enum (ACTIVE/INACTIVE) instead of hard delete
5. **Spring Batch Chunking**: Reconciliation processes 100 items per transaction
6. **JobRepository Management**: Spring Batch maintains separate transaction boundaries
7. **Audit Aspect**: @Auditable annotation triggers automatic audit logging via AOP
8. **OpenAPI Documentation**: All services publish OpenAPI specs (Springdoc-OpenAPI)
9. **HTTP Logging Filter**: Cross-cutting concern for request/response logging
10. **Method Security**: @PreAuthorize used for fine-grained authorization (e.g., ADMIN role)

---

## Unresolved Questions

None at this time. The codebase is well-structured with clear separation of concerns and comprehensive reconciliation module implementation.
