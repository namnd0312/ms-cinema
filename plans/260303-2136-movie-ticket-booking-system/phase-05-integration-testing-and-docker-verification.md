# Phase 5: Integration Testing & Docker Verification

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1-4](./plan.md)

## Overview
- **Priority:** P1
- **Status:** Pending
- **Effort:** 2h
- **Description:** End-to-end integration testing of the full booking flow across all services. Docker compose verification. Documentation update.

## Key Insights
- Test the complete flow: browse → select seats → reserve → pay → confirm
- Stripe CLI provides local webhook forwarding for testing
- All 9 services must run concurrently in Docker
- Update docs to reflect new services

## Requirements

### Functional
- Full booking flow works end-to-end
- All services register in Eureka
- Gateway routes correctly to all services
- JWT auth works across all services via starter library

### Non-functional
- All services start within 60s in Docker
- No port conflicts
- Databases created with correct schemas

## Implementation Steps

### 1. Build All Modules
```bash
mvn clean install
```

### 2. Docker Compose Up
```bash
docker compose up -d --build
# Wait for all services to register in Eureka
```

### 3. End-to-End Test Flow

**Step 1: Login (get JWT)**
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@test.com","password":"password"}'
# Save token
```

**Step 2: Create Movie (admin)**
```bash
curl -X POST http://localhost:8080/api/movies \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Inception","genre":"Sci-Fi","durationMin":148,"rating":"PG-13"}'
```

**Step 3: Create Theater (admin)**
```bash
curl -X POST http://localhost:8080/api/theaters \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"IMAX Hall 1","location":"District 1","totalRows":10,"totalColumns":15}'
```

**Step 4: Create Showtime (admin)**
```bash
curl -X POST http://localhost:8080/api/showtimes \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"movieId":1,"theaterId":1,"startTime":"2026-03-05T19:00","endTime":"2026-03-05T21:30","basePrice":12.00}'
```

**Step 5: Browse Showtimes (public)**
```bash
curl http://localhost:8080/api/showtimes
curl http://localhost:8080/api/showtimes/1/seats
```

**Step 6: Reserve Seats**
```bash
curl -X POST http://localhost:8080/api/bookings/reserve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"showtimeId":1,"seatIds":[1,2,3]}'
# Returns bookingId, totalAmount
```

**Step 7: Initiate Payment**
```bash
curl -X POST http://localhost:8080/api/bookings/1/pay \
  -H "Authorization: Bearer $TOKEN"
# Returns clientSecret for Stripe.js
```

**Step 8: Stripe Webhook (simulated)**
```bash
# Use Stripe CLI for local webhook forwarding:
stripe listen --forward-to localhost:8084/api/payments/webhook
stripe trigger payment_intent.succeeded
```

**Step 9: Verify Booking Confirmed**
```bash
curl http://localhost:8080/api/bookings/1 \
  -H "Authorization: Bearer $TOKEN"
# status should be CONFIRMED
```

### 4. Test Edge Cases
- Double-booking same seat → should fail with 409 Conflict
- Expired booking (wait 5 min) → status changes to EXPIRED, seats unlocked
- Cancel booking → seats released
- Access other user's booking → 403 Forbidden
- Admin endpoints without ADMIN role → 403

### 5. Update Documentation
- Update `docs/system-architecture.md` — add new services to topology diagram
- Update `docs/codebase-summary.md` — add new module descriptions
- Update `README.md` — add new endpoints and services

### 6. Update docker-compose verification
- All 9 services show "UP" in `docker compose ps`
- All services visible in Eureka dashboard (http://localhost:8761)
- Health endpoints respond: `/actuator/health` for each service

## Todo List
- [ ] Build all modules: `mvn clean install`
- [ ] Docker compose up with all 9 services
- [ ] Verify Eureka registration for all services
- [ ] Test complete booking flow end-to-end
- [ ] Test edge cases (double-booking, expiry, cancel, auth)
- [ ] Update system-architecture.md
- [ ] Update codebase-summary.md
- [ ] Update README.md with new endpoints

## Success Criteria
- Full booking flow works: browse → reserve → pay → confirm
- All 9 services running in Docker
- No double-booking possible
- Expired bookings auto-cleaned
- JWT auth works across all services
- Documentation updated

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Service startup order | Depends on Eureka/Config | docker-compose depends_on + retry config |
| Stripe webhook not reachable in Docker | Payment flow broken | Stripe CLI forwarding, or test with mock |
| Memory constraints (9 JVM services) | OOM on dev machine | Set JVM -Xmx256m per service |

## Security Considerations
- Never commit Stripe API keys to git
- Test with Stripe test mode keys only
- Verify webhook signature verification rejects tampered payloads

## Next Steps
- After all tests pass: commit, push, create PR
- Future: add Swagger/OpenAPI docs, rate limiting, monitoring
