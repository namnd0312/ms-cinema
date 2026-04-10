# Tổng Quan Dự Án & Yêu Cầu Phát Triển Sản Phẩm

**Dự án:** MS Cinema
**Phiên bản:** 0.0.1-SNAPSHOT
**Nhóm:** com.namnd
**Trạng thái:** Đang phát triển — Microservices sẵn sàng Kubernetes
**Cập nhật lần cuối:** 10 tháng 4 năm 2026

## Tóm Tắt

MS Cinema là một **nền tảng microservices Spring Boot gồm 6 dịch vụ + 2 thư viện dùng chung** dành cho đặt vé xem phim với kiến trúc hướng sự kiện, xác thực JWT, thanh toán Stripe với đối soát hàng ngày, ghi nhật ký kiểm toán toàn diện, và khả năng quan sát. Các dịch vụ được triển khai qua K8s Ingress (không có API Gateway) với khám phá dịch vụ qua K8s DNS / URI tĩnh. Bao gồm giao diện Angular 18.

**Đặc điểm chính:**
- **Định tuyến:** K8s NGINX Ingress (dựa trên đường dẫn) cho K8s; nginx docker-compose cho dev cục bộ — không có dịch vụ API Gateway riêng
- **Auth-service** (cổng 8081): Vòng đời xác thực JWT, kích hoạt email, khóa tài khoản (5 lần thử/15 phút), xoay vòng token, @Auditable integration, Google OAuth2 login, thay đổi mật khẩu với xác thực lịch sử
- **Movie-service** (cổng 8082): Phim, rạp, suất chiếu; tự động tạo lưới ghế (hàng A-Z); đánh giá sao (1-5), bình luận phân trang với xóa mềm, phản hồi bình luận (thích/không thích)
- **Booking-service** (cổng 8083): Đặt chỗ ghế với khóa Redis (TTL 5 phút), các trạng thái vòng đời (PENDING→CONFIRMED/CANCELLED/EXPIRED), @Auditable on operations, khả năng sẵn có ghế thời gian thực WebSocket (STOMP /ws/booking, độ trễ <100ms)
- **Payment-service** (cổng 8084): Tích hợp Stripe, payment intent idempotent, xác minh webhook, Spring Batch đối soát hàng ngày (2 AM cron), REST API admin, @Auditable on payments
- **Notification-service** (cổng 8085): Kafka consumer, gửi email SMTP, SSE thời gian thực, Redis dedup (TTL 24 giờ)
- **Audit-service** (cổng 8086): Kafka consumer, ghi nhật ký kiểm toán đầy đủ (login, register, logout, các thao tác CRUD), Admin API truy vấn
- **Module kafka-events:** Các sự kiện domain dùng chung (PaymentCompletedEvent, BookingCreatedEvent, AuditEvent)
- **jwt-auth-autoconfigure:** Bộ xác thực JWT tái sử dụng cho tất cả dịch vụ (JJWT 0.12.6, HS512)
- **Khám phá dịch vụ:** K8s DNS (URI liên dịch vụ: auth-service:8081, v.v.) hoặc tên máy chủ tĩnh trong docker-compose
- **Quản lý cấu hình:** Biến môi trường cho bí mật, không có Config Server tập trung
- **Kafka topics:** payment-events, movie-events, notification-events, notification.in_app, audit-events (3 lần thử lại, exponential backoff, DLT, giữ nhật ký kiểm toán 90 ngày)
- Redis cho danh sách đen token, khóa đặt chỗ, dedup thông báo
- PostgreSQL riêng cho từng dịch vụ (auth→testdb, movie→moviedb, booking→bookingdb, payment→paymentdb, notification→notificationdb, audit→auditdb)
- Bộ giám sát Prometheus (9090) + Grafana (3000) + Loki 3.0 (3100) + Zipkin (9411)

## Yêu Cầu Chức Năng

### Xác Thực (FR-001)
- **Đăng nhập:** Nhận email/mật khẩu, xác thực thông tin đăng nhập, trả về JWT token
  - Nhận JSON payload với email, password
  - Xác thực qua Spring AuthenticationManager
  - Tạo access token ký HS512 (hết hạn 15 phút) với JTI duy nhất
  - Tạo refresh token (hết hạn 7 ngày)
  - Trả về cả hai token + thông tin người dùng trong JwtResponseDto

