# Hướng Dẫn Migration Java 21 & Spring Boot 3.4.3

**Ngày migration:** Tháng 3, 2026
**Trạng thái:** Hoàn tất
**Thay đổi không tương thích:** Có - Jakarta EE namespace, các mẫu Spring Security 6.x

## Tổng Quan

Tài liệu này mô tả quá trình migration từ Java 8 / Spring Boot 2.6.4 sang Java 21 LTS / Spring Boot 3.4.3, bao gồm Spring Security 6.x và JJWT 0.12.6.

## Tóm Tắt Thay Đổi Phiên Bản

| Thành phần | Phiên bản cũ | Phiên bản mới | Ảnh hưởng |
|-----------|------------|------------|--------|
| Java | 1.8 | 21 LTS | Tính năng ngôn ngữ, sẵn sàng virtual threads |
| Spring Boot | 2.6.4 | 3.4.3 | Nâng cấp framework lớn |
| Spring Security | 5.x | 6.x | Mẫu SecurityFilterChain, @EnableMethodSecurity |
| JJWT | 0.9.0 | 0.12.6 | 3 artifact mô-đun (api, impl, jackson) |
| PostgreSQL | 13.1 | 16 | Tính năng nâng cao, cải thiện hiệu suất |
| Docker Base | openjdk:11 | eclipse-temurin:21-jre-alpine | Image nhỏ hơn, JDK 21, Alpine |
| Lombok | 1.18.30 | BOM-managed | Quản lý phiên bản nhất quán |

## Thay Đổi Không Tương Thích

### 1. Migration Jakarta EE Namespace

**Tất cả import `javax.*` phải được cập nhật thành `jakarta.*`**

**Ví dụ:**
```java
// Cũ (javax namespace)
import javax.servlet.http.HttpServletRequest;
import javax.persistence.Entity;
import javax.mail.Message;

// Mới (jakarta namespace)
import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.Entity;
import jakarta.mail.Message;
```

**Các khu vực bị ảnh hưởng:**
- Annotation JPA/Hibernate: `javax.persistence.*` → `jakarta.persistence.*`
- Servlet API: `javax.servlet.*` → `jakarta.servlet.*`
- Mail API: `javax.mail.*` → `jakarta.mail.*`
- Validation: `javax.validation.*` → `jakarta.validation.*`
- JSON/XML Binding: `javax.xml.bind.*` → `jakarta.xml.bind.*`

### 2. Thay Đổi Mẫu Spring Security 6.x

**Mẫu cũ (Spring Security 5.x / Spring Boot 2.x):**
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors()
            .and()
            .authorizeRequests()
            .antMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth
            .userDetailsService(userService)
            .passwordEncoder(passwordEncoder());
    }

    @Bean
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }
}
```

**Mẫu mới (Spring Security 6.x / Spring Boot 3.x):**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Thay thế @EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf().disable()
            .cors()
            .and()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

**Điểm khác biệt chính:**
- `WebSecurityConfigurerAdapter` đã bị loại bỏ; sử dụng method trả về bean `SecurityFilterChain`
- `configure(HttpSecurity)` → method `securityFilterChain(HttpSecurity http)`
- `configure(AuthenticationManagerBuilder)` → Autowire UserDetailsService vào service beans
- `@EnableGlobalMethodSecurity(prePostEnabled = true)` → `@EnableMethodSecurity`
- `authorizeRequests()` → `authorizeHttpRequests()` (dựa trên lambda)
- Fluent API sử dụng lambda expressions thay vì chuỗi method

### 3. Thay Đổi Đóng Gói: WAR sang JAR

**Cũ (WAR):**
- pom.xml: `<packaging>war</packaging>`
- Cần ServletInitializer.java cho triển khai servlet container
- Dependency: `<scope>provided</scope>` cho spring-boot-starter-tomcat

**Mới (JAR):**
- pom.xml: `<packaging>jar</packaging>` (mặc định)
- Đã xóa ServletInitializer.java
- Tomcat được nhúng trong spring-boot-starter-web
- Build: `mvn clean package` → `target/auth-service.jar` (thực thi được)

### 4. Tách Thư Viện JJWT

**Cũ (Một artifact duy nhất):**
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
    <version>0.9.0</version>
</dependency>
```

**Mới (3 artifact mô-đun):**
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**Thay đổi cách sử dụng API:**
```java
// Cũ (0.9.0)
Jwts.parser()
    .setSigningKey(SECRET_KEY)
    .parseClaimsJws(token)
    .getBody();

// Mới (0.12.6)
Jwts.parserBuilder()
    .setSigningKey(SECRET_KEY)
    .build()
    .parseClaimsJws(token)
    .getBody();

// Hoặc mẫu async mới hơn (tùy chọn)
Jwts.parserBuilder()
    .setSigningKey(SECRET_KEY)
    .build()
    .parseClaimsJws(token)
    .getPayload(); // getBody() vẫn hoạt động
```

