# Lộ Trình Dự Án

**Dự án:** ms-cinema
**Phiên bản:** 0.0.1-SNAPSHOT
**Cập nhật:** Tháng 3 năm 2026
**Trạng thái:** Đang phát triển

## Tầm Nhìn

MS Cinema là nền tảng đặt vé xem phim toàn diện được xây dựng trên microservices Spring Boot 3.4.3. Lộ trình tiến triển từ dịch vụ xác thực đơn lẻ đến hệ thống phân tán với thông báo hướng sự kiện, xử lý thanh toán, và khả năng quan sát đạt chuẩn production.

## Các Giai Đoạn Lộ Trình

### Giai Đoạn 1: Xác Thực Cốt Lõi (HOÀN THÀNH ✓)

**Trạng thái:** Hoàn thành
**Thời gian:** Đã qua
**Trọng tâm:** Xác thực JWT không trạng thái

**Tính năng đã hoàn thành:**
- ✓ Đăng nhập/đăng ký người dùng với mã hóa BCrypt
- ✓ Sinh JWT token (JJWT 0.12.6 HS512)
- ✓ Xác thực token với tính duy nhất JTI
- ✓ Kiểm soát truy cập dựa trên role (@PreAuthorize)
- ✓ Xoay vòng refresh token (TTL 7 ngày)
- ✓ Đăng xuất với Redis token blacklist
- ✓ Luồng kích hoạt email với token 24 giờ
- ✓ Luồng đặt lại mật khẩu qua email
- ✓ Khóa tài khoản sau 5 lần thất bại (tự mở khóa sau 15 phút)

---

### Giai Đoạn 2: Tích Hợp Microservice (HOÀN THÀNH ✓)

**Trạng thái:** Hoàn thành
**Thời gian:** Tháng 12 năm 2025 - Tháng 2 năm 2026
**Trọng tâm:** Kiến trúc đa module với service discovery & event streaming

**Tính năng đã hoàn thành:**
- ✓ Cấu trúc Maven 10 module (5 business services, 3 hạ tầng, 2 thư viện dùng chung, 1 frontend)
- ✓ Spring Cloud Eureka service discovery (:8761)
- ✓ Spring Cloud Config Server (:8888, classpath:/config-repo/)
- ✓ Spring Cloud Gateway MVC (:8080, tổng hợp OpenAPI)
- ✓ JWT tokens nhúng claims roles+userId cho dịch vụ downstream
- ✓ POST /api/auth/validate-token (xác thực JWT microservice, không truy vấn DB)
- ✓ GET /api/users/me (lấy hồ sơ người dùng đã xác thực)
- ✓ Thư viện jwt-auth-autoconfigure (plug-in JWT auth cho tất cả dịch vụ)
- ✓ **Tài liệu OpenAPI 3.0 (Swagger UI, SpringDoc 2.8.4)**
- ✓ movie-service (CRUD phim/rạp/suất chiếu, tự sinh ghế ngồi)
- ✓ booking-service (Redis locking, vòng đời trạng thái, Feign đến movie-service)
- ✓ payment-service (tích hợp Stripe, idempotency keys, xác minh webhook)
- ✓ notification-service (Kafka consumer, SMTP email, Redis dedup)
- ✓ Thư viện dùng chung kafka-events (EventEnvelope, domain events)
- ✓ Kafka topics (movie-events, payment-events, notification-events)
- ✓ Prometheus (:9090, scrape 15s) + Grafana (:3000, 2 dashboards) + Loki (:3100)

**Chỉ số thành công:**
- 10 dịch vụ + hạ tầng triển khai thành công qua docker-compose
- Xác thực JWT chéo dịch vụ < 50ms
- Độ trễ sự kiện booking-to-payment < 2s (p95)
- Tất cả endpoints được tài liệu hóa trong OpenAPI (0 tài liệu thủ công cần thiết)

---

### Giai Đoạn 3: Tính Năng & Cải Tiến (ĐANG TIẾN HÀNH)

