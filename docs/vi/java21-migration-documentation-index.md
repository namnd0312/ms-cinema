# Chỉ Mục Tài Liệu Migration Java 21

**Cập nhật lần cuối:** 2 tháng 3, 2026
**Trạng thái:** Hoàn tất & Hiện hành
**Phạm vi:** Tất cả tài liệu đã cập nhật cho migration Java 21 & Spring Boot 3.4.3

---

## Điều Hướng Nhanh

### Bắt Đầu Với Migration Java 21

**Mới tìm hiểu về migration?** Bắt đầu tại đây:
1. **[migration-java21.md](../migration-java21.md)** - Hướng dẫn migration toàn diện với thay đổi không tương thích, danh sách kiểm tra, và quy trình kiểm thử
2. **[README.md](../../README.md)** - Hướng dẫn bắt đầu nhanh, yêu cầu tiên quyết, và cấu hình đã cập nhật
3. **[code-standards.md](../code-standards.md)** - Mẫu Spring Security 6.x mới và quy chuẩn code

### Bản Đồ Tài Liệu Đầy Đủ

| Tài liệu | Mục đích | Chủ đề chính |
|----------|---------|-----------|
| **[README.md](../../README.md)** | Tổng quan dự án & bắt đầu nhanh | Yêu cầu tiên quyết, build, API, cấu hình |
| **[project-overview-pdr.md](../project-overview-pdr.md)** | Yêu cầu sản phẩm & quyết định | Tech stack, yêu cầu, quyết định kiến trúc |
| **[codebase-summary.md](../codebase-summary.md)** | Cấu trúc & thành phần codebase | Tổ chức file, dependency, metrics |
| **[system-architecture.md](../system-architecture.md)** | Thiết kế & triển khai hệ thống | Tầng, luồng, Docker, mở rộng, bảo mật |
| **[code-standards.md](../code-standards.md)** | Quy ước & mẫu code | Phong cách, Spring Security 6.x, kiểm thử |
| **[migration-java21.md](../migration-java21.md)** | Hướng dẫn migration & danh sách kiểm tra | Thay đổi không tương thích, các bước, kiểm thử, rollback |

---

## Thay Đổi Chính Theo Thành Phần

### Java & JDK
- **Phiên bản:** 1.8 → **21 LTS**
- **Docker:** openjdk:11 → **eclipse-temurin:21-jre-alpine**
- **Tính năng:** Virtual threads, records, sealed classes hiện đã có sẵn
- **Tài liệu:** Xem migration-java21.md → Cải Thiện Hiệu Suất

### Spring Framework
- **Spring Boot:** 2.6.4 → **3.4.3**
- **Spring Security:** 5.x → **6.x**
  - Mẫu mới: bean `SecurityFilterChain`
  - Annotation mới: `@EnableMethodSecurity`
  - Cấu hình: `authorizeHttpRequests()` dựa trên Lambda
- **Tài liệu:** Xem code-standards.md → Quy ước Spring Framework (Spring Security 6.x)

### Thay Đổi Namespace
- **Tất cả:** javax.* → **jakarta.***
  - javax.persistence → jakarta.persistence
  - javax.servlet → jakarta.servlet
  - javax.mail → jakarta.mail
  - javax.validation → jakarta.validation
- **Tài liệu:** Xem migration-java21.md → Thay Đổi Không Tương Thích (Jakarta EE)

### Dependency
- **JJWT:** 0.9.0 → **0.12.6** (tách thành 3 artifact)
  - jjwt-api (compile)
  - jjwt-impl (runtime)
  - jjwt-jackson (runtime)
- **Lombok:** 1.18.30 → **BOM-managed**
- **PostgreSQL:** 13.1 → **16**
- **Tài liệu:** Xem codebase-summary.md → Tệp Cấu Hình

### Cấu Hình
- **Đóng gói:** WAR → **JAR**
- **Cấu hình Redis:** spring.redis.* → **spring.data.redis.***
- **ServletInitializer:** **Đã xóa** (chỉ triển khai JAR)
- **Tài liệu:** Xem README.md → phần Cấu hình

---

## Tham Chiếu Nhanh Danh Sách Kiểm Tra Migration

Từ `migration-java21.md`, danh sách kiểm tra đầy đủ bao gồm:

### Thay Đổi Code
- [ ] Cập nhật import javax.* thành jakarta.*
- [ ] Cập nhật SecurityConfig thành mẫu SecurityFilterChain
- [ ] Thay thế @EnableGlobalMethodSecurity bằng @EnableMethodSecurity
- [ ] Cập nhật lệnh gọi JJWT parser thành parserBuilder()
- [ ] Xóa kế thừa WebSecurityConfigurerAdapter
- [ ] Kiểm tra import servlet filter
- [ ] Cập nhật import JPA entity
- [ ] Xác nhận import Mail API

### Thay Đổi POM.XML
- [ ] Cập nhật Spring Boot parent thành 3.4.3
- [ ] Cập nhật JJWT thành 0.12.6 (3 artifact)
- [ ] Đổi đóng gói từ war sang jar
- [ ] Xóa spring-boot-starter-tomcat (provided)
- [ ] Cập nhật Java source/target thành 21
- [ ] Cập nhật phiên bản dependency cho tương thích

