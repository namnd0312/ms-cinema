# Research Report: Movie-Service Patterns & Architecture

**Date:** 2026-03-12 | **Module:** movie-service

## Package Structure

```
com.namnd.movieservice/
├── model/              # JPA entities (Movie, Theater, Seat, Showtime, enums)
├── repository/         # Spring Data JPA repositories (MovieRepository, etc.)
├── service/            # Interfaces (MovieService, TheaterService, ShowtimeService)
├── service/impl/       # Implementation classes (*ServiceImpl)
├── dto/                # DTOs and request objects (MovieDto, CreateMovieRequest, etc.)
├── controller/         # REST controllers (*Controller)
├── config/             # Configuration classes (SecurityConfig, OpenApiConfig, etc.)
├── event/              # Event publishers (MovieEventPublisher)
├── controller/filter/  # HTTP filters (HttpLoggingFilter)
└── MovieServiceApplication.java  # Spring Boot entry point
```

## Entity Patterns (JPA)

**Annotations Used:**
- `@Entity` + `@Table(name = "movies")` - JPA persistence mapping
- `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)` - Auto-increment primary key
- `@Column(nullable = false)` - Constraints at DB level
- `@Enumerated(EnumType.STRING)` - Enum storage as string
- `@PrePersist` - Lifecycle hook for timestamps and defaults

**Example (Movie.java):**
- Uses Lombok: `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor` for boilerplate reduction
- Soft-delete pattern: status field set to `INACTIVE` instead of hard delete
- Timestamp tracking: `createdAt` field auto-set in `@PrePersist`
- No explicit "updatedAt" (only createdAt); soft deletes via status enum

## Service & Repository Layer

**Service Pattern:**
- Interfaces defined in `service/` package (MovieService, ShowtimeService, etc.)
- Implementations in `service/impl/` with `@Service` annotation
- `@RequiredArgsConstructor` (Lombok) for dependency injection
- `@Transactional` on write operations (create, update, delete)
- Static helper methods for DTO conversion (MovieServiceImpl::toDto)
- Exception handling: `EntityNotFoundException` for missing records

**Repository Pattern:**
- Simple `JpaRepository<T, ID>` extensions with no custom queries
- Basic CRUD operations inherited from JpaRepository
- No complex custom finder methods (yet)

## DTO Patterns

**Design:**
- Records used for immutable DTOs (Java 16+)
- Separate request objects: `CreateMovieRequest` vs response `MovieDto`
- Request objects validated with `@Valid` + Jakarta Validation annotations
- DTOs map directly from/to entity via static converters or record constructors

**Example (MovieDto):**
- Record with all fields (id, title, description, genre, durationMin, rating, posterUrl, releaseDate, status, createdAt)
- Status returned as String (enum name)
- Temporal types: LocalDate, LocalDateTime

## Controller Patterns

**REST Conventions:**
- `@RestController` with `@RequestMapping("/api/movies")`
- Descriptive `@Tag` for OpenAPI/Swagger documentation
- `@Operation` on each endpoint method

**Security Integration:**
- Public GET endpoints (no auth required)
- Protected mutations (POST, PUT, DELETE) with `@PreAuthorize("hasRole('ADMIN')")`
- `@SecurityRequirement(name = "bearerAuth")` in Swagger annotations
- `@PathVariable` for resource IDs, `@RequestBody @Valid` for payloads

**Response Format:**
- `ResponseEntity<T>` for type safety
- `HttpStatus.CREATED` for POST, `HttpStatus.ACCEPTED` for deletes
- `ResponseEntity.ok()` for success, `.noContent()` for deletions

## Authentication & Security

**Integration Points:**
- JWT via custom starter: `com.namnd:jwt-auth-spring-boot-starter`
- `JwtAuthenticationFilter` injected into `SecurityFilterChain`
- `@EnableMethodSecurity` for `@PreAuthorize` annotations
- Stateless session policy: `SessionCreationPolicy.STATELESS`

**Authorization Model:**
- Role-based access control: `hasRole('ADMIN')`
- Public GET endpoints; authenticated mutations
- CSRF disabled (stateless API)

## Dependencies (Key)

- **Spring Boot Web + Data JPA:** Core framework
- **Jakarta Validation:** Input validation
- **Lombok:** Boilerplate reduction
- **Spring Security:** Authentication/authorization
- **JWT Starter:** Custom JWT integration
- **SpringDoc OpenAPI:** Swagger UI & documentation
- **Spring Kafka:** Event publishing
- **PostgreSQL:** Database driver
- **Logstash + Loki:** Structured logging

## Naming Conventions

- **Packages:** `com.namnd.movieservice.<layer>` (e.g., `service`, `controller`, `model`)
- **Classes:** PascalCase entities/services/controllers (Movie, MovieService, MovieController)
- **Methods:** camelCase with descriptive verbs (findAll, findById, create, update, delete)
- **Constants:** UPPER_SNAKE_CASE (inherited from enums like MovieStatus)
- **DTOs:** Append "Dto" for responses, "Request" for inputs (MovieDto, CreateMovieRequest)
- **Tables:** Lowercase plural (movies, theaters, seats, showtimes)

## Key Insights for Vote/Comment Feature

1. **Soft-Delete Strategy:** Use status enum pattern for logical deletion, not hard deletes
2. **DTO Separation:** Create distinct request/response DTOs; use records for immutability
3. **Service Layer Converters:** Static methods in service impl handle entity-to-DTO conversion
4. **Method Security:** Role-based access via `@PreAuthorize`; public reads, authenticated writes
5. **Transactional Boundaries:** Mark write operations with `@Transactional`
6. **Event Publishing:** MovieEventPublisher pattern used for async communication (follow for vote/comment events)
7. **No Custom Queries Yet:** Keep repositories simple; add custom finders only when needed
8. **Timestamps:** Leverage `@PrePersist` lifecycle hooks for auto-dating

## Unresolved Questions

- Should votes/comments use the same soft-delete pattern (status enum)?
- Does comment deletion cascade from vote deletion, or independent?
- Are votes/comments published as Kafka events (MovieVotedEvent, MovieCommentedEvent)?
- Rating aggregation: cached at Movie level or computed on-demand?