### 5. Cấu Hình Spring Data Redis

**Cũ (spring.redis.*):**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
```

**Mới (spring.data.redis.*):**
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 6. Docker Base Image

**Cũ:**
```dockerfile
FROM openjdk:11
COPY target/auth-service.jar /opt/app/auth-service.jar
ENTRYPOINT ["java", "-jar", "/opt/app/auth-service.jar"]
```

**Mới:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
COPY target/auth-service.jar /opt/app/auth-service.jar
ENTRYPOINT ["java", "-jar", "/opt/app/auth-service.jar"]
```

**Lợi ích:**
- Kích thước image nhỏ hơn (nền tảng Alpine Linux)
- Bản phân phối chính thức Eclipse Temurin
- Hỗ trợ JDK 21 LTS
- Vá bảo mật tốt hơn

## Danh Sách Kiểm Tra Migration

### 1. Thay Đổi Code
- [ ] Cập nhật tất cả import `javax.*` thành `jakarta.*`
- [ ] Cập nhật SecurityConfig sử dụng mẫu bean SecurityFilterChain
- [ ] Thay thế `@EnableGlobalMethodSecurity` bằng `@EnableMethodSecurity`
- [ ] Xóa kế thừa WebSecurityConfigurerAdapter
- [ ] Cập nhật các lệnh gọi JJWT parser sử dụng parserBuilder()
- [ ] Xác nhận import JPA entity sử dụng jakarta.persistence
- [ ] Cập nhật import Mail API thành jakarta.mail
- [ ] Kiểm tra import servlet filter sử dụng jakarta.servlet

### 2. Thay Đổi POM.XML
- [ ] Cập nhật phiên bản spring-boot parent thành 3.4.3
- [ ] Cập nhật JJWT thành 0.12.6 (3 artifact)
- [ ] Đổi đóng gói từ `war` sang `jar`
- [ ] Xóa `spring-boot-starter-tomcat` (provided scope)
- [ ] Cập nhật tất cả phiên bản dependency cho tương thích Spring Boot 3.x
- [ ] Cập nhật Java source/target thành 21

### 3. Thay Đổi Cấu Hình
- [ ] Cập nhật application.yml: `spring.redis.*` → `spring.data.redis.*`
- [ ] Xác nhận JWT secret sử dụng mã hóa Base64 với biến môi trường
- [ ] Cập nhật các property javax namespace khác

### 4. Thay Đổi Docker
- [ ] Cập nhật Dockerfile base image thành eclipse-temurin:21-jre-alpine
- [ ] Cập nhật docker-compose.yml PostgreSQL thành postgres:16-alpine
- [ ] Kiểm tra docker-compose up build và chạy đúng

### 5. Kiểm Thử & Xác Nhận
- [ ] Chạy `mvn clean compile` - xác nhận không có lỗi biên dịch
- [ ] Chạy `mvn test` - xác nhận tất cả test pass
- [ ] Chạy `mvn spring-boot:run` - xác nhận khởi động local
- [ ] Kiểm tra `docker-compose up --build` - xác nhận khởi động container
- [ ] Xác nhận tất cả endpoint xác thực hoạt động
- [ ] Xác nhận cơ chế làm mới token
- [ ] Xác nhận đăng xuất và blacklist token
- [ ] Kiểm tra khóa tài khoản sau đăng nhập thất bại
- [ ] Kiểm tra luồng kích hoạt email
- [ ] Kiểm tra luồng đặt lại mật khẩu

### 6. Migration Database
- [ ] Sao lưu database PostgreSQL 13.1 hiện có
- [ ] Xác nhận tương thích schema với PostgreSQL 16
- [ ] Kiểm tra migration trong môi trường dev
- [ ] Lên kế hoạch khung thời gian migration production (nếu cần)

### 7. Tài Liệu
- [ ] Cập nhật tham chiếu phiên bản trong README.md
- [ ] Cập nhật tài liệu kiến trúc hệ thống
- [ ] Cập nhật quy chuẩn code cho các mẫu mới
- [ ] Ghi nhận thay đổi không tương thích trong changelog

## Khuyến Nghị Kiểm Thử

### Unit Test
```bash
mvn clean test
```
Kỳ vọng: Tất cả test hiện có nên pass (xác nhận UserDetails, JWT, Security test)

### Integration Test
```bash
# Khởi động docker-compose
docker-compose up -d

# Chạy ứng dụng
mvn spring-boot:run

# Kiểm tra API endpoint
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"pass123","fullName":"Test User","roles":[{"name":"ROLE_USER"}]}'

# Xác nhận kích hoạt tài khoản
curl -X GET "http://localhost:8080/api/auth/activate?token=<token-from-email>"

# Kiểm tra đăng nhập
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"pass123"}'
```

