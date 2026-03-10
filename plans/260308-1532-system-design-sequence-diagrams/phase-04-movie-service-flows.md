# Phase 4: Movie Service Flows

## Context Links
- [MovieController.java](../../movie-service/src/main/java/com/namnd/movieservice/controller/MovieController.java) (66 lines)
- [ShowtimeController.java](../../movie-service/src/main/java/com/namnd/movieservice/controller/ShowtimeController.java) (68 lines)
- [TheaterController.java](../../movie-service/src/main/java/com/namnd/movieservice/controller/TheaterController.java) (65 lines)
- [MovieEventPublisher.java](../../movie-service/src/main/java/com/namnd/movieservice/event/MovieEventPublisher.java) (54 lines)
- [MovieServiceImpl.java](../../movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java)
- [ShowtimeServiceImpl.java](../../movie-service/src/main/java/com/namnd/movieservice/service/impl/ShowtimeServiceImpl.java)
- [TheaterServiceImpl.java](../../movie-service/src/main/java/com/namnd/movieservice/service/impl/TheaterServiceImpl.java)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Mermaid sequence diagrams for movie catalog CRUD, showtime scheduling, theater management, and Kafka event publishing.

## Key Insights from Code

### MovieController endpoints
- GET /api/movies — public, list all
- GET /api/movies/{id} — public, get by ID
- POST /api/movies — ADMIN only, create movie -> MovieEventPublisher.publishMovieCreated()
- PUT /api/movies/{id} — ADMIN only, update
- DELETE /api/movies/{id} — ADMIN only, delete

### ShowtimeController endpoints
- GET /api/showtimes — public, list all (optional ?movieId filter)
- GET /api/showtimes/{id} — public, get by ID
- GET /api/showtimes/{id}/seats — public, seat availability (used by booking-service Feign)
- POST /api/showtimes — ADMIN only, create -> MovieEventPublisher.publishShowtimeCreated()
- PUT /api/showtimes/{id} — ADMIN only, update

### TheaterController endpoints
- GET /api/theaters — public, list all
- GET /api/theaters/{id} — public, get by ID
- GET /api/theaters/{id}/seats — public, get seats for theater
- POST /api/theaters — ADMIN only, create (auto-generates seat grid)
- PUT /api/theaters/{id} — ADMIN only, update

### Kafka Publishing (MovieEventPublisher)
- publishMovieCreated(movieId, title) -> EventEnvelope wrapping MovieCreatedEvent -> topic "movie-events", key=movieId
- publishShowtimeCreated(showtimeId, movieId, theaterId, startTime, availableSeats) -> EventEnvelope wrapping ShowtimeCreatedEvent -> topic "movie-events", key=showtimeId
- Fire-and-forget: Kafka failures logged but don't break movie/showtime creation

### SecurityConfig
- GET endpoints on /api/movies/**, /api/showtimes/**, /api/theaters/** are public
- POST/PUT/DELETE require ADMIN role via @PreAuthorize

## Diagrams to Create (3 total)

### 1. Movie CRUD Flow
Participants: Client, API Gateway, JwtAuthenticationFilter, MovieController, MovieServiceImpl, MovieRepository, MovieEventPublisher, Kafka, PostgreSQL
- GET (public, no JWT) and POST/PUT/DELETE (ADMIN, JWT required)
- POST includes Kafka publish of MovieCreatedEvent

### 2. Showtime CRUD & Seat Availability Flow
Participants: Client, API Gateway, JwtAuthenticationFilter, ShowtimeController, ShowtimeServiceImpl, ShowtimeRepository, SeatRepository, MovieEventPublisher, Kafka, PostgreSQL
- GET /api/showtimes/{id}/seats used by booking-service via Feign
- POST includes Kafka publish of ShowtimeCreatedEvent

### 3. Theater CRUD Flow
Participants: Client, API Gateway, JwtAuthenticationFilter, TheaterController, TheaterServiceImpl, TheaterRepository, SeatRepository, PostgreSQL
- POST auto-generates seat grid (no Kafka event)

## Source Files to Reference
- `movie-service/src/main/java/com/namnd/movieservice/controller/MovieController.java`
- `movie-service/src/main/java/com/namnd/movieservice/controller/ShowtimeController.java`
- `movie-service/src/main/java/com/namnd/movieservice/controller/TheaterController.java`
- `movie-service/src/main/java/com/namnd/movieservice/event/MovieEventPublisher.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/ShowtimeServiceImpl.java`
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/TheaterServiceImpl.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/MovieCreatedEvent.java`
- `kafka-events/src/main/java/com/namnd/kafka/events/domain/ShowtimeCreatedEvent.java`

## Todo
- [ ] Movie CRUD sequence diagram (with Kafka publish on create)
- [ ] Showtime CRUD + seat availability sequence diagram (with Kafka publish on create)
- [ ] Theater CRUD sequence diagram (with seat grid auto-generation)
- [ ] Show public vs ADMIN access patterns with alt blocks
- [ ] Verify Kafka event payload fields match domain event records

## Success Criteria
- All 3 controllers have sequence diagrams
- Public vs authenticated access clearly distinguished
- Kafka event publishing shown for movie and showtime creation
- Feign client usage by booking-service referenced in showtime seats diagram
