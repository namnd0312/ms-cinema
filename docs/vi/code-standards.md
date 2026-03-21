# Tiêu Chuẩn & Hướng Dẫn Viết Mã

**Dự án:** ms-cinema
**Phiên bản:** 1.0
**Cập nhật lần cuối:** Tháng 2 năm 2026

## Mục Đích

Tài liệu này thiết lập các quy ước viết mã, mẫu kiến trúc và tiêu chuẩn chất lượng cho dự án ms-cinema. Tất cả người đóng góp phải tuân thủ các tiêu chuẩn này để duy trì tính nhất quán, dễ đọc và bảo trì.

## Nguyên Tắc

**YAGNI** (You Aren't Gonna Need It)
- Chỉ triển khai tính năng được yêu cầu, tránh mã suy đoán
- Xóa mã chết ngay lập tức
- Trì hoãn quyết định kiến trúc cho đến khi cần thiết

**KISS** (Keep It Simple, Stupid)
- Ưu tiên giải pháp đơn giản
- Tránh thiết kế quá mức
- Viết mã cho con người trước, máy móc sau

**DRY** (Don't Repeat Yourself)
- Trích xuất logic chung thành phương thức/lớp tái sử dụng
- Sử dụng kế thừa, composition và lớp tiện ích
- Loại bỏ trùng lặp cấu hình

## Tổ Chức Tệp

### Cấu Trúc Package

```
com.namnd.cinema
├── config/              # Các lớp cấu hình
│   ├── security/        # Cấu hình Spring Security
│   ├── filter/          # Bộ lọc bảo mật
│   └── custom/          # Handler/processor tùy chỉnh
├── controller/          # REST controller
├── dto/                 # Đối tượng truyền dữ liệu
│   └── mapper/          # Mapper DTO
├── model/               # Model thực thể (JPA)
├── repository/          # Interface truy cập dữ liệu
├── service/             # Interface logic nghiệp vụ
│   └── impl/            # Triển khai service
└── exception/           # Exception tùy chỉnh (nếu thêm)
```

**Lý do:**
- Kiến trúc phân lớp hỗ trợ kiểm thử & bảo trì
- Phân tách trách nhiệm rõ ràng
- Dễ định vị mã theo tính năng

### Đặt Tên Tệp

**Lớp Java:** PascalCase, mô tả rõ ràng
```
✓ AuthController.java
✓ JwtAuthenticationFilter.java
✓ RegisterDtoMapper.java
✗ AC.java (quá viết tắt)
✗ AuthC.java (không rõ ràng)
```

**Tệp > 200 LOC:** Tách thành module nhỏ hơn
```
// Trước: LargeService.java (400 dòng)
// Sau:
├── UserService.java (interface)
├── impl/UserServiceImpl.java (100 dòng)
├── impl/UserValidationService.java (80 dòng)
└── impl/UserEncryptionService.java (60 dòng)
```

**Tệp cấu hình:** Chữ thường với dấu gạch ngang
```
application.yml
application-dev.yml
application-prod.yml
```

**Script SQL:** Mô tả với phiên bản
```
roles.sql (dữ liệu seed)
schema-v001-initial.sql (nếu đánh phiên bản)
```

## Quy Ước Viết Mã

### Phong Cách Mã Java

**Thụt lề & Định dạng**
- Sử dụng 4 dấu cách (không dùng tab)
- Độ dài dòng tối đa: 120 ký tự (xuống dòng tại điểm ngắt logic)
- Mỗi câu lệnh một dòng

```java
// ✓ Tốt
public ResponseEntity<?> authenticateUser(@RequestBody User user) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            user.getUsername(),
            user.getPassword()
        )
    );
    // ...
}

// ✗ Xấu
public ResponseEntity<?> authenticateUser(@RequestBody User user) {
    Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
}
```

**Quy Ước Đặt Tên**

| Loại | Phong cách | Ví dụ |
|------|------------|-------|
| Lớp | PascalCase | AuthController, UserService |
| Phương thức | camelCase | generateToken, validateUser |
| Biến | camelCase | userDetails, jwtToken |
| Hằng số | UPPER_SNAKE_CASE | DEFAULT_ROLE, MAX_TOKEN_AGE |
| Package | lowercase.dot.separated | com.namnd.cinema.service |

**Phạm vi & Access Modifier**
- Mặc định `private`, mở rộng chỉ khi cần thiết
- Dùng `protected` cho phương thức có thể kiểm thử trong lớp cơ sở
- Đánh dấu bean cấu hình là `public`

```java
// ✓ Tốt
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Public: triển khai interface
    @Override
    public void save(User user) { /* ... */ }

    // Private: phương thức nội bộ
    private User mapToEntity(RegisterDto dto) { /* ... */ }
}
```

**Chú Thích & Tài Liệu**

```java
// ✓ Tốt: Giải thích TẠI SAO, không phải CÁI GÌ
// BCrypt chống brute force tốt hơn hash đơn giản
private PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// ✗ Xấu: Hiển nhiên từ mã
// Tạo instance BCryptPasswordEncoder
private PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Javadoc cho API công khai**
```java
/**
 * Tạo JWT token cho người dùng đã xác thực.
 * Sử dụng thuật toán ký HS512 với thời hạn có thể cấu hình.
 *
 * @param authentication Đối tượng Authentication Spring Security với UserPrinciple
 * @return chuỗi JWT token đã ký
 * @throws IllegalArgumentException nếu authentication là null
 */
public String generateTokenLogin(Authentication authentication) {
    // ...
}
```

### Quy Ước Spring Framework

**Thứ tự Annotation:**
```java
// ✓ TỐT NHẤT: Pattern Spring Boot 3.x / Spring Security 6.x (SecurityFilterChain)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // JWT stateless không cần CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex ->
                ex.accessDeniedHandler(customAccessDeniedHandler())
            );
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

// ✓ Tốt: Annotation framework đặt trước trên lớp
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    // ...
}
```

**Dependency Injection** (Thực hành tốt nhất Spring Boot 3.x)
- **Ưu tiên mạnh:** Constructor injection qua Lombok @RequiredArgsConstructor
- **Tránh:** Field injection (@Autowired trên trường) - khó kiểm thử
- **Tránh:** Setter injection - vấn đề khởi tạo muộn

```java
// ✓ TỐT NHẤT: Constructor injection với Lombok
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}
```

**Ranh giới Transaction**
```java
// ✓ Tốt: Đánh dấu phương thức service với @Transactional
@Service
public class UserServiceImpl {
    @Transactional
    public void save(User user) {
        // Phương thức chạy trong transaction, tự động rollback khi exception
        userRepository.save(user);
    }
}
```

**Xử Lý Exception**
- Bắt exception cụ thể, tránh Exception chung
- Log ở mức phù hợp (error vs warn)
- Cung cấp thông báo lỗi có ý nghĩa

```java
// ✓ Tốt: Exception cụ thể, logging có ý nghĩa
public boolean validateJwtToken(String authToken) {
    try {
        Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken);
        return true;
    } catch (SignatureException e) {
        logger.error("Invalid JWT signature -> Message: {}", e.getMessage());
        return false;
    } catch (ExpiredJwtException e) {
        logger.warn("JWT token expired, requires re-authentication");
        return false;
    }
    // ... thêm các catch cụ thể
    return false;
}