### Ghi Chú Hiệu Suất
- Java 21 bao gồm các tối ưu hiệu suất (cải thiện G1GC)
- Spring Boot 3.x có tối ưu thời gian khởi động
- Virtual threads (tính năng Java 21) sẵn sàng cho cải thiện async trong tương lai
- Không có thay đổi hiệu suất không tương thích dự kiến với migration

## Kế Hoạch Rollback

Nếu phát sinh sự cố sau migration:

1. **Rollback code:**
   ```bash
   git revert <migration-commit>
   ```

2. **Rollback Docker:**
   ```bash
   docker-compose down
   # Đổi Dockerfile về openjdk:11
   # Đổi docker-compose.yml về postgres:13.1-alpine
   docker-compose up --build
   ```

3. **Database:**
   - Schema PostgreSQL 16 tương thích với 13.1
   - Rollback bằng cách khôi phục từ bản sao lưu nếu có thay đổi cấu trúc

## Vấn Đề Đã Biết & Giải Pháp Tạm Thời

### Vấn đề: Xung đột xử lý annotation Lombok
**Triệu chứng:** `@Data` không tạo getter/setter
**Giải pháp tạm thời:** Đảm bảo Maven compiler plugin loại trừ Lombok khỏi spring-boot-maven-plugin

### Vấn đề: Lỗi phân tích JWT token
**Triệu chứng:** "Unable to find a signing key that matches"
**Giải pháp tạm thời:** Xác nhận định dạng SECRET_KEY (Base64 nếu sử dụng secret mã hóa Base64)

### Vấn đề: Lỗi kết nối Redis
**Triệu chứng:** "Connection refused" trên spring.data.redis.host
**Giải pháp tạm thời:** Xác nhận tên property là `spring.data.redis.*` (không phải `spring.redis.*`)

### Vấn đề: Không tìm thấy cấu hình Mail
**Triệu chứng:** "Error sending email" khi đặt lại mật khẩu
**Giải pháp tạm thời:** Xác nhận biến môi trường MAIL_USERNAME và MAIL_PASSWORD đã được cài đặt

## Cải Thiện Hiệu Suất

Java 21 và Spring Boot 3.4.3 cung cấp:
- **Thời gian khởi động nhanh hơn:** cải thiện ~30% so với Java 8 + Spring Boot 2.6.4
- **Thu gom rác tốt hơn:** Tối ưu G1GC giảm thời gian tạm dừng
- **Sẵn sàng virtual threads:** Xử lý request async trong tương lai không cần thread pool
- **Hiệu quả bộ nhớ:** Quản lý bộ nhớ cải thiện trong Spring 3.x
- **Bảo mật:** Bản vá bảo mật mới nhất cho Java 21 LTS

## Lộ Trình Nâng Cấp Tương Lai

Java 21 LTS nhận cập nhật đến tháng 9, 2028. Kế hoạch cho:
- Java 23 (Tháng 9, 2023, non-LTS) - tính năng tiên phong
- Java 25 (Tháng 9, 2025, non-LTS) - cải tiến dần dần
- Java 27 (Tháng 9, 2027, ứng viên LTS) - phiên bản LTS tiếp theo

Spring Boot 3.x sẽ hỗ trợ Java 21 trong suốt chu kỳ phát hành.

## Tham Khảo

- [Java 21 Release Notes](https://docs.oracle.com/en/java/javase/21/release-notes/)
- [Spring Boot 3.4 Migration Guide](https://spring.io/blog/2024/02/26/spring-boot-3-2-released)
- [Spring Security 6.x Migration Guide](https://docs.spring.io/spring-security/reference/migration/index.html)
- [JJWT 0.12 Changelog](https://github.com/jwtk/jjwt/blob/master/CHANGELOG.md)
- [Jakarta EE Migration Guide](https://jakarta.ee/)

## Các Bước Sau Migration

Sau khi migration thành công:

1. **Xóa code cũ:**
   - Xóa ServletInitializer.java nếu chưa được xóa
   - Loại bỏ mọi import javax.* dự phòng

2. **Cập nhật CI/CD:**
   - Cập nhật GitHub Actions sử dụng JDK 21
   - Cập nhật các cấu hình pipeline build khác

3. **Giám sát:**
   - Ghi nhận các metrics hiệu suất
   - Theo dõi tỷ lệ lỗi cho bất kỳ vấn đề tương thích nào
   - Theo dõi cập nhật bảo mật cho Java 21 LTS

4. **Tài liệu:**
   - Cập nhật tài liệu onboarding của nhóm
   - Ghi nhận quyết định migration trong ADRs (Architecture Decision Records)
   - Chia sẻ bài học kinh nghiệm với nhóm

## Liên Hệ & Hỗ Trợ

Cho các vấn đề hoặc câu hỏi về migration:
- Xem hướng dẫn migration chính thức Spring Boot 3.x
- Kiểm tra tài liệu JJWT 0.12 cho thay đổi API
- Tham khảo Java 21 release notes cho thay đổi tính năng ngôn ngữ