- **Đăng ký:** Nhận dữ liệu đăng ký, tạo người dùng với vai trò
  - Nhận JSON với username, email (bắt buộc), password, fullName, mảng roles
  - Xác thực tính duy nhất của email (username không bắt buộc duy nhất)
  - Mã hóa mật khẩu qua BCrypt
  - Tạo vai trò mới nếu chưa có, gán vai trò hiện có theo ID
  - Lưu trữ thực thể User với liên kết vai trò

- **Làm mới Token:** Nhận refresh token, trả về cặp token mới
  - Nhận JSON với refreshToken
  - Xác thực refresh token tồn tại và chưa hết hạn
  - Tạo access token mới với JTI mới
  - Xoay vòng refresh token (xóa cũ, tạo mới)
  - Trả về cặp token mới trong TokenRefreshResponseDto

- **Đặt lại mật khẩu:** Quy trình đặt lại mật khẩu qua email sử dụng Kafka
  - Quên mật khẩu: Nhận email, tạo token đặt lại 24 giờ, phát sự kiện Kafka
  - Đặt lại mật khẩu: Nhận token đặt lại + mật khẩu mới, xác thực token với 3 hash mật khẩu gần nhất, cập nhật mật khẩu
  - notification-service tiêu thụ sự kiện, gửi email SMTP
  - Bảo mật: Trả về thông báo chung bất kể email có tồn tại hay không

- **Đổi mật khẩu:** Cho phép người dùng đã xác thực đổi mật khẩu với xác thực
  - POST /api/auth/change-password (yêu cầu Bearer JWT token)
  - Xác thực mật khẩu hiện tại khớp với hash đã lưu
  - Kiểm tra mật khẩu mới không nằm trong 3 mục lịch sử mật khẩu gần nhất
  - Cập nhật mật khẩu và ghi vào bảng lịch sử mật khẩu
  - Trả về thành công/thất bại với thông báo lỗi mô tả

- **Đăng xuất:** Đưa token vào danh sách đen và xóa refresh token
  - Nhận Authorization header với access token
  - Trích xuất và đưa JTI vào danh sách đen với ngày hết hạn
  - Xóa refresh token của người dùng khỏi cơ sở dữ liệu
  - Dọn dẹp theo lịch: công việc chạy hàng giờ xóa các mục danh sách đen đã hết hạn

### Phân Quyền (FR-002)
- **Xác thực Token:** Xác thực JWT trên các yêu cầu được bảo vệ
  - Trích xuất Bearer token từ Authorization header
  - Phân tích & xác minh chữ ký HS512
  - Kiểm tra hết hạn token
  - Tải người dùng từ cơ sở dữ liệu qua SecurityContext

- **Kiểm soát truy cập theo vai trò:** Áp dụng vai trò qua bảo mật cấp phương thức
  - Hỗ trợ @PreAuthorize("hasRole('ROLE_ADMIN')")
  - Hỗ trợ @PreAuthorize("hasAnyRole('ROLE_USER', 'ROLE_PM')")
  - Trả về 403 Forbidden khi thiếu vai trò

### Quản Lý Người Dùng (FR-003)
- **Thực thể User:** Lưu trữ thông tin đăng nhập & hồ sơ người dùng
  - Username (không duy nhất - cho phép trùng lặp)
  - Email (duy nhất - dùng làm định danh đăng nhập)
  - Password (mã hóa BCrypt)
  - Họ tên đầy đủ
  - Tập vai trò được gán

- **Thực thể Role:** Định nghĩa vai trò phân quyền
  - Tên vai trò (ROLE_USER, ROLE_PM, ROLE_ADMIN)
  - Quan hệ nhiều-nhiều với User

### Quản Lý Thông Báo (FR-004)
- **Email hướng sự kiện:** Thông báo bất đồng bộ qua Kafka
  - Phát NotificationRequestedEvent khi kích hoạt tài khoản, đặt lại mật khẩu
  - notification-service (cổng 8085) tiêu thụ sự kiện từ Kafka
  - Gửi email định dạng HTML qua SMTP (không gửi email trực tiếp trong auth-service)
  - Tách auth-service khỏi mối quan tâm gửi email

