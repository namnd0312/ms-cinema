# Code Standards & Guidelines

**Project:** ms-cinema
**Version:** 1.0
**Last Updated:** February 2026

## Purpose

This document establishes coding conventions, architectural patterns, and quality standards for the ms-cinema project. All contributors must adhere to these standards to maintain consistency, readability, and maintainability.

## Principles

**YAGNI** (You Aren't Gonna Need It)
- Implement only features requested, avoid speculative code
- Remove dead code immediately
- Delay architecture decisions until necessary

**KISS** (Keep It Simple, Stupid)
- Prefer straightforward solutions
- Avoid over-engineering
- Write code for humans first, machines second

**DRY** (Don't Repeat Yourself)
- Extract common logic into reusable methods/classes
- Use inheritance, composition, and utility classes
- Deduplicate configuration

## File Organization

### Package Structure

```
com.namnd.cinema
├── config/              # Configuration classes
│   ├── security/        # Spring Security config
│   ├── filter/          # Security filters
│   └── custom/          # Custom handlers/processors
├── controller/          # REST controllers
├── dto/                 # Data transfer objects
│   └── mapper/          # DTO mappers
├── model/               # Entity models (JPA)
├── repository/          # Data access interfaces
├── service/             # Business logic interfaces
│   └── impl/            # Service implementations
└── exception/           # Custom exceptions (if added)
```

**Rationale:**
- Layered architecture supports testing & maintenance
- Clear responsibility separation
- Easy to locate code by feature

### File Naming

**Java Classes:** PascalCase, descriptive
```
✓ AuthController.java
✓ JwtAuthenticationFilter.java
✓ RegisterDtoMapper.java
✗ AC.java (too abbreviated)
✗ AuthC.java (unclear)
```

**Files > 200 LOC:** Split into smaller modules
```
// Before: LargeService.java (400 lines)
// After:
├── UserService.java (interface)
├── impl/UserServiceImpl.java (100 lines)
├── impl/UserValidationService.java (80 lines)
└── impl/UserEncryptionService.java (60 lines)
```

**Configuration Files:** Lowercase with hyphens
```
application.yml
application-dev.yml
application-prod.yml
```

**SQL Scripts:** descriptive with version
```
roles.sql (seed data)
schema-v001-initial.sql (if versioned)
```

## Coding Conventions

### Java Code Style

**Indentation & Formatting**
- Use 4 spaces (no tabs)
- Max line length: 120 characters (wrapped at logical breakpoints)
- One statement per line

```java
// ✓ Good
public ResponseEntity<?> authenticateUser(@RequestBody User user) {
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            user.getUsername(),
            user.getPassword()
        )
    );
    // ...
}

// ✗ Bad
public ResponseEntity<?> authenticateUser(@RequestBody User user) {
    Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getUsername(), user.getPassword()));
}
```

**Naming Conventions**

| Type | Style | Example |
|------|-------|---------|
| Classes | PascalCase | AuthController, UserService |
| Methods | camelCase | generateToken, validateUser |
| Variables | camelCase | userDetails, jwtToken |
| Constants | UPPER_SNAKE_CASE | DEFAULT_ROLE, MAX_TOKEN_AGE |
| Packages | lowercase.dot.separated | com.namnd.cinema.service |

**Visibility & Access Modifiers**
- Default to `private`, expand only when necessary
- Use `protected` for testable methods in base classes
- Mark configuration beans as `public`

```java
// ✓ Good
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // Public: implements interface
    @Override
    public void save(User user) { /* ... */ }

    // Private: internal method
    private User mapToEntity(RegisterDto dto) { /* ... */ }
}
```

**Comments & Documentation**

```java
// ✓ Good: Explain WHY, not WHAT
// BCrypt is more resistant to brute force than simple hash
private PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}

// ✗ Bad: Obvious from code
// Create a BCryptPasswordEncoder instance
private PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

**Javadoc for Public APIs**
```java
/**
 * Generates JWT token for authenticated user.
 * Uses HS512 signature algorithm with configurable expiration.
 *
 * @param authentication Spring Security Authentication object with UserPrinciple
 * @return signed JWT token string
 * @throws IllegalArgumentException if authentication is null
 */
public String generateTokenLogin(Authentication authentication) {
    // ...
}
```

### Spring Framework Conventions

**Annotation Order:**
```java
// ✓ BEST: Spring Boot 3.x / Spring Security 6.x pattern (SecurityFilterChain)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // Stateless JWT doesn't need CSRF
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

// ✓ Good: Framework annotations first on class
@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {
    // ...
}
```

**Dependency Injection** (Spring Boot 3.x Best Practice)
- **Strongly Prefer:** Constructor injection via Lombok @RequiredArgsConstructor
- **Avoid:** Field injection (@Autowired on fields) - harder to test
- **Avoid:** Setter injection - late initialization issues

```java
// ✓ BEST: Constructor injection with Lombok
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
}
```

**Transactional Boundaries**
```java
// ✓ Good: Mark service methods with @Transactional
@Service
public class UserServiceImpl {
    @Transactional
    public void save(User user) {
        // Method runs in transaction, auto-rollback on exception
        userRepository.save(user);
    }
}
```

**Audit Logging Pattern (After-Commit Event Publishing)**
```java
// ✓ Good: Publish audit events after transaction commit
@Service
@RequiredArgsConstructor
public class UserServiceImpl {
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    @Transactional
    @Auditable(action = "CREATE_USER")
    public User createUser(UserDto dto) {
        User user = new User(dto);
        userRepository.save(user);
        // Event published AFTER commit, not before
        return user;
    }
}

// Listener captures event after transaction commit
@Component
@RequiredArgsConstructor
public class AuditAfterCommitListener {
    private final AuditEventPublisher auditPublisher;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAuditEvent(AuditSpringEvent event) {
        // Send to Kafka only after DB commit succeeds
        auditPublisher.publishAuditEvent(event.getAuditEvent());
    }
}
```

**Exception Handling**
- Catch specific exceptions, avoid generic Exception
- Log at appropriate level (error vs warn)
- Provide meaningful error messages

```java
// ✓ Good: Specific exceptions, meaningful logging
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
    // ... more specific catches
    return false;
}

