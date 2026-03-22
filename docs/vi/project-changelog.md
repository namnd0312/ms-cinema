# Nhật Ký Thay Đổi Dự Án

**Dự án:** ms-cinema
**Cập nhật:** 15 tháng 3, 2026

## Phiên bản 0.0.1-SNAPSHOT

### [Chưa phát hành]

#### Ghi Nhật Ký Kiểm Toán Tập Trung (v0.0.1) — 22 tháng 3, 2026
- **Tính năng:** Dịch vụ audit-service độc lập với Kafka consumer, ghi nhật ký kiểm toán chi tiết vào PostgreSQL auditdb
  - Theo dõi tất cả hành động: LOGIN, REGISTER, LOGOUT, CREATE, UPDATE, DELETE, RESERVE, CANCEL, CONFIRM_PAYMENT, CREATE_PAYMENT_INTENT, CHANGE_PASSWORD
  - Lưu trữ: userId, action, entityType, entityId, beforeState (JSONB), afterState (JSONB), ipAddress, userAgent, timestamp
  - Admin API: GET /api/audit/logs (lọc userId, action, entityType, dateRange), GET /api/audit/logs/{id}
- **Triển khai Backend:**
  - Module audit-service (cổng 8086) với controller AuditLogController
  - Service AuditLogService (CRUD, truy vấn với filter), AuditEventListener (Kafka consumer)
  - Entity JPA AuditLog với JSONB beforeState/afterState cho lưu trữ uốn dẻo
  - Topic Kafka audit-events với listener tiêu thụ AuditEvent từ tất cả dịch vụ
  - Bảo mật: Tất cả endpoints yêu cầu ROLE_ADMIN (@PreAuthorize("hasRole('ROLE_ADMIN')"))
  - Chỉ mục: (userId, timestamp DESC), (action, timestamp DESC), (entityType, entityId) cho truy vấn nhanh
- **Kafka Events:**
  - Record AuditEvent (userId, action, entityType, entityId, beforeState, afterState, ipAddress, userAgent, timestamp)
  - Enum AuditAction [LOGIN, REGISTER, LOGOUT, CREATE, UPDATE, DELETE, RESERVE, CANCEL, CONFIRM_PAYMENT, CREATE_PAYMENT_INTENT, CHANGE_PASSWORD]
  - Topic audit-events: Tiêu thụ bởi audit-service
- **AOP @Auditable:**
  - Annotation @Auditable(action="...", entityType="...") trên controller/service methods
  - Aspect tự động bắt các phương thức được đánh dấu, trích xuất entityId, phát AuditEvent
  - Áp dụng trên: auth (login/register/logout/change-password), movie (CRUD), booking (reserve/cancel), payment (createPaymentIntent)
- **Pattern After-Commit:**
  - @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  - Chỉ phát AuditEvent đến Kafka sau khi DB transaction commit thành công
  - Ngăn nhật ký được ghi nếu DB rollback
- **Schema Cơ Sở Dữ Liệu:**
  - Bảng audit_logs: id (PK), userId (FK), action (ENUM), entityType, entityId, beforeState (JSONB), afterState (JSONB), ipAddress, userAgent, timestamp
  - Chỉ mục: (userId, timestamp DESC), (action, timestamp DESC), (entityType, entityId)