// ✗ Xấu: Catch chung, mất thông tin
public boolean validateJwtToken(String authToken) {
    try {
        Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken);
        return true;
    } catch (Exception e) {
        return false;  // Thất bại im lặng
    }
}
```

## Tiêu Chuẩn Cơ Sở Dữ Liệu & ORM

**Đặt Tên Entity**
- Tên bảng: chữ thường, số nhiều (users, roles, user_roles)
- Tên cột: chữ thường, snake_case
- Tên lớp Entity: PascalCase, số ít (User, Role)

```java
// ✓ Tốt
@Entity
@Table(name = "users")
public class User {
    @Column(name = "user_id")
    private Long id;

    @Column(name = "full_name")
    private String fullName;
}

// ✗ Xấu
@Entity
@Table(name = "User")  // Sai kiểu chữ
public class User {
    @Column(name = "user_id")
    private Long id;

    @Column(name = "fullname")  // Không dùng snake_case
    private String fullName;
}
```

**Quan hệ**
- Chỉ dùng EAGER loading khi quan hệ luôn cần thiết
- Dùng LAZY loading làm mặc định, fetch có chiến lược
- Chú thích lý do chiến lược fetch

```java
// ✓ Tốt: EAGER có lý do (role cần trong SecurityContext)
@Entity
public class User {
    @ManyToMany(fetch = FetchType.EAGER)  // Chú thích lý do nếu không hiển nhiên
    @JoinTable(name = "user_roles",
        joinColumns = {@JoinColumn(name = "user_id")},
        inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<Role> roles;
}
```

**Phương thức truy vấn**
```java
// ✓ Tốt: Tên phương thức mô tả (mẫu repository)
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}

