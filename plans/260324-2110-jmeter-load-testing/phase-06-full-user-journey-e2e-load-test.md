# Phase 6: Full User Journey E2E Load Test

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: Phases 1-5 (all individual service tests validated)

## Overview
- **Priority**: P0 (most realistic test)
- **Status**: pending
- **Description**: Simulate complete user journey from login to payment with realistic think times and traffic distribution

## Key Insights
- Real users don't hit APIs at machine speed — think times are critical
- Traffic distribution: 70% browsing, 20% booking attempts, 10% payments
- Each user session: login → browse movies → select showtime → book seats → pay
- Must test cross-service communication under realistic load

## Requirements

### Functional
- 1000 concurrent users executing full journeys
- Realistic think times between actions (2-10s)
- Mixed traffic: browsers + bookers + payers
- Test spike scenario: 0→1000→1500→0

### Non-Functional
- End-to-end journey (login to payment) < 30s (excluding think time)
- System recovery after spike within 60s
- No data corruption across services

## Implementation Steps

### Test Plan: `full-journey-e2e-load-test.jmx`

1. **Thread Group: Full Journey**
   - Threads: 1000, Ramp-up: 300s (5 min)
   - Duration: 1200s (20 min total)

   **User Flow:**
   ```
   ┌─ Login (POST /api/auth/login)
   │  └─ Extract JWT
   ├─ Browse Movies (GET /api/movies) [think 3-5s]
   ├─ View Movie Detail (GET /api/movies/{id}) [think 2-4s]
   ├─ View Showtimes (GET /api/showtimes) [think 2-3s]
   ├─ Rate Movie (POST /api/movies/{id}/ratings) [30% of users]
   ├─ [IF booking user - 20%]
   │  ├─ Reserve Seats (POST /api/bookings/reserve) [think 5-10s]
   │  ├─ Pay (POST /api/payments/fake-success) [think 3-5s]
   │  └─ Check Booking (GET /api/bookings/{id})
   ├─ Check Notifications (GET /api/notifications)
   └─ Logout (POST /api/auth/logout)
   ```

2. **Traffic Distribution (Throughput Controller)**
   - 100% → Login + Browse movies
   - 70% → View details + showtimes (casual browsers)
   - 30% → Rate a movie
   - 20% → Full booking + payment flow
   - 100% → Logout

3. **Think Time Configuration**
   - Gaussian Random Timer per request
   - Browse: mean 3s, deviation 1s
   - Decision points (book/pay): mean 7s, deviation 3s

4. **Spike Test (Separate Thread Group)**
   - Stepping Thread Group or Ultimate Thread Group
   - 0→1000 (5 min) → sustained 1000 (10 min) → spike 1500 (2 min) → cool-down (3 min)

5. **Assertions**
   - Response code checks on every request
   - Booking → Payment → Booking CONFIRMED chain validation
   - No 5xx errors (server failures)

## Todo
- [ ] Create `full-journey-e2e-load-test.jmx`
- [ ] Implement user flow with Throughput Controllers
- [ ] Add Gaussian Random Timers for realistic think times
- [ ] Configure spike test with Ultimate Thread Group
- [ ] Add cross-service validation assertions
- [ ] Add Transaction Controllers for end-to-end timing

## Success Criteria
- 1000 concurrent users complete journeys with <2% error rate
- End-to-end booking flow p95 < 30s (excluding think time)
- System handles 1500 user spike without crashing
- System recovers to normal latency within 60s after spike
- Zero data inconsistencies (no orphan bookings, no unpaid confirmed bookings)

## Risk Assessment
- **Risk**: Cascading failure — one slow service blocks entire journey
  - **Mitigation**: Circuit breaker monitoring, per-service response time tracking
- **Risk**: JMeter itself becomes bottleneck at 1500 threads
  - **Mitigation**: Increase JMeter heap to 4GB, consider distributed JMeter

## Next Steps
- Phase 7: Execution Scripts & Reporting