### Thông Báo Trong Ứng Dụng Thời Gian Thực (FR-006)
- **Server-Sent Events (SSE):** Truyền thông báo thời gian thực đến client đã xác thực
  - GET /api/notifications/stream (endpoint SSE, xác thực JWT qua query parameter)
  - Heartbeat 30 giây để ngăn timeout kết nối
  - Kafka consumer group duy nhất cho mỗi instance theo mô hình broadcast
  - Chiến lược kết nối lại exponential backoff khi client ngắt kết nối (1s→tối đa 30s)
- **Lưu trữ:** PostgreSQL notificationdb với bảng notifications
  - Theo dõi userId, title, message, notificationType, isRead, createdAt
  - Hỗ trợ truy xuất phân trang và đánh dấu đã đọc
- **REST API cho Thông Báo:**
  - GET /api/notifications (danh sách phân trang, sắp xếp giảm dần theo createdAt)
  - PATCH /api/notifications/{id}/read (đánh dấu đã đọc từng thông báo)
  - PATCH /api/notifications/read-all (đánh dấu đã đọc hàng loạt)
  - GET /api/notifications/unread-count (số thông báo chưa đọc)
  - POST /api/notifications/broadcast (chỉ admin, broadcast thử nghiệm)
- **Loại sự kiện:** PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM
- **Tích hợp Frontend:** Component chuông thông báo với badge, snackbar alert, trang danh sách thông báo

### Đánh Giá & Bình Luận Phim (FR-005)
- **Đánh giá sao:** Thang điểm 1-5 cho mỗi phim, upsert theo người dùng
  - POST /api/movies/{movieId}/ratings (đã xác thực, upsert)
  - GET /api/movies/{movieId}/ratings (công khai, trả về trung bình/tổng/đánh giá của người dùng)
- **Bình luận:** Cấu trúc phẳng với xóa mềm (trạng thái ACTIVE/DELETED)
  - POST /api/movies/{movieId}/comments (đã xác thực)
  - GET /api/movies/{movieId}/comments?page=0&size=20 (công khai, phân trang)
  - PUT /api/comments/{commentId} (chỉ chủ sở hữu)
  - DELETE /api/comments/{commentId} (chủ sở hữu hoặc admin, xóa mềm)
- **Phản hồi bình luận:** Chuyển đổi thích/không thích theo người dùng
  - POST /api/comments/{commentId}/reactions (đã xác thực, chuyển đổi)
  - DELETE /api/comments/{commentId}/reactions (xóa phản hồi)

## Yêu Cầu Phi Chức Năng

### Bảo Mật (NFR-001)
- **Mã hóa mật khẩu:** BCrypt với Spring Security encoder
- **Ký JWT:** Thuật toán HMAC SHA-512 (HS512)
- **Hết hạn Access Token:** 15 phút (900000 ms, có thể cấu hình)
- **Hết hạn Refresh Token:** 7 ngày (604800000 ms, có thể cấu hình)
- **Xoay vòng Token:** Refresh token được thay thế mỗi lần sử dụng
- **Thu hồi Token:** Danh sách đen dựa trên JTI cho đăng xuất
- **Xác thực Email:** Yêu cầu email duy nhất khi đăng ký
- **Đặt lại mật khẩu:** Token hết hạn 24 giờ qua email bảo mật
- **Quản lý phiên:** Stateless (SessionCreationPolicy.STATELESS)
- **Dọn dẹp theo lịch:** Công việc hàng giờ dọn các mục danh sách đen hết hạn
- **Bảo vệ CSRF:** Tắt cho JWT API (phù hợp)
- **CORS:** Bật cho tất cả origin (có thể cấu hình)

### Hiệu Suất (NFR-002)
- **Truy vấn cơ sở dữ liệu:** Tối ưu hóa qua Spring Data JPA
- **Xử lý JWT:** Xác thực token trong bộ nhớ (không truy vấn cơ sở dữ liệu khi xác thực)
- **Tải Eager:** Vai trò người dùng được tải eager để giảm thiểu truy vấn
- **Connection Pooling:** Spring Boot datasource tiêu chuẩn (HikariCP)

