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
- **Strongly Prefer:** Constructor injection for immutability and testability
- **Acceptable:** Lombok @RequiredArgsConstructor for boilerplate reduction
- **Avoid:** Field injection (@Autowired on fields) - harder to test
- **Avoid:** Setter injection - late initialization issues

```java
// ✓ BEST: Constructor injection (Spring 3.x pattern)
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Constructor auto-generated by Lombok
}

// ✓ Good: Explicit constructor (no Lombok)
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
}

// ✗ Avoid: Field injection (legacy, harder to test)
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;
}

// ✗ Avoid: Setter injection (late initialization)
@Service
public class UserServiceImpl implements UserService {
    private UserRepository userRepository;

    @Autowired
    public void setUserRepository(UserRepository repo) {
        this.userRepository = repo;
    }
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
// ✓ Good: Use status enum or deleted flag
@Entity
public class MovieComment {
    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private CommentStatus status;  // ACTIVE, DELETED

    @PreUpdate
    public void softDelete() {
        this.status = CommentStatus.DELETED;
        this.updatedAt = LocalDateTime.now();
    }
}

// In repository, filter by status
@Query("SELECT c FROM MovieComment c WHERE c.movieId = :movieId AND c.status = 'ACTIVE'")
List<MovieComment> findActiveCommentsByMovieId(@Param("movieId") Long movieId);
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

**Test Naming**
```java
// ✓ Good: Descriptive, follows Given-When-Then
@Test
public void testAuthenticateUserWithValidCredentials_ReturnsJwtToken() {
    // Given: valid user in database
    // When: authenticate with username/password
    // Then: JWT token returned
}

// ✗ Bad: Unclear what's being tested
@Test
public void testLogin() {
    // What's being tested?
}
```

**Test Structure (AAA Pattern)**
```java
@Test
public void testRegisterNewUser_CreatesUserAndAssignsRoles() {
    // Arrange: setup test data
    RegisterDto registerDto = new RegisterDto("jane", "pass123", "Jane Doe",
        Set.of(new Role(null, "ROLE_USER")));

    // Act: perform action
    ResponseEntity<String> response = authController.registerUser(registerDto);

    // Assert: verify results
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(userService.existsByUsername("jane"));
}
```

**Avoid Common Test Mistakes**
- Don't use real database in unit tests (use @MockBean, @WebMvcTest)
- Don't test Spring Framework behavior (test your code)
- Don't ignore failing tests (fix them immediately)

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

**Before Commit:**
1. Run `mvn clean compile` to verify no syntax errors
2. Run `mvn test` to verify tests pass
3. Run `mvn spotbugs:check` for bug detection (if configured)
4. Fix all warnings before committing

**Maven Configuration:**
- Keep pom.xml clean (alphabetize dependencies)
- Use dependency management for version consistency
- Exclude transitive dependencies causing conflicts

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

Before submitting PR:
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Code follows naming conventions
- [ ] Methods under 30 lines (unless justified)
- [ ] No debug logging left in code
- [ ] No secrets in commits
- [ ] Error handling for null/invalid input
- [ ] Javadoc on public methods
- [ ] Commit messages follow conventional commits

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

**IDE Setup:**
- Use IntelliJ IDEA CE (free) or Eclipse
- Enable code inspections
- Set up Maven plugin for validation
- Configure pre-commit hooks

**Pre-commit Hooks (Optional):**
```bash
#!/bin/bash
mvn clean compile
if [ $? -ne 0 ]; then
    echo "Build failed, commit aborted"
    exit 1
fi
```

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

## Deprecated Patterns (Avoid)

| Pattern | Reason | Alternative |
|---------|--------|-------------|
| Field injection (@Autowired on fields) | Hard to test | Constructor injection |
| Default methods without @RequestMapping | Ambiguous routing | Explicit @PostMapping |
| Magic numbers (86400000) | Unclear meaning | Named constant |
| Catching generic Exception | Loses error type | Catch specific exceptions |
| Custom validation logic | Scattered | @Valid + annotation validators |