// ✗ Bad: Generic catch, loses information
public boolean validateJwtToken(String authToken) {
    try {
        Jwts.parser().setSigningKey(SECRET_KEY).parseClaimsJws(authToken);
        return true;
    } catch (Exception e) {
        return false;  // Silent failure
    }
}
```

## Database & ORM Standards

**Entity Naming**
- Table names: lowercase, plural (users, roles, user_roles)
- Column names: lowercase, snake_case
- Entity class names: PascalCase, singular (User, Role)

```java
// ✓ Good
@Entity
@Table(name = "users")
public class User {
    @Column(name = "user_id")
    private Long id;

    @Column(name = "full_name")
    private String fullName;
}

// ✗ Bad
@Entity
@Table(name = "User")  // Wrong case
public class User {
    @Column(name = "user_id")
    private Long id;

    @Column(name = "fullname")  // No snake_case
    private String fullName;
}
```

**Relationships**
- Use EAGER loading only when relationship is always needed
- Use LAZY loading as default, fetch strategically
- Comment on fetch strategy rationale

```java
// ✓ Good: EAGER justified (roles needed in SecurityContext)
@Entity
public class User {
    @ManyToMany(fetch = FetchType.EAGER)  // Comment why if non-obvious
    @JoinTable(name = "user_roles",
        joinColumns = {@JoinColumn(name = "user_id")},
        inverseJoinColumns = {@JoinColumn(name = "role_id")})
    private Set<Role> roles;
}
```

**Query Methods**
```java
// ✓ Good: Descriptive method names (repository pattern)
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}

