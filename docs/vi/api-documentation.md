# Tài Liệu API

**Dự án:** ms-cinema
**Framework:** SpringDoc OpenAPI 2.8.4
**Phiên bản OpenAPI:** 3.0

## Điểm Truy Cập Swagger UI

Tất cả dịch vụ xuất tài liệu tương tác OpenAPI 3.0 qua SpringDoc OpenAPI với Swagger UI:

| Dịch vụ | Swagger UI | OpenAPI JSON | Cổng |
|---------|-----------|---|------|
| **api-gateway (tổng hợp)** | http://localhost:8080/swagger-ui.html | /v3/api-docs | 8080 |
| auth-service | http://localhost:8081/swagger-ui.html | /v3/api-docs | 8081 |
| movie-service | http://localhost:8082/swagger-ui.html | /v3/api-docs | 8082 |
| booking-service | http://localhost:8083/swagger-ui.html | /v3/api-docs | 8083 |
| payment-service | http://localhost:8084/swagger-ui.html | /v3/api-docs | 8084 |

## Kiến Trúc Cấu Hình

### Lớp OpenApiConfig

Mỗi dịch vụ (auth-service, movie-service, booking-service, payment-service, api-gateway) chứa một lớp `OpenApiConfig` trong package `config` có chức năng:

**1. Tùy chỉnh thông tin API:**
- Tiêu đề, mô tả, phiên bản API
- Thông tin liên hệ (email hỗ trợ, url)
- Thông tin giấy phép

**2. Định nghĩa URL Server:**
```
Development: http://localhost:{port}
Production: https://api.example.com (nếu được cấu hình)
```

**3. Cấu hình Security Scheme:**
- Tên security scheme: `bearerAuth`
- Loại: HTTP Bearer với JWT tokens
- Định dạng Bearer: JWT

**4. Nhóm Operations theo Tag:**
- Tổ chức endpoints thành các nhóm logic (ví dụ: Authentication, User Profile)
- Mỗi tag bao gồm mô tả và thứ tự

### Controller Annotations

Tất cả controllers sử dụng SpringDoc annotations để tạo tài liệu toàn diện:

```
@Tag(name="Authentication", description="User authentication endpoints")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  @Operation(
    summary="User login",
    description="Authenticates user with email and password. Returns JWT tokens."
  )
  @ApiResponse(responseCode="200", description="Login successful")
  @ApiResponse(responseCode="401", description="Invalid credentials or inactive account")
  @ApiResponse(responseCode="423", description="Account locked after failed attempts")
  @SecurityRequirement(name="bearerAuth")
  @PostMapping("/login")
  public ResponseEntity<?> authenticateUser(...) { ... }
}
```

**Annotations chính:**
- `@Tag` — Nhóm các endpoints liên quan; hiển thị trong sidebar Swagger UI
- `@Operation` — Mô tả mục đích và hành vi endpoint
- `@ApiResponse` — Tài liệu cho mỗi mã phản hồi HTTP (200, 400, 401, v.v.)
- `@SecurityRequirement` — Đánh dấu endpoint yêu cầu xác thực
- `@Parameter` — Tài liệu cho request/query parameters
- `@RequestBody` — Mô tả cấu trúc request body

## Cấu Trúc OpenAPI JSON

Ví dụ schema OpenAPI 3.0 (từ `/v3/api-docs`):

```json
{
  "openapi": "3.0.1",
  "info": {
    "title": "MS Cinema API",
    "version": "0.0.1-SNAPSHOT"
  },
  "servers": [
    {
      "url": "http://localhost:8081",
      "description": "Development Server"
    }
  ],
  "paths": {
    "/api/auth/login": {
      "post": {
        "tags": ["Authentication"],
        "operationId": "authenticateUser",
        "requestBody": { ... },
        "responses": { ... },
        "security": [{ "bearerAuth": [] }]
      }
    }
  },
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT"
      }
    },
    "schemas": { ... }
  }
}
```

#### Endpoint Đổi Mật Khẩu