// ✗ Xấu: Không rõ trả về gì
public interface UserRepository extends JpaRepository<User, Long> {
    User getByUsername(String username);  // get vs find, optional?
    boolean userExists(String username);  // tên lạ
}
```

## Tiêu Chuẩn REST API

**Đặt Tên Endpoint**
- Dùng đường dẫn chữ thường
- Dùng danh từ cho tài nguyên (không dùng động từ)
- Dùng danh từ số nhiều cho collection

```
✓ POST /api/auth/login       (endpoint hành động OK cho động từ)
✓ POST /api/auth/register
✓ GET /api/users             (collection)
✓ GET /api/users/{id}        (tài nguyên)
✓ POST /api/users            (tạo)
✓ PUT /api/users/{id}        (cập nhật)
✓ DELETE /api/users/{id}     (xóa)
✓ POST /api/movies/{movieId}/ratings       (tài nguyên lồng nhau)
✓ GET /api/movies/{movieId}/comments       (collection lồng nhau)
✓ POST /api/comments/{commentId}/reactions (hành động lồng nhau)

✗ POST /api/auth/doLogin     (động từ thừa)
✗ GET /api/GetUserById       (không chữ thường)
✗ POST /api/createUser       (động từ trong đường dẫn)
```

**Mẫu Xóa Mềm**
```java
// Dùng ENUM status để đánh dấu đã xóa, lọc trong truy vấn repository
@Entity
public class MovieComment {
    @Enumerated(EnumType.STRING)
    private CommentStatus status;  // ACTIVE, DELETED
}
```

**Mẫu Phân Trang**
```java
// ✓ Tốt: Dùng interface Page của Spring Data
@GetMapping("/movies/{movieId}/comments")
public ResponseEntity<Page<MovieCommentDto>> listComments(
    @PathVariable Long movieId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    Page<MovieComment> comments = commentService.findByMovieId(movieId, PageRequest.of(page, size));
    return ResponseEntity.ok(comments.map(this::toDto));
}

// Phản hồi bao gồm metadata: totalElements, totalPages, currentPage, size
{
    "content": [ { comment1 }, { comment2 } ],
    "pageable": { "pageNumber": 0, "pageSize": 20, "totalElements": 150 },
    "totalPages": 8
}
```

**Mẫu Upsert (Đánh Giá)**
```java
// ✓ Tốt: Cập nhật nếu tồn tại, tạo nếu chưa có
@PostMapping("/movies/{movieId}/ratings")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> createOrUpdateRating(
    @PathVariable Long movieId,
    @RequestBody CreateRatingRequest request,
    @AuthenticationPrincipal UserPrincipal user) {
    MovieRating rating = ratingService.createOrUpdateRating(
        movieId, user.getId(), request.getRating());
    return ResponseEntity.ok(ratingToDto(rating));
}

