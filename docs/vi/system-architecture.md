# Kiến Trúc Hệ Thống

**Dự án:** ms-cinema
**Phiên bản:** 0.0.1-SNAPSHOT
**Java:** 21 LTS | **Spring Boot:** 3.4.3 | **Spring Cloud:** 2024.0.1

## Tổng Quan Cấp Cao

MS Cinema là nền tảng microservices Spring Boot gồm 6 dịch vụ + 2 thư viện dùng chung dành cho đặt vé xem phim:

```
                        CLIENT (Web/Mobile)
                              │
              ┌───────────────▼────────────────┐
              │   K8s NGINX Ingress            │  (hoặc nginx trong docker-compose)
              │   định tuyến theo đường dẫn    │
              └───────────────┬────────────────┘
                              │
     ┌──────┬──────┬──────────┼──────┬──────┬──────┐
     ▼      ▼      ▼          ▼      ▼      ▼      ▼
   auth   movie  booking    pay   notif  audit  frontend
  :8081  :8082  :8083      :8084  :8085  :8086   :80

Hạ tầng:
- PostgreSQL (auth→testdb, movie→moviedb, booking→bookingdb, payment→paymentdb, notification→notificationdb, audit→auditdb)
- Redis (:6379) - danh sách đen token, khóa, dedup
- Kafka (:9092) - event streaming (5 topic: movie-events, payment-events, notification-events, notification.in_app, audit-events)
- Prometheus (:9090) + Grafana (:3000) + Loki (:3100) - giám sát
- Tempo (:3200) - backend distributed tracing
- OTel Collector (:4317/:4318) - bộ thu OTLP, xuất sang Tempo
```

## Kiến Trúc Module

### Lớp Định Tuyến (K8s Ingress / nginx docker-compose)

Định tuyến theo đường dẫn — không có gateway service riêng:
- `/api/auth/**`, `/api/users/**`, `/oauth2/**`, `/login/oauth2/**` → auth-service:8081
- `/api/movies/**`, `/api/showtimes/**`, `/api/theaters/**` → movie-service:8082
- `/api/bookings/**` → booking-service:8083
- `/api/payments/**` → payment-service:8084
- `/api/notifications/**` → notification-service:8085
- `/api/audit/**` → audit-service:8086
- `/ws/**` → booking-service:8083 (WebSocket upgrade)

**K8s:** `k8s/ingress.yml` | **Docker Compose:** `cinema-frontend/nginx.conf`

### Dịch Vụ Nghiệp Vụ
  - `/api/bookings/**` → booking-service
  - `/api/payments/**` → payment-service
  - `/api/notifications/**` → notification-service (SSE stream, REST CRUD, broadcast)
  - `/api/notifications/stream` → notification-service (endpoint SSE, **ContentCachingResponseWrapper bỏ qua để ngăn cạn kiệt thread**)
  - `/api/audit/**` → audit-service (truy vấn nhật ký kiểm toán, yêu cầu ADMIN)
  - `/ws/**` → Proxy Nginx trực tiếp đến booking-service (endpoint STOMP WebSocket, vượt gateway)
- Tổng hợp tài liệu OpenAPI: `/v3/api-docs`
- Swagger UI: `/swagger-ui.html`
- HttpLoggingFilter: Ghi log yêu cầu với X-Correlation-ID, bỏ qua cache phản hồi cho đường dẫn SSE
- Endpoint Actuator (chỉ nội bộ, không lộ qua gateway)

### Dịch Vụ Nghiệp Vụ (5 module)

**auth-service (:8081)** - Xác thực & quản lý người dùng
- Controller: AuthController, TokenValidationController
- Service: JwtService, UserService, ActivationService, PasswordResetService, BlacklistedTokenService, AccountLockService, PasswordHistoryService, RedisService, OAuth2UserLinkingService
- Bảo mật: Spring Security 6.x với @EnableMethodSecurity, OAuth2 client, pattern SecurityFilterChain
- JWT: JJWT 0.12.6 HS512 (access token 15 phút, refresh 7 ngày, claims roles+userId)
- Danh sách đen Token: Redis với auto-TTL (fail-closed khi gián đoạn)
- Khóa tài khoản: 5 lần thử thất bại → tự động mở khóa sau 15 phút
- Lịch sử mật khẩu: Lưu 3 hash mật khẩu gần nhất cho mỗi người dùng, ngăn tái sử dụng trong quy trình đặt lại & đổi mật khẩu
- OAuth2 Login: Tích hợp Google OAuth2 qua Spring Security (tự động liên kết email_verified)
  - Entity UserOAuthProvider: Lưu trữ liên kết provider (provider_name, provider_user_id, linkedAt)
  - OAuth2AuthenticationSuccessHandler: Tạo JWT + refresh token, chuyển hướng frontend với query params
  - OAuth2UserLinkingService: Tìm/tạo người dùng, tự động liên kết theo email nếu đã xác minh, xử lý race condition
