# Phase 2: Movie Service — Entities, Repositories, Services, Controllers

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1: Scaffolding](./phase-01-project-scaffolding-maven-docker-gateway.md)
- [DB Schema Research](./research/researcher-02-redis-distributed-seat-locking-and-postgresql-schema-per-service-design-report.md)
- [Code Standards](../../docs/code-standards.md)

## Overview
- **Priority:** P1
- **Status:** Pending
- **Effort:** 4h
- **Description:** Full CRUD for movies, theaters, seats, showtimes. Public browse endpoints + admin-only management. Layered architecture following auth-service patterns.

## Key Insights
- Use Long/BIGSERIAL IDs (consistent with existing project)
- `@Enumerated(EnumType.STRING)` for all enums
- Seat data is static (physical layout per theater) — loaded once, queried often
- Showtime links movie + theater + time + price
- Seat availability = seats in theater MINUS booked seats (queried by booking-service via Feign)

## Requirements

### Functional
- CRUD Movies: title, description, genre, duration, rating, posterUrl, releaseDate, status
- CRUD Theaters: name, location, capacity, rows, columns
- Seats per theater: auto-generated from rows/columns, type (STANDARD/VIP/PREMIUM), price multiplier
- Showtime CRUD: movie + theater + startTime + endTime + basePrice + status
- GET seats for showtime (used by booking-service to check availability)
- Public: browse movies, showtimes, theaters. Admin: create/update/delete

### Non-functional
- All files under 200 LOC
- Constructor injection, interface+impl pattern
- Input validation with `@Valid`

## Architecture

### Package Structure
```
com.namnd.movieservice/
├── MovieServiceApplication.java
├── config/
│   └── SecurityConfig.java          # Override publicPaths for admin endpoints
├── controller/
│   ├── MovieController.java         # /api/movies
│   ├── TheaterController.java       # /api/theaters
│   └── ShowtimeController.java      # /api/showtimes
├── dto/
│   ├── MovieDto.java
│   ├── CreateMovieRequest.java
│   ├── TheaterDto.java
│   ├── CreateTheaterRequest.java
│   ├── SeatDto.java
│   ├── ShowtimeDto.java
│   └── CreateShowtimeRequest.java
├── model/
│   ├── Movie.java
│   ├── Theater.java
│   ├── Seat.java
│   ├── Showtime.java
│   ├── SeatType.java                # enum
│   ├── MovieStatus.java             # enum
│   └── ShowtimeStatus.java          # enum
├── repository/
│   ├── MovieRepository.java
│   ├── TheaterRepository.java
│   ├── SeatRepository.java
│   └── ShowtimeRepository.java
└── service/
    ├── MovieService.java            # interface
    ├── TheaterService.java          # interface
    ├── ShowtimeService.java         # interface
    └── impl/
        ├── MovieServiceImpl.java
        ├── TheaterServiceImpl.java
        └── ShowtimeServiceImpl.java
```

### Data Model
```
movies (id, title, description, genre, duration_min, rating, poster_url, release_date, status, created_at)
theaters (id, name, location, total_rows, total_columns, created_at)
seats (id, theater_id FK, row_label, seat_number, seat_type, price_multiplier, UNIQUE(theater_id, row_label, seat_number))
showtimes (id, movie_id FK, theater_id FK, start_time, end_time, base_price, status, created_at)
```

## Related Code Files

