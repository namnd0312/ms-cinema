# Phase 4: Movie Service Events

## Context
- [Plan Overview](./plan.md)
- [Phase 1: Shared Events Module](./phase-01-shared-kafka-events-module.md)
- Current: movie-service has no Kafka dependency, booking-service fetches movie/showtime data via Feign (synchronous)

## Overview
- **Priority:** P2
- **Status:** Pending
- **Effort:** 1.5h
- **Description:** Add Kafka publishing to movie-service for movie and showtime CRUD events. Enables future cache invalidation and event-driven integrations.

## Key Insights
- movie-service currently has NO spring-kafka dependency -- needs to be added
- Primary use case: publish events when showtimes/movies created/updated so other services can react
- Booking-service could optionally consume showtime events for local cache warming, but this is YAGNI for now -- just publish, consume later when needed
- Keep it simple: publish-only in movie-service this phase

## Requirements

### Functional
- movie-service publishes `EventEnvelope<MovieCreatedEvent>` when movie created
- movie-service publishes `EventEnvelope<ShowtimeCreatedEvent>` when showtime created
- Events published to `movie-events` topic
- movie-service Kafka producer configured with JsonSerializer

### Non-Functional
- Event publishing must not break movie creation if Kafka unavailable (fire-and-forget with logging)
- <200 LOC per file

## Architecture

### Event Flow
```
Admin creates movie/showtime via REST
    |
    v
MovieService / ShowtimeService
    |  saves to DB
    |  publishes event
    v
Kafka "movie-events"
    |
    v
(No consumers yet -- future: booking-service cache invalidation)
```

## Related Code Files

### Modify
- `movie-service/pom.xml` -- add `spring-kafka` and `kafka-events` dependencies
- `movie-service/src/main/resources/application.yml` -- add Kafka producer config + KAFKA_HOST
- `docker-compose.yml` -- add `KAFKA_HOST: kafka` to movie-service environment, add `kafka` to depends_on

### Create
- `movie-service/src/main/java/com/namnd/movieservice/event/MovieEventPublisher.java` -- publishes movie and showtime events

### May Need to Modify
- Movie/showtime service impl files -- add event publishing after DB save

## Implementation Steps

1. **Add dependencies** to `movie-service/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.kafka</groupId>
       <artifactId>spring-kafka</artifactId>
   </dependency>
   <dependency>
       <groupId>com.namnd</groupId>
       <artifactId>kafka-events</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. **Update movie-service application.yml**:
   ```yaml
   spring:
     kafka:
       bootstrap-servers: ${KAFKA_HOST:localhost}:9092
       # Producer config inherited from config-server shared config
   ```

3. **Update docker-compose.yml**: Add `KAFKA_HOST: kafka` env var and `kafka` to movie-service `depends_on`

4. **Create MovieEventPublisher**:
   - Inject `KafkaTemplate<String, Object>`
   - `publishMovieCreated(Long movieId, String title)` -- wraps in EventEnvelope, sends to `KafkaTopics.MOVIE_EVENTS`
   - `publishShowtimeCreated(Long showtimeId, Long movieId, Long theaterId, LocalDateTime startTime, Integer availableSeats)`
   - Fire-and-forget with error logging (don't throw on Kafka failure)

5. **Add event publishing** to movie/showtime service implementations:
   - After successful DB save, call `movieEventPublisher.publishMovieCreated(...)`
   - After successful showtime creation, call `movieEventPublisher.publishShowtimeCreated(...)`

6. Run `mvn compile -pl movie-service`

## Todo List
- [ ] Add spring-kafka + kafka-events dependencies to movie-service pom.xml
- [ ] Update movie-service application.yml with Kafka config
- [ ] Update docker-compose.yml for movie-service Kafka access
- [ ] Create MovieEventPublisher.java
- [ ] Add event publishing to movie service impl
- [ ] Add event publishing to showtime service impl
- [ ] Verify compilation

## Success Criteria
- movie-service compiles with Kafka dependencies
- Movie/showtime creation triggers Kafka event publishing
- Kafka failure does not break movie/showtime creation (graceful degradation)

## Risk Assessment
- **Kafka unavailable:** Mitigated by try-catch in publisher with error logging only
- **Performance impact:** Minimal -- async Kafka send, no blocking

## Security Considerations
- Movie events contain public data only (titles, showtimes) -- no sensitive info
- Events flow over internal Docker network

## Next Steps
- Future: booking-service can consume movie-events for cache invalidation
- Phase 6: Verify movie events appear in Kafka topic during integration test
