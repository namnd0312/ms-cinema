# Database Schema & Initialization Patterns Research

## Executive Summary
MS Cinema uses **Hibernate DDL auto-update strategy** with **PostgreSQL per-service databases**. No Flyway/Liquibase migrations. Entities follow Jakarta JPA conventions with `@ManyToOne` relationships, ID-based foreign keys for cross-service refs, and timestamp tracking via `@PrePersist`.

---

## Key Findings

### 1. Database Initialization Strategy
- **approach:** DDL auto-update via Hibernate (`spring.jpa.hibernate.ddl-auto: update`)
- **location:** `movie-service/src/main/resources/application.yml` line 13
- **db_per_service:** Each microservice has isolated PostgreSQL database
  - auth-service → testdb
  - movie-service → moviedb
  - booking-service → bookingdb
  - payment-service → paymentdb
- **bootstrap:** `init-databases.sql` creates these 4 DBs on fresh PostgreSQL volume
- **no migrations:** No Flyway/Liquibase found in codebase; Hibernate handles schema sync

### 2. JPA/Hibernate DDL Settings
```yml
spring:
  jpa:
    hibernate:
      ddl-auto: update           # Auto-sync schema on startup
    show-sql: true               # Log SQL (verbose for development)
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/moviedb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

**Impact:** Tables auto-created from `@Entity` classes; new cols auto-added on startup. Safe for local dev, requires caution in production.

### 3. Naming Conventions (Kebab-case in SQL)
| Java Entity | Table Name | Column Name | Key Pattern |
|-------------|-----------|------------|------------|
| `Movie` (class) | `movies` | `id`, `title`, `duration_min` | snake_case |
| `Showtime` (class) | `showtimes` | `movie_id`, `theater_id` | fk = `{entity}_id` |
| `Theater` (class) | `theaters` | (see entity) | — |

**SQL Pattern:** Table names = lowercase plural entity names; FK columns = `{entity}_id`.

### 4. Relationship Patterns

#### ManyToOne with JoinColumn (Showtime → Movie/Theater)
```java
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "movie_id", nullable = false)
private Movie movie;
```
- Creates `movie_id` (BIGINT) FK column in showtimes table
- `nullable = false` enforces DB constraint
- `fetch = FetchType.EAGER` loads related entity immediately
- Alternative: `LAZY` for large collections (e.g., Theater → Seats)

#### OneToMany (Theater ↔ Seats)
```java
@OneToMany(mappedBy = "theater", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
private List<Seat> seats;
```
- `mappedBy = "theater"` (inverse side, no new FK)
- `cascade = CascadeType.ALL` deletes child seats when theater deleted
- `fetch = FetchType.LAZY` optimizes queries (load on demand)

### 5. Cross-Service User References
Payment entity pattern shows how to reference external service entities:
```java
@Column(name = "user_id", nullable = false)
private Long userId;
```
- **No FK constraint** (user_id not in movies DB)
- Store `Long userId` as column, not JPA `@ManyToOne`
- Rationale: users in auth-service (testdb), movies in moviedb (separate schema)
- Validation happens via inter-service calls, not DB constraints

### 6. Timestamp Management
All entities use `@PrePersist` lifecycle callback:
```java
@PrePersist
public void prePersist() {
    if (createdAt == null) {
        createdAt = LocalDateTime.now();
    }
    if (status == null) {
        status = MovieStatus.ACTIVE;
    }
}
```
- Auto-sets `created_at` timestamp on INSERT
- Sets default status/values before persist
- No `@UpdateTimestamp` (not tracked across services)

### 7. Enums in Database
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false)
private MovieStatus status = MovieStatus.ACTIVE;
```
- Stored as VARCHAR (e.g., 'ACTIVE', 'INACTIVE') not numbers
- Benefit: human-readable in direct SQL queries
- Table DDL: `status VARCHAR(255) NOT NULL`

---

## How to Add New Tables for Ratings/Comments/Comment-Likes

### Table Schema (Auto-Generated from Entities)
1. **MovieRating entity**
   ```java
   @Entity
   @Table(name = "movie_ratings")
   public class MovieRating {
       @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @ManyToOne(fetch = FetchType.EAGER)
       @JoinColumn(name = "movie_id", nullable = false)
       private Movie movie;

       @Column(name = "user_id", nullable = false)
       private Long userId;

       @Column(nullable = false)
       private Integer rating; // 1-5

       @Column(name = "created_at", nullable = false, updatable = false)
       private LocalDateTime createdAt;

       @PrePersist
       public void prePersist() { createdAt = LocalDateTime.now(); }
   }
   ```
   **Auto-generates:** `movie_ratings(id BIGINT PK, movie_id BIGINT FK, user_id BIGINT, rating INT, created_at TIMESTAMP)`

2. **MovieComment entity**
   ```java
   @Entity
   @Table(name = "movie_comments")
   public class MovieComment {
       @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @ManyToOne(fetch = FetchType.EAGER)
       @JoinColumn(name = "movie_id", nullable = false)
       private Movie movie;

       @Column(name = "user_id", nullable = false)
       private Long userId;

       @Column(columnDefinition = "TEXT", nullable = false)
       private String content;

       @Column(name = "created_at", nullable = false, updatable = false)
       private LocalDateTime createdAt;

       @PrePersist
       public void prePersist() { createdAt = LocalDateTime.now(); }
   }
   ```
   **Auto-generates:** `movie_comments(id, movie_id FK, user_id, content TEXT, created_at)`

3. **CommentLike entity** (self-referential)
   ```java
   @Entity
   @Table(name = "comment_likes")
   public class CommentLike {
       @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
       private Long id;

       @ManyToOne(fetch = FetchType.EAGER)
       @JoinColumn(name = "comment_id", nullable = false)
       private MovieComment comment;

       @Column(name = "user_id", nullable = false)
       private Long userId;

       @Column(name = "created_at", nullable = false, updatable = false)
       private LocalDateTime createdAt;

       @UniqueConstraint(columnNames = {"comment_id", "user_id"}) // One like per user per comment

       @PrePersist
       public void prePersist() { createdAt = LocalDateTime.now(); }
   }
   ```
   **Auto-generates:** `comment_likes(id, comment_id FK, user_id BIGINT, created_at, UNIQUE(comment_id, user_id))`

### Integration Steps
1. Create entity class in `movie-service/src/main/java/com/namnd/movieservice/model/`
2. Create repository interface in `movie-service/src/main/java/com/namnd/movieservice/repository/`
3. Start movie-service → Hibernate DDL auto-update creates tables automatically
4. Create controllers/services as needed

---

## Code File Templates

### Repository Pattern (CRUD)
```java
public interface MovieRatingRepository extends JpaRepository<MovieRating, Long> {
    List<MovieRating> findByMovieIdOrderByCreatedAtDesc(Long movieId);
    Optional<MovieRating> findByMovieIdAndUserId(Long movieId, Long userId);
}
```

### Service Pattern
```java
@Service
@RequiredArgsConstructor
public class MovieRatingService {
    private final MovieRatingRepository repository;

    public void addRating(Long movieId, Long userId, Integer rating) {
        repository.save(new MovieRating(null, movie, userId, rating, null));
    }
}
```

---

## Database Design Decisions

**Why no Flyway/Liquibase?**
- Project uses `ddl-auto: update` for simplicity in development
- Single DB per service reduces version management complexity
- Trade-off: schema drift risk in production (mitigated by Docker snapshots)

**Why `@Column(name = "user_id")` instead of `@ManyToOne User`?**
- User entity lives in auth-service (different DB)
- Can't enforce FK constraint across databases
- Pattern: store ID as Long, validate via service calls

**Timestamp Strategy:**
- Only `created_at` tracked (via `@PrePersist`)
- No `updated_at` (immutable comments/ratings)
- Aligns with event sourcing mindset (append-only)

---

## Unresolved Questions

1. **Should comment-likes be soft-deletable or hard-deleted?** (Affects audit trail)
2. **DB indexing strategy:** Need indexes on `(movie_id, created_at)` and `(user_id)` for query performance; defer to implementation phase?
3. **Pagination:** MovieComment list queries could be large; need cursor pagination vs offset-limit?