**Trạng thái:** Đang tiến hành
**Thời gian:** Tháng 3 - Tháng 5 năm 2026
**Trọng tâm:** Tính năng nghiệp vụ và trải nghiệm người dùng

**Tính năng đã hoàn thành:**
- ✓ Đánh giá phim (1-5 sao) - POST/GET với thống kê tóm tắt (HOÀN THÀNH: 12 tháng 3, 2026)
- ✓ Bình luận phim (phẳng, phân trang, xóa mềm) (HOÀN THÀNH: 12 tháng 3, 2026)
- ✓ Phản ứng bình luận (bật/tắt thích/không thích) (HOÀN THÀNH: 12 tháng 3, 2026)
- ✓ API Gateway route /api/comments/** (HOÀN THÀNH: 12 tháng 3, 2026)

**Tính năng đã hoàn thành (Tiếp):**
- ✓ Bảng điều khiển CRUD Admin Frontend (4 trang quản lý: Phim, Rạp, Suất chiếu, Thanh toán) (HOÀN THÀNH: 13 tháng 3, 2026)
- ✓ Danh sách dạng MatTable với thao tác sửa/xóa (HOÀN THÀNH: 13 tháng 3, 2026)
- ✓ Form MatDialog cho thao tác tạo/sửa (HOÀN THÀNH: 13 tháng 3, 2026)
- ✓ Điều hướng tab admin (HOÀN THÀNH: 13 tháng 3, 2026)
- ✓ PaymentManagementComponent với GET /api/payments chỉ admin (HOÀN THÀNH: 13 tháng 3, 2026)

**Tính năng đã hoàn thành (Thông báo thời gian thực - HOÀN THÀNH ✓ 14 tháng 3, 2026):**
- ✓ Thông báo thời gian thực (SSE + Kafka) - Server-Sent Events với heartbeat 30s cho giao hàng tức thì
- ✓ NotificationSseService với exponential backoff reconnect (1s→30s tối đa, 5 lần thử)
- ✓ NotificationBellComponent (badge toolbar với matBadge, snackbar alerts, tăng dần khi có mới)
- ✓ NotificationListComponent (danh sách Mat-card phân trang, giao diện tối, viền màu theo loại)
- ✓ Lưu trữ PostgreSQL (notificationdb: bảng notifications với userId, title, message, notificationType, isRead, createdAt)
- ✓ InAppNotificationEvent và enum NotificationType (PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM)
- ✓ SseEmitterRegistryService (registry dựa trên ConcurrentHashMap, thao tác atomic, heartbeat 30s)
- ✓ NotificationRestController (GET danh sách, PATCH đánh dấu đã đọc, PATCH đánh dấu tất cả, GET số chưa đọc, POST broadcast)
- ✓ Xác thực JWT qua query parameter (?token=JWT) cho endpoint SSE
- ✓ Thông báo sự kiện thanh toán (thành công/thất bại broadcast đến người dùng qua SSE)
- ✓ NotificationPublisherService trong booking-service (publish đến topic notification.in_app)
- ✓ Sửa lỗi: race condition, OOM broadcast, subscription leak, màu badge toolbar, giao diện tối
- ✓ Hỗ trợ SSE API Gateway (bỏ qua ContentCachingResponseWrapper để ngăn cạn kiệt thread)
- ✓ Cập nhật cấu hình Docker (init-databases.sql, biến môi trường docker-compose notification-service)

**Tính năng đã hoàn thành (Xác thực lịch sử mật khẩu - HOÀN THÀNH ✓ 15 tháng 3, 2026):**
- ✓ Endpoint POST /api/auth/change-password (yêu cầu Bearer JWT, xác thực mật khẩu hiện tại & mới)
- ✓ Bảng password_history (id, user_id, password_hash, created_at) để theo dõi mật khẩu gần đây
- ✓ Entity JPA PasswordHistory và PasswordHistoryRepository với findTop3ByUserIdOrderByCreatedAtDesc()
- ✓ PasswordHistoryService quản lý CRUD và ngăn tái sử dụng 3 mật khẩu (isPasswordReused, savePasswordToHistory)
- ✓ Tăng cường xác thực đặt lại mật khẩu so với 3 hash gần nhất qua PasswordHistoryService.isPasswordReused()
- ✓ Luồng đăng ký seed mật khẩu ban đầu vào lịch sử qua PasswordHistoryService.savePasswordToHistory()
- ✓ Frontend ChangePasswordComponent với reactive form tại /profile/change-password
- ✓ Tích hợp: Nút "Change Password" trên ProfileComponent
- ✓ DTOs: ChangePasswordRequest, ChangePasswordResponse
- ✓ SecurityConfig cập nhật để yêu cầu xác thực cho POST /api/auth/change-password

**Tính năng dự kiến:**

**FR-3.0: Thông báo thời gian thực (HOÀN THÀNH ✓ - 14 tháng 3, 2026)**
- ✓ Hạ tầng Server-Sent Events (SSE) cho thông báo thời gian thực
- ✓ Lưu trữ PostgreSQL (notificationdb)
- ✓ Kafka topic InAppNotificationEvent (notification.in_app)
- ✓ Frontend SSE client với exponential backoff reconnect
- ✓ Component chuông thông báo với badge chưa đọc
- ✓ Trang danh sách thông báo (phân trang, đánh dấu đã đọc)
- ✓ Broadcast sự kiện thanh toán (xác nhận/thất bại đến người dùng)
- ✓ Xác thực JWT qua query parameter cho endpoint SSE
- **Trạng thái:** HOÀN THÀNH (14 tháng 3, 2026)
- **Triển khai:** SSE emitter registry theo người dùng, heartbeat 30s, Kafka consumer group riêng mỗi instance

**FR-3.1: Hiển Thị Lưới Ghế & Giao Diện Đặt Vé**
- Trực quan hóa bản đồ ghế frontend (bố trí rạp hàng A-Z)
- Chọn ghế tương tác với hover/highlight
- Cập nhật tình trạng ghế thời gian thực
- Chọn nhiều ghế cho đặt nhóm
- **Ưu tiên:** CAO
- **Công sức:** Trung bình (4-5 ngày)

**FR-3.2: Tích Hợp Thanh Toán Đặt Vé**
- Luồng thanh toán Stripe hoàn chỉnh trong frontend
- Trao đổi client secret cho xác nhận thanh toán
- Xử lý lỗi cho thanh toán thất bại
- API hoàn tiền cho admin (chỉ role ADMIN)
- **Ưu tiên:** CAO
- **Công sức:** Trung bình (3-4 ngày)

**FR-3.3: Lịch Sử Đặt Vé Người Dùng**
- GET /api/bookings/user (tất cả đặt vé của người dùng với trạng thái)
- GET /api/bookings/{bookingId} (chi tiết đặt vé + trạng thái thanh toán)
- GET /api/payments/user (lịch sử thanh toán)
- API hủy đặt vé (đặt vé PENDING/CONFIRMED)
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Nhỏ (2-3 ngày)

**FR-3.4: Bảng Điều Khiển Admin (HOÀN THÀNH ✓)**
- ✓ Quản lý phim (CRUD, phim nổi bật)
- ✓ Quản lý rạp (sức chứa, vị trí)
- ✓ Lịch chiếu (thao tác CRUD)
- ✓ Xem lịch sử thanh toán (chỉ đọc cho admin)
- Phân tích đặt vé (tỉ lệ lấp đầy, tỉ lệ hủy) - dự kiến Giai đoạn 4
- **Trạng thái:** HOÀN THÀNH (13 tháng 3, 2026)
- **Triển khai:** Danh sách MatTable với form MatDialog, điều hướng tab admin

**FR-3.5: Giới Hạn Tốc Độ Trên Endpoint Nhạy Cảm**
- /api/auth/login: 5 lần thử mỗi IP mỗi phút
- /api/auth/register: 1 lần mỗi IP mỗi giờ
- /api/auth/forgot-password: 3 lần mỗi IP mỗi giờ
- Trả về 429 Too Many Requests
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Nhỏ (2-3 ngày)

---

### Giai Đoạn 4: Bảo Mật & Vận Hành (DỰ KIẾN)

**Trạng thái:** Dự kiến
**Thời gian:** Tháng 5 - Tháng 7 năm 2026
**Trọng tâm:** Củng cố production và tuân thủ

**Tính năng dự kiến:**

**FR-4.1: Ghi Log Kiểm Toán**
- Ghi log tất cả sự kiện xác thực (IP, user agent, timestamp)
- Ghi log các thao tác nhạy cảm (xác nhận thanh toán, hoàn tiền)
- Bảng audit log riêng (bất biến, lưu giữ 1 năm)
- GET /api/admin/audit-logs (chỉ admin, phân trang)
- **Ưu tiên:** CAO
- **Công sức:** Trung bình (3-4 ngày)

**FR-4.2: Xác Thực Hai Yếu Tố (2FA)**
- Hỗ trợ TOTP qua ứng dụng authenticator
- POST /api/auth/2fa/enable (sinh mã QR)
- POST /api/auth/2fa/disable (yêu cầu mật khẩu)
- Mã dự phòng cho khôi phục tài khoản
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Lớn (6-8 ngày)

**FR-4.3: Tích Hợp OAuth2 (HOÀN THÀNH ✓ 16 tháng 3, 2026)**
- ✓ Đăng nhập Google OAuth2 qua Spring Security OAuth2 Client
- ✓ Tự tạo người dùng khi đăng nhập OAuth2 lần đầu (password=NULL, ROLE_USER, active=true)
- ✓ Tự liên kết người dùng hiện có theo email khi email_verified=true (xác minh bởi Google)
- ✓ Bảng UserOAuthProvider cho theo dõi liên kết nhà cung cấp
- ✓ OAuth2AuthenticationSuccessHandler: Sinh JWT + refresh token, chuyển hướng đến frontend
- ✓ Custom OAuth2UserLinkingService: Tìm/tạo người dùng, xử lý đăng nhập đồng thời
- ✓ API Gateway routes: /oauth2/authorization/**, /login/oauth2/code/**
- ✓ Frontend: OAuth2CallbackComponent, nút "Sign in with Google"
- ✓ Guards: Đổi mật khẩu bị chặn cho người dùng chỉ OAuth (password=NULL)
- **Trạng thái:** HOÀN THÀNH (16 tháng 3, 2026)
- **Ưu tiên:** Trước đây THẤP, nay HOÀN THÀNH
- **Thời gian triển khai:** ~7 ngày

**FR-4.4: Distributed Tracing**
- Tích hợp OpenTelemetry
- Correlation IDs trên tất cả requests (X-Correlation-ID)
- Xuất traces đến Jaeger
- Trace tin nhắn Kafka async
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Trung bình (3-4 ngày)

**FR-4.5: Triển Khai Kubernetes**
- Helm charts cho tất cả dịch vụ
- Liveness/readiness probes
- Giới hạn & yêu cầu tài nguyên
- ConfigMaps cho cấu hình môi trường
- Secrets cho dữ liệu nhạy cảm
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Lớn (5-7 ngày)
- Tài liệu tất cả tham số cấu hình
- **Ưu tiên:** CAO
- **Công sức:** Nhỏ (1-2 ngày)

**Chỉ số thành công Giai đoạn 3:**
- Khả dụng 99.95% trong production
- Thời gian phản hồi p95: < 300ms
- Log có thể truy vấn và tìm kiếm trong ELK stack
- Tất cả triển khai được theo dõi trong metrics
- Quy tắc cảnh báo được cấu hình cho các vấn đề nghiêm trọng

---

### Giai Đoạn 4: Mở Rộng & Kiến Trúc (DỰ KIẾN)

**Trạng thái:** Dự kiến
**Thời gian:** Tháng 8 - Tháng 10 năm 2026
**Trọng tâm:** Quy mô doanh nghiệp và tích hợp microservices

**Tính năng dự kiến:**

**FR-4.1: Lớp Caching**
- Triển khai Redis cho caching session/token
- Cache user roles để giảm truy vấn DB
- Cache kết quả xác thực (chữ ký token)
- Vô hiệu cache dựa trên TTL
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Trung bình (3-4 ngày)

**FR-4.2: Tích Hợp API Gateway**
- Tài liệu tích hợp với API Gateway (Kong, AWS API Gateway)
- Cung cấp middleware xác thực JWT
- Hỗ trợ ủy quyền xác thực API Gateway
- Giới hạn tốc độ tại tầng gateway
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Trung bình (2-3 ngày)

**FR-4.3: Multi-Tenancy (Tùy chọn)**
- Hỗ trợ nhiều tổ chức/tenant
- Cách ly dữ liệu tại tầng cơ sở dữ liệu/ứng dụng
- Tenant ID trong JWT claims
- Audit logs riêng theo tenant
- **Ưu tiên:** THẤP
- **Công sức:** Lớn (8-10 ngày)

**FR-4.4: Bảng Điều Khiển Admin (Dịch vụ đồng hành)**
- Tạo giao diện admin riêng (React/Angular)
- Quản lý người dùng (CRUD)
- Quản lý role
- Xem audit logs và metrics
- **Ưu tiên:** THẤP
- **Công sức:** Lớn (10-15 ngày, không trong dịch vụ này)

**FR-4.5: Triển Khai Kubernetes**
- Tạo Helm chart cho triển khai Kubernetes
- ConfigMap cho cấu hình không nhạy cảm
- Secret cho thông tin đăng nhập (jwtSecret, dbPassword)
- Liveness/readiness probes
- Cấu hình Horizontal Pod Autoscaler
- **Ưu tiên:** TRUNG BÌNH
- **Công sức:** Trung bình (2-3 ngày)

**FR-4.6: CI/CD Pipeline**
- GitHub Actions cho kiểm tra tự động khi PR
- Build Docker image khi merge vào main
- Push lên container registry (Docker Hub, ECR)
- Chạy quét bảo mật (Trivy, Snyk)
- Triển khai tự động lên staging
- **Ưu tiên:** CAO
- **Công sức:** Trung bình (2-3 ngày)

**Chỉ số thành công Giai đoạn 4:**
- Hỗ trợ 10K người dùng đồng thời mỗi instance
- Độ trễ p95 dưới 100ms với caching
- Triển khai tự động 10+ lần mỗi ngày
- Không có bước triển khai thủ công
- Infrastructure as Code cho tất cả triển khai

---

## Chuỗi Phụ Thuộc

```
Phase 1 ──┬─→ Phase 2 ──┬─→ Phase 3 ──┬─→ Phase 4
          │            │             │
          │ (Chặn:     │ (Chặn:     │ (Chặn:
          │ Xác thực   │ Ghi log    │ Caching,
          │ đầu vào)   │ kiểm toán, │ K8s,
          │            │ Giới hạn   │ CI/CD)
          │            │ tốc độ)    │
          └────────────┴────────────┴──→ Sẵn sàng Production
```

**Đường dẫn quan trọng:**
- Giai đoạn 1: Refresh Token + Xác thực đầu vào (nền tảng)
- Giai đoạn 2: Thu hồi Token + Củng cố bảo mật (doanh nghiệp)
- Giai đoạn 3: Kiểm tra sức khỏe + Migrations (vận hành)
- Giai đoạn 4: Kubernetes + CI/CD (mở rộng)

---

## Thời Gian & Mốc Quan Trọng

| Giai đoạn | Bắt đầu | Kết thúc | Công sức | Trạng thái |
|-----------|---------|----------|----------|-----------|
| Giai đoạn 0 | Đã qua | Đã qua | ~20 ngày | HOÀN THÀNH ✓ |
| Giai đoạn 1.1 (Refresh) | 10/2 | 20/2 | 3-5 ngày | CẦN LÀM |
| Giai đoạn 1.2 (Hồ sơ) | 20/2 | 5/3 | 3-4 ngày | CẦN LÀM |
| Giai đoạn 1.3 (Đặt lại MK) | 5/3 | 15/3 | 4-5 ngày | CẦN LÀM |
| Giai đoạn 1.4 (Xác thực) | 15/3 | 22/3 | 2-3 ngày | CẦN LÀM |
| Giai đoạn 1.5 (Giới hạn) | 22/3 | 30/3 | 2-3 ngày | CẦN LÀM |
| Hoàn thành Giai đoạn 1 | 30/3 | | | DỰ KIẾN |
| Giai đoạn 2.1 (JWT Claims) | 1/4 | 5/4 | 1-2 ngày | CẦN LÀM |
| Giai đoạn 2.2 (Thu hồi) | 5/4 | 15/4 | 3-4 ngày | CẦN LÀM |
| Giai đoạn 2.3 (2FA) | 15/4 | 5/5 | 5-7 ngày | CẦN LÀM |
| Giai đoạn 2.4 (Audit Logs) | 5/5 | 15/5 | 3-4 ngày | CẦN LÀM |
| Giai đoạn 2.5 (OAuth2) | 15/5 | 1/6 | 5-7 ngày | CẦN LÀM |
| Giai đoạn 2.6 (Quét bảo mật) | 1/6 | 5/6 | 2-3 ngày | CẦN LÀM |
| Hoàn thành Giai đoạn 2 | 5/6 | | | DỰ KIẾN |

---

## Phân Bổ Nguồn Lực

**Thành phần đội ngũ:**
- 1 Kỹ sư Backend (triển khai chính)
- 1 Kỹ sư QA/Test (kiểm thử & chất lượng)
- 1 DevOps/Hạ tầng (Giai đoạn 3+, Docker/K8s)
- 1 Đánh giá bảo mật (Giai đoạn 2, OAuth2/2FA/Audit)

**Ước tính tổng công sức:**
- Giai đoạn 1: ~18-20 ngày
- Giai đoạn 2: ~20-25 ngày
- Giai đoạn 3: ~12-15 ngày
- Giai đoạn 4: ~10-15 ngày
- **Tổng: 60-75 ngày công**

---

## Tiêu Chí Thành Công Theo Giai Đoạn

### Giai Đoạn 1: Cải Tiến
- [ ] Tất cả tính năng mới có unit tests (> 70% coverage)
- [ ] Tất cả endpoints được tài liệu hóa trong OpenAPI/Swagger
- [ ] Không có lỗ hổng bảo mật trong code mới
- [ ] Đặt lại mật khẩu hoạt động đầu cuối
- [ ] Giới hạn tốc độ ngăn chặn tấn công brute force
- [ ] Người dùng có thể refresh token mà không cần đăng nhập lại

### Giai Đoạn 2: Củng Cố Bảo Mật
- [ ] 2FA hoạt động với ứng dụng authenticator (Google Authenticator, Authy)
- [ ] Thu hồi token đã xác minh (blacklist hoạt động)
- [ ] Audit logs được lưu trữ và có thể truy vấn
- [ ] Đăng nhập OAuth2 hoạt động với ít nhất 2 nhà cung cấp
- [ ] Tất cả biện pháp giảm thiểu OWASP Top 10 đã có
- [ ] Không có CVE cao/nghiêm trọng trong dependencies

### Giai Đoạn 3: Vận Hành
- [ ] Kiểm tra sức khỏe trả về trạng thái chính xác
- [ ] Metrics được xuất theo định dạng Prometheus
- [ ] Distributed traces hiển thị trong giao diện Jaeger
- [ ] Schema cơ sở dữ liệu được quản lý phiên bản với Flyway
- [ ] Triển khai được tự động hóa qua CI/CD
- [ ] 99.5%+ uptime trong môi trường staging

### Giai Đoạn 4: Mở Rộng
- [ ] Load tests cho thấy hỗ trợ 10K+ người dùng đồng thời
- [ ] Tỉ lệ cache hit > 80% cho dữ liệu nóng
- [ ] Mở rộng ngang đã kiểm tra (2+ replicas)
- [ ] Kubernetes deployment manifests đã được đánh giá
- [ ] Helm chart có thể cài đặt không cần bước thủ công
- [ ] Danh sách kiểm tra triển khai production đã được tài liệu hóa

---

## Vấn Đề Đã Biết & Nợ Kỹ Thuật

**Hạn chế hiện tại:**
1. **Không có token refresh:** Người dùng phải đăng nhập lại sau 24 giờ
2. **Không có đăng xuất:** Token không thể bị vô hiệu trước khi hết hạn
3. **Xác thực hạn chế:** Không có kiểm tra độ mạnh mật khẩu
4. **Phiên bản JJWT:** 0.9.0 ổn định nhưng cũ (0.12.x khả dụng)
5. **Không có audit trail:** Các lần đăng nhập không được ghi log
6. **Secret cố định:** JWT secret trong application.yml (nên là biến môi trường)
7. **Test coverage hạn chế:** Chỉ 1 smoke test

**Nợ kỹ thuật:**
- Thêm integration tests cho luồng xác thực
- Triển khai request/response logging
- Trích xuất magic numbers thành constants
- Thêm middleware xử lý lỗi (global exception handler)
- Cải thiện thông báo lỗi (hiện tại chung chung)

---

## Đánh Giá Rủi Ro

| Rủi ro | Tác động | Xác suất | Biện pháp giảm thiểu |
|--------|---------|----------|----------------------|
| Lỗ hổng bảo mật JJWT | CAO | THẤP | Giai đoạn 2: Nâng cấp lên 0.12.x |
| Thay đổi phá vỡ Spring Security | CAO | THẤP | Theo dõi release notes, kiểm tra nâng cấp trong CI |
| Giới hạn khả năng mở rộng cơ sở dữ liệu | TRUNG BÌNH | TRUNG BÌNH | Giai đoạn 4: Thêm caching, tinh chỉnh connection pooling |
| Đánh cắp token (không có đăng xuất) | CAO | TRUNG BÌNH | Giai đoạn 2: Triển khai token blacklist |
| Tấn công brute force | TRUNG BÌNH | CAO | Giai đoạn 1: Thêm giới hạn tốc độ |
| Suy giảm hiệu năng khi mở rộng | TRUNG BÌNH | TRUNG BÌNH | Giai đoạn 3: Thêm metrics, Giai đoạn 4: caching |

---

## Câu Hỏi Mở

1. **Multi-tenancy:** Đây có phải là yêu cầu? (Hiện chưa có kế hoạch)
2. **API Gateway:** Sẽ tích hợp với Kong, AWS API Gateway, hay tùy chỉnh?
3. **Dịch vụ Email:** Nhà cung cấp nào cho email đặt lại mật khẩu? (SendGrid, AWS SES)
4. **Thời gian lưu giữ Audit:** Giữ audit logs bao lâu? (Đề xuất: 90 ngày)
5. **Nhà cung cấp OAuth2:** Ưu tiên nhà cung cấp nào? (Đề xuất: Google, GitHub)
6. **Mục tiêu triển khai:** Chỉ Kubernetes, hay cả Docker Swarm/server truyền thống?
7. **Bảng điều khiển Admin:** Xây dựng trong repo này hay dịch vụ frontend riêng?
8. **Tương thích ngược:** Phải duy trì HS512 hay có thể chuyển sang RS256?

---

## Tài Liệu Liên Quan

- [Tổng quan dự án & PDR](./project-overview-pdr.md)
- [Kiến trúc hệ thống](./system-architecture.md)
- [Hướng dẫn triển khai](./deployment-guide.md)
- [Tiêu chuẩn code](./code-standards.md)
- [Quy tắc phát triển](./../.claude/rules/development-rules.md)