// ✗ Bad: Unclear what gets returned
public interface UserRepository extends JpaRepository<User, Long> {
    User getByUsername(String username);  // get vs find, optional?
    boolean userExists(String username);  // awkward
}
```

## REST API Standards

**Endpoint Naming**
- Use lowercase paths
- Use nouns for resources (not verbs)
- Use plural nouns for collections

```
✓ POST /api/auth/login       (action endpoints OK for verbs)
✓ POST /api/auth/register
✓ GET /api/users             (collection)
✓ GET /api/users/{id}        (resource)
✓ POST /api/users            (create)
✓ PUT /api/users/{id}        (update)
✓ DELETE /api/users/{id}     (delete)
✓ POST /api/movies/{movieId}/ratings       (nested resource)
✓ GET /api/movies/{movieId}/comments       (nested collection)
✓ POST /api/comments/{commentId}/reactions (nested action)

✗ POST /api/auth/doLogin     (redundant verb)
✗ GET /api/GetUserById       (not lowercase)
✗ POST /api/createUser       (verb in path)
```

**Soft-Delete Pattern**
```java
// Use status ENUM to mark deleted, filter in repository queries
@Entity
public class MovieComment {
    @Enumerated(EnumType.STRING)
    private CommentStatus status;  // ACTIVE, DELETED
}
```

**Pagination Pattern**
```java
// ✓ Good: Use Spring Data Page interface
@GetMapping("/movies/{movieId}/comments")
public ResponseEntity<Page<MovieCommentDto>> listComments(
    @PathVariable Long movieId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    Page<MovieComment> comments = commentService.findByMovieId(movieId, PageRequest.of(page, size));
    return ResponseEntity.ok(comments.map(this::toDto));
}

// Response includes metadata: totalElements, totalPages, currentPage, size
{
    "content": [ { comment1 }, { comment2 } ],
    "pageable": { "pageNumber": 0, "pageSize": 20, "totalElements": 150 },
    "totalPages": 8
}
```

**Upsert Pattern (Rating)**
```java
// ✓ Good: Update if exists, create if not
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

// Service implementation
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

**Reaction Toggle Pattern**
```java
// ✓ Good: Toggle based on reaction type
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

// Service: toggle removes if same type, replaces if different type
public CommentReaction toggleReaction(Long commentId, Long userId, ReactionType type) {
    Optional<CommentReaction> existing = repository.findByCommentIdAndUserId(commentId, userId);
    if (existing.isPresent() && existing.get().getType() == type) {
        repository.delete(existing.get());
        return null;  // or return success DTO
    }
    if (existing.isPresent()) {
        existing.get().setType(type);
        return repository.save(existing.get());
    }
    return repository.save(new CommentReaction(commentId, userId, type));
}
```

**Request/Response Structure**
```java
// ✓ Good: Clear DTO structure
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest request) {
    // Returns JwtResponseDto with consistent fields
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

**Error Response Format**
```java
// Standardize error responses
{
    "timestamp": "2026-02-10T15:30:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Username is already taken",
    "path": "/api/auth/register"
}
```

## Configuration Standards

**application.yml Structure**
```yaml
# ✓ Good: Organized by concern
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

**Environment Variables**
- Use env vars for secrets (jwtSecret, dbPassword)
- Never hardcode sensitive values
- Document all env var requirements

```bash
# .env (local development, not committed)
JWT_SECRET=your-secret-key
DATABASE_PASSWORD=postgres_password
DATABASE_URL=jdbc:postgresql://localhost:5432/testdb
```

## Testing Standards

Use descriptive test names following Given-When-Then pattern:
```java
// ✓ Good
@Test
public void testAuthenticateUserWithValidCredentials_ReturnsJwtToken() { }

// Arrange-Act-Assert structure with mocked dependencies
```
- Don't use real database in unit tests (@MockBean, @WebMvcTest)
- Don't test Spring behavior, only your code
- Never ignore failing tests