// Triển khai Service
public MovieRating createOrUpdateRating(Long movieId, Long userId, Integer rating) {
    return ratingRepository.findByMovieIdAndUserId(movieId, userId)
        .map(existing -> {
            existing.setRating(rating);
            existing.setUpdatedAt(LocalDateTime.now());
            return ratingRepository.save(existing);
        })
        .orElseGet(() -> {
            MovieRating newRating = new MovieRating(movieId, userId, rating);
            return ratingRepository.save(newRating);
        });
}
```

**Mẫu Chuyển Đổi Phản Hồi**
```java
// ✓ Tốt: Chuyển đổi dựa trên loại phản hồi
@PostMapping("/comments/{commentId}/reactions")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> toggleReaction(
    @PathVariable Long commentId,
    @RequestBody CommentReactionRequest request,
    @AuthenticationPrincipal UserPrincipal user) {
    CommentReaction reaction = reactionService.toggleReaction(
        commentId, user.getId(), request.getReactionType());
    return ResponseEntity.ok(reactionToDto(reaction));
}

// Service: toggle xóa nếu cùng loại, thay thế nếu khác loại
public CommentReaction toggleReaction(Long commentId, Long userId, ReactionType type) {
    Optional<CommentReaction> existing = repository.findByCommentIdAndUserId(commentId, userId);
    if (existing.isPresent() && existing.get().getType() == type) {
        repository.delete(existing.get());
        return null;  // hoặc trả về DTO thành công
    }
    if (existing.isPresent()) {
        existing.get().setType(type);
        return repository.save(existing.get());
    }
    return repository.save(new CommentReaction(commentId, userId, type));
}
```

**Cấu Trúc Request/Response**
```java
// ✓ Tốt: Cấu trúc DTO rõ ràng
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest request) {
    // Trả về JwtResponseDto với các trường nhất quán
}

// Request
{
    "username": "john",
    "password": "pass123"
}

// Response (200 OK)
{
    "id": 1,
    "token": "eyJhbGc...",
    "type": "Bearer",
    "username": "john",
    "name": "John Doe",
    "roles": ["ROLE_USER"]
}
```

**Định Dạng Phản Hồi Lỗi**
```java
// Chuẩn hóa phản hồi lỗi
{
    "timestamp": "2026-02-10T15:30:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Username is already taken",
    "path": "/api/auth/register"
}
```

## Tiêu Chuẩn Cấu Hình

**Cấu trúc application.yml**
```yaml
# ✓ Tốt: Tổ chức theo mối quan tâm
server:
  port: 8080

spring:
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: 123456

namnd:
  app:
    jwtSecret: ${JWT_SECRET:bezKoderSecretKey}
    jwtExpiration: ${JWT_EXPIRATION:86400000}

logging:
  level:
    com.namnd.cinema: debug
```

**Biến Môi Trường**
- Dùng biến môi trường cho secret (jwtSecret, dbPassword)
- Không bao giờ hardcode giá trị nhạy cảm
- Tài liệu hóa tất cả yêu cầu biến môi trường

```bash
# .env (phát triển cục bộ, không commit)
JWT_SECRET=your-secret-key
DATABASE_PASSWORD=postgres_password
DATABASE_URL=jdbc:postgresql://localhost:5432/testdb
```

## Tiêu Chuẩn Kiểm Thử

Dùng tên test mô tả theo mẫu Given-When-Then:
```java
// ✓ Tốt
@Test
public void testAuthenticateUserWithValidCredentials_ReturnsJwtToken() { }

// Cấu trúc Arrange-Act-Assert với dependency được mock
```
- Không dùng cơ sở dữ liệu thật trong unit test (@MockBean, @WebMvcTest)
- Không kiểm thử hành vi Spring, chỉ kiểm thử mã của bạn
- Không bao giờ bỏ qua test thất bại

## Tiêu Chuẩn Bảo Mật

**Xử Lý Mật Khẩu**
- Luôn dùng BCryptPasswordEncoder, không bao giờ mật khẩu thuần
- Không bao giờ log mật khẩu
- Dùng PasswordEncoder.encode() trên đầu vào, chỉ so sánh giá trị đã mã hóa

```java
// ✓ Tốt
private final PasswordEncoder passwordEncoder;

