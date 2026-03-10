# Phase Implementation Report

## Executed Phase
- Phase: phase-04-movie-service-events
- Plan: /plans/260307-2358-kafka-event-driven-design
- Status: completed

## Files Modified
- `movie-service/pom.xml` — added spring-kafka + kafka-events dependencies (+10 lines)
- `movie-service/src/main/resources/application.yml` — added kafka bootstrap-servers config (+2 lines)
- `docker-compose.yml` — added kafka dependency + KAFKA_HOST env to movie-service (+2 lines)
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/MovieServiceImpl.java` — injected MovieEventPublisher, publish on create (+4 lines)
- `movie-service/src/main/java/com/namnd/movieservice/service/impl/ShowtimeServiceImpl.java` — injected MovieEventPublisher, publish on create (+10 lines)

## Files Created
- `movie-service/src/main/java/com/namnd/movieservice/event/MovieEventPublisher.java` — fire-and-forget Kafka publisher for movie.created and showtime.created events (56 lines)

## Tasks Completed
- [x] Add spring-kafka + kafka-events deps to movie-service/pom.xml
- [x] Add kafka bootstrap-servers to application.yml
- [x] Update docker-compose.yml (depends_on kafka, KAFKA_HOST env)
- [x] Create MovieEventPublisher with fire-and-forget pattern
- [x] Wire MovieEventPublisher into MovieServiceImpl.create()
- [x] Wire MovieEventPublisher into ShowtimeServiceImpl.create()
- [x] Verify compilation: `mvn compile -pl movie-service -am` — PASS

## Tests Status
- Type check: pass (Maven compile clean)
- Unit tests: not run (no test files created per instructions)
- Integration tests: not applicable

## Implementation Notes
- `Showtime` entity has no `availableSeats` field; derived from `theater.getTotalRows() * theater.getTotalColumns()` at publish time — accurate for a newly created showtime
- Kafka calls wrapped in try-catch; failures logged at ERROR, never propagated to caller
- `@RequiredArgsConstructor` (Lombok) handles injection via final fields

## Issues Encountered
None.

## Next Steps
- Phase 5 (booking-service consumer) can now consume `movie-events` topic for showtime.created events
- Phase 6 (payment-service) unblocked for payment-events consumption
