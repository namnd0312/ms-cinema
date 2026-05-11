# Tóm Tắt Codebase

**Dự án:** ms-cinema
**Ngày tạo:** Tháng 4 năm 2026
**Kiến trúc:** 6 Dịch vụ Nghiệp vụ + 2 Thư viện Dùng chung + Angular Frontend
**Java Version:** 21 LTS
**Spring Boot:** 3.4.3
**Spring Cloud:** 2024.0.1 (Service Discovery qua K8s DNS / URI Tĩnh)

## Tổng Quan Module Maven

```
ms-cinema/ (root pom: packaging=pom)
├── Dịch vụ Nghiệp vụ (6 module)
│   ├── auth-service (:8081) - Xác thực JWT, quản lý người dùng
│   ├── movie-service (:8082) - Phim, rạp, suất chiếu
│   ├── booking-service (:8083) - Đặt chỗ ghế, Feign → movie-service
│   ├── payment-service (:8084) - Thanh toán Stripe, webhook
│   ├── notification-service (:8085) - Kafka consumer, email (SMTP), thông báo SSE
│   └── audit-service (:8086) - Kafka consumer, ghi nhật ký kiểm toán vào auditdb
├── Thư viện Dùng chung (2 module)
│   ├── kafka-events - Model sự kiện domain (AuditEvent, AuditAction)
│   └── jwt-auth-autoconfigure - Bộ xác thực JWT tái sử dụng
├── Frontend (1 module)
│   └── cinema-frontend (:4200→80) - Angular 18
└── Cấu hình Hạ tầng
    ├── docker-compose.yml - PostgreSQL, Kafka, Redis, Prometheus, Grafana, Loki, Tempo, OTel Collector
    ├── monitoring/ - Prometheus.yml, dashboard Grafana, cấu hình Loki
    ├── init-databases.sql - Khởi tạo schema (testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb)
    └── docs/ - Tài liệu
```

## auth-service (Cổng 8081)

**Tính năng chính:** Xác thực JWT, kích hoạt email, khóa tài khoản, xoay vòng token, phát sự kiện Kafka

```
src/main/java/com/namnd/cinema/
├── CinemaAuthApplication.java
├── config/
│   ├── SecurityConfig.java - Spring Security 6.x, @EnableMethodSecurity
│   ├── JwtAuthenticationFilter.java - Trích xuất & xác thực JWT trên mỗi yêu cầu
│   ├── CustomAccesDeniedHandler.java - Phản hồi lỗi 403
│   ├── OpenApiConfig.java - SpringDoc (Swagger UI)
│   ├── RedisConfig.java - Template danh sách đen token
│   └── RedisKeyPrefix.java - Hằng số (blacklist:, lock:)
├── controller/
│   ├── AuthController.java (~230 dòng) - login, register, activate, forgot-password, reset-password, refresh-token, logout
│   ├── TokenValidationController.java - validate-token (cho microservice), /api/users/me
│   └── TestController.java - Kiểm tra sức khỏe
├── model/ - User, Role, RefreshToken, PasswordResetToken, ActivationToken
├── dto/ - LoginRequestDto, JwtResponseDto, RegisterDto, ForgotPasswordDto, ResetPasswordDto, RefreshTokenRequestDto, TokenRefreshResponseDto, ValidateTokenRequestDto/ResponseDto, UserInfoResponseDto
├── service/ - JwtService, UserService, RoleService, RefreshTokenService, PasswordResetService, EmailService, ActivationService, BlacklistedTokenService, AccountLockService, RedisService
├── repository/ - UserRepository, RoleRepository, RefreshTokenRepository, PasswordResetTokenRepository, ActivationTokenRepository
└── resources/
    ├── application.yml - cổng 8081, import config-server, đăng ký eureka
    └── schema.sql - Bảng Users, roles, tokens (7 bảng)
```

**Dịch vụ chính:**
- **JwtService** (147 dòng): Ký HS512, xác thực chữ ký+hết hạn+danh sách đen, nhúng claims roles+userId
- **EmailServiceImpl** (35 dòng): Phát NotificationRequestedEvent lên Kafka topic "notification-events" (không gửi SMTP trực tiếp)
- **ActivationServiceImpl** (~90 dòng): Token kích hoạt email 24 giờ (dựa trên UUID)
- **PasswordResetServiceImpl** (~80 dòng): Token đặt lại mật khẩu 24 giờ
- **BlacklistedTokenServiceImpl** (~50 dòng): Danh sách đen Redis JTI với auto-TTL (fail-closed)
- **AccountLockServiceImpl** (~60 dòng): Khóa sau 5 lần thử, tự động mở khóa sau 15 phút
- **OAuth2UserLinkingService** (~100 dòng): Tìm/tạo người dùng từ dữ liệu provider OAuth2, tự động liên kết email nếu đã xác minh, xử lý race condition

**Endpoint API:** Xem phần Tham khảo API trong README.md

**Schema cơ sở dữ liệu:**
- users (id, username, email UNIQUE, password [nullable cho OAuth-only], fullName, active, failedAttempts, lockTime)
- roles (id, name)
- user_roles (user_id FK, role_id FK)
- refresh_tokens (id, token UNIQUE, expiryDate, user_id FK)
- password_reset_tokens (id, token UNIQUE, expiryDate, user_id FK)
- activation_tokens (id, token UNIQUE, expiryDate, user_id FK, used)
- password_history (id, user_id FK, password_hash, created_at)
- blacklisted_tokens (id, jti UNIQUE, expiry_date)
- user_oauth_providers (id, user_id FK, provider_name, provider_user_id, provider_email, linked_at; UC: (provider_name+provider_user_id, user_id+provider_name))