### Khả Năng Mở Rộng (NFR-003)
- **Kiến trúc Stateless:** Không cần session affinity
- **Khám phá dịch vụ:** Eureka cho phép auth-service chạy nhiều instance với cân bằng tải
- **Cấu hình dùng chung:** JWT secret phân phối qua Config Server (tất cả instance nhất quán)
- **JWT claims:** roles + userId được nhúng, dịch vụ downstream tránh truy vấn DB
- **Định tuyến Gateway:** `lb://auth-service` sử dụng Eureka cho cân bằng tải

### Khả Dụng (NFR-004)
- **Phụ thuộc cơ sở dữ liệu:** PostgreSQL bắt buộc để khởi động
- **Suy giảm nhẹ nhàng:** Xác thực token thất bại nếu khóa chữ ký bị hỏng
- **Khởi động lại Container:** Chính sách khởi động lại docker-compose: unless-stopped

### Bảo Trì (NFR-005)
- **Tổ chức mã:** Cấu trúc gói theo lớp (controller, service, model, repository)
- **Logging:** Mức DEBUG cho gói ứng dụng, logging truy vấn SQL
- **Cấu hình:** Dựa trên YAML với ghi đè biến môi trường
- **Phụ thuộc:** Tối thiểu, được bảo trì tốt (Spring Boot 3.4.3, JJWT 0.12.6)

### Khả Năng Quan Sát (NFR-006)
- **Thu thập Metrics:** Micrometer auto-instrumentation trên tất cả dịch vụ; xuất tại `/actuator/prometheus`
- **Tần suất Scrape:** 15 giây (Prometheus `global.scrape_interval`)
- **Gắn thẻ dịch vụ:** Tất cả metrics được gắn thẻ với `application=${spring.application.name}`
- **Dashboard:** JVM Micrometer dashboard (bộ nhớ, GC, thread, CPU); Spring Boot HTTP Overview (tốc độ request, tỷ lệ lỗi, độ trễ, HikariCP, bộ đếm nghiệp vụ)
- **Bộ đếm nghiệp vụ:** auth.login/register/logout, booking.created/confirmed/cancelled, payment.initiated/completed/failed
- **Bảo mật Actuator:** `/actuator/**` cho phép trong Docker `my-net`; không lộ qua API Gateway

## Ràng Buộc Kỹ Thuật

| Ràng buộc | Thông số | Lý do |
|-----------|----------|-------|
| Java Version | 21 | LTS mới nhất, tính năng hiện đại (records, sealed classes, pattern matching) |
| Spring Boot | 3.4.3 | LTS mới nhất, hỗ trợ Java 21, sẵn sàng virtual threads |
| Spring Security | 6.x | Pattern SecurityFilterChain mới, @EnableMethodSecurity |
| Database | PostgreSQL 16 | Tính năng nâng cao, JSON/JSONB, hiệu suất cải thiện |
| Thư viện JWT | JJWT 0.12.6 (3 artifact) | Thiết kế module, API hiện đại, hỗ trợ async |
| Message Broker | Apache Kafka | Event streaming; tách auth khỏi gửi email |
| Dịch vụ Email | Spring Mail SMTP | Gửi email bất đồng bộ qua notification-service |
| Đóng gói | JAR | Triển khai gọn nhẹ, Tomcat nhúng |
| Thuật toán Token | HS512 | Deterministic, nhanh, đối xứng |
| Chính sách phiên | STATELESS | Phù hợp thiết kế JWT stateless |

## Chỉ Số Thành Công

| Chỉ số | Mục tiêu | Phương pháp đo |
|--------|----------|----------------|
| Thời gian phản hồi đăng nhập | < 200ms | Xác thực Spring Security + tạo JWT |
| Xác thực Token | < 50ms | Phân tích token + xác minh chữ ký |
| Khả dụng cơ sở dữ liệu | 99.5% uptime | PostgreSQL giám sát qua container |
| Độ phủ mã | > 70% (unit test) | Maven jacoco plugin |
| Tuân thủ bảo mật | Bao phủ OWASP Top 10 | BCrypt, HS512, CSRF tắt, CORS kiểm soát |

## Hợp Đồng API