**POST /api/auth/change-password** - Người dùng đã xác thực đổi mật khẩu

Request body:
```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "confirmPassword": "newPassword456"
}
```

Mã phản hồi:
- 200 OK: Đổi mật khẩu thành công; mật khẩu mới được thêm vào lịch sử
- 400 Bad Request: Xác thực thất bại (mật khẩu không khớp, mật khẩu mới trùng với lịch sử gần đây, thiếu trường)
- 401 Unauthorized: Thiếu hoặc bearer token không hợp lệ
- 500 Internal Error: Lỗi server

Ghi chú bảo mật:
- Mật khẩu hiện tại được xác minh qua so sánh BCrypt
- Mật khẩu mới được kiểm tra với 3 hash mật khẩu gần nhất (ngăn tái sử dụng)
- Lịch sử mật khẩu được lưu vào bảng `password_history` với timestamp
- Mật khẩu ban đầu được seed vào lịch sử khi đăng ký người dùng

## Tổng Hợp API Gateway

API Gateway (:8080) tổng hợp tài liệu OpenAPI từ tất cả dịch vụ downstream:

**Cách hoạt động:**
1. API Gateway có `OpenApiConfig` riêng (cổng 8080)
2. Client truy cập http://localhost:8080/swagger-ui.html
3. Swagger UI của Gateway hiển thị routes đến từng dịch vụ
4. Mỗi route hiển thị operations từ OpenAPI spec của dịch vụ đó