**Tính năng Lịch sử Mật khẩu:**
- Entity PasswordHistory: Lưu tối đa 3 hash mật khẩu trước đó cho mỗi người dùng để ngăn tái sử dụng
- PasswordHistoryService: Quản lý CRUD lịch sử, xác thực mật khẩu mới với các mục gần đây
- POST /api/auth/change-password: Endpoint đổi mật khẩu cho người dùng đã xác thực (chặn cho người dùng OAuth-only)
- Đặt lại mật khẩu (POST /api/auth/reset-password): Xác thực mật khẩu mới với 3 hash gần nhất
- Quy trình đăng ký: Lưu mật khẩu ban đầu vào bảng lịch sử khi tạo người dùng

**Tích hợp OAuth2:**
- Entity UserOAuthProvider: Lưu trữ liên kết provider (provider_name, provider_user_id, providerEmail, linkedAt)
- UserOAuthProviderRepository: findByProviderNameAndProviderUserId(), existsByUserIdAndProviderName()
- OAuth2AuthenticationSuccessHandler: Tạo JWT+refresh token, chuyển hướng frontend với token dưới dạng query params
- OAuth2UserLinkingService: Thứ tự tìm kiếm (1) liên kết provider hiện có (2) email khớp nếu đã xác minh (3) tạo mới
- Ràng buộc duy nhất ngăn liên kết trùng lặp cho mỗi provider và mỗi người dùng
- Race condition đăng nhập đồng thời: Xử lý qua DataIntegrityViolationException catch khi tạo liên kết provider
- Tự động tạo người dùng OAuth-only: password=NULL, active=true, gán ROLE_USER mặc định
- **Sửa tháng 3:** LazyInitializationException trên user.getRoles() được giải quyết bằng cách force-initialize roles trong context @Transactional trong OAuth2UserLinkingService

## movie-service (Cổng 8082)

**Tính năng chính:** CRUD phim, CRUD rạp (tự động tạo lưới ghế hàng A-Z), quản lý suất chiếu, phát MovieCreatedEvent/ShowtimeCreatedEvent

**Controller:**
- MovieController - GET tất cả, GET theo id, POST (ADMIN), PUT (ADMIN), DELETE (ADMIN)
- TheaterController - GET tất cả, GET theo id, POST (ADMIN, tự động tạo ghế), PUT (ADMIN), DELETE (ADMIN)
- ShowtimeController - GET tất cả, GET theo id, POST (ADMIN), PUT (ADMIN), DELETE (ADMIN)
- MovieRatingController - POST (tạo/cập nhật đánh giá 1-5, upsert), GET (tổng hợp với trung bình/tổng/đánh giá người dùng)
- MovieCommentController - POST (tạo), GET (danh sách phân trang, 20/trang), PUT (cập nhật, chỉ chủ sở hữu), DELETE (xóa mềm)
- CommentReactionController - POST (chuyển đổi thích/không thích), DELETE (xóa phản hồi)

**Model:**
- Movie (id, title, description, duration, genre, releaseDate; bao gồm averageRating, totalRatings, commentCount trong DTO)
- Theater (id, name, location, totalSeats, seats list LAZY)
- Seat (id, seatNumber [định dạng hàng A-Z], theaterRef, available)
- Showtime (id, movieRef, theaterRef, startTime, endTime, price)
- MovieRating (id, movie_id, user_id, rating [1-5], created_at, updated_at; UNIQUE(movie_id, user_id))
- MovieComment (id, movie_id, user_id, content, status ENUM [ACTIVE/DELETED], created_at, updated_at)
- CommentReaction (id, comment_id, user_id, reaction_type ENUM [LIKE/DISLIKE], created_at; UNIQUE(comment_id, user_id))

**Service:**
- MovieService - CRUD + phát sự kiện (MovieCreatedEvent); tổng hợp khi truy vấn trong toDto()
- TheaterService - CRUD + tự động tạo lưới ghế
- ShowtimeService - CRUD + phát sự kiện (ShowtimeCreatedEvent)
- MovieRatingService - Upsert đánh giá, lấy tổng hợp (trung bình, tổng, đánh giá người dùng)
- MovieCommentService - Tạo, danh sách (phân trang), cập nhật, xóa mềm
- CommentReactionService - Chuyển đổi thích/không thích, xóa phản hồi

**Repository (Truy vấn tùy chỉnh):**
- MovieRatingRepository - findAverageRatingByMovieId(), countByMovieId()
- MovieCommentRepository - findByMovieIdAndStatusActive (tùy chỉnh @Query)
- CommentReactionRepository - countLikesByCommentId(), countDislikesByCommentId()

**Sự kiện Kafka phát:** MovieCreatedEvent, ShowtimeCreatedEvent → topic: movie-events