public User registerUser(RegisterDto dto) {
    user.setPassword(passwordEncoder.encode(dto.getPassword()));
    return userRepository.save(user);
}

// ✗ Xấu
public User registerUser(RegisterDto dto) {
    user.setPassword(dto.getPassword());  // Mật khẩu thuần!
    return userRepository.save(user);
}
```

**Xử Lý Token**
- Xác thực chữ ký token trước khi tin tưởng claims
- Kiểm tra hết hạn trên mỗi lần truy cập
- Không bao giờ log toàn bộ token (cắt ngắn trong log)

```java
// ✓ Tốt: Log token cắt ngắn để gỡ lỗi
String truncatedToken = token.substring(0, 10) + "...";
logger.debug("Validating token: {}", truncatedToken);

// Không bao giờ lưu token trong cookie mà không có cờ HttpOnly
response.addHeader("Set-Cookie", "token=" + token + "; HttpOnly; Secure");
```

**Cấu Hình Nhạy Cảm**
- Lưu secret trong biến môi trường hoặc vault bảo mật
- Không bao giờ commit `.env`, `application-prod.yml` chứa secret
- Dùng GitCrypt hoặc tương tự cho cấu hình mã hóa trong repo

## Sử Dụng Lombok

**Khi nào dùng Lombok**
- @Data cho POJO với getter/setter/equals/hashCode
- @Slf4j cho khai báo logger
- @RequiredArgsConstructor cho dependency injection
- @Getter/@Setter cho trường chọn lọc

```java
// ✓ Tốt: Dùng Lombok để giảm mã boilerplate
@Data
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
}

// ✓ Tốt: Constructor injection với Lombok
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Constructor tự động tạo bởi Lombok
}
```

**Khi KHÔNG dùng Lombok**
- Tránh @Getter trên entity với collection lazy-loaded
- Không dùng @EqualsAndHashCode trên entity có quan hệ JPA
- Loại trừ khỏi mã sinh tự động nơi auto-generation gây vấn đề

## Tiêu Chuẩn Biên Dịch & Build

Trước commit: `mvn clean compile && mvn test`
Giữ pom.xml có tổ chức với quản lý dependency cho nhất quán phiên bản.

## Tiêu Chuẩn Tài Liệu

**Tệp Markdown:**
- Dùng tiêu đề rõ ràng (#, ##, ###)
- Bao gồm mục lục cho tệp > 100 dòng
- Cung cấp ví dụ mã
- Liên kết đến tài liệu liên quan

**Tài liệu mã:**
- Tài liệu hóa phương thức public bằng Javadoc
- Tài liệu hóa logic không hiển nhiên bằng chú thích inline
- Bao gồm ví dụ cho API phức tạp
- Tài liệu hóa giả định và tiền điều kiện

## Danh Sách Kiểm Tra Code Review

- Mã biên dịch không lỗi, tất cả test pass
- Phương thức dưới 30 dòng, quy ước đặt tên đúng
- Không có debug logging, không có secret trong commit
- Xử lý lỗi cho đầu vào null/không hợp lệ
- Javadoc trên phương thức public, thông điệp commit conventional

## Hướng Dẫn Tái Cấu Trúc

**Khi nào tái cấu trúc:**
- Lớp/phương thức vượt giới hạn kích thước (200 LOC, phương thức 30 dòng)
- Mã trùng lặp xuất hiện ở 3+ nơi
- Đặt tên không rõ ràng sau khi thêm chú thích
- Độ phức tạp cyclomatic > 10

**Cách tái cấu trúc an toàn:**
1. Đảm bảo test tồn tại và pass
2. Thực hiện thay đổi nhỏ, từng bước
3. Chạy test sau mỗi thay đổi
4. Commit thường xuyên
5. Tài liệu hóa lý do trong thông điệp commit

## Công Cụ & Tự Động Hóa

- Dùng IntelliJ IDEA CE hoặc Eclipse với code inspection bật
- Cấu hình Maven cho xác thực pre-commit: `mvn clean compile`
- Bật pre-commit hook để ngăn commit xấu

## Tiêu Chuẩn Redis

**Mẫu Key:**
- Dùng tiền tố mô tả phân cách bằng dấu hai chấm: `namespace:entity:identifier`
- Ví dụ: `blacklist:jti:abc123`, `notification:processed:event-uuid`
- Tài liệu hóa kỳ vọng TTL trong chú thích (chiến lược hết hạn tự động)

**Xử lý lỗi (Fail-Open vs Fail-Closed):**
```java
// Fail-Closed: từ chối khi Redis không khả dụng (bảo thủ, ưu tiên bảo mật)
// Ví dụ: kiểm tra danh sách đen token — thà gửi 401 còn hơn bỏ qua blacklist
return redisTemplate.opsForValue().get(key) != null;  // false khi lỗi = bị chặn