### Files to Create
- `movie-service/src/main/java/com/namnd/movieservice/config/SecurityConfig.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/Movie.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/Theater.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/Seat.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/Showtime.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/SeatType.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/MovieStatus.java`
- `movie-service/src/main/java/com/namnd/movieservice/model/ShowtimeStatus.java`
- `movie-service/src/main/java/com/namnd/movieservice/repository/MovieRepository.java`
- `movie-service/src/main/java/com/namnd/movieservice/repository/TheaterRepository.java`
- `movie-service/src/main/java/com/namnd/movieservice/repository/SeatRepository.java`
- `movie-service/src/main/java/com/namnd/movieservice/repository/ShowtimeRepository.java`
- `movie-service/src/main/java/com/namnd/movieservice/dto/*.java` (7 files)
- `movie-service/src/main/java/com/namnd/movieservice/service/*.java` (3 interfaces)
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/*.java` (3 impls)
- `movie-service/src/main/java/com/namnd/movieservice/controller/*.java` (3 controllers)

## Implementation Steps

### 1. Enums
```java
public enum SeatType { STANDARD, VIP, PREMIUM }
public enum MovieStatus { ACTIVE, INACTIVE }
public enum ShowtimeStatus { SCHEDULED, CANCELLED, COMPLETED }
```

### 2. Entities

**Movie.java:**
- `id` Long, BIGSERIAL
- `title` String, NOT NULL
- `description` String (TEXT)
- `genre` String
- `durationMin` Integer, NOT NULL
- `rating` String (e.g., "PG-13")
- `posterUrl` String
- `releaseDate` LocalDate
- `status` MovieStatus (STRING), default ACTIVE
- `createdAt` LocalDateTime, auto

**Theater.java:**
- `id` Long, BIGSERIAL
- `name` String, NOT NULL
- `location` String, NOT NULL
- `totalRows` Integer, NOT NULL
- `totalColumns` Integer, NOT NULL
- `seats` OneToMany → Seat (LAZY)
- `createdAt` LocalDateTime

**Seat.java:**
- `id` Long, BIGSERIAL
- `theater` ManyToOne → Theater
- `rowLabel` String (e.g., "A", "B")
- `seatNumber` Integer
- `seatType` SeatType (STRING), default STANDARD
- `priceMultiplier` BigDecimal, default 1.0
- UNIQUE constraint on (theater_id, row_label, seat_number)

**Showtime.java:**
- `id` Long, BIGSERIAL
- `movie` ManyToOne → Movie (EAGER — always need movie info)
- `theater` ManyToOne → Theater (EAGER — always need theater info)
- `startTime` LocalDateTime, NOT NULL
- `endTime` LocalDateTime, NOT NULL
- `basePrice` BigDecimal, NOT NULL
- `status` ShowtimeStatus (STRING), default SCHEDULED
- `createdAt` LocalDateTime

### 3. Repositories
Standard JPA repositories with custom queries:
```java
public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {
    List<Showtime> findByMovieIdAndStartTimeAfter(Long movieId, LocalDateTime after);
    List<Showtime> findByTheaterIdAndStartTimeBetween(Long theaterId, LocalDateTime start, LocalDateTime end);
}

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByTheaterId(Long theaterId);
    List<Seat> findByIdIn(List<Long> ids);
}
```

### 4. Services (interface + impl)

**MovieService:** findAll, findById, create, update, delete
**TheaterService:** findAll, findById, create, update, delete, generateSeats(theaterId)
**ShowtimeService:** findAll, findByMovie, findById, create, update, delete, getSeatsForShowtime(showtimeId)

`TheaterServiceImpl.generateSeats()`: auto-create Seat rows from theater dimensions:
```java
for (int r = 0; r < theater.getTotalRows(); r++) {
    String rowLabel = String.valueOf((char) ('A' + r));
    for (int c = 1; c <= theater.getTotalColumns(); c++) {
        Seat seat = new Seat();
        seat.setTheater(theater);
        seat.setRowLabel(rowLabel);
        seat.setSeatNumber(c);
        seat.setSeatType(SeatType.STANDARD);
        seat.setPriceMultiplier(BigDecimal.ONE);
        seats.add(seat);
    }
}
seatRepository.saveAll(seats);
```

### 5. DTOs
Keep lean — only fields needed for API response. Use records where possible.

### 6. Controllers

**MovieController** (`/api/movies`):
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | / | Public | List all active movies |
| GET | /{id} | Public | Get movie by ID |
| POST | / | ROLE_ADMIN | Create movie |
| PUT | /{id} | ROLE_ADMIN | Update movie |
| DELETE | /{id} | ROLE_ADMIN | Delete (set INACTIVE) |

**TheaterController** (`/api/theaters`):
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | / | Public | List theaters |
| GET | /{id} | Public | Get theater + seats |
| POST | / | ROLE_ADMIN | Create theater (auto-generates seats) |
| PUT | /{id} | ROLE_ADMIN | Update theater |

**ShowtimeController** (`/api/showtimes`):
| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | / | Public | List showtimes (filter by movie/date) |
| GET | /{id} | Public | Get showtime detail |
| GET | /{id}/seats | Public | Get all seats for showtime's theater |
| POST | / | ROLE_ADMIN | Create showtime |
| PUT | /{id} | ROLE_ADMIN | Update showtime |

### 7. SecurityConfig Override
Override the starter's default SecurityFilterChain to add admin-only paths:
```java
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // Starter handles JWT filter; just use @PreAuthorize on admin endpoints
}
```
Use `@PreAuthorize("hasRole('ADMIN')")` on admin controller methods.

### 8. Compile & Test
```bash
cd movie-service && mvn clean compile
```

## Todo List
- [ ] Create enums: SeatType, MovieStatus, ShowtimeStatus
- [ ] Create entities: Movie, Theater, Seat, Showtime
- [ ] Create repositories (4)
- [ ] Create DTOs (7)
- [ ] Create service interfaces (3) and implementations (3)
- [ ] Create controllers (3)
- [ ] Add SecurityConfig with @PreAuthorize for admin endpoints
- [ ] Compile and verify no errors
- [ ] Start service and verify entity tables created in moviedb

## Success Criteria
- All CRUD endpoints work for movies, theaters, showtimes
- Creating a theater auto-generates seat grid
- GET /api/showtimes/{id}/seats returns complete seat list
- Admin endpoints return 401 without JWT, 403 without ADMIN role
- Public endpoints accessible without authentication

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Seat generation logic for large theaters | Slow creation | Batch saveAll, max 30x30 = 900 seats |
| EAGER fetch on Showtime.movie/theater | N+1 queries on list | Use @EntityGraph or JOIN FETCH in repo |

## Security Considerations
- Public browse endpoints: read-only, no sensitive data
- Admin CRUD: protected by ROLE_ADMIN via @PreAuthorize
- Input validation on all request DTOs

## Next Steps
- Phase 3: Booking service uses GET /api/showtimes/{id}/seats to check availability