### Endpoint Đăng Nhập
**POST /api/auth/login**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Không (công khai)

Yêu cầu:
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

Phản hồi (200 OK):
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

Lỗi (401 Unauthorized):
- Thông tin đăng nhập không hợp lệ, email không tìm thấy, hoặc mật khẩu không khớp

### Endpoint Đăng Ký
**POST /api/auth/register**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Không (công khai)

Yêu cầu:
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

Phản hồi (200 OK):
```
"User registered successfully!"
```

Lỗi (400 Bad Request):
- Email đã được sử dụng, hoặc thiếu email

### Endpoint Quên Mật Khẩu
**POST /api/auth/forgot-password**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Không (công khai)

Yêu cầu:
```json
{
  "email": "jane@example.com"
}
```

Phản hồi (200 OK):
```
"If the email exists, a password reset link has been sent."
```

### Endpoint Đặt Lại Mật Khẩu
**POST /api/auth/reset-password**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Không (dựa trên token)

Yêu cầu:
```json
{
  "token": "reset-token-from-email",
  "newPassword": "newSecure123"
}
```

Phản hồi (200 OK):
```
"Password reset successful."
```

Lỗi (400 Bad Request):
- Token không hợp lệ, hết hạn, hoặc đã sử dụng

### Endpoint Làm Mới Token
**POST /api/auth/refresh-token**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Không (dựa trên refresh token)