## Security Standards

**Password Handling**
- Always use BCryptPasswordEncoder, never plain passwords
- Never log passwords
- Use PasswordEncoder.encode() on input, only compare encoded values

```java
// ✓ Good
private final PasswordEncoder passwordEncoder;

public User registerUser(RegisterDto dto) {
    user.setPassword(passwordEncoder.encode(dto.getPassword()));
    return userRepository.save(user);
}

// ✗ Bad
public User registerUser(RegisterDto dto) {
    user.setPassword(dto.getPassword());  // Plain password!
    return userRepository.save(user);
}
```

**Token Handling**
- Validate token signature before trusting claims
- Check expiration on every access
- Never log full tokens (truncate in logs)

```java
// ✓ Good: Log truncated token for debugging
String truncatedToken = token.substring(0, 10) + "...";
logger.debug("Validating token: {}", truncatedToken);

// Never store tokens in cookie without HttpOnly flag
response.addHeader("Set-Cookie", "token=" + token + "; HttpOnly; Secure");
```

**Sensitive Configuration**
- Store secrets in environment variables or secure vaults
- Never commit `.env`, `application-prod.yml` with secrets
- Use GitCrypt or similar for encrypted config in repo

## Lombok Usage

**When to Use Lombok**
- @Data for POJOs with getters/setters/equals/hashCode
- @Slf4j for logger declaration
- @RequiredArgsConstructor for dependency injection
- @Getter/@Setter for selective fields

```java
// ✓ Good: Use Lombok to reduce boilerplate
@Data
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;
}

// ✓ Good: Constructor injection with Lombok
@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Constructor auto-generated by Lombok
}
```

**When NOT to Use Lombok**
- Avoid @Getter on entities with lazy-loaded collections
- Don't use @EqualsAndHashCode on entities with JPA relationships
- Exclude from generated code where auto-generation causes issues

## Compilation & Build Standards

Before commit: `mvn clean compile && mvn test`
Keep pom.xml organized with dependency management for version consistency.

## Documentation Standards