**Bảo mật:** /api/comments/** thêm vào permitAll trong SecurityConfig cho GET (bình luận công khai)

## booking-service (Cổng 8083)

**Tính năng chính:** Đặt chỗ ghế với khóa Redis (TTL 5 phút), vòng đời đặt vé (PENDING→CONFIRMED/CANCELLED/EXPIRED), Feign client đến movie-service, tiêu thụ PaymentCompletedEvent/PaymentFailedEvent, BookingExpiryScheduler (kiểm tra 60 giây), transactional event listener

**Controller:**
- BookingController - reserve, getBooking, getUserBookings, confirmBooking, cancelBooking, getBookedSeats

**Model:**
- Booking (id, showtimeRef, userRef, status ENUM, createdAt, expiresAt)
- BookingSeat (id, bookingRef, seatRef, status ENUM)

**Service:**
- BookingService - Quản lý vòng đời, khóa Redis, xử lý sự kiện
- SeatReservationService - Lấy khóa (mẫu key: seat:lock:{showtimeId}:{seatId})

**Feign Client:**
- MovieServiceClient - Lấy thông tin suất chiếu & ghế

**Xử lý sự kiện Kafka:**
- Tiêu thụ PaymentCompletedEvent → chuyển đặt vé CONFIRMED, phát InAppNotificationEvent
- Tiêu thụ PaymentFailedEvent → chuyển đặt vé CANCELLED, giải phóng khóa, phát InAppNotificationEvent
- Phát BookingCreatedEvent → notification-events
- Phát InAppNotificationEvent → notification.in_app (sự kiện xác nhận/thất bại thanh toán)

**Scheduler:**
- BookingExpiryScheduler (60 giây): Tìm đặt vé PENDING đã qua expiresAt, chuyển EXPIRED, giải phóng khóa Redis

**Mẫu key khóa Redis:** `seat:lock:{showtimeId}:{seatId}`
**TTL khóa:** 5 phút (có thể cấu hình)

**Cấu hình WebSocket (MỚI - 22 tháng 3, 2026):**
- WebSocketConfig.java: Cấu hình Spring WebSocket, endpoint STOMP /ws/booking, fallback SockJS, xác thực JWT trong handshake
- Message broker: Trong bộ nhớ (app:/booking/seats/*)
- Xác thực qua JWT trong WebSocket handshake (tích hợp SpringSecurity)
- SeatStatusMessage.java: DTO (showtimeId, seatId, status, userId, action: LOCK/RESERVE/CANCEL)
- SeatWebSocketPublisher.java: Service để broadcast thay đổi trạng thái ghế cho client kết nối qua STOMP (/topic/showtime/{id}/seats)
- Các hành động sự kiện: LOCK (người dùng lấy ghế), RESERVE (thanh toán xác nhận), CANCEL (hết hạn đặt vé/thất bại)
- BookingServiceImpl sửa đổi: Gọi SeatWebSocketPublisher.publishSeatStatusChange() khi lock/reserve/cancel
- BookingExpiryScheduler sửa đổi: Phát hành hành động CANCEL khi hết hạn đặt vé
- Frontend đăng ký /topic/showtime/{showtimeId}/seats để cập nhật thời gian thực (<100ms độ trễ)

## payment-service (Cổng 8084)

**Tính năng chính:** Tích hợp Stripe với idempotency key (pay-{bookingId}), xử lý webhook với xác minh chữ ký, hoàn tiền (chỉ ADMIN), phát PaymentCompletedEvent/PaymentFailedEvent, TransactionalEventListener cho Kafka publish sau commit, **đối soát Spring Batch với tác vụ hàng ngày theo lịch trình**

**Controller:**
- PaymentController - create-intent, confirm, getPayment, getUserPayments, refund (ADMIN), webhook (POST)
- ReconciliationController (MỚI, @PreAuthorize("hasRole('ADMIN')"), base: /api/payments/reconciliation)
  - POST /trigger - Kích hoạt đối soát cho khoảng ngày (xác thực tối đa 31 ngày)
  - GET /runs - Danh sách phân trang của các lần chạy đối soát
  - GET /runs/{runId} - Chi tiết lần chạy đơn với đếm
  - GET /runs/{runId}/items - Mục phân trang với bộ lọc loại chênh lệch tùy chọn
  - GET /summary - Thống kê lần chạy mới nhất (đếm tóm tắt)
  - PUT /items/{itemId}/resolve - Đánh dấu mục đã giải quyết với ghi chú

**Model:**
- Payment (id, bookingRef, amount, currency, status ENUM, stripePaymentIntentId, createdAt)
- ReconciliationRun (startDate, endDate, status [RUNNING/COMPLETED/FAILED], đếm cho matched/mismatched/missing)
- ReconciliationItem (runId FK, stripePaymentIntentId, localPaymentId, discrepancyType ENUM, amounts, statuses, resolved, notes)
- DiscrepancyType enum: MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE
- ReconciliationStatus enum: RUNNING, COMPLETED, FAILED

**Tác vụ Batch (Spring Batch):**
- JobConfig: Job + Step bean (chunk size 100)
- LocalPaymentReader: ItemReader truy vấn paymentdb theo khoảng createdAt
- ReconciliationProcessor: ItemProcessor gọi PaymentIntent.retrieve() cho mỗi thanh toán, phân loại chênh lệch
- ReconciliationItemWriter: ItemWriter lưu trữ chunk ReconciliationItem vào paymentdb
- ReconciliationJobListener: afterJob gọi Stripe list API cho MISSING_LOCAL, hoàn thiện đếm lần chạy

**Service:**
- PaymentService - Tạo Stripe PaymentIntent (idempotency key: pay-{bookingId}), xử lý webhook
- StripeWebhookService - Xác minh chữ ký (header stripeSig), dedup stripeEventId (ngăn phát lại)
- ReconciliationService (interface) & ReconciliationServiceImpl
  - triggerReconciliation(startDate, endDate) - Xác thực khoảng ngày ≤31 ngày, tạo ReconciliationRun, khởi chạy tác vụ batch
  - getRuns(pageable) - Trả về các lần chạy phân trang sắp xếp theo createdAt giảm dần
  - getRunDetails(runId) - Trả về lần chạy đơn với đếm tóm tắt
  - getRunItems(runId, discrepancyType, pageable) - Mục phân trang với bộ lọc tùy chọn
  - getSummary() - Thống kê lần chạy mới nhất
  - resolveItem(itemId, notes) - Đánh dấu mục đã giải quyết với ghi chú admin

**Xử lý Batch Theo Lịch Trình:**
- Cron: Hàng ngày lúc 2 AM múi giờ Asia/Saigon (có thể cấu hình qua thuộc tính reconciliation.cron)
- EnableScheduling: Tự động kích hoạt qua @Scheduled trên bean ReconciliationScheduler
- ConditionalOnProperty: reconciliation.auto-run=true bật tự động kích hoạt
- Khoảng ngày tối đa: 31 ngày (có thể cấu hình qua reconciliation.max-date-range-days)

**Repository:**
- ReconciliationRunRepository - phân trang, sắp xếp theo createdAt giảm dần
- ReconciliationItemRepository - lọc theo runId + discrepancyType, truy vấn đếm
- PaymentRepository.findByDateRangeWithStripeId(startDate, endDate) - PHƯƠNG THỨC MỚI cho batch reader

**Tích hợp Stripe:**
- Biến môi trường: STRIPE_SECRET_KEY, STRIPE_WEBHOOK_SECRET
- Tạo PaymentIntent với idempotency key (pay-{bookingId})
- Webhook: POST /api/payments/webhook (xác minh chữ ký, dedup sự kiện)
- Đối soát batch: PaymentIntent.retrieve() cho mỗi thanh toán, StripeClient.listPaymentIntents() cho missing local
- Phát PaymentCompletedEvent/PaymentFailedEvent sau DB commit (TransactionalEventListener)

**Sự kiện Kafka phát:** PaymentCompletedEvent, PaymentFailedEvent → topic: payment-events

**Tích hợp Audit:**
- PaymentController: @Auditable trên phương thức createPaymentIntent
- Audit actions: CREATE_PAYMENT

**Cơ sở dữ liệu (paymentdb):**
- Bảng payments: id, bookingRef, amount, currency, status, stripePaymentIntentId, createdAt
- Bảng reconciliation_runs: id, startDate, endDate, status, matchedCount, mismatchedCount, missingLocalCount, missingStripeCount, totalChecked, createdAt
- Bảng reconciliation_items: id, runId FK, stripePaymentIntentId, localPaymentId, discrepancyType ENUM, stripeAmount, localAmount, stripeStatus, localStatus, resolved, notes, createdAt
  - Index: (run_id), (discrepancy_type) để lọc

**Cấu hình (application.yml / config-repo/payment-service.yml):**
- spring.batch.jdbc.initialize-schema=always (tự động tạo bảng metadata Spring Batch)
- spring.batch.job.enabled=false (vô hiệu hóa auto-run khi khởi động)
- reconciliation.cron: 0 2 * * * (2 AM hàng ngày, múi giờ Asia/Saigon)
- reconciliation.auto-run: true (bật đối soát theo lịch trình)
- reconciliation.max-date-range-days: 31 (tối đa 31 ngày cho mỗi đối soát)
- stripe.api.key: ${STRIPE_API_KEY:} (biến môi trường, không hardcode khóa test)

## notification-service (Cổng 8085)

**Tính năng chính:** Kafka consumer (notification-events + sự kiện SSE trong ứng dụng), gửi email SMTP, truyền Server-Sent Events (SSE), lưu trữ PostgreSQL, REST API cho CRUD + đánh dấu đã đọc, xác thực JWT qua query param cho SSE

**Kafka Consumer:**
- Topic: notification-events (NotificationRequestedEvent) → EmailSenderService → gửi SMTP
- Topic: notification.in_app (InAppNotificationEvent) → SseEmitterService → broadcast đến tất cả SSE client kết nối
- Consumer group: `notification-service-{instance-id}` (duy nhất cho mỗi instance để broadcast đến tất cả instance)

**JPA Entity:**
- Notification (id, userId, title, message, notificationType ENUM [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM], isRead, createdAt)

**REST Controller:**
- `NotificationSseController` - GET /api/notifications/stream (endpoint SSE, JWT qua query param ?token=JWT)
- `NotificationRestController`:
  - GET /api/notifications (phân trang, sắp xếp createdAt giảm dần)
  - PATCH /api/notifications/{id}/read (đánh dấu đơn đã đọc)
  - PATCH /api/notifications/read-all (đánh dấu hàng loạt đã đọc)
  - GET /api/notifications/unread-count (trả về số lượng cho badge)
  - POST /api/notifications/broadcast (chỉ admin, broadcast thử nghiệm)

**Service & Thành phần:**
- `SseEmitterRegistryService` - Quản lý emitter dựa trên ConcurrentHashMap, heartbeat mỗi 30 giây
- `InAppNotificationServiceImpl` - Lưu trữ & emit thông báo, đánh dấu đã đọc, broadcast
- `InAppNotificationEventListener` (Kafka) - Tiêu thụ InAppNotificationEvent, broadcast qua SSE
- `NotificationPublisherService` - Được gọi bởi booking-service để phát InAppNotificationEvent
- `EmailSenderService` - SMTP (Gmail, xác thực qua MAIL_USERNAME/MAIL_PASSWORD)
- `NotificationDeduplicationService` - Redis: mẫu key notification:processed:{eventId}, TTL 24 giờ

**JPA Repository:**
- `NotificationRepository`:
  - findByUserIdOrderByCreatedAtDesc (phân trang)
  - countByUserIdAndIsReadFalse (số chưa đọc)
  - markAllAsReadByUserId (cập nhật hàng loạt)
  - findDistinctUserIds (cho broadcast, tránh OOM)

**Cấu hình SSE:**
- Heartbeat: Sự kiện chỉ comment mỗi 30 giây (keep-alive, không ngắt kết nối khi timeout)
- Timeout emitter: 30 phút (có thể cấu hình)
- Consumer group duy nhất cho mỗi instance đảm bảo tất cả SSE client nhận broadcast
- Kết nối lại nhẹ nhàng: Client triển khai exponential backoff (1s→tối đa 30s, 5 lần thử)

**Cơ sở dữ liệu (notificationdb):**
- Bảng notifications: id (PK), userId (FK), title, message, notificationType, isRead, createdAt
- Index: (userId, createdAt DESC) cho phân trang hiệu quả

**Cấu hình:**
- spring.mail.host: smtp.gmail.com
- spring.mail.port: 587
- spring.mail.username: ${MAIL_USERNAME}
- spring.mail.password: ${MAIL_PASSWORD}

**Xử lý lỗi:**
- Kafka: 3 lần thử lại, exponential backoff (1s→2s→4s giới hạn 10s), DLT cho lỗi
- Redis fail-open: NotificationDeduplicationService là tùy chọn; tiếp tục nếu Redis không khả dụng
- Sửa race condition SSE: computeIfPresent atomic trong removeEmitter để ngăn lỗi đồng thời

## audit-service (Cổng 8086)

**Tính năng chính:** Kafka consumer, ghi nhật ký kiểm toán vào PostgreSQL auditdb, Admin API để truy vấn nhật ký

**Controller:**
- AuditLogController - GET /api/audit/logs (lọc theo userId, action, entityType, dateRange), GET /api/audit/logs/{id}

**Model:**
- AuditLog (id, userId, action: AuditAction, entityType, entityId, beforeState JSON, afterState JSON, ipAddress, userAgent, timestamp)

**Service:**
- AuditLogService - CRUD, truy vấn với filter, lưu trữ Kafka event
- AuditEventListener - Kafka consumer cho topic audit-events

**Kafka Listener:**
- Topic audit-events: AuditEvent → AuditLogService → PostgreSQL auditdb

**Bảo mật:**
- GET /api/audit/logs yêu cầu ROLE_ADMIN
- GET /api/audit/logs/{id} yêu cầu ROLE_ADMIN

**Schema cơ sở dữ liệu (auditdb):**
- Bảng audit_logs: id (PK), userId (FK), action (ENUM), entityType, entityId, beforeState (JSONB), afterState (JSONB), ipAddress, userAgent, timestamp
- Chỉ mục: (userId, timestamp DESC), (action, timestamp DESC), (entityType, entityId)

## kafka-events (Thư viện Dùng chung)

**Mục đích:** Model sự kiện domain dùng chung cho tất cả dịch vụ

**Enum:**
- `NotificationType` [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM]
- `AuditAction` [LOGIN, REGISTER, LOGOUT, CREATE, UPDATE, DELETE, RESERVE, CANCEL, CONFIRM_PAYMENT, CREATE_PAYMENT_INTENT, CHANGE_PASSWORD]

**Record (Java Records):**
- `PaymentCompletedEvent` (bookingId, amount, status)
- `PaymentFailedEvent` (bookingId, reason)
- `BookingCreatedEvent` (bookingId, userId, showtimeId)
- `MovieCreatedEvent` (movieId, title, genre)
- `ShowtimeCreatedEvent` (showtimeId, movieId, theaterId, startTime)
- `NotificationRequestedEvent` (recipientEmail, subject, body, eventType)
- `InAppNotificationEvent` (userId, title, message, notificationType: NotificationType)
- `AuditEvent` (userId, action: AuditAction, entityType, entityId, beforeState, afterState, ipAddress, userAgent, timestamp)

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

**Cấu hình Kafka Topic:**
- movie-events (partition 1, replication 3)
- payment-events (partition 1, replication 3)
- notification-events (partition 1, replication 3)
- notification.in_app (partition 1, replication 3, broadcast đến tất cả kết nối SSE)
- audit-events (partition 1, replication 3, tiêu thụ bởi audit-service)

**Xử lý lỗi (Cấu hình Docker Compose Kafka):**
```
num.network.threads: 8
offsets.topic.replication.factor: 3
transaction.state.log.replication.factor: 3
spring.kafka.producer.retries: 3
spring.kafka.producer.properties.linger.ms: 10
spring.kafka.consumer.max.poll.records: 100
spring.kafka.listener.error-handler: org.springframework.kafka.listener.DefaultErrorHandler (3 retries, exponential backoff 1s-4s-10s)
```

## jwt-auth-autoconfigure (Thư viện Dùng chung)

**Mục đích:** Bộ xác thực JWT tái sử dụng cho dịch vụ downstream (booking, payment, v.v.)

**Thành phần:**
- JwtAutoConfiguration - Auto-config Spring Boot (bean có điều kiện)
- JwtAuthProperties - @ConfigurationProperties(prefix="jwt.auth")
- JwtTokenValidator - Xác thực chữ ký HS512, hết hạn
- JwtAuthenticationFilter - Thiết lập SecurityContext từ JWT claims (roles, userId)
- JwtAuthenticatedUser - Model Principal

**Thuộc tính cấu hình:**
```yaml
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}
    publicPaths: ["/actuator/health"]
    enabled: true
```

**Kích hoạt:** @ConditionalOnProperty(name="jwt.auth.enabled", havingValue="true")

**Sử dụng trong dịch vụ Downstream:**
- Thêm jwt-auth-autoconfigure làm dependency
- Cấu hình jwt.auth.secret trong application.yml (từ config-server)
- Đánh dấu phương thức controller với @PreAuthorize("hasRole('ROLE_USER')")
- JwtAuthenticationFilter tự động kết nối qua auto-config

## Định Tuyến (K8s Ingress / nginx docker-compose)

Định tuyến theo đường dẫn — không có gateway service riêng:
- `/api/auth/**`, `/api/users/**`, `/oauth2/**` → auth-service:8081
- `/api/movies/**`, `/api/showtimes/**`, `/api/theaters/**` → movie-service:8082
- `/api/bookings/**` → booking-service:8083
- `/api/payments/**` → payment-service:8084
- `/api/notifications/**` → notification-service:8085
- `/api/audit/**` → audit-service:8086
- `/ws/**` → booking-service:8083 (WebSocket upgrade)

**K8s:** `k8s/ingress.yml` | **Docker Compose:** `cinema-frontend/nginx.conf`

## eureka-server (Cổng 8761)

**Mục đích:** Registry khám phá dịch vụ

**Cấu hình:**
- eureka.server.enable-self-preservation: false
- eureka.client.register-with-eureka: false
- eureka.client.fetch-registry: false
- Tần suất heartbeat: 10 giây (client → server)
- Thời gian chờ lease: 30 giây

## config-server (Cổng 8888)

**Mục đích:** Quản lý cấu hình tập trung

**Cấu hình:**
- Tải từ classpath:/config-repo/ (native profile)
- Hỗ trợ thay thế: Git repository

**Cấu hình dùng chung (config-repo/application.yml):**
```yaml
namnd.app.jwtSecret: ${JWT_SECRET}
jwt.auth.secret: ${JWT_SECRET}
```

**Cấu hình riêng cho từng dịch vụ:**
- auth-service/application.yml (riêng cho auth)
- movie-service/application.yml
- booking-service/application.yml
- payment-service/application.yml
- notification-service/application.yml

## cinema-frontend (Angular 18)

**Cổng:** 4200 (dev) → 80 (production qua Nginx)

**Stack:** TypeScript 5.5, Material 18, Stripe.js 8.9, RxJS

**Tính năng:**
- Luồng xác thực (đăng nhập, đăng ký, làm mới token)
- Duyệt phim, chọn suất chiếu
- Đặt chỗ lưới ghế tương tác
- Tích hợp thanh toán Stripe
- Hồ sơ người dùng, lịch sử đặt vé
- Dashboard admin CRUD (ROLE_ADMIN)
- **Đánh giá & bình luận phim:** Hiển thị đánh giá sao, danh sách bình luận, nút phản hồi
- **Model:** movie-rating.model.ts, movie-comment.model.ts (bao gồm interface Page<T> cho phân trang)
- **Service:** MovieRatingService, MovieCommentService, TheaterService, ShowtimeAdminService
- **Component:** StarRatingComponent, CommentListComponent, CommentItemComponent
- **Auth interceptor:** Sửa để luôn gắn token khi có; PUBLIC_URLS chỉ kiểm soát refresh 401
- **MovieDetailComponent:** Tích hợp đánh giá sao + danh sách bình luận

**Admin Dashboard (Mới):**
- **AdminNavComponent:** Điều hướng dựa trên tab (Phim, Rạp, Suất chiếu, Thanh toán, Đối soát)
- **MovieManagementComponent:** Danh sách phim trong MatTable với hành động sửa/xóa
- **MovieFormDialogComponent:** Form modal cho tạo/sửa phim
- **TheaterManagementComponent:** Danh sách rạp trong MatTable với hành động sửa/xóa
- **TheaterFormDialogComponent:** Form modal cho tạo/sửa rạp
- **ShowtimeManagementComponent:** Danh sách suất chiếu trong MatTable với hành động sửa/xóa
- **ShowtimeFormDialogComponent:** Form modal cho tạo/sửa suất chiếu
- **PaymentManagementComponent:** Danh sách tất cả thanh toán trong MatTable (chỉ admin xem)

**Bảng Điều Khiển Đối Soát Stripe (MỚI - 31 tháng 3, 2026):**
- **reconciliation-dashboard.component.ts:** Thành phần chính cho tab đối soát
  - Hiển thị: Lịch sử chạy đối soát trong MatTable (startDate, endDate, status, đếm matched/mismatched/missing)
  - Hành động: Nút kích hoạt thủ công (mở dialog chọn khoảng ngày)
  - Bộ lọc: Bấm vào hàng để xem chi tiết (phân tích chênh lệch)
  - Xuất CSV: Tải các mục đối soát dưới dạng CSV (discrepancyType, amounts, statuses, resolved)
- **reconciliation-detail.component.ts:** Chế độ xem chi tiết cho lần chạy đơn
  - Hiển thị bảng ReconciliationItem: stripePaymentIntentId, localPaymentId, discrepancyType, amounts, statuses, resolved flag
  - Bộ lọc tùy chọn: dropdown theo discrepancyType (MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE)
  - Hành động giải quyết: Bấm nút "Resolve" để đánh dấu mục đã giải quyết với ghi chú admin
- **Dịch vụ API Đối Soát:**
  - triggerReconciliation(startDate, endDate) - POST /api/payments/reconciliation/trigger
  - getRuns(page) - GET /api/payments/reconciliation/runs
  - getRunDetails(runId) - GET /api/payments/reconciliation/runs/{runId}
  - getRunItems(runId, discrepancyType?, page?) - GET /api/payments/reconciliation/runs/{runId}/items
  - getSummary() - GET /api/payments/reconciliation/summary
  - resolveItem(itemId, notes) - PUT /api/payments/reconciliation/items/{itemId}/resolve

**Thông Báo Thời Gian Thực (Mới - 14 tháng 3, 2026):**
- **notification.model.ts:** Interface (Notification, NotificationPage, UnreadCountResponse)
- **notification-sse.service.ts:** EventSource với exponential backoff (1s→tối đa 30s, 5 lần thử), xác thực JWT qua query param
- **notification-api.service.ts:** Gọi REST (GET danh sách, PATCH đánh dấu đã đọc, GET số chưa đọc, POST broadcast)
- **notification-bell.component.ts:** Badge trên toolbar với matBadge, snackbar alert, tự động tăng khi có thông báo mới
- **notification-list.component.ts:** Danh sách Mat-card với giao diện tối, viền màu theo notificationType, phân trang
- **notifications.routes.ts:** Cấu hình lazy route
- **Tích hợp Toolbar:** notification-bell thêm vào toolbar.component.ts cho truy cập liên tục
- **Tích hợp App:** Route /notifications thêm vào app.routes.ts

**Route Lazy-Loaded:**
- /auth (đăng nhập, đăng ký, đặt lại mật khẩu, callback OAuth2)
- /movies (duyệt, chi tiết)
- /booking (chọn ghế, xác nhận)
- /payment (thanh toán Stripe)
- /profile (thông tin người dùng, đặt vé, đổi mật khẩu)
- /admin (dashboard admin với tab)
- /notifications (lịch sử thông báo, đánh dấu đã đọc)

**Tính năng Đổi Mật Khẩu (Frontend):**
- Route: /profile/change-password (được bảo vệ, yêu cầu xác thực)
- Component: ChangePasswordComponent với reactive form
- Trường: currentPassword, newPassword, confirmPassword
- Xác thực: Kiểm tra mật khẩu khớp, xác minh mật khẩu hiện tại
- Tích hợp: Nút "Change Password" trên trang hồ sơ (ProfileComponent)
- Xử lý lỗi: Hiển thị lỗi xác thực và thông báo phản hồi server
- Guard: Chặn truy cập cho người dùng OAuth-only (password=NULL)

**Tích hợp OAuth2 (Frontend):**
- Route: /oauth2-callback (component standalone, xử lý callback OAuth2)
- Component: OAuth2CallbackComponent
  - Trích xuất token + refreshToken từ URL query params
  - Xóa token khỏi lịch sử trình duyệt ngay lập tức (bảo mật: ngăn rò rỉ)
  - Gọi AuthService.handleOAuth2Callback() để lưu token
  - Chuyển hướng đến /movies khi thành công, /auth/login khi lỗi (delay 2 giây)
  - Hiển thị spinner "Signing in..." trong quá trình xử lý
- Cập nhật AuthService: handleOAuth2Callback() lưu token giống đăng nhập truyền thống
- Component Đăng nhập: Nút "Sign in with Google" chuyển hướng đến /oauth2/authorization/google
- Xử lý lỗi: Hiển thị thông báo lỗi nếu thiếu email hoặc callback không hợp lệ

**API Proxy:** nginx.conf định tuyến /api/* trực tiếp đến từng dịch vụ backend (K8s Ingress trong Kubernetes)

**Cấu hình Nginx (Prod):** SPA fallback cho định tuyến phía client

## Stack Giám Sát

**Prometheus (Cổng 9090)**
- Tần suất scrape: 15 giây
- Lưu trữ: 7 ngày
- Mục tiêu scrape (tất cả 8 dịch vụ): endpoint /actuator/prometheus
- Metrics: JVM (bộ nhớ, GC, thread), HTTP (tốc độ request, độ trễ, lỗi), bộ đếm nghiệp vụ tùy chỉnh

**Grafana (Cổng 3000)**
- Datasource tự động cung cấp: Prometheus, Loki, Tempo (với `tracesToLogsV2` + `tracesToMetrics`)
- 2 dashboard dựng sẵn:
  - JVM Micrometer (bộ nhớ, GC, thread, sử dụng CPU)
  - Spring Boot HTTP Overview (tốc độ request, tỷ lệ lỗi, độ trễ, database pool, bộ đếm nghiệp vụ)
- Metrics nghiệp vụ tùy chỉnh: auth.login.success/failure, booking.created/confirmed/cancelled, payment.initiated/completed/failed

**Loki (Cổng 3100)**
- Lưu trữ 7 ngày
- Nhãn log: job, instance, application (tên dịch vụ)
- Khám phá log qua Grafana
- Tự động bao gồm traceId và spanId từ Micrometer Tracing MDC

**Tempo (Cổng 3200) + OTel Collector (Cổng 4317/4318)**
- Pipeline distributed tracing: Spring Boot → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP → OTel Collector (contrib) → Grafana Tempo
- Thu thập trace tập trung từ tất cả 6 dịch vụ nghiệp vụ
- Docker images cố định: `grafana/tempo:2.6.0`, `otel/opentelemetry-collector-contrib:0.115.1`
- App config: `management.otlp.tracing.endpoint: http://otel-collector:4318/v1/traces`
- Resource attributes: `service.name`, `deployment.environment`
- Lấy mẫu: 100% mặc định (qua biến môi trường TRACING_SAMPLING_PROBABILITY)
- Tempo retention: 24h (compactor)
- Tự động trace: giữa dịch vụ (HTTP/Feign), Kafka producer/consumer (W3C `traceparent`), thao tác cơ sở dữ liệu
- Không cần thay đổi mã: kích hoạt bởi auto-configuration Spring Boot 3.4.3

**Metrics Actuator (/actuator/prometheus):**
- Lộ trên tất cả dịch vụ
- Bảo mật: Chỉ mạng Docker nội bộ (không qua API Gateway)

## Tệp Cấu Hình

**Root pom.xml:**
- Packaging: pom
- 11 module
- Spring Cloud BOM: 2024.0.1
- JJWT: 0.12.6 (api, impl, jackson)

**Phiên bản phụ thuộc chính:**
- Spring Boot: 3.4.3 (bao gồm Micrometer Tracing, Spring Cloud Sleuth)
- Spring Cloud: 2024.0.1
- Spring Kafka: (qua Boot)
- JJWT: 0.12.6
- Stripe Java SDK: mới nhất
- SpringDoc OpenAPI: 2.8.4
- PostgreSQL Driver: 42.x
- Lombok: 1.18.x (quản lý BOM)
- Micrometer Tracing: (tự động bao gồm qua Spring Boot 3.4.3)
- OpenTelemetry SDK: (qua cầu nối Micrometer)
- OpenTelemetry OTLP Exporter: 1.43.x (qua auto-config Spring Boot)

## Build & Triển Khai

```bash
# Build tất cả module
mvn clean install

# Build module cụ thể
mvn -pl auth-service clean package

# Docker Compose (tất cả dịch vụ + hạ tầng)
docker-compose up --build

# Build Docker từng dịch vụ
docker build -t auth-service ./auth-service
docker build -t movie-service ./movie-service
# ... v.v.
```

## Metrics & Giám Sát

**Bộ đếm Nghiệp vụ:**
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

**Metrics HTTP (Micrometer):**
- http.server.requests (timer)
- http.client.requests (timer, Feign)
- Thống kê jdbc connection pool
- jvm.memory, jvm.gc, jvm.threads

## Mẫu Giao Tiếp Giữa Dịch Vụ

| Mẫu | Ví dụ | Công nghệ |
|------|-------|-----------|
| Dịch vụ-Dịch vụ (Đồng bộ) | booking-service → movie-service (thông tin ghế) | Feign HTTP |
| Hướng sự kiện (Bất đồng bộ) | payment-service → booking-service (PaymentCompletedEvent) | Kafka |
| Chia sẻ cấu hình | tất cả dịch vụ ← config-server (JWT secret) | Spring Cloud Config |
| Khám phá dịch vụ | tất cả dịch vụ ← eureka-server (định tuyến động) | Eureka |
| Xác thực Token | dịch vụ downstream xác thực JWT | jwt-auth-autoconfigure |

## Cách Ly Dữ Liệu

**Cơ sở dữ liệu PostgreSQL riêng cho từng dịch vụ:**
- auth-service: testdb (7 bảng: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens)
- movie-service: moviedb (7 bảng: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 bảng: bookings, booking_seats)
- payment-service: paymentdb (1 bảng: payments)
- notification-service: notificationdb (1 bảng: notifications với userId, title, message, notificationType, isRead, createdAt)
- audit-service: auditdb (1 bảng: audit_logs với userId, action, entityType, entityId, beforeState, afterState, ipAddress, userAgent, timestamp)

**Tài nguyên dùng chung:**
- Cụm PostgreSQL (tất cả cơ sở dữ liệu trên cùng instance)
- Redis (tất cả dịch vụ chia sẻ danh sách đen token, khóa, thông báo)
- Kafka (tất cả topic, partition/replica có thể cấu hình)