### Thay Đổi Cấu Hình
- [ ] Cập nhật application.yml: spring.redis.* → spring.data.redis.*
- [ ] Xác nhận JWT secret sử dụng biến môi trường
- [ ] Kiểm tra các property javax namespace khác

### Thay Đổi Docker
- [ ] Cập nhật Dockerfile base thành eclipse-temurin:21-jre-alpine
- [ ] Cập nhật docker-compose.yml PostgreSQL thành 16
- [ ] Kiểm tra docker-compose build và khởi động

### Kiểm Thử & Xác Nhận
- [ ] Chạy mvn clean compile (không lỗi)
- [ ] Chạy mvn test (tất cả pass)
- [ ] Chạy mvn spring-boot:run (khởi động local)
- [ ] Kiểm tra docker-compose up (containerized)
- [ ] Kiểm tra tất cả endpoint xác thực
- [ ] Kiểm tra cơ chế làm mới token
- [ ] Kiểm tra đăng xuất/blacklist
- [ ] Kiểm tra luồng kích hoạt email
- [ ] Kiểm tra luồng đặt lại mật khẩu

---

## Migration Mẫu Spring Security 6.x

**Thay đổi quan trọng:** Cách cấu hình Spring Security đã thay đổi đáng kể.

### Mẫu cũ (Spring Boot 2.x / Spring Security 5.x)
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception { ... }
}
```

### Mẫu mới (Spring Boot 3.x / Spring Security 6.x)
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // ← Annotation mới
public class SecurityConfig {  // ← Không còn kế thừa adapter
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth  // ← Dựa trên Lambda
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .build();
    }
}
```

**Ví dụ đầy đủ có sẵn:** Xem [code-standards.md](../code-standards.md#annotation-order-spring-security-6x)

---

## Migration Property Cấu Hình

### Spring Data Redis (trước đây là spring.redis.*)
```yaml
# Cũ (Spring Boot 2.x)
spring:
  redis:
    host: localhost
    port: 6379

# Mới (Spring Boot 3.x)
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

**Ví dụ cấu hình đầy đủ:** Xem [README.md](../../README.md#configuration)

---

## Thay Đổi Database & Hạ Tầng

### PostgreSQL
- **Cũ:** 13.1
- **Mới:** 16
- **Hành động:** Xác nhận tương thích schema, kiểm tra migration

### Docker Base Image
- **Cũ:** openjdk:11
- **Mới:** eclipse-temurin:21-jre-alpine
- **Lợi ích:** Kích thước image nhỏ hơn, Alpine Linux, JDK 21 LTS

### Redis
- **Cấu hình:** spring.redis.* → spring.data.redis.*
- **Chức năng:** Không có thay đổi không tương thích, chỉ đổi tên property

---

## Kiến Trúc & Bảo Mật Không Thay Đổi

**Không có thay đổi cơ bản về:**
- Tạo/xác thực JWT token (vẫn sử dụng HS512)
- Cơ chế làm mới token
- Logic khóa tài khoản
- Luồng kích hoạt email
- Luồng đặt lại mật khẩu
- Phân quyền dựa trên role (@PreAuthorize)
- Token blacklist (Redis)
- Schema database

**Spring Security 6.x cung cấp:**
- Đảm bảo bảo mật tương tự
- Mẫu cấu hình cải tiến
- Hiệu suất tốt hơn
- Fluent API dựa trên Lambda
- Quản lý bean đơn giản hơn

---

## Kiểm Thử Sau Migration

### Kiểm Tra Nhanh (Smoke Test)
```bash
# 1. Build
mvn clean package

# 2. Chạy local
mvn spring-boot:run

# 3. Kiểm tra endpoint đăng nhập
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"pass123"}'