- **Cấu hình:**
  - Topic audit-events trong Kafka config
  - auth-service, movie-service, booking-service, payment-service được trang bị @Auditable annotation
  - Gateway route /api/audit/** → audit-service
  - Config-server profile cho audit-service
- **Xử lý Lỗi:**
  - Kafka: 3 lần thử lại, exponential backoff (1s→2s→4s giới hạn 10s), DLT cho thất bại
  - Serialization JSONB: Jackson ObjectMapper cho beforeState/afterState
- **DTOs:** AuditLogDto, AuditLogFilterRequest
- **Kiểm Thử:**
  - Integration tests: Luồng audit (action → AuditEvent → Kafka → audit-service → DB)
  - Unit tests: AuditEventListener, query filter, RBAC admin-only
  - Query tests: Lọc userId, action, entityType, dateRange

#### Đăng Nhập Google OAuth2 (v0.0.1) — 16 tháng 3, 2026
- **Tính năng:** Xác thực Google OAuth2 với Spring Security OAuth2 Client
  - Endpoint ủy quyền OAuth2: GET /oauth2/authorization/google
  - Xử lý callback OAuth2: GET /login/oauth2/code/google (với authorization code)
  - Tự tạo người dùng khi đăng nhập OAuth2 lần đầu (password=NULL, active=true, ROLE_USER)
  - Tự liên kết người dùng hiện có theo email khi Google email_verified=true
  - Xử lý race condition đăng nhập đồng thời qua DataIntegrityViolationException catch
- **Triển khai Backend:**
  - Entity JPA UserOAuthProvider với ràng buộc unique (provider_name+provider_user_id, user_id+provider_name)
  - UserOAuthProviderRepository: findByProviderNameAndProviderUserId(), existsByUserIdAndProviderName()
  - OAuth2AuthenticationSuccessHandler: Xử lý đăng nhập OAuth2 thành công, sinh JWT+refresh token, chuyển hướng với query params
  - OAuth2UserLinkingService & Impl: Tra cứu theo liên kết provider → tra cứu theo email (nếu đã xác minh) → tạo người dùng mới
  - SecurityConfig cập nhật: sessionCreationPolicy=IF_REQUIRED (cho phép OAuth2 state param), oauth2Login(successHandler)
  - API Gateway routes: /oauth2/authorization/**, /login/oauth2/code/** → auth-service
- **Triển khai Frontend:**
  - OAuth2CallbackComponent: Trích xuất token+refreshToken từ URL, lưu qua AuthService, xóa lịch sử URL (bảo mật), điều hướng đến /movies
  - AuthService.handleOAuth2Callback(): Lưu tokens, khởi tạo trạng thái xác thực
  - Component đăng nhập: Nút "Sign in with Google" chuyển hướng đến /oauth2/authorization/google
  - Route: /oauth2-callback (xử lý callback OAuth2)
- **Schema Cơ Sở Dữ Liệu (auth-service):**
  - Bảng user_oauth_providers: id, user_id FK, provider_name (50 ký tự), provider_user_id, provider_email, linked_at
  - Ràng buộc unique: (provider_name, provider_user_id), (user_id, provider_name)
  - PrePersist: Tự động đặt linked_at thành thời gian UTC hiện tại
- **Bảo Mật & Ủy Quyền:**
  - Người dùng chỉ OAuth có password=NULL; POST /api/auth/change-password bị chặn cho những người dùng này (kiểm tra guard)
  - Xác minh email bởi nhà cung cấp: Tự liên kết chỉ khi email_verified=true từ Google
  - Tokens được sinh giống hệt đăng nhập truyền thống (cùng JWT claims: sub, roles, userId)
  - OAuth state parameter và bảo vệ CSRF được xử lý bởi Spring Security
- **Cấu hình:**
  - namnd.app.oauth2CallbackUrl: URL callback OAuth2 Frontend (ví dụ: http://localhost:4200/oauth2-callback)
  - Thông tin Google OAuth2: Cấu hình qua application.yml spring.security.oauth2.client.registration
- **Xử lý lỗi:**
  - Thiếu email: Chuyển hướng đến callback với ?error=no_email
  - Đăng nhập đồng thời lần đầu: Bắt DataIntegrityViolationException, truy vấn lại liên kết provider
  - Lỗi callback Frontend: Trì hoãn 2 giây trước khi chuyển hướng đến /auth/login
- **DTOs:** Không có DTOs mới; tái sử dụng mẫu JwtResponseDto qua query params
- **Kiểm thử:**
  - Integration tests: Luồng OAuth2 với Google mocked response
  - Unit tests: OAuth2UserLinkingService (tạo mới, tự liên kết, kịch bản đồng thời)
  - Frontend: OAuth2CallbackComponent trích xuất token, điều hướng

#### Xác Thực Lịch Sử Mật Khẩu (v0.0.1) — 15 tháng 3, 2026
- **Endpoint mới:** POST `/api/auth/change-password` - Đổi mật khẩu người dùng đã xác thực
  - Request body: { currentPassword, newPassword, confirmPassword }
  - Xác thực mật khẩu hiện tại, xác nhận mật khẩu mới khớp, kiểm tra lịch sử mật khẩu
  - Trả về 200 khi thành công, 400 cho lỗi xác thực, 401 cho lỗi xác thực danh tính
- **Cơ sở dữ liệu:** Bảng `password_history` (id, user_id, password_hash, created_at)
- **Triển khai Backend:**
  - Entity JPA PasswordHistory với FK người dùng và lưu trữ BCrypt hash
  - PasswordHistoryService: Thao tác CRUD, xác thực mật khẩu gần đây (3 gần nhất)
  - Endpoint controller POST /api/auth/change-password với @PreAuthorize("isAuthenticated()")
  - POST /api/auth/reset-password nâng cao: Xác thực mật khẩu reset so với 3 hash gần đây
  - Luồng đăng ký (POST /api/auth/register): Seed mật khẩu ban đầu vào lịch sử khi tạo người dùng
- **Triển khai Frontend:**
  - ChangePasswordComponent mới tại route /profile/change-password
  - Reactive form với các trường currentPassword, newPassword, confirmPassword
  - Tích hợp nút "Change Password" trên ProfileComponent
  - Xác thực thời gian thực và hiển thị lỗi từ backend API
- **Bảo Mật & Ủy Quyền:**
  - POST /api/auth/change-password yêu cầu Bearer JWT token
  - Mật khẩu hiện tại được xác minh qua so sánh BCrypt
  - Mật khẩu mới được ngăn tái sử dụng 3 hash gần nhất
  - Tất cả thay đổi mật khẩu được ghi log với timestamp trong bảng lịch sử
- **DTOs đã thêm:**
  - ChangePasswordRequest (currentPassword, newPassword, confirmPassword)
  - ChangePasswordResponse (thông báo thành công hoặc chi tiết lỗi)

#### Tính Năng Đã Thêm
- **Đánh Giá Phim (v0.0.1)** — 12 tháng 3, 2026
  - POST `/api/movies/{movieId}/ratings` - Tạo/cập nhật đánh giá (1-5 sao)
  - GET `/api/movies/{movieId}/ratings` - Lấy tóm tắt đánh giá (trung bình, tổng số, đánh giá của người dùng đã xác thực)
  - Cơ sở dữ liệu: Bảng `movie_ratings` (movieId, userId, rating, createdAt, updatedAt)
  - Entity Spring Data JPA với khóa composite (movieId, userId)

- **Bình Luận Phim (v0.0.1)** — 12 tháng 3, 2026
  - POST `/api/movies/{movieId}/comments` - Tạo bình luận với nội dung văn bản
  - GET `/api/movies/{movieId}/comments` - Danh sách bình luận phân trang (20 mỗi trang, sắp xếp theo createdAt DESC)
  - PUT `/api/comments/{commentId}` - Cập nhật bình luận của mình (chỉ chủ sở hữu)
  - DELETE `/api/comments/{commentId}` - Xóa mềm bình luận (chủ sở hữu hoặc ADMIN)
  - Cơ sở dữ liệu: Bảng `movie_comments` (movieId, userId, content, status [ACTIVE/DELETED], createdAt, updatedAt)
  - Xóa mềm qua cột `status` (không xóa cứng)

- **Phản Ứng Bình Luận (v0.0.1)** — 12 tháng 3, 2026
  - POST `/api/comments/{commentId}/reactions` - Bật/tắt thích/không thích trên bình luận
  - DELETE `/api/comments/{commentId}/reactions` - Xóa phản ứng
  - Cơ sở dữ liệu: Bảng `comment_reactions` (commentId, userId, reactionType [LIKE/DISLIKE], createdAt)
  - Khóa composite (commentId, userId) - một phản ứng mỗi người dùng mỗi bình luận

#### Thay Đổi API
- **Endpoints mới (tổng 8):**
  - 2 endpoint đánh giá (POST, GET)
  - 4 endpoint bình luận (POST, GET, PUT, DELETE)
  - 2 endpoint phản ứng (POST, DELETE)

- **API Gateway:**
  - Thêm route `/api/comments/**` → movie-service
  - Routes `/api/movies/{movieId}/comments*` đến MovieCommentController
  - Routes `/api/comments/{commentId}*` đến CommentReactionController

#### Schema Cơ Sở Dữ Liệu
- **movie-service (moviedb):**
  - `movie_ratings` — pk: (movie_id, user_id); cột: rating, created_at, updated_at
  - `movie_comments` — pk: id; cột: movie_id, user_id, content, status, created_at, updated_at
  - `comment_reactions` — pk: (comment_id, user_id); cột: reaction_type, created_at
  - Tự tạo bởi Hibernate ddl-auto=update

#### Bảo Mật & Ủy Quyền
- **MovieRatingController:** POST yêu cầu @PreAuthorize("isAuthenticated()"), GET là công khai
- **MovieCommentController:**
  - POST yêu cầu xác thực
  - PUT yêu cầu quyền sở hữu hoặc role admin
  - DELETE yêu cầu quyền sở hữu hoặc role admin
  - GET là công khai
- **CommentReactionController:** Tất cả endpoints yêu cầu xác thực

#### DTOs Đã Thêm
- `CreateRatingRequest` (rating: Integer [1-5])
- `MovieRatingDto` (id, movieId, userId, rating, createdAt, updatedAt)
- `MovieRatingSummaryDto` (averageRating, totalRatings, userRating [nullable])
- `CreateCommentRequest` (content: String)
- `UpdateCommentRequest` (content: String)
- `MovieCommentDto` (id, movieId, userId, content, status, likeCount, dislikeCount, userReaction, createdAt, updatedAt)
- `CommentReactionRequest` (reactionType: LIKE/DISLIKE)
- `CommentReactionDto` (id, commentId, userId, reactionType, createdAt)

#### Tài Liệu
- Cập nhật `/docs/api-documentation.md` — Thêm bảng endpoints đánh giá, bình luận, phản ứng
- Cập nhật `/docs/codebase-summary.md` — Thêm models, services, controllers mới, 7 bảng cho moviedb
- Cập nhật `/docs/system-architecture.md` — Thêm schema cơ sở dữ liệu, tính năng mới, routes api-gateway
- Cập nhật `/docs/project-roadmap.md` — Đánh dấu tính năng Giai đoạn 3 là hoàn thành

#### Kiểm Thử
- Integration tests đầy đủ bao phủ tất cả thao tác CRUD
- Tests ủy quyền (kịch bản chủ sở hữu/admin/công khai)
- Tests phân trang cho danh sách bình luận
- Xác minh xóa mềm

#### Chi Tiết Triển Khai Backend
- Xóa MovieComment là chỉ xóa mềm (cập nhật status thành DELETED, giữ audit trail)
- CommentReaction dựa trên toggle (POST hai lần với cùng loại sẽ xóa, loại khác sẽ cập nhật)
- Tóm tắt đánh giá bao gồm đánh giá của người dùng chỉ cho request đã xác thực
- Bình luận được sắp xếp theo mới nhất trước (DESC theo createdAt)
- Đánh giá dùng ràng buộc UNIQUE(movie_id, user_id) cho mẫu upsert
- Truy vấn repository tùy chỉnh: AVG() cho đánh giá, COUNT() cho số thích/không thích

#### Chi Tiết Triển Khai Frontend
- **StarRatingComponent:** Hiển thị 5 sao với hover/chọn tương tác, chế độ chỉ đọc hoặc chỉnh sửa
- **CommentListComponent:** Danh sách bình luận phân trang với tải thêm hoặc cuộn vô hạn, hiển thị công khai
- **CommentItemComponent:** Thẻ bình luận cá nhân với bộ đếm phản ứng, nút sửa/xóa (nếu là chủ sở hữu/admin)
- **MovieRatingService & MovieCommentService:** HTTP clients xử lý POST/GET/PUT/DELETE với phân trang
- **Sửa Auth Interceptor:** Nay luôn đính kèm JWT token từ storage khi có sẵn; PUBLIC_URLS chỉ kiểm soát thử lại refresh 401 (cho phép truy cập ẩn danh)
- **MovieDetailComponent:** Tích hợp components mới bên dưới chi tiết phim, lazy-load đánh giá/bình luận khi chuyển tab

---

## Hệ Thống Thông Báo Thời Gian Thực (HOÀN THÀNH ✓)

**Ngày phát hành:** 14 tháng 3, 2026

### Tổng Quan

Hệ thống thông báo thời gian thực hoàn chỉnh với streaming Server-Sent Events (SSE) + kiến trúc hướng sự kiện Kafka. Giao hàng xác nhận thanh toán/đặt vé đến frontend theo thời gian thực với lưu trữ PostgreSQL và REST API cho quản lý thông báo.

### Tính Năng Đã Thêm

**notification-service (Cập nhật lớn)**
- Entity JPA mới: Notification (userId, title, message, notificationType, isRead, createdAt)
- REST Controller mới: NotificationRestController với thao tác CRUD + đánh dấu đã đọc
- SSE Controller mới: NotificationSseController (GET /api/notifications/stream?token=JWT)
- Service mới: SseEmitterRegistryService (registry emitter ConcurrentHashMap, thao tác atomic, heartbeat 30s)
- Service mới: InAppNotificationServiceImpl (lưu trữ, phát, đánh dấu đã đọc, broadcast, getUnreadCount)
- Service mới: InAppNotificationEventListener (Kafka consumer cho topic notification.in_app)
- Service mới: NotificationPublisherService (publish InAppNotificationEvent đến Kafka)
- Cơ sở dữ liệu: notificationdb với bảng notifications (chỉ mục userId, createdAt DESC)

**kafka-events (Cập nhật Module)**
- Enum mới: NotificationType [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM]
- Record mới: InAppNotificationEvent (userId, title, message, notificationType)

**booking-service (Cập nhật nhỏ)**
- Service mới: NotificationPublisherService (publish InAppNotificationEvent sau thanh toán thành công/thất bại)
- Publish InAppNotificationEvent → topic notification.in_app

**api-gateway (Sửa lỗi)**
- HttpLoggingFilter: Bỏ qua ContentCachingResponseWrapper cho đường dẫn SSE (/api/notifications/stream)
- Ngăn cạn kiệt thread gateway trên kết nối SSE lâu dài

**Angular Frontend (Components mới)**
- Model mới: notification.model.ts (interfaces Notification, NotificationPage, UnreadCountResponse)
- Service mới: notification-sse.service.ts (EventSource với exponential backoff reconnect: 1s→30s tối đa, 5 lần thử)
- Service mới: notification-api.service.ts (REST calls cho CRUD + đánh dấu đã đọc)
- Component mới: notification-bell.component.ts (matBadge, snackbar alerts, bộ đếm tự tăng)
- Component mới: notification-list.component.ts (danh sách Mat-card, paginator, giao diện tối, viền màu theo loại)
- Routes mới: notifications.routes.ts (route /notifications lazy-loaded)
- Cập nhật Toolbar: notification-bell thêm vào toolbar.component.ts
- Cập nhật App Routes: route /notifications thêm vào app.routes.ts

**Docker/Config (Cập nhật)**
- init-databases.sql: Thêm CREATE DATABASE notificationdb;
- docker-compose.yml: Thêm postgres + biến môi trường cho notification-service

### Endpoints API

| Phương thức | Đường dẫn | Xác thực | Mô tả |
|------------|-----------|----------|-------|
| GET | /api/notifications/stream | JWT (query param) | SSE stream với heartbeat 30s |
| GET | /api/notifications | Bearer JWT | Danh sách phân trang (mặc định page=0&size=20) |
| PATCH | /api/notifications/{id}/read | Bearer JWT | Đánh dấu một thông báo đã đọc |
| PATCH | /api/notifications/read-all | Bearer JWT | Đánh dấu tất cả đã đọc |
| GET | /api/notifications/unread-count | Bearer JWT | Lấy số chưa đọc cho badge |
| POST | /api/notifications/broadcast | Bearer JWT (ADMIN) | Broadcast thử nghiệm admin |

### Sửa Lỗi

1. **Race Condition trong removeEmitter** — computeIfPresent atomic ngăn vấn đề sửa đổi đồng thời
2. **Rủi ro OOM Broadcast** — findDistinctUserIds thay vì findAll() để tránh cạn kiệt bộ nhớ
3. **Rò rỉ Subscription** — notification-bell.component dùng take(1) để tự hủy đăng ký observable
4. **Loại Exception sai** — Đổi SecurityException thành AccessDeniedException cho phản hồi 403
5. **Màu Chuông Thông Báo** — Thêm color: inherit cho badge toolbar (trắng trên primary)
6. **Giao Diện Tối Danh Sách Thông Báo** — Sửa background Mat-card với rgba(0,0,0,0.04) overlay tối

### Chi Tiết Kỹ Thuật

**Cấu hình SSE:**
- Heartbeat: Comment-only `:heartbeat` mỗi 30 giây (overhead tối thiểu, ngăn timeout)
- Timeout Emitter: 30 phút (có thể cấu hình, client tự kết nối lại)
- Consumer Group duy nhất: `notification-service-{instanceId}` đảm bảo tất cả instance nhận broadcast
- Registry an toàn Thread: ConcurrentHashMap với synchronized iterator cho cập nhật emitter

**Kafka Events:**
- Topic: notification.in_app (publish bởi booking-service, consume bởi notification-service)
- Consumer Group: `notification-service-{instanceId}` (duy nhất mỗi instance cho mẫu broadcast)
- Xử lý lỗi: 3 lần thử lại, exponential backoff (1s→2s→4s giới hạn 10s), DLT cho thất bại

**Kết nối lại Frontend:**
- Chiến lược: Exponential backoff bắt đầu từ 1s, giới hạn 30s tối đa, tối đa 5 lần thử
- Kích hoạt: Mất kết nối, server timeout, đóng thủ công
- Không cần hành động người dùng; tự kết nối lại minh bạch

**Cơ sở dữ liệu:**
- Bảng Notifications: id (PK), userId (FK), title, message, notificationType, isRead, createdAt
- Chỉ mục: (userId, createdAt DESC) cho phân trang O(log n)
- Lưu trữ: Triển khai TTL hoặc dọn dẹp theo lịch cho tin nhắn > 90 ngày (tùy chọn)

### Cân Nhắc Bảo Mật

- Xác thực JWT qua query parameter (?token=JWT) là chuẩn cho endpoint SSE (header Authorization không được hỗ trợ bởi EventSource API)
- Xác thực token được thực hiện mỗi request; token hết hạn gây ngắt kết nối SSE
- Enum NotificationType ngăn injection loại thông báo không hợp lệ
- Endpoint admin broadcast yêu cầu ROLE_ADMIN để ủy quyền

### Kiểm Thử

- Unit tests: SSE emitter registry, notification service CRUD
- Integration tests: Luồng sự kiện Kafka (payment → booking → notification)
- Frontend tests: EventSource mock, logic exponential backoff, tăng badge

### Cập Nhật Tài Liệu

- README.md: Cập nhật mô tả notification-service, luồng sự kiện Kafka
- docs/project-overview-pdr.md: Thêm FR-006 Thông báo trong ứng dụng thời gian thực
- docs/codebase-summary.md: Thêm chi tiết notification-service, enum kafka-events, Angular components
- docs/system-architecture.md: Thêm sơ đồ luồng SSE, kiến trúc notification-service, sửa lỗi API Gateway
- docs/api-documentation.md: Thêm tất cả 6 endpoints thông báo với ví dụ
- docs/project-roadmap.md: Đánh dấu thông báo thời gian thực Giai đoạn 3 là HOÀN THÀNH

### Chỉ Số Hiệu Năng

- Overhead Heartbeat SSE: ~1 KB mỗi sự kiện (comment 30 byte + dòng mới)
- Bộ nhớ Emitter Registry: O(n) với n = kết nối SSE đồng thời
- Độ trễ Kafka: <100ms p95 từ sự kiện thanh toán đến giao hàng thông báo
- Truy vấn DB: Phân trang (O(log n) với chỉ mục) + truy vấn đếm (O(1) với trigger)

### Hạn Chế Đã Biết

- EventSource API (native trình duyệt) không hỗ trợ custom headers; JWT truyền qua query param
- Kết nối SSE ngắt khi JWT hết hạn; client phải yêu cầu token mới và kết nối lại
- Giới hạn kết nối đồng thời mỗi instance (có thể cấu hình, ví dụ: 1000 mỗi instance notification-service)
- Danh sách thông báo phân trang 20 mỗi trang; thông báo cũ không tự dọn dẹp

---

## Phát Hành Giai Đoạn 2 (Lịch Sử)

**Giai đoạn 2: Tích hợp Microservice (HOÀN THÀNH)** — Tháng 12 năm 2025 - Tháng 2 năm 2026
- Cấu trúc Maven 10 module với Spring Cloud
- Eureka service discovery, Config Server, API Gateway
- Xác thực JWT (JJWT 0.12.6 HS512)
- movie-service, booking-service, payment-service, notification-service
- Kafka event streaming (PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent)
- Tài liệu OpenAPI 3.0 (Swagger UI, SpringDoc 2.8.4)
- Giám sát Prometheus + dashboards Grafana + ghi log Loki

---

## Tài Liệu Liên Quan

- [Lộ trình dự án](./project-roadmap.md)
- [Tài liệu API](./api-documentation.md)
- [Kiến trúc hệ thống](./system-architecture.md)
- [Tóm tắt codebase](./codebase-summary.md)