// Fail-Open: tiếp tục khi Redis không khả dụng (ưu tiên khả dụng)
// Ví dụ: dedup sự kiện — thà gửi trùng còn hơn mất thông báo
try {
    Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
    return Boolean.TRUE.equals(result);
} catch (Exception e) {
    log.warn("Redis unavailable, proceeding: {}", e.getMessage());
    return true;  // fail-open: cho phép xử lý
}
```

Tài liệu hóa chiến lược mỗi dịch vụ sử dụng và lý do trong chú thích.

## Mẫu Hướng Sự Kiện (Kafka)

**Phát sự kiện:**
```java
// ✓ Tốt: Dùng TransactionalEventListener cho phát sau commit
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment completePayment(Long paymentId) {
        Payment payment = paymentRepository.save(/* ... */);
        // Chỉ phát sau khi DB commit thành công
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            paymentId, payment.getAmount(), "COMPLETED");
        eventPublisher.publishEvent(event);
        return payment;
    }
}

// ✗ Xấu: Phát trước khi transaction commit
public Payment completePayment(Long paymentId) {
    PaymentCompletedEvent event = new PaymentCompletedEvent(/* ... */);
    kafkaTemplate.send("topic", event);  // Gửi trước khi save commit
    paymentRepository.save(/* ... */);
}
```

**Tiêu thụ sự kiện:**
```java
// ✓ Tốt: Topic cụ thể, error handler, retry
@KafkaListener(topics = "payment-events", groupId = "booking-service")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    bookingService.confirmBooking(event.getBookingId());
}

// Error handler với retry (cấu hình trong application.yml)
// spring.kafka.listener.error-handler: DefaultErrorHandler
// spring.kafka.listener.ack-mode: manual_immediate
```

## Mẫu Lịch Sử Mật Khẩu

**Ngăn tái sử dụng mật khẩu:**
```java
// Entity: theo dõi 3 mật khẩu gần nhất
@Entity
@Table(name = "password_history")
public class PasswordHistory {
    @Id @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}

// Service: xác thực mật khẩu mới với 3 hash gần nhất
public boolean isPasswordReused(Long userId, String newPassword) {
    List<PasswordHistory> recent = repository
        .findTop3ByUserIdOrderByCreatedAtDesc(userId);
    return recent.stream()
        .anyMatch(ph -> passwordEncoder.matches(newPassword, ph.getPasswordHash()));
}

// Endpoint: đổi mật khẩu (yêu cầu xác thực)
@PostMapping("/api/auth/change-password")
@PreAuthorize("isAuthenticated()")
@Transactional
public ResponseEntity<?> changePassword(
    @RequestBody ChangePasswordDto request,
    @AuthenticationPrincipal UserDetails user) {
    // Xác thực mật khẩu hiện tại, kiểm tra tái sử dụng, cập nhật, lưu vào lịch sử
}
```

## Mẫu Không Dùng Nữa (Tránh)

| Mẫu | Lý do | Thay thế |
|------|-------|----------|
| Field injection (@Autowired trên trường) | Khó kiểm thử | Constructor injection |
| Số ma thuật | Ý nghĩa không rõ | Hằng số có tên |
| Catch Exception chung | Mất loại lỗi | Catch exception cụ thể |
| Logic xác thực tùy chỉnh rải rác | Trùng lặp | @Valid + validator |