# 4. Kiểm tra Docker
docker-compose up --build
# Xác nhận: PostgreSQL trên 5432, Redis trên 6379, API trên 8080
```

### Danh Sách Kiểm Thử Đầy Đủ
Xem [migration-java21.md](../migration-java21.md#testing-recommendations) cho unit test, integration test, và performance test toàn diện.

---

## Vấn Đề Thường Gặp & Giải Pháp

### Vấn đề: "Unable to find a signing key"
**Nguyên nhân:** Vấn đề định dạng JWT secret
**Giải pháp:** Xác nhận secret được mã hóa Base64, kiểm tra ghi đè biến môi trường

### Vấn đề: "Connection refused" trên Redis
**Nguyên nhân:** Lỗi đánh máy tên property
**Giải pháp:** Sử dụng `spring.data.redis.*` (không phải `spring.redis.*`)

### Vấn đề: Lỗi biên dịch với import
**Nguyên nhân:** javax.* vẫn còn trong code
**Giải pháp:** Tìm/thay thế toàn bộ javax → jakarta

### Vấn đề: Không tìm thấy bean SecurityConfig
**Nguyên nhân:** Thiếu giá trị trả về bean SecurityFilterChain
**Giải pháp:** Method phải trả về SecurityFilterChain (không phải void)

**Xử lý sự cố đầy đủ:** Xem [migration-java21.md](../migration-java21.md#known-issues--workarounds)

---

## Quy Trình Rollback

Nếu phát sinh vấn đề nghiêm trọng, rollback theo quy trình trong [migration-java21.md](../migration-java21.md#rollback-plan):

1. **Code:** `git revert <migration-commit>`
2. **Docker:** Cập nhật Dockerfile & docker-compose.yml về phiên bản cũ
3. **Database:** Khôi phục từ bản sao lưu nếu có thay đổi schema
4. **Xác nhận:** Chạy lại smoke test với môi trường cũ

---

## Ghi Chú Hiệu Suất

Java 21 và Spring Boot 3.4.3 cung cấp:
- Thời gian khởi động nhanh hơn ~30%
- Cải thiện thời gian tạm dừng GC (tối ưu G1GC)
- Hiệu quả bộ nhớ tốt hơn
- Sẵn sàng virtual threads (sử dụng trong tương lai)
- Không có thay đổi hiệu suất không tương thích

Xem [migration-java21.md](../migration-java21.md#performance-improvements) để biết chi tiết.

---

## Nâng Cấp Tương Lai

Java 21 LTS nhận cập nhật đến **tháng 9, 2028**:
- Java 23 (Tháng 9, 2023, non-LTS) - Tiên phong
- Java 25 (Tháng 9, 2025, non-LTS) - Cập nhật dần
- Java 27 (Tháng 9, 2027, ứng viên LTS) - Phiên bản LTS tiếp theo

Lên kế hoạch nâng cấp theo chu kỳ 2-3 năm, giữ trên các phiên bản LTS.

---

## Liên Kết Tài Liệu Chính

### Để Hiểu Thay Đổi
- **Chi tiết migration đầy đủ:** [migration-java21.md](../migration-java21.md)
- **Giải thích thay đổi không tương thích:** [migration-java21.md#breaking-changes](../migration-java21.md)
- **Mẫu code mới:** [code-standards.md](../code-standards.md)

### Để Triển Khai
- **Ví dụ Spring Security 6.x:** [code-standards.md#annotation-order-spring-security-6x](../code-standards.md)
- **Cập nhật cấu hình:** [README.md#configuration](../../README.md)
- **Danh sách dependency:** [codebase-summary.md#external-dependencies](../codebase-summary.md)

### Để Triển Khai Hệ Thống
- **Yêu cầu tiên quyết:** [README.md#prerequisites](../../README.md)
- **Kiến trúc:** [system-architecture.md](../system-architecture.md)
- **Thiết lập Docker:** [README.md#docker-compose](../../README.md)

### Để Kiểm Thử
- **Khuyến nghị kiểm thử:** [migration-java21.md#testing-recommendations](../migration-java21.md)
- **Xử lý sự cố:** [migration-java21.md#known-issues--workarounds](../migration-java21.md)

---

## Tệp Báo Cáo

Tài liệu migration được theo dõi trong các báo cáo:
- **Chi tiết migration đầy đủ:** `plans/260302-2102-java21-spring-boot-migration/reports/docs-manager-260302-2126-java21-migration-documentation-updates.md`
- **Tóm tắt:** `plans/260302-2102-java21-spring-boot-migration/reports/docs-manager-260302-2127-documentation-update-summary.md`

---

## Tóm Tắt Trạng Thái

| Khía cạnh | Trạng thái | Chi tiết |
|--------|--------|---------|
| Cập nhật tài liệu | Hoàn tất | 5 tệp đã cập nhật, 1 hướng dẫn mới |
| Ví dụ code | Hoàn tất | Các mẫu Spring Security 6.x đã trình bày |
| Thay đổi cấu hình | Hoàn tất | Tất cả cập nhật property đã ghi nhận |
| Thay đổi không tương thích | Hoàn tất | Jakarta EE, Spring Security 6.x, JJWT |
| Quy trình kiểm thử | Hoàn tất | Unit, integration, smoke test |
| Kế hoạch rollback | Hoàn tất | Rollback từng bước đã ghi nhận |
| Đảm bảo chất lượng | Hoàn tất | Tất cả cập nhật đã xác nhận |

---

## Câu Hỏi Hoặc Vấn Đề?

1. **Kiểm tra:** [migration-java21.md](../migration-java21.md) để có câu trả lời toàn diện
2. **Xem lại:** [code-standards.md](../code-standards.md) cho mẫu code
3. **Xác nhận:** [README.md](../../README.md) cho cấu hình và thiết lập
4. **Tìm hiểu:** [system-architecture.md](../system-architecture.md) cho tác động kiến trúc

---

**Cập nhật tài liệu lần cuối:** 2 tháng 3, 2026
**Trạng thái migration:** Hoàn tất và đã ghi nhận
**Sẵn sàng cho:** Nhóm phát triển, Triển khai, Migration Production