- Sự kiện Email: Phát NotificationRequestedEvent lên Kafka (không gửi SMTP trực tiếp)
- Endpoint: /api/auth/login, /register, /refresh-token, /logout, /forgot-password, /reset-password, /change-password (yêu cầu xác thực), /oauth2/authorization/**, /login/oauth2/code/**
- Cơ sở dữ liệu: testdb (9 bảng: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history, user_oauth_providers)

**movie-service (:8082)** - Danh mục phim, suất chiếu, đánh giá, bình luận, phản hồi
- Controller: MovieController, TheaterController, ShowtimeController, MovieRatingController, MovieCommentController, CommentReactionController
- Model: Movie, Theater, Seat, Showtime, MovieRating, MovieComment, CommentReaction
- Tính năng:
  - Tự động tạo lưới ghế (hàng A-Z) khi tạo rạp
  - Đánh giá sao (1-5): Upsert theo người dùng, tổng hợp với trung bình/tổng/đánh giá người dùng
  - Bình luận phẳng: Phân trang (20/trang), xóa mềm qua cột status (ACTIVE/DELETED), chủ sở hữu/admin có thể cập nhật
  - Phản hồi bình luận: Chuyển đổi theo người dùng (thích/không thích), mỗi người dùng một phản hồi cho mỗi bình luận
  - MovieDto nâng cao: Bao gồm averageRating, totalRatings, commentCount
- Sự kiện phát: MovieCreatedEvent, ShowtimeCreatedEvent → topic movie-events
- Cơ sở dữ liệu: moviedb (7 bảng: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- Bảo mật: /api/comments/** permitAll cho GET (công khai), POST/PUT/DELETE yêu cầu xác thực

**booking-service (:8083)** - Đặt chỗ ghế & vòng đời đặt vé
- Controller: BookingController
- Model: Booking (PENDING→CONFIRMED/CANCELLED/EXPIRED), BookingSeat
- Feign Client: Gọi movie-service để lấy thông tin suất chiếu/ghế
- Kafka Listener: Tiêu thụ PaymentCompletedEvent (CONFIRMED), PaymentFailedEvent (CANCELLED)
- Khóa Redis: Seat:lock:{showtimeId}:{seatId} với TTL 5 phút
- Scheduler: BookingExpiryScheduler (kiểm tra 60 giây) chuyển đổi đặt vé PENDING đã hết hạn
- **Cấu hình WebSocket (MỚI 22 tháng 3, 2026):**
  - WebSocketConfig.java: Spring WebSocket + STOMP, in-memory broker (app:/topic/showtime/*)
  - SeatStatusMessage.java: DTO (showtimeId, seatId, status, userId, action: LOCK/RESERVE/CANCEL)
  - SeatWebSocketPublisher.java: Broadcasts SeatStatusMessage đến endpoint /topic/showtime/{showtimeId}/seats
  - BookingServiceImpl sửa đổi: Gọi publishSeatStatusChange() khi lock/reserve/cancel
  - BookingExpiryScheduler sửa đổi: Phát hành CANCEL khi hết hạn đặt vé
  - **Proxy Nginx:** Endpoint /ws/* định tuyến trực tiếp đến booking-service:8083 với header upgrade WebSocket (Connection: Upgrade, Upgrade: websocket)
  - **Kết nối Frontend:** Endpoint /ws kết nối qua Nginx trực tiếp đến booking-service (độ trễ thấp)
- Cơ sở dữ liệu: bookingdb (2 bảng: bookings, booking_seats)

**payment-service (:8084)** - Xử lý thanh toán Stripe
- Controller: PaymentController
- Model: Payment, PaymentIntent
- Tích hợp Stripe:
  - Tạo PaymentIntent với idempotency key (pay-{bookingId})
  - Endpoint Webhook: POST /api/payments/webhook
  - Xác minh chữ ký (header stripeSig)
  - Dedup stripeEventId (ngăn tấn công phát lại)
- Sự kiện Kafka: Phát PaymentCompletedEvent/PaymentFailedEvent sau DB commit (TransactionalEventListener)
- Cơ sở dữ liệu: paymentdb (1 bảng: payments)

**notification-service (:8085)** - Thông báo email + Thông báo trong ứng dụng SSE thời gian thực
- **Kafka Listener:**
  - Topic notification-events: NotificationRequestedEvent → EmailSenderService → gửi SMTP
  - Topic notification.in_app: InAppNotificationEvent → SseEmitterRegistryService → broadcast đến tất cả SSE client
  - Consumer group: `notification-service-{instanceId}` (duy nhất cho mỗi instance theo mô hình broadcast)
- **REST Controller:**
  - `NotificationSseController`:
    - GET /api/notifications/stream (endpoint SSE, JWT qua query param: ?token=JWT)
    - Trả về: event: InAppNotificationEvent, :heartbeat (comment), Connection: keep-alive, interval 30 giây
  - `NotificationRestController`:
    - GET /api/notifications (phân trang, createdAt giảm dần, yêu cầu xác thực)
    - PATCH /api/notifications/{id}/read (đánh dấu đơn, yêu cầu xác thực)
    - PATCH /api/notifications/read-all (đánh dấu hàng loạt, yêu cầu xác thực)
    - GET /api/notifications/unread-count (trả về {count: N}, yêu cầu xác thực)
    - POST /api/notifications/broadcast (chỉ admin, broadcast thử nghiệm)
- **Model:** JPA entity Notification (id, userId, title, message, notificationType, isRead, createdAt)
- **Service:**
  - SseEmitterRegistryService: Registry dựa trên ConcurrentHashMap, heartbeat mỗi 30 giây, thao tác atomic
  - InAppNotificationServiceImpl: CRUD, đánh dấu đã đọc, broadcast, quản lý emitter
  - EmailSenderService: SMTP (Gmail smtp.gmail.com:587, thông tin xác thực MAIL_USERNAME/MAIL_PASSWORD)
  - NotificationDeduplicationService: Redis key notification:processed:{eventId}, TTL 24 giờ
  - NotificationPublisherService: Phát InAppNotificationEvent lên topic notification.in_app
- **Cấu hình SSE:**
  - Heartbeat: Sự kiện SSE chỉ comment (:heartbeat) mỗi 30 giây (ngăn timeout, chi phí tối thiểu)
  - Timeout emitter: 30 phút (có thể cấu hình, client tự động kết nối lại khi ngắt)
  - Kết nối lại client: Exponential backoff 1s→tối đa 30s, tối đa 5 lần thử
- **Cơ sở dữ liệu (notificationdb):** Bảng notifications (id PK, userId FK, title, message, notificationType ENUM, isRead bool, createdAt timestamp)
  - Index: (userId, createdAt DESC) cho phân trang hiệu quả
- **Xử lý lỗi:**
  - Kafka: 3 lần thử lại, exponential backoff (1s→2s→4s giới hạn 10s), DLT cho lỗi
  - Sửa race condition: computeIfPresent atomic trong removeEmitter ngăn lỗi sửa đổi đồng thời
  - Tối ưu broadcast: findDistinctUserIds thay vì findAll để tránh OOM trên tập dữ liệu lớn
- **Fail-Open:** Redis không khả dụng không chặn gửi email hoặc SSE emit; dedup là tùy chọn

**audit-service (:8086)** - Ghi nhật ký kiểm toán tập trung
- Kafka Listener: Tiêu thụ AuditEvent từ topic audit-events
- Service: Lưu trữ vào PostgreSQL auditdb (bảng audit_logs)
- Controller: REST API GET /api/audit/logs (lọc userId, action, entityType, dateRange), GET /api/audit/logs/{id}
- Bảo mật: Yêu cầu ROLE_ADMIN cho tất cả endpoints
- Cơ sở dữ liệu: auditdb (1 bảng audit_logs với userId, action, entityType, entityId, beforeState JSONB, afterState JSONB, ipAddress, userAgent, timestamp)

### Thư Viện Dùng Chung (2 module)

**jwt-auth-autoconfigure** - Bộ xác thực JWT tái sử dụng cho dịch vụ downstream
- JwtAutoConfiguration: Bean có điều kiện qua @ConditionalOnProperty(jwt.auth.enabled=true)
- JwtAuthProperties: Thuộc tính cấu hình (prefix=jwt.auth)
- JwtTokenValidator: Xác thực chữ ký HS512, hết hạn
- JwtAuthenticationFilter: Trích xuất token, xác thực, thiết lập SecurityContext
- Sử dụng: Dịch vụ downstream thêm dependency này, cấu hình jwt.auth.secret từ config-server, tự động kích hoạt qua SecurityFilterChain

**kafka-events** - Model sự kiện domain dùng chung
- EventEnvelope<T>: Wrapper (eventId UUID, eventType, source service, correlationId, timestamp, payload)
- Các lớp Event: PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent, MovieCreatedEvent, ShowtimeCreatedEvent, NotificationRequestedEvent
- Topic: movie-events, payment-events, notification-events (cấu hình trong docker-compose.yml)

### Frontend (1 module)

**cinema-frontend (Angular 18)** - Giao diện Web
- Cổng: 4200 (dev) → 80 (prod qua Nginx)
- Stack: TypeScript 5.5, Material 18, Stripe.js 8.9, RxJS
- Route lazy-loaded: /auth, /movies, /booking, /payment, /profile (bao gồm /profile/change-password), /admin, /notifications
- Component: ChangePasswordComponent (reactive form với các trường current/new/confirm, chuyển đổi hiển thị, xác thực)
- API proxy: nginx.conf định tuyến /api/* trực tiếp đến từng dịch vụ backend (K8s Ingress trong Kubernetes)
- Nginx SPA fallback cho định tuyến phía client
- Tích hợp đổi mật khẩu: Nút "Change Password" trên ProfileComponent

## Mẫu Luồng Dữ Liệu

### Luồng Xác Thực

#### Đăng Nhập Truyền Thống (Email + Mật khẩu)
```
CLIENT: POST /api/auth/login
        └─► Ingress/nginx (định tuyến đến auth-service)
            └─► auth-service AuthController
                ├─ AccountLockService.isLocked() → 423 nếu bị khóa
                ├─ AuthenticationManager.authenticate() → so khớp mật khẩu BCrypt
                │  └─ UserServiceImpl.loadUserByUsername(email) → DB
                ├─ [BadCredentials] → AccountLockService.loginFailed() (tăng bộ đếm)
                ├─ [Success] → AccountLockService.loginSucceeded() (đặt lại bộ đếm)
                ├─ JwtService.generateTokenLogin(auth) → ký HS512, JTI, roles+userId, 15 phút
                ├─ RefreshTokenService.createRefreshToken() → token 7 ngày, DB
                └─ 200 OK JwtResponseDto(token, refreshToken, id, email, username, roles)
```

#### Đăng Nhập OAuth2 (Google)
```
CLIENT: Nhấn nút "Sign in with Google"
        └─► GET /oauth2/authorization/google
            └─► Ingress/nginx → auth-service (Spring Security OAuth2)
                ├─ Chuyển hướng đến màn hình đồng ý Google
                └─ Người dùng cấp quyền → Google chuyển hướng về callback

CLIENT: [OAuth2 callback với authorization code]
        └─► GET /login/oauth2/code/google?code=...&state=...
            └─► Ingress/nginx → auth-service
                └─► OAuth2AuthenticationSuccessHandler.onAuthenticationSuccess()
                    ├─ Trích xuất OAuth2User attributes (sub, email, name, email_verified)
                    ├─ OAuth2UserLinkingService.processOAuth2User()
                    │  ├─ [1] Kiểm tra liên kết provider hiện có (sub) → trả về user
                    │  ├─ [2] Kiểm tra email khớp + email_verified=true → tự động liên kết
                    │  ├─ [3] Tạo user mới (password=NULL, active=true, ROLE_USER)
                    │  └─ Tạo bản ghi UserOAuthProvider
                    ├─ JwtService.generateTokenFromEmail() → HS512, 15 phút
                    ├─ RefreshTokenService.createRefreshToken() → 7 ngày, DB
                    └─ Chuyển hướng frontend: /oauth2-callback?token={jwt}&refreshToken={refreshToken}

FRONTEND: OAuth2CallbackComponent
         └─► Trích xuất token + refreshToken từ URL
             ├─ Xóa token khỏi lịch sử trình duyệt (bảo mật)
             ├─ AuthService.handleOAuth2Callback() → lưu vào localStorage
             └─ Chuyển hướng đến /movies
```

#### Yêu Cầu Đã Xác Thực Tiếp Theo
```
CLIENT: GET /api/movies, Authorization: Bearer {accessToken}
        └─► Ingress/nginx → auth-service
            └─► JwtAuthenticationFilter.doFilterInternal()
                ├─ Trích xuất Bearer token
                ├─ JwtService.validateJwtToken() → kiểm tra chữ ký+hết hạn+danh sách đen
                ├─ Tải UserDetails từ SecurityContext (roles, userId nhúng trong token)
                └─ Chuyển đến endpoint handler
```

### Luồng Hướng Sự Kiện (Đặt Vé + Thanh Toán)
```
USER: Đặt chỗ ghế → booking-service BookingController.reserve()
      ├─ BookingService.reserveSeats()
      │  ├─ Lấy thông tin suất chiếu/ghế từ movie-service (Feign)
      │  ├─ Lấy khóa Redis (seat:lock:{showtimeId}:{seatId}, TTL 5 phút)
      │  ├─ Tạo Booking (PENDING), bản ghi BookingSeat
      │  ├─ Phát BookingCreatedEvent → Kafka
      │  └─ Trả về xác nhận đặt vé
      └─ 200 OK

USER: Thanh toán qua Stripe → payment-service PaymentController.createPaymentIntent()
      ├─ PaymentService.createPaymentIntent()
      │  ├─ Stripe API: Tạo PaymentIntent (idempotency key: pay-{bookingId})
      │  ├─ Lưu bản ghi Payment (DB, trạng thái INITIATED)
      │  └─ Trả về clientSecret cho frontend Stripe.js
      │
      └─ Client gửi form Stripe
         └─ Stripe POST {baseUrl}/webhook → payment-service
            ├─ PaymentWebhookService.handleWebhookEvent()
            │  ├─ Xác minh chữ ký (header stripeSig)
            │  ├─ Kiểm tra stripeEventId (dedup, ngăn phát lại)
            │  ├─ Cập nhật Payment (trạng thái COMPLETED)
            │  ├─ Phát PaymentCompletedEvent → Kafka payment-events
            │  └─ DB commit kích hoạt TransactionalEventListener
            │
            └─ booking-service Kafka Listener
               ├─ Tiêu thụ PaymentCompletedEvent
               ├─ Chuyển Booking → CONFIRMED
               ├─ Giải phóng khóa Redis (thành công → giữ đặt chỗ)
               └─ Gửi email qua notification-events

TRƯỜNG HỢP LỖI: Thanh toán thất bại
      └─ payment-service phát PaymentFailedEvent
         └─ booking-service listener
            ├─ Chuyển Booking → CANCELLED
            ├─ Giải phóng khóa Redis (thất bại → trả ghế)
            └─ Gửi thông báo thất bại
```

### Luồng Khả Dụng Ghế Thời Gian Thực (WebSocket STOMP - MỚI 22 tháng 3, 2026 - FR-3.1 HOÀN THÀNH)
```
cinema-frontend: Tải Trang Chọn Ghế (Triển Khai FR-3.1)
      ├─ seat-websocket.service.ts: Kết nối /ws qua SockJS+STOMP (proxy Nginx đến booking-service:8083)
      │  └─ Xác thực JWT trong WebSocket handshake (tích hợp SpringSecurity)
      │
      ├─ Đăng ký /topic/showtime/{showtimeId}/seats
      │  └─ Lắng nghe sự kiện SeatStatusMessage (showtimeId, seatId, status, userId, action)
      │
      └─ Người dùng chọn ghế → BookingController.reserve()
         └─ booking-service BookingService.reserveSeats()
            ├─ Lấy khóa Redis (seat:lock:{showtimeId}:{seatId}, TTL 5 phút)
            ├─ Tạo Booking (PENDING), BookingSeat (LOCKED)
            ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP endpoint
            │  └─ Thông báo /topic/showtime/{showtimeId}/seats (action: LOCK, userId, seatId, status)
            └─ Tất cả client kết nối nhận cập nhật LOCK, ghế UI đánh dấu không khả dụng

USER: Thanh toán hoàn tất → payment-service webhook
      ├─ PaymentWebhookService.handlePaymentCompleted()
      ├─ booking-service PaymentCompletedEvent listener
      │  ├─ Chuyển Booking → CONFIRMED
      │  ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP
      │  │  └─ /topic/showtime/{showtimeId}/seats (action: RESERVE, userId, seatId, status)
      │  └─ Tất cả client: ghế đánh dấu RESERVED (màu xám, vĩnh viễn)
      │
      └─ Trường hợp Hết hạn: Đặt vé hết hạn (PENDING quá expiresAt)
         ├─ BookingExpiryScheduler (kiểm tra 60 giây) phát hiện hết hạn
         ├─ Chuyển Booking → EXPIRED, giải phóng khóa Redis
         ├─ SeatWebSocketPublisher.publishSeatStatusChange() → STOMP
         │  └─ /topic/showtime/{showtimeId}/seats (action: CANCEL, userId, seatId, status)
         └─ Tất cả client: màu ghế đặt lại AVAILABLE (xanh lục/xanh dương/hổ phách)

FRONTEND: seat-websocket.service.ts event handlers
      ├─ onSeatStatusChange(message) xử lý SeatStatusMessage đến
      │  ├─ LOCK: Cập nhật seat-grid component (màu người dùng, đánh dấu bận)
      │  ├─ RESERVE: Mờ ghế, hiển thị chỉ báo đặt vé vĩnh viễn
      │  ├─ CANCEL: Đặt lại màu khả dụng (xanh lục/xanh dương/hổ phách)
      │  └─ Cập nhật trạng thái nội bộ, hiển thị lại lưới ghế
      │
      ├─ Logik Kết nối lại: Tự động kết nối lại với exponential backoff
      │  └─ Phát hiện ngắt kết nối → thử lại 1 giây, 2 giây, 4 giây, 8 giây, 16 giây, 30 giây tối đa
      │
      ├─ Định tuyến Nginx (MỚI 22 tháng 3, 2026):**
      │  ├─ /ws/* định tuyến trực tiếp đến booking-service:8083
      │  ├─ Header upgrade WebSocket: Connection: Upgrade, Upgrade: websocket
      │  └─ Độ trễ <100ms (so với 2-3 giây polling; nhanh hơn 100 lần)
      │
      └─ Bảng Đề xuất Ghế Lân cận (nếu chọn không đầy đủ)
         └─ seat-suggestion.service.ts: findBestAdjacentGroup()
            ├─ Thuật toán phía client O(n*m) trên ghế khả dụng
            ├─ Số liệu: gần (khoảng cách Euclidean), căn chỉnh hàng, tính đồng nhất loại
            ├─ Đánh điểm ghế: cùng hàng tối ưu, phù hợp loại PREMIUM/VIP, khoảng cách gần nhất
            └─ seat-suggestion-panel.component.ts hiển thị đề xuất hàng đầu + nút chấp nhận/bỏ qua
```

### Luồng Thông Báo (Email + SSE Thời Gian Thực)
```
==== THÔNG BÁO EMAIL (Đồng bộ) ====
auth-service: Người dùng đăng ký
      └─► ActivationService.createActivationToken()
          ├─ Tạo UUID token (hết hạn 24 giờ)
          ├─ Phát NotificationRequestedEvent(email, subject, body, type=ACTIVATION)
          │  └─ KafkaTemplate.send("notification-events", event)
          └─ Lưu ActivationToken vào DB

notification-service: Kafka Consumer (topic notification-events)
      ├─ KafkaListener.handleNotificationEvent(event)
      │  ├─ NotificationDeduplicationService: Kiểm tra Redis (notification:processed:{eventId})
      │  ├─ [Trùng lặp] Bỏ qua → ngăn gửi lại
      │  ├─ [Mới] EmailSenderService.sendEmail()
      │  │  └─ Spring Mail JavaMailSender → Gmail SMTP
      │  ├─ Lưu bản ghi Notification (DB, trạng thái SENT)
      │  ├─ Đặt Redis dedup key (TTL 24 giờ)
      │  └─ Commit Kafka offset
      │
      └─ Xử lý lỗi: Nếu exception
         └─ Kafka error handler
            ├─ Thử lại lần 1: delay 1 giây
            ├─ Thử lại lần 2: delay 2 giây
            ├─ Thử lại lần 3: delay 4 giây
            └─ Thất bại: Gửi đến DLT (notification-events.DLT)

==== THÔNG BÁO TRONG ỨNG DỤNG (SSE Thời Gian Thực) ====
payment-service: Thanh toán hoàn tất (webhook)
      ├─ PaymentWebhookService.handlePaymentCompleted()
      │  ├─ Cập nhật trạng thái Payment → COMPLETED
      │  └─ TransactionalEventListener: Phát PaymentCompletedEvent
      │
      └─ booking-service: Kafka Listener (payment-events)
         ├─ Tiêu thụ PaymentCompletedEvent
         ├─ Chuyển Booking → CONFIRMED
         ├─ NotificationPublisherService.publishInAppNotification()
         │  └─ KafkaTemplate.send("notification.in_app", InAppNotificationEvent)
         └─ Lưu bản ghi Notification (DB, isRead=false)

notification-service: Kafka Consumer (topic notification.in_app)
      ├─ Consumer group duy nhất cho mỗi instance (notification-service-{instanceId})
      │  └─ Đảm bảo tất cả SSE client kết nối nhận broadcast
      │
      ├─ KafkaListener.handleInAppNotification(event)
      │  ├─ Lưu bản ghi Notification (DB, trạng thái DELIVERED)
      │  ├─ Cho mỗi userId trong event:
      │  │  └─ SseEmitterService.sendToUser(userId, notificationEvent)
      │  │     └─ Emit SSE event đến tất cả client kết nối cho userId này
      │  └─ Commit Kafka offset
      │
      └─ Xử lý lỗi: Suy giảm nhẹ nhàng (Redis gián đoạn không chặn SSE emit)

cinema-frontend: Kết Nối SSE Thời Gian Thực
      ├─ NotificationSseService.connect(jwt)
      │  ├─ EventSource đến /api/notifications/stream?token={jwt}
      │  ├─ Heartbeat mỗi 30 giây (duy trì kết nối)
      │  └─ Kết nối lại exponential backoff khi ngắt kết nối
      │
      ├─ Lắng nghe: InAppNotificationEvent (loại message SSE)
      │  ├─ NotificationBellComponent.onNotification()
      │  │  ├─ Tăng số badge chưa đọc
      │  │  └─ Hiển thị snackbar toast
      │  └─ NotificationListComponent: Tự động làm mới nếu đang mở
      │
      ├─ Lắng nghe: Heartbeat (SSE chỉ comment, không xử lý)
      │  └─ Đặt lại timeout kết nối, chỉ thị keep-alive
      │
      └─ Hành động người dùng:
         ├─ Nhấn chuông → mở NotificationListComponent
         ├─ GET /api/notifications (phân trang, sắp xếp theo createdAt giảm dần)
         ├─ Nhấn thông báo → PATCH /api/notifications/{id}/read (đánh dấu đã đọc)
         └─ Cập nhật badge: GET /api/notifications/unread-count
```

## Lưu Trữ Dữ Liệu

**Cơ sở dữ liệu riêng cho từng dịch vụ (PostgreSQL 16):**
- auth-service: testdb (9 bảng: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history, user_oauth_providers)
- movie-service: moviedb (7 bảng: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions)
- booking-service: bookingdb (2 bảng: bookings, booking_seats)
- payment-service: paymentdb (1 bảng: payments)
- notification-service: notificationdb (1 bảng: notifications với các cột userId, title, message, notificationType, isRead, createdAt; index (userId, createdAt DESC))
- audit-service: auditdb (1 bảng: audit_logs với userId, action, entityType, entityId, beforeState JSONB, afterState JSONB, ipAddress, userAgent, timestamp; index (userId, timestamp DESC), (action, timestamp DESC))

**Tài nguyên dùng chung:**
- Cụm PostgreSQL (cùng instance, cơ sở dữ liệu khác nhau)
- Redis: danh sách đen token, khóa ghế, dedup thông báo
- Kafka: topic sự kiện (replication factor 3, partition 1)

## Mô Hình Bảo Mật

**Xác thực:** JWT (JJWT 0.12.6) ký đối xứng HS512
- Access token: 15 phút (900000 ms)
- Refresh token: 7 ngày (604800000 ms), xoay vòng mỗi lần sử dụng
- Token claims: sub (email), roles, userId, iat, exp, jti (ID duy nhất)

**Phân quyền:** Spring Security @PreAuthorize cấp phương thức
- Ví dụ: `@PreAuthorize("hasRole('ROLE_ADMIN')")`

**Thu hồi Token:** Danh sách đen Redis dựa trên JTI
- Khi đăng xuất: Trích xuất JTI, lưu trong Redis với thời hạn = thời gian hết hạn token
- Khi yêu cầu: JwtAuthenticationFilter kiểm tra danh sách đen (fail-closed)

**Khóa tài khoản:** Sau 5 lần đăng nhập thất bại
- Bộ đếm lưu trong User.failedAttempts
- Thời gian khóa lưu trong User.lockTime
- Tự động mở khóa sau 15 phút (có thể cấu hình namnd.app.lockDurationMs)

**Mã hóa mật khẩu:** BCrypt (Spring Security PasswordEncoder)

## Khả Năng Quan Sát

**Prometheus (:9090)**
- Tần suất scrape: 15 giây
- Lưu trữ: 7 ngày
- Mục tiêu scrape: /actuator/prometheus trên tất cả 8 dịch vụ

**Grafana (:3000)**
- 2 dashboard dựng sẵn:
  - JVM Micrometer (bộ nhớ, GC, thread, CPU)
  - Spring Boot HTTP Overview (tốc độ request, độ trễ, lỗi, DB pool, bộ đếm nghiệp vụ)
- Bộ đếm nghiệp vụ: auth.login/logout/register, booking.created/confirmed/cancelled, payment.initiated/completed/failed
- Datasource Tempo được cung cấp với `tracesToLogsV2` (Loki) + `tracesToMetrics` (Prometheus) để tương quan
- Service Graph (đồ thị node) tự động hiển thị các kết nối liên dịch vụ

**Loki (:3100)**
- Tổng hợp log, lưu trữ 7 ngày
- Nhãn: job, instance, service (tên dịch vụ)
- Tự động chèn traceId/spanId qua MDC + LogstashEncoder (hiển thị trong truy vấn log)
- Grafana `tracesToLogsV2` join nhãn Loki `service` với thuộc tính span Tempo `service.name`

**Tempo (:3200) + OTel Collector (:4317/:4318)**
- Pipeline distributed tracing: Spring Boot apps → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP → OpenTelemetry Collector (contrib) → Grafana Tempo
- App OTLP endpoint: `http://otel-collector:4318/v1/traces` (ghi đè qua `OTEL_COLLECTOR_HOST`)
- Collector nhận trên 4317/gRPC + 4318/HTTP, gửi sang Tempo qua 4317/gRPC
- Tempo storage: local FS, retention 24h (compactor), PVC 5Gi trong k8s
- Lấy mẫu: 100% (có thể cấu hình qua biến môi trường `TRACING_SAMPLING_PROBABILITY`)
- Trace tất cả yêu cầu giữa dịch vụ, sự kiện Kafka (header W3C `traceparent`), gọi cơ sở dữ liệu
- traceId/spanId tự động chèn vào log qua MDC để tương quan với Loki
- Không cần thay đổi mã: tự động cấu hình bởi Spring Boot 3.4.3
- Docker images cố định: `grafana/tempo:2.6.0`, `otel/opentelemetry-collector-contrib:0.115.1`

## Tổng Hợp Stack Công Nghệ

| Thành phần | Phiên bản | Mục đích |
|------------|-----------|----------|
| Java | 21 LTS | Runtime |
| Spring Boot | 3.4.3 | Framework |
| Spring Cloud | 2024.0.1 | Microservices (Eureka, Config, Gateway) |
| Spring Security | 6.x | Xác thực/Phân quyền |
| JJWT | 0.12.6 | Xử lý JWT |
| Spring Kafka | (qua Boot) | Event streaming |
| Spring Mail | (qua Boot) | Tích hợp SMTP |
| Spring Data JPA | (qua Boot) | ORM |
| PostgreSQL | 16 | Cơ sở dữ liệu quan hệ |
| Redis | 7 | Cache, khóa, dedup |
| Kafka | 3.7 KRaft | Message broker |
| Stripe SDK | mới nhất | Xử lý thanh toán |
| Micrometer Tracing | (qua Boot) | Cầu nối OpenTelemetry cho distributed tracing |
| OpenTelemetry OTLP Exporter | 1.43.x (qua Boot) | Bộ xuất OTLP/HTTP sang OTel Collector |
| OpenTelemetry Collector (contrib) | 0.115.1 | Nhận/xử lý/xuất OTLP |
| Grafana Tempo | 2.6.0 | Backend lưu trữ & truy vấn trace |
| SpringDoc OpenAPI | 2.8.4 | Tài liệu API |
| Lombok | 1.18.x | Giảm mã boilerplate |

## Triển Khai

**Docker Compose Stack:**
```bash
docker-compose up --build
```

Khởi động:
- PostgreSQL (5432)
- Kafka (9092, 3 broker)
- Redis (6379)
- Tất cả 8 dịch vụ + Prometheus + Grafana + Loki

**Lưu ý cho Production:**
- Cơ sở dữ liệu: Chuyển từ riêng cho từng dịch vụ sang schema dùng chung với row-level security
- Secret: Sử dụng vault bên ngoài (HashiCorp Vault, AWS Secrets Manager)
- Replica: Mở rộng dịch vụ qua Kubernetes, khám phá dịch vụ qua Eureka
- Cân bằng tải: API Gateway có thể chạy nhiều instance sau load balancer bên ngoài
- Giám sát: Thêm quy tắc cảnh báo Prometheus, tích hợp PagerDuty
- Logging: Thu thập log vào Loki, truy vấn qua Grafana