Yêu cầu:
```json
{
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Phản hồi (200 OK):
```json
{
  "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
  "refreshToken": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Lỗi (400 Bad Request):
- Refresh token không hợp lệ hoặc hết hạn

### Endpoint Đăng Xuất
**POST /api/auth/logout**
- Tiêu thụ: (không)
- Tạo ra: application/json
- Xác thực: Yêu cầu JWT Bearer token
- Header: `Authorization: Bearer <accessToken>`

Phản hồi (200 OK):
```
"Logged out successfully."
```

Lỗi (400 Bad Request):
- Không cung cấp token
- Token không hợp lệ

### Endpoint Xác Thực Token (MỚI — dùng cho microservice)
**POST /api/auth/validate-token**
- Tiêu thụ: application/json
- Xác thực: Không (được gọi bởi các dịch vụ khác)

Yêu cầu:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```

Phản hồi (200 OK — token hợp lệ):
```json
{
  "valid": true,
  "userId": 1,
  "email": "john@example.com",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Phản hồi (200 OK — không hợp lệ/hết hạn/trong danh sách đen):
```json
{
  "valid": false
}
```

### Endpoint Lấy Thông Tin Người Dùng Hiện Tại (MỚI)
**GET /api/users/me**
- Xác thực: Yêu cầu JWT Bearer token
- Header: `Authorization: Bearer <accessToken>`

Phản hồi (200 OK):
```json
{
  "id": 1,
  "email": "john@example.com",
  "username": "john",
  "fullName": "John Doe",
  "roles": ["ROLE_USER", "ROLE_PM"]
}
```

Lỗi (401 Unauthorized): thiếu/không hợp lệ/hết hạn token

### Endpoint Đổi Mật Khẩu
**POST /api/auth/change-password**
- Tiêu thụ: application/json
- Tạo ra: application/json
- Xác thực: Yêu cầu JWT Bearer token
- Header: `Authorization: Bearer <accessToken>`

Yêu cầu:
```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "confirmPassword": "newPassword456"
}
```

Phản hồi (200 OK):
```
"Password changed successfully."
```

Lỗi (400 Bad Request):
- Mật khẩu hiện tại không đúng
- Mật khẩu mới trùng với 1 trong 3 mật khẩu gần đây (không cho phép tái sử dụng)
- Mật khẩu không khớp

Lỗi (401 Unauthorized):
- Thiếu/không hợp lệ/hết hạn token

### Ví Dụ Endpoint Được Bảo Vệ
**GET /api/protected** (hoặc bất kỳ endpoint không phải auth)
- Xác thực: Yêu cầu JWT Bearer token
- Header: `Authorization: Bearer <accessToken>`

Phản hồi (401 Unauthorized):
- Thiếu/không hợp lệ token
- Token hết hạn (15 phút) - sử dụng endpoint làm mới
- Token trong danh sách đen (đã đăng xuất) - cần đăng nhập lại
- Chữ ký không hợp lệ

Phản hồi (403 Forbidden):
- Người dùng thiếu vai trò yêu cầu

## Quyết Định Kiến Trúc

### Quyết định: JWT Stateless vs Dựa trên Session
**Đã chọn:** JWT Stateless với Refresh Token
- **Lý do:** Thân thiện microservices, mở rộng ngang, không lưu trạng thái server cho access token
- **Đánh đổi:** Kích thước token lớn hơn so với giảm tải cơ sở dữ liệu; refresh token lưu trong DB để thu hồi

### Quyết định: Ký Đối Xứng (HS512) vs Bất Đối Xứng (RS256)
**Đã chọn:** HS512 đối xứng
- **Lý do:** Triển khai shared-secret, vận hành đơn giản hơn, xác thực nhanh hơn
- **Đánh đổi:** Tất cả instance phải bảo vệ secret so với mô hình tin cậy phân tán

### Quyết định: Tải Eager vs Lazy cho Role
**Đã chọn:** Eager (FetchType.EAGER)
- **Lý do:** Role cần trong SecurityContext, truy vấn đơn hiệu quả hơn
- **Đánh đổi:** Luôn tải role kể cả không cần so với truy vấn N+1

### Quyết định: Schema Thủ Công vs Hibernate DDL
**Đã chọn:** Thủ công (ddl-auto: none, create-drop cho dev)
- **Lý do:** Cơ sở dữ liệu là nguồn chân lý, linh hoạt kiểm soát phiên bản
- **Đánh đổi:** Bảo trì thêm so với kiểm soát tiến hóa schema

### Quyết định: Chiến Lược Thu Hồi Token
**Đã chọn:** Danh sách đen dựa trên JTI với dọn dẹp theo lịch
- **Lý do:** Thu hồi hiệu quả không cần sửa JWT claims, dọn dẹp theo lịch ngăn phình bảng
- **Đánh đổi:** Truy vấn cơ sở dữ liệu khi xác thực so với hỗ trợ đăng xuất hoàn chỉnh

### Quyết định: Xoay Vòng Refresh Token
**Đã chọn:** Thay thế token mỗi lần làm mới
- **Lý do:** Giới hạn cửa sổ rủi ro nếu refresh token bị xâm phạm
- **Đánh đổi:** Cập nhật cơ sở dữ liệu khi làm mới so với giảm tác động vi phạm

### Quyết định: Phương Thức Gửi Đặt Lại Mật Khẩu
**Đã chọn:** Dựa trên email với token có trạng thái
- **Lý do:** An toàn, có thể kiểm toán, quen thuộc với người dùng
- **Đánh đổi:** Yêu cầu cấu hình SMTP so với phương thức gửi khác

## Lộ Trình

### Giai Đoạn 1: Nền Tảng (HOÀN THÀNH)
- ✓ Xác thực cốt lõi (đăng nhập/đăng ký)
- ✓ Tạo & xác thực JWT với JTI
- ✓ Phân quyền dựa trên vai trò
- ✓ Lưu trữ PostgreSQL
- ✓ Docker hóa
- ✓ Kiểm thử cơ bản

### Giai Đoạn 2: Quản Lý Token (HOÀN THÀNH)
- ✓ Cơ chế làm mới token với xoay vòng
- ✓ Quy trình đặt lại mật khẩu qua email
- ✓ Đăng xuất với đưa token vào danh sách đen (Redis JTI, auto-TTL)
- ✓ Xác minh email (liên kết kích hoạt)
- ✓ Khóa tài khoản sau N lần thử thất bại (tự động mở khóa)

### Giai Đoạn 3: Tích Hợp Microservice (HOÀN THÀNH)
- ✓ Dự án Maven 10 module: 5 dịch vụ nghiệp vụ, 3 hạ tầng, 2 thư viện dùng chung, 1 frontend
- ✓ Spring Cloud Eureka (registry dịch vụ, :8761)
- ✓ Spring Cloud Config Server (JWT secret dùng chung, :8888, classpath:/config-repo/)
- ✓ Spring Cloud Gateway MVC (điểm vào duy nhất :8080, routes, tổng hợp OpenAPI, HttpLoggingFilter)
- ✓ JWT token bao gồm claims `roles` + `userId` cho downstream
- ✓ POST /api/auth/validate-token (xác thực microservice, không truy vấn DB)
- ✓ GET /api/users/me (lấy hồ sơ người dùng đã xác thực)
- ✓ jwt-auth-autoconfigure (JJWT 0.12.6, bộ xác thực JWT tái sử dụng, @ConditionalOnProperty)
- ✓ **Tài liệu OpenAPI 3.0** (Swagger UI trên tất cả dịch vụ, tổng hợp tại gateway)
- ✓ notification-service (Kafka consumer, SMTP qua Spring Mail, fail-open trên Redis)
- ✓ Module kafka-events (PaymentCompletedEvent, BookingCreatedEvent, MovieCreatedEvent, v.v.)
- ✓ Auth-service phát NotificationRequestedEvent (không gửi email trực tiếp)
- ✓ Prometheus (scrape 15s, lưu trữ 7 ngày) + Grafana (3000, 2 dashboard) + Loki 3.0 log
- ✓ Movie-service tự động tạo lưới ghế (hàng A-Z) khi tạo rạp
- ✓ Booking-service Redis locking (TTL 5 phút, mẫu key: seat:lock:{showtimeId}:{seatId})
- ✓ Payment-service tích hợp Stripe (idempotency key, xác minh chữ ký webhook)
- ✓ Đánh giá & bình luận phim (FR-005): Đánh giá sao, bình luận phẳng, xóa mềm, phản hồi
- [ ] Giới hạn tốc độ trên endpoint đăng nhập/quên mật khẩu

### Giai Đoạn 4: Tăng Cường Bảo Mật (Dự kiến)
- [ ] Ghi nhật ký kiểm toán các hành động nhạy cảm (IP, timestamp)
- [ ] Xác thực JWT claim (issuer, audience)
- [ ] Cơ chế xoay vòng secret
- [ ] Danh sách IP cho phép
- [ ] Mã hóa token khi lưu trữ

### Giai Đoạn 5: Vận Hành (MỘT PHẦN)
- ✓ Thu thập metrics (Micrometer/Prometheus) — tất cả 8 dịch vụ được instrument
- ✓ Dashboard Grafana — JVM Micrometer + Spring Boot HTTP Overview
- ✓ Bộ đếm nghiệp vụ tùy chỉnh — sự kiện auth, booking, payment
- ✓ Logging tập trung (Loki 3.0 lưu trữ 7 ngày)
- [ ] Pipeline CI/CD (GitHub Actions) — dự kiến
- [ ] Quy tắc cảnh báo (Prometheus alertmanager) — dự kiến
- [ ] Kubernetes deployment manifest — dự kiến
- [ ] Load testing & benchmark hiệu suất — dự kiến

## Phụ Thuộc

| Thư viện | Phiên bản | Mục đích |
|----------|-----------|----------|
| Spring Boot | 3.4.3 | Framework |
| Spring Cloud | 2024.0.1 | Eureka, Config, Gateway |
| Spring Security | 6.x (qua Spring Boot) | Xác thực/Phân quyền |
| Spring Data JPA | qua Spring Boot | ORM |
| Spring Kafka | qua Spring Boot | Tích hợp message broker |
| Spring Mail | qua Spring Boot | Gửi email SMTP (notification-service) |
| Spring Boot Actuator | qua Spring Boot | Endpoint metrics /actuator/prometheus |
| Micrometer | qua Actuator | JVM + HTTP + metrics nghiệp vụ tùy chỉnh |
| JJWT | 0.12.6 (api, impl, jackson) | Xử lý JWT (HS512) |
| Spring Cloud | 2024.0.1 | Eureka, Config, Gateway, LoadBalancer |
| Stripe Java SDK | mới nhất | Xử lý thanh toán, webhook |
| Feign Client | qua Spring Cloud | Gọi HTTP giữa các dịch vụ |
| PostgreSQL Driver | mới nhất | Cơ sở dữ liệu (auth-service) |
| Lombok | quản lý BOM | Giảm mã boilerplate |
| BCrypt | qua Spring Security | Mã hóa mật khẩu |
| Jakarta EE | 10+ | Namespace (javax.* → jakarta.*) |

## Tham Số Cấu Hình

| Tham số | Mặc định | Phạm vi | Ghi chú |
|---------|----------|---------|---------|
| server.port (auth-service) | 8081 | Spring Boot | Đã đổi từ 8080 |
| namnd.app.jwtSecret | (Base64 key) | Tùy chỉnh | Chia sẻ qua Config Server |
| namnd.app.jwtExpiration | 900000 | Tùy chỉnh (ms) | 15 phút |
| namnd.app.jwtRefreshExpiration | 604800000 | Tùy chỉnh (ms) | 7 ngày |
| jwt.auth.secret | (từ namnd.app.jwtSecret) | Thư viện Starter | Cho dịch vụ downstream |
| jwt.auth.enabled | true | Thư viện Starter | Tắt bằng false |
| jwt.auth.publicPaths | [] | Thư viện Starter | Đường dẫn bỏ qua xác thực |
| spring.datasource.url | jdbc:postgresql://localhost:5432/testdb | JPA | Chỉ auth-service |

## Tiêu Chí Chấp Nhận

### Câu Chuyện Người Dùng: Quy Trình Đăng Nhập
**Cho** người dùng có thông tin đăng nhập hợp lệ đã đăng ký trong hệ thống
**Khi** người dùng POST đến /api/auth/login với email & mật khẩu
**Thì** API trả về 200 OK với JWT token có hiệu lực 15 phút

**Và** token có thể giải mã để trích xuất email (JWT sub claim)
**Và** chữ ký token xác minh với jwtSecret đã cấu hình
**Và** các yêu cầu tiếp theo với Authorization header được xác thực

### Câu Chuyện Người Dùng: Quy Trình Đăng Ký
**Cho** email duy nhất chưa có trong hệ thống
**Khi** người dùng POST đến /api/auth/register với thông tin đăng nhập & vai trò
**Thì** tài khoản người dùng được tạo với mật khẩu mã hóa BCrypt
**Và** người dùng được gán vai trò đã chỉ định
**Và** thử đăng ký email trùng trả về 400 Bad Request (username trùng cho phép)

### Câu Chuyện Người Dùng: Truy Cập Theo Vai Trò
**Cho** người dùng có vai trò ROLE_ADMIN
**Khi** truy cập endpoint được bảo vệ bởi @PreAuthorize("hasRole('ROLE_ADMIN')")
**Thì** yêu cầu thành công (200 OK)

**Và** người dùng không có ROLE_ADMIN truy cập cùng endpoint nhận 403 Forbidden

## Ghi Chú Triển Khai

### Thứ Tự Build Multi-Module
Các dịch vụ hạ tầng (PostgreSQL, Kafka, Redis) phải chạy trước khi khởi động các business services.

### Sử Dụng Thư Viện JWT Starter
Dịch vụ downstream thêm `jwt-auth-autoconfigure` làm dependency, cấu hình:
```yaml
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}  # received from Config Server
    publicPaths: ["/public/**"]
```
Auto-configuration kết nối `JwtAuthenticationFilter` và `SecurityFilterChain` stateless.

### Cấu Hình Email (auth-service)
- Gmail SMTP (smtp.gmail.com:587)
- Biến môi trường: MAIL_USERNAME, MAIL_PASSWORD (mật khẩu ứng dụng)
- `activationBaseUrl` / `passwordResetBaseUrl` — cấu hình cho frontend của bạn

### Danh Sách Đen Token
- Redis auto-TTL: không cần công việc dọn dẹp
- Fail-closed: Redis gián đoạn → token bị từ chối

## Câu Hỏi Mở

1. **Multi-tenancy:** Hỗ trợ nhiều tổ chức trong tương lai?
2. **Phiên bản API:** Cách đánh phiên bản endpoint (v1/v2)?
3. **Giới hạn tốc độ:** Endpoint đăng nhập có nên giới hạn tốc độ để ngăn brute force?
4. **Gửi Email:** Sử dụng dịch vụ như SendGrid/Mailgun thay vì SMTP?
5. **TTL thu hồi Token:** Nên sử dụng whitelist thay vì blacklist cho token ngắn hạn?
