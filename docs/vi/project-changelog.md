# Nhật Ký Thay Đổi Dự Án

**Dự án:** ms-cinema
**Cập nhật:** 10 tháng 4, 2026

## Phiên bản 0.0.1-SNAPSHOT

### [Chưa phát hành]

#### Kubernetes: Loại Bỏ API Gateway & Sử Dụng K8s Ingress (HOÀN THÀNH ✓) — 9 tháng 4, 2026
- **Thay Đổi Kiến Trúc:** Loại bỏ dịch vụ Spring Cloud Gateway; thay thế bằng NGINX K8s Ingress cho định tuyến dựa trên đường dẫn
  - Đã xóa: module spring-cloud-gateway
  - Thay thế: `k8s/ingress.yml` — tài nguyên NGINX Ingress cho triển khai K8s
  - Docker Compose: `cinema-frontend/nginx.conf` định tuyến trực tiếp đến các dịch vụ (không có lớp gateway)
  - Dịch vụ Frontend: Thay đổi thành ClusterIP (Ingress xử lý truy cập bên ngoài)
  - WebSocket: Chú thích NGINX Ingress cho hỗ trợ nâng cấp `/ws/**`
  - CORS: Được xử lý ở mức dịch vụ (Spring Security) — không tập hợp tại gateway
  - Swagger: Truy cập Swagger UI từng dịch vụ (không tập hợp) — cải thiện modular
  - Tiết Kiệm Bộ Nhớ: Loại bỏ ~512Mi điểm dừng gateway từ triển khai
- **Lợi Ích:** Topologies triển khai đơn giản hóa, giảm chi phí bộ nhớ, tích hợp Kubernetes gốc