**URL Server trong Gateway Swagger:**
- auth-service routes: /api/auth/**, /api/users/**
- movie-service routes: /api/movies/**
- booking-service routes: /api/bookings/**
- payment-service routes: /api/payments/**

## Tài Liệu Theo Dịch Vụ

### auth-service (/api/auth)

| Endpoint | Xác thực | Mô tả |
|----------|----------|-------|
| POST /api/auth/login | không | Xác thực với email + mật khẩu |
| POST /api/auth/register | không | Tạo tài khoản người dùng mới |
| GET /api/auth/activate | token param | Kích hoạt tài khoản qua liên kết email |
| POST /api/auth/resend-activation | không | Gửi lại email kích hoạt |
| POST /api/auth/forgot-password | không | Khởi tạo luồng đặt lại mật khẩu |
| POST /api/auth/reset-password | token | Hoàn tất đặt lại mật khẩu (kiểm tra 3 mật khẩu gần nhất) |
| POST /api/auth/change-password | Bearer JWT | Đổi mật khẩu cho người dùng đã xác thực |
| POST /api/auth/refresh-token | refresh token | Lấy access token mới |
| POST /api/auth/logout | Bearer JWT | Đăng xuất và đưa token vào blacklist |
| POST /api/auth/validate-token | không | Xác thực JWT (cho dịch vụ downstream) |
| GET /api/users/me | Bearer JWT | Lấy hồ sơ người dùng hiện tại |

### movie-service (/api/movies, /api/comments)

**Quản lý phim:**
Được tài liệu hóa trong MovieController với annotations @Tag và @Operation.

**Đánh giá (1-5 sao):**
| Endpoint | Xác thực | Mô tả |
|----------|----------|-------|
| POST /api/movies/{movieId}/ratings | USER | Tạo hoặc cập nhật đánh giá |
| GET /api/movies/{movieId}/ratings | công khai | Lấy tóm tắt đánh giá (trung bình, số lượng, đánh giá của người dùng) |

**Bình luận (phẳng, xóa mềm):**
| Endpoint | Xác thực | Phương thức | Mô tả |
|----------|----------|------------|-------|
| /api/movies/{movieId}/comments | công khai | GET | Danh sách bình luận (phân trang, mặc định page=0&size=20) |
| /api/movies/{movieId}/comments | USER | POST | Đăng bình luận với nội dung văn bản |
| /api/comments/{commentId} | USER (chủ sở hữu) | PUT | Cập nhật nội dung bình luận của mình |
| /api/comments/{commentId} | USER (chủ sở hữu/ADMIN) | DELETE | Xóa mềm bình luận (status→DELETED) |

**Phản ứng bình luận (thích/không thích):**
| Endpoint | Xác thực | Phương thức | Mô tả |
|----------|----------|------------|-------|
| /api/comments/{commentId}/reactions | USER | POST | Bật/tắt thích/không thích (gửi: reactionType: LIKE hoặc DISLIKE) |
| /api/comments/{commentId}/reactions | USER | DELETE | Xóa phản ứng của người dùng |

**Mã phản hồi:**
- 200 OK: Thao tác thành công
- 400 Bad Request: Đầu vào không hợp lệ (đánh giá không phải 1-5, thiếu nội dung, tham số phân trang không hợp lệ)
- 401 Unauthorized: Thiếu/hết hạn token trên endpoint được bảo vệ
- 403 Forbidden: Người dùng thiếu role (không phải chủ sở hữu bình luận, không phải admin)
- 404 Not Found: Không tìm thấy phim/bình luận
- 500 Internal Error: Lỗi server

Swagger UI: http://localhost:8082/swagger-ui.html

### booking-service (/api/bookings)

Được tài liệu hóa trong BookingController với annotations @Tag và @Operation.
Swagger UI: http://localhost:8083/swagger-ui.html

### payment-service (/api/payments)

Được tài liệu hóa trong PaymentController với annotations @Tag và @Operation.

**Endpoint thanh toán:**
| Endpoint | Xác thực | Mô tả |
|----------|----------|-------|
| POST /api/payments/create-intent | USER | Tạo Stripe PaymentIntent cho đặt vé |
| POST /api/payments/{id}/confirm | USER (chủ sở hữu) | Xác nhận trạng thái thanh toán từ Stripe |
| GET /api/payments/{id} | USER (chủ sở hữu) | Lấy chi tiết thanh toán theo ID |
| GET /api/payments/my | USER | Danh sách lịch sử thanh toán của người dùng |
| GET /api/payments | ADMIN | Danh sách tất cả thanh toán (chỉ admin) |
| POST /api/payments/{id}/refund | ADMIN | Hoàn tiền thanh toán (chỉ admin) |
| POST /api/payments/fake-success | không | Giả lập thành công để kiểm tra (bỏ qua Stripe) |

**Mã phản hồi:**
- 200 OK: Thao tác thành công
- 401 Unauthorized: Thiếu/hết hạn token
- 403 Forbidden: Người dùng thiếu quyền (endpoint chỉ ADMIN, chỉ chủ sở hữu thanh toán)
- 404 Not Found: Không tìm thấy thanh toán
- 500 Internal Error: Lỗi server

Swagger UI: http://localhost:8084/swagger-ui.html

### notification-service (/api/notifications)

**Streaming SSE Thời Gian Thực:**
| Endpoint | Xác thực | Mô tả |
|----------|----------|-------|
| GET /api/notifications/stream | JWT (query param) | Luồng Server-Sent Events với heartbeat 30 giây |

**REST API Thông Báo:**
| Endpoint | Xác thực | Phương thức | Mô tả |
|----------|----------|------------|-------|
| /api/notifications | USER | GET | Danh sách phân trang (page=0, size=20, sắp xếp createdAt DESC) |
| /api/notifications/{id}/read | USER (chủ sở hữu) | PATCH | Đánh dấu một thông báo đã đọc |
| /api/notifications/read-all | USER | PATCH | Đánh dấu tất cả thông báo của người dùng đã đọc |
| /api/notifications/unread-count | USER | GET | Lấy số thông báo chưa đọc cho badge |
| /api/notifications/broadcast | ADMIN | POST | Broadcast thử nghiệm chỉ admin đến tất cả người dùng |

**Chi tiết SSE Stream:**
- **Xác thực:** JWT token qua query parameter: `?token=<JWT>`
- **Sự kiện nhận được:**
  - `event: InAppNotificationEvent` — payload với userId, title, message, notificationType
  - `:heartbeat` (comment) — keep-alive 30 giây, không cần xử lý
- **Hành vi Client:**
  - Sử dụng EventSource API (native trình duyệt)
  - Tự động kết nối lại khi ngắt kết nối với exponential backoff (1s→30s tối đa)
  - Xử lý heartbeat như no-op (giữ kết nối)
- **Mã phản hồi:**
  - 200 OK: Stream được thiết lập, nhận sự kiện
  - 401 Unauthorized: JWT không hợp lệ/hết hạn
  - 429 Too Many Requests: Emitter registry đầy (quá nhiều kết nối đồng thời)

**Mã phản hồi CRUD Thông Báo:**
- 200 OK: Thao tác thành công
- 401 Unauthorized: Thiếu/hết hạn token
- 403 Forbidden: Người dùng không phải chủ sở hữu thông báo (cho PATCH đơn)
- 404 Not Found: Không tìm thấy thông báo
- 500 Internal Error: Lỗi server

**Ví dụ Request:**

GET /api/notifications/stream?token=eyJhbGc...
```
Accept: text/event-stream
```

Trả về định dạng SSE:
```
event: InAppNotificationEvent
data: {"userId":123,"title":"Payment Received","message":"Your booking payment confirmed","notificationType":"PAYMENT_SUCCESS"}

:heartbeat

event: InAppNotificationEvent
data: {"userId":123,"title":"New Booking","message":"Booking confirmed for March 20","notificationType":"ADMIN_BROADCAST"}

:heartbeat
```

PATCH /api/notifications/read-all
```
Authorization: Bearer <token>
```

GET /api/notifications/unread-count
```
Authorization: Bearer <token>
Response: {"count": 3}
```

Swagger UI: http://localhost:8085/swagger-ui.html

### audit-service (/api/audit)

**Truy vấn Nhật Ký Kiểm Toán:**

| Endpoint | Xác thực | Mô tả |
|----------|----------|-------|
| GET /api/audit/logs | ADMIN | Danh sách nhật ký phân trang (lọc userId, action, entityType, startDate, endDate) |
| GET /api/audit/logs/{id} | ADMIN | Chi tiết nhật ký theo ID |

**Chi tiết Query Parameters:**
- `userId` (tùy chọn): Lọc nhật ký của người dùng cụ thể
- `action` (tùy chọn): Lọc theo hành động (LOGIN, REGISTER, LOGOUT, CREATE, UPDATE, DELETE, RESERVE, CANCEL, CONFIRM_PAYMENT, CREATE_PAYMENT_INTENT, CHANGE_PASSWORD)
- `entityType` (tùy chọn): Lọc theo loại thực thể (User, Movie, Booking, Payment)
- `startDate` (tùy chọn): Lọc từ ngày (ISO format: 2026-03-22)
- `endDate` (tùy chọn): Lọc đến ngày (ISO format: 2026-03-22)
- `page` (mặc định 0): Trang hiện tại
- `size` (mặc định 20): Kích thước trang

**Mã phản hồi:**
- 200 OK: Truy vấn thành công
- 401 Unauthorized: Thiếu/hết hạn token
- 403 Forbidden: Người dùng không có role ADMIN
- 404 Not Found: Nhật ký không tìm thấy
- 500 Internal Error: Lỗi server

**Ví dụ Response:**

GET /api/audit/logs?userId=1&action=LOGIN&page=0&size=10
```json
{
  "content": [
    {
      "id": 1,
      "userId": 1,
      "action": "LOGIN",
      "entityType": "User",
      "entityId": 1,
      "beforeState": null,
      "afterState": {"email": "user@example.com", "loginTime": "2026-03-22T10:30:00Z"},
      "ipAddress": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "timestamp": "2026-03-22T10:30:00Z"
    }
  ],
  "pageable": {"pageNumber": 0, "pageSize": 10, "totalElements": 150},
  "totalPages": 15
}
```

Swagger UI: http://localhost:8086/swagger-ui.html

## Bảo Mật trong OpenAPI

### Xác Thực Bearer Token

Tất cả endpoint được bảo vệ yêu cầu header `Authorization: Bearer <token>`:

```bash
curl -H "Authorization: Bearer eyJhbGc..." http://localhost:8081/api/users/me
```

**Trong Swagger UI:**
1. Nhấn nút "Authorize" (góc trên bên phải)
2. Dán JWT token (không có tiền tố "Bearer ")
3. Swagger UI sẽ bao gồm token trong tất cả request tiếp theo

### Khai Báo Bảo Mật Endpoint

Endpoint được bảo vệ hiển thị badge `🔒 Authorize` trong Swagger UI:

```
@SecurityRequirement(name="bearerAuth")
@GetMapping("/users/me")
public ResponseEntity<?> getUserProfile(...) { ... }
```

Endpoint công khai (login, register) KHÔNG bao gồm `@SecurityRequirement`.

## Tích Hợp với Công Cụ Sinh Code

### OpenAPI Codegen

Sinh thư viện client từ `/v3/api-docs` của bất kỳ dịch vụ nào:

```bash
# Sinh TypeScript client từ auth-service
openapi-generator-cli generate \
  -i http://localhost:8081/v3/api-docs \
  -g typescript-axios \
  -o ./generated-client

# Sinh Java client từ API Gateway
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./java-client
```

### Swagger Editor

Import OpenAPI JSON trực tiếp vào Swagger Editor:
1. Truy cập https://editor.swagger.io/
2. File → Import URL → dán URL `/v3/api-docs` của dịch vụ
3. Khám phá tài liệu API và kiểm tra endpoints

## Xác Thực & Thực Hành Tốt Nhất

### Danh Sách Kiểm Tra Annotation Controller

Trước khi commit code controller, đảm bảo:
- ✓ `@Tag(name="...", description="...")` trên lớp
- ✓ `@Operation(summary="...", description="...")` trên mỗi endpoint
- ✓ `@ApiResponse(responseCode="...", description="...")` cho tất cả mã phản hồi (200, 400, 401, 403, 404, 500)
- ✓ `@SecurityRequirement(name="bearerAuth")` trên endpoint được bảo vệ
- ✓ `@RequestBody` tài liệu hóa các trường request body
- ✓ `@Parameter` tài liệu hóa query/path parameters

### Mã Phản Hồi Phổ Biến

| Mã | Ý nghĩa | Trường hợp sử dụng |
|----|---------|-------------------|
| 200 | OK | Request thành công |
| 400 | Bad Request | Xác thực đầu vào thất bại |
| 401 | Unauthorized | Thiếu hoặc auth token không hợp lệ |
| 403 | Forbidden | Người dùng thiếu role yêu cầu |
| 404 | Not Found | Tài nguyên không tồn tại |
| 423 | Locked | Tài khoản bị khóa sau nhiều lần thất bại |
| 500 | Internal Error | Lỗi phía server |

## Xử Lý Sự Cố

**Swagger UI hiển thị spec trống**
- Kiểm tra dịch vụ có đang chạy trên cổng mong đợi không
- Xác minh bean `OpenApiConfig` đã được khởi tạo
- Kiểm tra log về lỗi khởi tạo SpringDoc

**Thiếu endpoint trong Swagger UI**
- Đảm bảo controller có annotation `@RestController`
- Xác minh controller nằm trong đường dẫn Spring component scan
- Kiểm tra annotation `@Tag` và `@Operation` đã có

**Bearer token không hoạt động trong Swagger UI**
- Nhấn nút "Authorize", không phải "Execute" của từng endpoint
- Dán token không có tiền tố "Bearer "
- Token không được hết hạn (mặc định: 15 phút)

**Lỗi CORS khi truy cập /v3/api-docs**
- Xác minh CORS đã được bật trong SecurityConfig
- Kiểm tra origin của client có trong danh sách allowed origins