**Markdown Files:**
- Use clear headings (#, ##, ###)
- Include table of contents for files > 100 lines
- Provide code examples
- Link to related docs

**Code Documentation:**
- Document public methods with Javadoc
- Document non-obvious logic with inline comments
- Include examples for complex APIs
- Document assumptions and preconditions

## Code Review Checklist

- Code compiles without errors, all tests pass
- Methods under 30 lines, proper naming conventions
- No debug logging, no secrets in commits
- Error handling for null/invalid input
- Javadoc on public methods, conventional commit messages

## Refactoring Guidelines

**When to Refactor:**
- Class/method exceeds size limits (200 LOC, 30 line methods)
- Duplicate code appears in 3+ places
- Naming is unclear after adding comments
- Cyclomatic complexity > 10

**How to Refactor Safely:**
1. Ensure tests exist and pass
2. Make small, incremental changes
3. Run tests after each change
4. Commit frequently
5. Document rationale in commit message

## Tools & Automation

- Use IntelliJ IDEA CE or Eclipse with code inspections enabled
- Configure Maven for pre-commit validation: `mvn clean compile`
- Enable pre-commit hooks to prevent bad commits

## Redis Standards

**Key Patterns:**
- Use descriptive prefixes separated by colons: `namespace:entity:identifier`
- Examples: `blacklist:jti:abc123`, `notification:processed:event-uuid`
- Document TTL expectations in comments (auto-expiration strategy)

**Error Handling (Fail-Open vs Fail-Closed):**
```java
// Fail-Closed: reject on Redis unavailable (conservative, security-first)
// Example: token blacklist check — rather send 401 than skip blacklist
return redisTemplate.opsForValue().get(key) != null;  // false on error = blocked

// Fail-Open: proceed on Redis unavailable (availability-first)
// Example: event deduplication — rather send duplicate than lose notification
try {
    Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
    return Boolean.TRUE.equals(result);
} catch (Exception e) {
    log.warn("Redis unavailable, proceeding: {}", e.getMessage());
    return true;  // fail-open: allow processing
}
```

Document which strategy each service uses and rationale in comments.

## Event-Driven Patterns (Kafka)

**Publishing Events:**
```java
// ✓ Good: Use TransactionalEventListener for after-commit publishing
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Payment completePayment(Long paymentId) {
        Payment payment = paymentRepository.save(/* ... */);
        // Publish only after DB commit succeeds
        PaymentCompletedEvent event = new PaymentCompletedEvent(
            paymentId, payment.getAmount(), "COMPLETED");
        eventPublisher.publishEvent(event);
        return payment;
    }
}

// ✗ Bad: Publish before transaction commits
public Payment completePayment(Long paymentId) {
    PaymentCompletedEvent event = new PaymentCompletedEvent(/* ... */);
    kafkaTemplate.send("topic", event);  // Sends before save commits
    paymentRepository.save(/* ... */);
}
```

**Consuming Events:**
```java
// ✓ Good: Specific topic, error handler, retries
@KafkaListener(topics = "payment-events", groupId = "booking-service")
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    bookingService.confirmBooking(event.getBookingId());
}

// Error handler with retries (configured in application.yml)
// spring.kafka.listener.error-handler: DefaultErrorHandler
// spring.kafka.listener.ack-mode: manual_immediate
```

## Password History Pattern

**Preventing Reused Passwords:**
```java
// Entity: tracks last 3 passwords
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

// Service: validate new password against last 3 hashes
public boolean isPasswordReused(Long userId, String newPassword) {
    List<PasswordHistory> recent = repository
        .findTop3ByUserIdOrderByCreatedAtDesc(userId);
    return recent.stream()
        .anyMatch(ph -> passwordEncoder.matches(newPassword, ph.getPasswordHash()));
}

// Endpoint: change password (requires authentication)
@PostMapping("/api/auth/change-password")
@PreAuthorize("isAuthenticated()")
@Transactional
public ResponseEntity<?> changePassword(
    @RequestBody ChangePasswordDto request,
    @AuthenticationPrincipal UserDetails user) {
    // Validate current password, check reuse, update, save to history
}
```

## Feign Client Standards

**Feign Client Declaration (Service-to-Service Calls):**
```java
// ✓ Good: Typed Feign client with clear contracts
@FeignClient(name = "movie-service", url = "http://localhost:8082")
public interface MovieServiceClient {
    @GetMapping("/api/showtimes/{showtimeId}")
    ShowtimeDto getShowtime(@PathVariable Long showtimeId);

    @GetMapping("/api/theaters/{theaterId}/seats")
    List<SeatDto> getSeats(@PathVariable Long theaterId);
}

// Service usage with error handling
@Service
@RequiredArgsConstructor
public class BookingServiceImpl {
    private final MovieServiceClient movieClient;

    @Transactional
    public Booking reserve(ReserveRequest request) {
        try {
            ShowtimeDto showtime = movieClient.getShowtime(request.getShowtimeId());
            List<SeatDto> seats = movieClient.getSeats(showtime.getTheaterId());
            // Process booking...
        } catch (FeignException.NotFound e) {
            throw new EntityNotFoundException("Showtime not found");
        } catch (FeignException e) {
            throw new ServiceUnavailableException("Movie service unavailable");
        }
    }
}

// ✗ Bad: Raw RestTemplate or hardcoded URLs
RestTemplate restTemplate = new RestTemplate();
String url = "http://movie-service:8082/api/showtimes/" + showtimeId;
ShowtimeDto showtime = restTemplate.getForObject(url, ShowtimeDto.class);
```

**Error Handling with Feign:**
```java
// ✓ Good: Decode errors and handle specific cases
@FeignClient(name = "movie-service", decoder = ErrorDecoder.class)
public interface MovieServiceClient { }

@Component
public class CustomErrorDecoder implements ErrorDecoder {
    @Override
    public Exception decode(String methodKey, Response response) {
        if (response.status() == 404) {
            return new EntityNotFoundException("Resource not found");
        }
        if (response.status() == 503) {
            return new ServiceUnavailableException("Service temporarily unavailable");
        }
        return new FeignException.ServerErrorException(
            response.status(),
            response.reason(),
            response.request(),
            response.body().asInputStream().toString().getBytes()
        );
    }
}
```

**Feign + Hystrix (Circuit Breaker Pattern - for resilience):**
```yaml
# application.yml
feign:
  client:
    default-config: default
    config:
      movie-service:
        connectTimeout: 5000
        readTimeout: 10000
        loggerLevel: FULL
        errorDecoder: com.namnd.cinema.config.CustomErrorDecoder

resilience4j:
  circuitbreaker:
    instances:
      movie-service:
        registerHealthIndicator: true
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        failureRateThreshold: 50.0
```

## WebSocket Patterns (STOMP Over SockJS - NEW March 22, 2026)

**WebSocket Configuration (Backend):**
```java
// ✓ Good: Spring WebSocket with STOMP broker
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SockJsWebSocketHandler(), "/ws")
            .setAllowedOrigins("*");  // Or specify allowed origins
    }

    @Configuration
    @EnableWebSocketMessageBroker
    public static class BrokerConfig extends AbstractWebSocketMessageBrokerConfigurer {
        @Override
        public void configureMessageBroker(MessageBrokerRegistry config) {
            config.enableSimpleBroker("/topic/");
            config.setApplicationDestinationPrefixes("/app");
        }

        @Override
        public void registerStompEndpoints(StompEndpointRegistry registry) {
            registry.addEndpoint("/ws")
                .setAllowedOrigins("*")
                .withSockJS();
        }
    }
}

// ✓ Good: Publish to specific topic per user/resource
@Service
@RequiredArgsConstructor
public class SeatWebSocketPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishSeatStatusChange(SeatStatusMessage message) {
        messagingTemplate.convertAndSend(
            "/topic/showtime/" + message.getShowtimeId() + "/seats",
            message
        );
    }
}

// ✗ Bad: Broadcast to all users without filtering
messagingTemplate.convertAndSendToUsers("*", "/queue/updates", message);
```

**Message DTO Pattern (Type-Safe):**
```java
// ✓ Good: Clear, type-safe message structure
@Data
@AllArgsConstructor
public class SeatStatusMessage {
    private Long showtimeId;
    private String seatId;
    private String status;  // AVAILABLE, LOCKED, RESERVED
    private Long userId;
    private String action;  // LOCK, RESERVE, CANCEL
}

// ✗ Bad: Generic Map, type-unsafe
Map<String, Object> message = new HashMap<>();
message.put("showtimeId", 123);
message.put("action", "LOCK");
```

**Frontend WebSocket Consumer (TypeScript):**
```typescript
// ✓ Good: Structured STOMP client with reconnect logic
import SockJS from 'sockjs-client';
import Stomp, { Client } from '@stomp/stompjs';

@Injectable()
export class SeatWebSocketService {
    private stompClient: Client | null = null;
    private reconnectAttempt = 0;
    private readonly MAX_RECONNECT_ATTEMPTS = 5;
    private readonly RECONNECT_DELAYS = [1000, 2000, 4000, 8000, 16000, 30000];

    connect(token: string, onMessage: (msg: any) => void): void {
        const socket = new SockJS('http://localhost/ws');
        this.stompClient = Stomp.over(socket);

        this.stompClient.connect(
            { 'Authorization': `Bearer ${token}` },
            () => {
                this.reconnectAttempt = 0;
                this.stompClient!.subscribe(`/topic/showtime/123/seats`, onMessage);
            },
            (error) => {
                this.handleConnectionError(error, token, onMessage);
            }
        );
    }

    private handleConnectionError(
        error: any,
        token: string,
        onMessage: (msg: any) => void
    ): void {
        if (this.reconnectAttempt < this.MAX_RECONNECT_ATTEMPTS) {
            const delay = this.RECONNECT_DELAYS[this.reconnectAttempt];
            setTimeout(() => this.connect(token, onMessage), delay);
            this.reconnectAttempt++;
        }
    }
}

// ✗ Bad: No error handling or reconnect
this.stompClient.connect({ headers }, () => {
    this.stompClient.subscribe('/topic/seats', (msg) => { });
});
```

**Nginx Proxy for WebSocket (NEW March 22, 2026):**
```nginx
# ✓ Good: Proper WebSocket proxy with upgrade headers
location /ws/ {
    proxy_pass http://booking-service:8083;
    proxy_http_version 1.1;

    # WebSocket upgrade headers
    proxy_set_header Connection $http_connection;
    proxy_set_header Upgrade $http_upgrade;

    # Standard proxy headers
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    # Prevent timeout
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}

# ✗ Bad: Missing upgrade headers
location /ws/ {
    proxy_pass http://booking-service:8083;
    # WebSocket handshake fails without upgrade headers
}
```

**Security Considerations:**
- Always validate JWT during WebSocket handshake (Spring Security integration recommended)
- Use wss:// (WebSocket over TLS) in production
- Implement topic-based authorization (users can only subscribe to their own data)
- Set connection timeouts to prevent zombie connections

## Spring Batch Patterns (NEW March 31, 2026)

**Batch Job Configuration:**
- Use @Configuration class with @EnableBatchProcessing
- Define Job and Step as @Bean methods in config class
- Register Step in Job using StepBuilderFactory
- Set chunk size (e.g., 100) to balance memory vs. database round-trips

**Implementing Batch Components:**
```java
// Reader: Implement ItemReader<T> or extend JdbcCursorItemReader/RepositoryItemReader
public class LocalPaymentReader implements ItemReader<Payment> {
  @Override public Payment read() throws Exception { ... }
}

// Processor: Implement ItemProcessor<I, O> for transformation/filtering
public class ReconciliationProcessor implements ItemProcessor<Payment, ReconciliationItem> {
  @Override public ReconciliationItem process(Payment payment) throws Exception { ... }
}

// Writer: Implement ItemWriter<T> for batch persistence
public class ReconciliationItemWriter implements ItemWriter<ReconciliationItem> {
  @Override public void write(List<ReconciliationItem> items) throws Exception { ... }
}

// Listener: Implement StepExecutionListener or JobExecutionListener
public class ReconciliationJobListener implements JobExecutionListener {
  @Override public void afterJob(JobExecution execution) { ... }
}
```

**Best Practices:**
- Chunking: Break large datasets into chunks (100-1000 records) to manage memory
- Idempotency: Design processors to be re-enterable (safe if job restarts)
- Error Handling: Implement SkipListener/RetryListener for transient failures
- Logging: Use JobRepository to track execution; log start/end/status
- Scheduling: Use @Scheduled cron for daily/hourly jobs; disable auto-run with spring.batch.job.enabled=false
- Database Init: Use spring.batch.jdbc.initialize-schema=always to auto-create metadata tables
- Transaction Management: Chunked writing auto-wraps in @Transactional; custom listeners may need explicit tx

## Deprecated Patterns (Avoid)

| Pattern | Reason | Alternative |
|---------|--------|-------------|
| Field injection (@Autowired on fields) | Hard to test | Constructor injection |
| Magic numbers | Unclear meaning | Named constants |
| Catching generic Exception | Loses error type | Catch specific exceptions |
| Custom validation logic scattered | Duplication | @Valid + validators |