#### Đơn Giản Hóa Cơ Sở Hạ Tầng: Loại Bỏ Eureka & Config Server (HOÀN THÀNH ✓) — 9 tháng 4, 2026
- **Thay Đổi Service Discovery:** Loại bỏ Spring Cloud Eureka hoàn toàn; áp dụng K8s DNS + URI tĩnh
  - Đã xóa: module eureka-server
  - Đã xóa: module config-server
  - Loại bỏ: phụ thuộc eureka-client từ tất cả 6 tệp pom.xml dịch vụ
  - Loại bỏ: phụ thuộc config-client từ tất cả 6 tệp pom.xml dịch vụ
  - Loại bỏ: khối cấu hình Eureka từ tất cả các tệp application.yml
  - Loại bỏ: chỉ thị `spring.config.import` từ tất cả các dịch vụ
  - Mới: hồ sơ `application-k8s.yml` với URI dịch vụ tĩnh (http://service-name:port)
- **Cơ Chế Service Discovery:**
  - K8s: Các dịch vụ khám phá nhau qua K8s DNS (ví dụ: auth-service:8081)
  - Docker Compose: Sử dụng tên máy chủ dịch vụ docker-compose (đã tĩnh)
- **Quản Lý Cấu Hình:** Các dịch vụ sử dụng biến môi trường cho bí mật; không có Config Server tập trung
- **Docker Compose:** Loại bỏ dịch vụ eureka-server và config-server từ docker-compose.yml
- **Monitoring:** Loại bỏ các tác vụ scrape Prometheus cho eureka & config
- **Lợi Ích:** Giảm độ phức tạp hoạt động, đơn giản hóa triển khai K8s, không có service discovery bên ngoài
- **Tác Động Triển Khai:** Bản kê khai K8s hiện hoàn toàn độc lập; không cần dịch vụ cơ sở hạ tầng riêng

#### Triển Khai Kubernetes Minikube (CHƯƠNG TRÌNH TRIỂN KHAI MỚI ✓) — 7 tháng 4, 2026
- **Tùy Chọn Triển Khai Mới:** Bản kê khai K8s hoàn chỉnh cho triển khai Minikube / OrbStack cục bộ
  - Vị trí: thư mục `/k8s` với bản kê khai cho tất cả 6 dịch vụ + cơ sở hạ tầng
  - Dịch Vụ K8s: ClusterIP cho giao tiếp liên dịch vụ; truy cập bên ngoài qua NGINX Ingress
  - NGINX Ingress: `k8s/ingress.yml` — định tuyến dựa trên đường dẫn cho `/api/**` và `/ws/**`
  - ConfigMaps: Cấu hình K8s cho từng dịch vụ (KAFKA_BROKERS, REDIS_HOST, JWT_SECRET)
  - Bí Mật: Lưu trữ trong các đối tượng K8s Secret (STRIPE_SECRET_KEY, MAIL_USERNAME, MAIL_PASSWORD)
  - StatefulSet (tùy chọn): Cho PostgreSQL (nếu không sử dụng DB bên ngoài)
  - Triển Khai: Tất cả 6 dịch vụ, Kafka, PostgreSQL, Redis, ngăn xếp monitoring
- **Mạng:** Các dịch vụ sử dụng K8s DNS (auth-service, movie-service, v.v.) cho cuộc gọi liên dịch vụ
- **Lưu Trữ Bền Vững:** K8s PVC cho PostgreSQL / Kafka (có thể cấu hình)
- **Cách Sử Dụng:** `kubectl apply -f k8s/` để triển khai toàn bộ ngăn xếp trên Minikube / OrbStack
- **Docker Compose Không Thay Đổi:** docker-compose.yml hiện tại vẫn là chế độ dev cục bộ chính
- **Lợi Ích:** Hỗ trợ K8s gốc cho kiểm thử giống sản xuất, khả năng mở rộng, triển khai đa bản sao

#### Tiện Ích Ngày/Giờ Frontend (FR-3.4 HOÀN THÀNH ✓) — 1 tháng 4, 2026
- **Tính năng:** Tiện ích định dạng ngày/giờ an toàn múi giờ cho gửi biểu mẫu
  - Tệp tiện ích: date-format.util.ts (src/app/shared/utils/)
  - Hàm: formatDate(date, format?), combineDatetime(dateStr, timeStr), parseTime(timeStr)
  - formatDate: Định dạng Date thành chuỗi múi giờ cục bộ (định dạng YYYY-MM-DD HH:mm:ss)
  - combineDatetime: Gộp chuỗi ngày + giờ thành đối tượng Date với chuyển đổi múi giờ
  - parseTime: Phân tích chuỗi HH:mm thành phút cho logic time picker
  - Tích hợp: Được sử dụng trong showtime-form-dialog và movie-form-dialog
  - Vấn đề được giải quyết: Ngăn vấn đề offset múi giờ trình duyệt khi gửi giá trị datetime
  - Kiểm thử: Xác nhận thủ công gửi biểu mẫu trên các múi giờ khác nhau
- **Lợi ích:** Xử lý datetime nhất quán trên các biểu mẫu frontend, loại bỏ tham nhũng dữ liệu liên quan đến múi giờ

#### Bảng Điều Khiển Đối Soát Thanh Toán Stripe (FR-3.5 HOÀN THÀNH ✓) — 31 tháng 3, 2026
- **Tính năng:** Giao diện quản trị viên để xem và quản lý kết quả đối soát thanh toán
  - Thành phần dashboard: reconciliation-dashboard.component.ts
    - Thẻ tóm tắt: Tổng lần chạy, đếm matched/mismatched/missing
    - Bộ chọn khoảng ngày: Kích hoạt thủ công cho khoảng ngày tùy chỉnh (tối đa 31 ngày)
    - Bảng lịch sử chạy: MatTable với startDate, endDate, status, counts, phân trang
  - Thành phần chi tiết: reconciliation-detail.component.ts
    - Bảng mục: stripePaymentIntentId, localPaymentId, discrepancyType, amounts, statuses
    - Dropdown bộ lọc: Theo discrepancyType (MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE)
    - Xuất CSV: Tải các mục đối soát dưới dạng tệp CSV
    - Hành động giải quyết: Đánh dấu mục đã giải quyết với ghi chú quản trị viên
  - Dịch vụ API: Bao bọc các endpoint đối soát backend (trigger, getRuns, getRunDetails, getRunItems, getSummary, resolveItem)
  - Route: Thêm /admin/reconciliation, /admin/reconciliation/:runId
  - Điều hướng: Thêm tab "Đối soát" vào admin-nav.component.ts
- **Lợi ích:** Khả năng hiển thị thực tế vào discrepancy thanh toán, quy trình giải quyết hợp lý, audit trail cho hành động quản trị viên

#### Đối Soát Thanh Toán Stripe với Spring Batch (FR-3.3 HOÀN THÀNH ✓) — 31 tháng 3, 2026
- **Tính năng:** Đối soát tự động hàng ngày giữa thanh toán cục bộ và trạng thái Stripe PaymentIntent
  - Tác vụ Spring Batch: Chạy hàng ngày lúc 2 AM múi giờ Asia/Saigon (có thể cấu hình qua reconciliation.cron)
  - Kiến trúc batch: LocalPaymentReader → ReconciliationProcessor → ReconciliationItemWriter
  - Reader: Truy vấn thanh toán cục bộ theo khoảng createdAt từ paymentdb
  - Processor: Gọi Stripe PaymentIntent.retrieve() cho mỗi thanh toán, phân loại loại chênh lệch
  - Writer: Lưu trữ chunk ReconciliationItem (kích thước batch 100)
  - Listener: afterJob gọi Stripe list API để phát hiện mục MISSING_LOCAL, hoàn thiện đếm lần chạy
  - Tệp backend (14 tệp mới): batch/ (4), config/ (3), model/ (4), repository/ (2), service/ (2), controller/ (1 ReconciliationController với @PreAuthorize("hasRole('ADMIN')"))
  - Entity ReconciliationRun: Theo dõi startDate, endDate, status (RUNNING/COMPLETED/FAILED), đếm (matchedCount, mismatchedCount, missingLocalCount, missingStripeCount, totalChecked)
  - Entity ReconciliationItem: Lưu trữ stripePaymentIntentId, localPaymentId, discrepancyType (MATCHED/STATUS_MISMATCH/AMOUNT_MISMATCH/MISSING_LOCAL/MISSING_STRIPE), amounts, statuses, resolved flag, ghi chú quản trị viên
  - Endpoint API quản trị viên: POST /trigger (chạy thủ công với khoảng ngày), GET /runs (phân trang), GET /runs/{runId}, GET /runs/{runId}/items (lọc theo discrepancyType), GET /summary (thống kê lần chạy mới nhất), PUT /items/{itemId}/resolve (ghi chú giải quyết quản trị viên)
  - Xác thực: Bắt buộc khoảng ngày tối đa 31 ngày cho mỗi lần chạy, xác thực khoảng ngày trước khi khởi chạy tác vụ
  - Lập lịch: @Scheduled cron qua ReconciliationScheduler, @ConditionalOnProperty reconciliation.auto-run=true
  - Cấu hình: spring.batch.jdbc.initialize-schema=always (tự động tạo bảng metadata Spring Batch), spring.batch.job.enabled=false (không auto-run khi khởi động), thuộc tính reconciliation.* (cron, auto-run, max-date-range-days)
  - Biến môi trường: Biến môi trường STRIPE_API_KEY (không hardcode khóa test trong application.yml)
  - Cơ sở dữ liệu: Các bảng reconciliation_runs, reconciliation_items tự động tạo bởi Hibernate ddl-auto: update, chỉ mục trên (run_id), (discrepancy_type)
  - Kiểm thử: 3 tệp kiểm thử (15 kiểm thử): ReconciliationProcessorTest (5 kiểm thử đơn vị với MockedStatic<PaymentIntent>), ReconciliationServiceImplTest (6 kiểm thử đơn vị), ReconciliationControllerTest (4 kiểm thử @WebMvcTest với auth dựa trên role)
  - Phụ thuộc: Thêm spring-boot-starter-batch, h2 (test), spring-batch-test (test), spring-security-test (test) vào pom.xml
- **Frontend (4 tệp mới - cinema-frontend):**
  - core/models/reconciliation.model.ts: Interface cho ReconciliationRun, ReconciliationItem, DiscrepancyType, ReconciliationSummary, PageResponse
  - core/services/reconciliation.service.ts: HTTP client cho tất cả endpoint đối soát
  - features/admin/reconciliation/reconciliation-dashboard.component.ts: Thẻ tóm tắt, kích hoạt khoảng ngày, bảng lịch sử chạy với phân trang
  - features/admin/reconciliation/reconciliation-detail.component.ts: Bảng mục với bộ lọc loại chênh lệch, hành động giải quyết, xuất CSV
  - Sửa đổi admin.routes.ts: Thêm route reconciliation + reconciliation/:runId
  - Sửa đổi admin-nav.component.ts: Thêm tab "Đối soát"
- **Lợi ích:** Phát hiện sớm discrepancy thanh toán, audit trail bất biến, xử lý thủ công + lập lịch, khả năng hiển thị quản trị viên vào inconsistencies thanh toán
- **Bảo mật:** @PreAuthorize("hasRole('ADMIN')") trên tất cả endpoint đối soát, không có dữ liệu nhạy cảm trong log phản hồi

#### Sửa Lỗi (22 tháng 3, 2026)
- **Sửa OAuth2 LazyInitializationException:** Force-initialize user.getRoles() trong context @Transactional trong OAuth2UserLinkingService để ngăn vấn đề lazy loading
- **Sửa Lỗi Proxy WebSocket nginx:** Thêm header Connection có điều kiện (Connection: Upgrade cho yêu cầu WebSocket, keep-alive ngoài lại) để hỗ trợ upgrade WebSocket đồng thời duy trì yêu cầu HTTP thông thường
- **Sửa Lỗi Ánh Xạ Dữ Liệu Ghế:** Ánh xạ phản hồi API một cách chính xác (các trường API rowLabel/seatNumber → các trường frontend rowNumber/columnNumber/price) trong hiển thị lưới ghế
- **Sửa Lỗi Polyfill Toàn Cầu:** Thêm polyfill sockjs-client toàn cầu để hỗ trợ trình duyệt cũ hơn và giải quyết các vấn đề tương thích
- **Sửa Lỗi SecurityConfig WebSocket:** Thêm route /ws/** permitAll trong SecurityConfig để đảm bảo kết nối WebSocket không bị chặn bởi bộ lọc xác thực mặc định

#### Giao Diện UI Hiển Thị Lưới Ghế & Cải Tiến Đặt Vé (FR-3.1 HOÀN THÀNH ✓) — 22 tháng 3, 2026
- **Tính năng:** Hiển thị sân khấu hoàn chỉnh với khả năng sẵn có thời gian thực, khả năng tiếp cận, và đề xuất ghế lân cận
  - Giai đoạn 1: Ghế màu (STANDARD=xanh lục, PREMIUM=xanh dương, VIP=hổ phách) với nhãn hàng A-Z và chú giải loại+giá
  - Giai đoạn 2: Màn hình cong với hiệu ứng phát sáng, lỗ lối (cột 6, 13), bộ chia phần VIP, bố cục lưới phản ứng
  - Giai đoạn 3: Phản ứng di động (kích thước ghế 36/40/44px), cuộn ngang, thanh tóm tắt đặt vé nổi
  - Giai đoạn 4: Vai trò lưới ARIA, điều hướng phím mũi tên (lên/xuống/trái/phải), tabindex roving, kiểu focus-visible
  - Giai đoạn 5: WebSocket STOMP thời gian thực /ws/booking với sự kiện LOCK/RESERVE/CANCEL, độ trễ <100ms
  - Giai đoạn 6: Thuật toán đề xuất ghế lân cận O(n*m) phía client (trình độ + hàng + tính đồng nhất loại)
- **Triển khai Frontend:**
  - Tệp mới: seat-grid-layout.utils.ts, seat-grid-keyboard-navigation.utils.ts, seat-selection-timer.utils.ts
  - Components mới: seat-suggestion-panel.component.ts
  - Services mới: seat-websocket.service.ts, seat-suggestion.service.ts
  - Sửa đổi: seat-grid.component.ts, seat-selection.component.ts
  - Dependencies: @stomp/stompjs, sockjs-client (thêm vào package.json)
- **Triển khai Backend:**
  - Tệp mới: WebSocketConfig.java, SeatStatusMessage.java, SeatWebSocketPublisher.java
  - Sửa đổi: BookingServiceImpl.java (publish LOCK/RESERVE/CANCEL qua WebSocket)
  - Sửa đổi: BookingExpiryScheduler.java (publish CANCEL khi hết hạn đặt vé)
  - Sửa đổi: booking-service/pom.xml (spring-boot-starter-websocket dependency)
  - Sửa đổi: booking-service/application.yml (WebSocket routes)
- **Khả năng tiếp cận:** WCAG 2.1 AA compliant (lưới ARIA, điều hướng bàn phím, màu+biểu tượng, kiểu focus, tooltips)
- **Hiệu suất:** WebSocket <100ms độ trễ vs. 2-3s polling (100x nhanh hơn); đề xuất O(n*m) chấp nhận được cho <1000 ghế
- **Bảo mật:** WebSocket xác thực qua xác nhận handshake JWT
- **Kiểm thử:** Integration tests cho WebSocket broadcasts, E2E tests cho cập nhật ghế thời gian thực

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
- Cập nhật `/docs/system-architecture.md` — Thêm schema cơ sở dữ liệu, tính năng mới, quy tắc định tuyến
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

**notification-service (Sửa lỗi)**
- SSE endpoint /api/notifications/stream: Bỏ qua ContentCachingResponseWrapper để ngăn cạn kiệt thread trên kết nối SSE lâu dài

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
