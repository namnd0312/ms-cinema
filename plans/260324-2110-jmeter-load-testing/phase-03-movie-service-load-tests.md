# Phase 3: Movie Service Load Tests

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: [Phase 2](./phase-02-auth-service-load-tests.md) (JWT tokens needed)

## Overview
- **Priority**: P1
- **Status**: pending
- **Description**: Load test movie browsing, showtime queries, ratings, and comments — the most read-heavy service

## Key Insights
- Movie/showtime GET endpoints are the highest-traffic APIs (browsing)
- Ratings use upsert pattern — concurrent upserts on same (movie_id, user_id) may cause contention
- Comments are paginated (20/page default)
- Comment reactions use toggle pattern — idempotent
- Theater seats are LAZY loaded — may cause N+1 if not handled

## Requirements

### Functional
- Test GET /api/movies (list) with 1000 concurrent readers
- Test GET /api/movies/{id} (detail) with random movie IDs
- Test GET /api/showtimes with query params
- Test POST ratings and GET rating summary under load
- Test comment creation, listing, and reactions

### Non-Functional
- Read endpoints p95 < 500ms (these should be fast)
- Write endpoints (rating, comment) p95 < 1s
- Error rate < 0.5% for read operations

## Implementation Steps

### Test Plan: `movie-load-test.jmx`

1. **Thread Group: Browsing (Read-Heavy)**
   - Threads: 1000, Ramp-up: 300s, Loop: 5
   - 80% of traffic = reads (realistic ratio)

   **Requests:**
   - GET /api/movies (list all)
   - GET /api/movies/${movieId} (random from CSV)
   - GET /api/showtimes (list)
   - GET /api/movies/${movieId}/ratings (summary)
   - GET /api/movies/${movieId}/comments?page=0&size=20

2. **Thread Group: User Actions (Write)**
   - Threads: 200, Ramp-up: 120s, Loop: 3
   - Requires JWT (login first, then reuse token)

   **Requests:**
   - POST /api/movies/${movieId}/ratings `{"rating": ${__Random(1,5)}}`
   - POST /api/movies/${movieId}/comments `{"content": "Load test comment ${__threadNum}"}`
   - POST /api/comments/${commentId}/reactions `{"reactionType": "LIKE"}`

3. **CSV Data**
   - `movies.csv`: movieId values from seed data
   - Randomize with `__Random` or CSV round-robin

4. **Think Time**
   - Gaussian Random Timer: 1000ms deviation, 2000ms offset (simulates browsing)

## Todo
- [ ] Create `movie-load-test.jmx`
- [ ] Configure read-heavy thread group (1000 threads)
- [ ] Configure write thread group (200 threads with auth)
- [ ] Add CSV for movie/showtime IDs
- [ ] Add think time between requests
- [ ] Add response assertions (200 OK, JSON body)

## Success Criteria
- GET /api/movies p95 < 500ms at 1000 concurrent users
- GET /api/movies/{id} p95 < 300ms
- Rating upsert handles concurrent writes without errors
- Comment pagination returns correct page sizes under load

## Risk Assessment
- **Risk**: N+1 query on theater seats when fetching showtimes
  - **Mitigation**: Monitor SQL query count via Zipkin traces
- **Risk**: Comment table grows fast during load test — pagination slows
  - **Mitigation**: Clean up test comments after test run

## Next Steps
- Phase 4: Booking Service Load Tests
