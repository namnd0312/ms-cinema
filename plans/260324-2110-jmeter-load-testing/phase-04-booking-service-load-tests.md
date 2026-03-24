# Phase 4: Booking Service Load Tests

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: [Phase 1](./phase-01-environment-setup-jmeter-config.md), [Phase 2](./phase-02-auth-service-load-tests.md)

## Overview
- **Priority**: P0 (core business flow, most complex)
- **Status**: pending
- **Description**: Stress test seat reservation with Redis locking, booking lifecycle, and concurrent seat contention

## Key Insights
- Seat reservation uses Redis distributed locks: `seat:lock:{showtimeId}:{seatId}` with 5-min TTL
- **Concurrent seat contention** is the primary test target — multiple users trying same seats
- Booking expires after 5 minutes (BookingExpiryScheduler runs every 60s)
- Feign call to movie-service for showtime/seat validation — cascading failure risk
- WebSocket broadcasts seat status changes — may add overhead under load
- Kafka events published on booking create/confirm/cancel

## Requirements

### Functional
- 500 concurrent users trying to book seats on same showtime (contention test)
- 1000 concurrent users booking different showtimes (throughput test)
- Verify only one user gets each seat (no double-booking)
- Test booking cancellation under load
- Test booking expiry mechanism

### Non-Functional
- Seat lock acquisition p95 < 1s
- Zero double-bookings (correctness > speed)
- Redis lock contention handled gracefully (proper 409/conflict responses)

## Implementation Steps

### Test Plan: `booking-load-test.jmx`

1. **Setup Thread Group: Login**
   - 500 threads login and store JWT tokens
   - Once-only controller for login

2. **Thread Group: Seat Contention Test**
   - Threads: 500, Ramp-up: 60s
   - All users target SAME showtime, different seats
   - Some users target SAME seats (intentional conflict)

   **Requests:**
   - POST /api/bookings/reserve
     ```json
     {
       "showtimeId": ${showtimeId},
       "seatIds": [${seatId}]
     }
     ```
   - JSON Extractor: `$.id` → `BOOKING_ID`
   - Response Assertion: 200 (success) or 409 (seat already locked)

3. **Thread Group: Full Booking Lifecycle**
   - Threads: 200, Ramp-up: 120s
   - Each user: reserve → (wait) → confirm/cancel

   **Flow:**
   - POST /api/bookings/reserve → extract bookingId
   - Constant Timer: 5s (simulate user reviewing)
   - POST /api/bookings/${BOOKING_ID}/confirm OR
   - POST /api/bookings/${BOOKING_ID}/cancel

4. **Thread Group: Throughput Test**
   - Threads: 1000, Ramp-up: 300s
   - Users book across 20 different showtimes (less contention)
   - Measures overall booking throughput

5. **Validation Assertions**
   - GET /api/bookings/${BOOKING_ID} — verify status
   - GET /api/bookings/booked-seats?showtimeId=${showtimeId} — verify no duplicates

## Todo
- [ ] Create `booking-load-test.jmx`
- [ ] Create seat contention test (500 users, same showtime)
- [ ] Create full lifecycle test (reserve → confirm/cancel)
- [ ] Create throughput test (1000 users, multiple showtimes)
- [ ] Add double-booking validation assertions
- [ ] Add Redis lock monitoring (check lock key count)

## Success Criteria
- **Zero double-bookings** across all test runs
- Seat lock acquisition p95 < 1s
- 409 Conflict returned properly for already-locked seats
- Booking expiry scheduler correctly releases locks after 5 min
- Feign calls to movie-service don't cascade failure under load

## Risk Assessment
- **Risk**: Redis becomes bottleneck under 500 concurrent lock acquisitions
  - **Mitigation**: Monitor Redis ops/sec, check maxclients config
- **Risk**: Feign timeout to movie-service under load
  - **Mitigation**: Check Feign timeout config (connectTimeout: 5s, readTimeout: 10s)
- **Risk**: WebSocket broadcast overhead slows booking processing
  - **Mitigation**: Monitor booking latency with/without WebSocket enabled
- **Risk**: BookingExpiryScheduler interferes with active load test
  - **Mitigation**: Set booking TTL longer for test, or disable scheduler temporarily

## Security Considerations
- Verify users can only access their own bookings
- Verify seats can't be reserved by manipulating other users' booking IDs

## Next Steps
- Phase 5: Payment Service Load Tests
