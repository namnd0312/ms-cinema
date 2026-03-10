# Phase 3: Booking Service — Seat Locking, Reservations, Booking Flow

## Context Links
- [Parent Plan](./plan.md)
- [Phase 2: Movie Service](./phase-02-movie-service-entities-and-api.md)
- [Seat Locking Research](./research/researcher-02-redis-distributed-seat-locking-and-postgresql-schema-per-service-design-report.md)
- [OpenFeign Research](./research/researcher-01-stripe-feign-report.md)

## Overview
- **Priority:** P1
- **Status:** Pending
- **Effort:** 4h
- **Description:** Real-time seat selection with Redis distributed locks, booking lifecycle management, inter-service communication with movie-service and payment-service via OpenFeign.

## Key Insights
- Redis SET NX + TTL (5 min) for seat locking — KISS, no Redisson dependency
- Two-layer double-booking prevention: Redis lock (first gate) + DB partial unique index (second gate)
- Lock key pattern: `seat:lock:{showtimeId}:{seatId}` → value: `{userId}:{bookingId}`
- Snapshot seat data (type, price) at booking time to avoid cross-service reads later
- Booking references showtime_id and seat_id by Long ID only — no cross-DB foreign keys

## Requirements

### Functional
- Lock seats in Redis during booking (5-min TTL)
- Create booking with PENDING status
- Confirm booking after payment success (called by payment-service)
- Cancel booking (user-initiated or payment failure)
- Expire stale PENDING bookings via scheduled task
- Booking history per user
- Prevent double-booking at both Redis and DB level

### Non-functional
- Atomic multi-seat locking (all-or-nothing)
- Sub-second seat availability response
- Handle concurrent booking attempts gracefully

## Architecture

### Package Structure
```
com.namnd.bookingservice/
├── BookingServiceApplication.java    # @EnableFeignClients, @EnableScheduling
├── config/
│   ├── RedisConfig.java
│   └── FeignInterceptorConfig.java   # JWT propagation
├── client/
│   ├── MovieServiceClient.java       # Feign → movie-service
│   └── PaymentServiceClient.java     # Feign → payment-service
├── controller/
│   └── BookingController.java        # /api/bookings
├── dto/
│   ├── BookingRequestDto.java        # {showtimeId, seatIds[]}
│   ├── BookingResponseDto.java
│   ├── SeatInfoDto.java              # from movie-service
│   ├── ShowtimeInfoDto.java          # from movie-service
│   └── PaymentRequestDto.java        # sent to payment-service
├── model/
│   ├── Booking.java
│   ├── BookingSeat.java
│   └── BookingStatus.java            # enum
├── repository/
│   ├── BookingRepository.java
│   └── BookingSeatRepository.java
├── service/
│   ├── BookingService.java           # interface
│   ├── SeatLockService.java          # interface
│   └── impl/
│       ├── BookingServiceImpl.java
│       ├── SeatLockServiceImpl.java
│       └── BookingExpiryScheduler.java
└── exception/
    ├── SeatAlreadyLockedException.java
    └── BookingNotFoundException.java
```

### Booking Flow
```
1. POST /api/bookings/reserve {showtimeId, seatIds[]}
   → Feign: GET movie-service /api/showtimes/{id}/seats → get seat details
   → SeatLockService.lockSeats(showtimeId, seatIds, userId) → Redis SET NX
   → Create Booking (PENDING) + BookingSeats in DB
   → Return bookingId + totalAmount

2. POST /api/bookings/{id}/pay
   → Feign: POST payment-service /api/payments/create-intent {bookingId, amount}
   → Return Stripe clientSecret to frontend

3. POST /api/bookings/{id}/confirm  (called internally by payment-service webhook)
   → Update Booking status → CONFIRMED
   → Release Redis locks

4. POST /api/bookings/{id}/cancel (user or system)
   → Update Booking/BookingSeat status → CANCELLED
   → Release Redis locks
```

### Data Model
```sql
-- bookings
CREATE TABLE bookings (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT NOT NULL,       -- ref auth-service user.id
  showtime_id  BIGINT NOT NULL,       -- ref movie-service showtime.id
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  total_amount NUMERIC(10,2) NOT NULL,
  reserved_at  TIMESTAMPTZ DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL,  -- reserved_at + 5min
  confirmed_at TIMESTAMPTZ,
  CONSTRAINT chk_booking_status CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED'))
);

-- booking_seats
CREATE TABLE booking_seats (
  id          BIGSERIAL PRIMARY KEY,
  booking_id  BIGINT NOT NULL REFERENCES bookings(id),
  showtime_id BIGINT NOT NULL,
  seat_id     BIGINT NOT NULL,        -- ref movie-service seat.id
  seat_label  VARCHAR(10) NOT NULL,   -- snapshot: "A5"
  seat_type   VARCHAR(20) NOT NULL,   -- snapshot: STANDARD/VIP/PREMIUM
  price       NUMERIC(10,2) NOT NULL  -- snapshot: calculated price
);

-- Partial unique index: prevent double-booking at DB level
CREATE UNIQUE INDEX ux_active_seat_booking
  ON booking_seats (showtime_id, seat_id)
  WHERE booking_id IN (SELECT id FROM bookings WHERE status IN ('PENDING','CONFIRMED'));
```

Note: The partial unique index above is complex. Simpler alternative — check in application logic + optimistic locking. Implement the simpler version first.

## Related Code Files

### Files to Create
- All files in package structure above (~18 files)

### Files to Modify
- `booking-service/src/main/resources/application.yml` (already created in Phase 1)

## Implementation Steps

### 1. Redis Configuration
```java
@Configuration
public class RedisConfig {
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }
}
```

### 2. Feign Clients

**MovieServiceClient.java:**
```java
@FeignClient(name = "movie-service")
public interface MovieServiceClient {
    @GetMapping("/api/showtimes/{id}")
    ShowtimeInfoDto getShowtime(@PathVariable("id") Long id);

    @GetMapping("/api/showtimes/{id}/seats")
    List<SeatInfoDto> getSeatsForShowtime(@PathVariable("id") Long id);
}
```

**PaymentServiceClient.java:**
```java
@FeignClient(name = "payment-service")
public interface PaymentServiceClient {
    @PostMapping("/api/payments/create-intent")
    PaymentResponseDto createPaymentIntent(@RequestBody PaymentRequestDto request);
}
```

### 3. JWT Propagation Interceptor
```java
@Component
public class FeignInterceptorConfig implements RequestInterceptor {
    @Override
    public void apply(RequestTemplate template) {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String auth = attrs.getRequest().getHeader("Authorization");
            if (auth != null) template.header("Authorization", auth);
        }
    }
}
```

### 4. SeatLockService

```java
public interface SeatLockService {
    boolean lockSeats(Long showtimeId, List<Long> seatIds, Long userId);
    void unlockSeats(Long showtimeId, List<Long> seatIds);
    boolean isLocked(Long showtimeId, Long seatId);
}
```

**SeatLockServiceImpl:** Uses StringRedisTemplate
- `lockSeats()`: iterate sorted seatIds, `SET seat:lock:{showtimeId}:{seatId} {userId} NX EX 300`. If any fails → rollback previously locked seats, return false.
- `unlockSeats()`: DELETE all lock keys
- `isLocked()`: EXISTS check

### 5. Entities

**Booking.java:**
- id, userId (Long), showtimeId (Long), status (BookingStatus), totalAmount (BigDecimal)
- reservedAt (LocalDateTime), expiresAt (LocalDateTime), confirmedAt (LocalDateTime)
- seats: OneToMany → BookingSeat (CASCADE ALL)

**BookingSeat.java:**
- id, booking (ManyToOne), showtimeId, seatId, seatLabel, seatType, price

**BookingStatus:** PENDING, CONFIRMED, CANCELLED, EXPIRED

### 6. BookingService Implementation

**reserve(userId, showtimeId, seatIds):**
1. Feign call → movie-service get seat details
2. Lock seats via SeatLockService (all-or-nothing)
3. Calculate total: sum(basePrice * seat.priceMultiplier) per seat
4. Create Booking (PENDING, expiresAt = now + 5min) + BookingSeats
5. Return BookingResponseDto

**confirm(bookingId):**
1. Load booking, verify PENDING status
2. Set status = CONFIRMED, confirmedAt = now
3. Unlock seats in Redis (confirmed = permanent, no longer need lock)

**cancel(bookingId, userId):**
1. Load booking, verify ownership
2. Set status = CANCELLED
3. Unlock seats in Redis

### 7. BookingExpiryScheduler
```java
@Component
@EnableScheduling
public class BookingExpiryScheduler {
    @Scheduled(fixedRate = 60000) // every minute
    public void expireStaleBookings() {
        List<Booking> stale = bookingRepository
            .findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());
        for (Booking b : stale) {
            b.setStatus(BookingStatus.EXPIRED);
            seatLockService.unlockSeats(b.getShowtimeId(), extractSeatIds(b));
        }
        bookingRepository.saveAll(stale);
    }
}
```

### 8. BookingController

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/bookings/reserve | Authenticated | Lock seats + create booking |
| GET | /api/bookings/{id} | Authenticated | Get booking detail (owner only) |
| GET | /api/bookings/my | Authenticated | User's booking history |
| POST | /api/bookings/{id}/pay | Authenticated | Initiate payment |
| POST | /api/bookings/{id}/confirm | Internal | Called by payment-service |
| POST | /api/bookings/{id}/cancel | Authenticated | Cancel booking |

### 9. Compile & Test
```bash
cd booking-service && mvn clean compile
```

## Todo List
- [ ] Create RedisConfig
- [ ] Create Feign clients (MovieServiceClient, PaymentServiceClient)
- [ ] Create JWT Feign interceptor
- [ ] Create enums: BookingStatus
- [ ] Create entities: Booking, BookingSeat
- [ ] Create repositories (2)
- [ ] Create DTOs (5)
- [ ] Create SeatLockService interface + impl
- [ ] Create BookingService interface + impl
- [ ] Create BookingExpiryScheduler
- [ ] Create BookingController
- [ ] Create exception classes
- [ ] Compile and verify

## Success Criteria
- Seat locking prevents double-booking (concurrent requests)
- Booking reserve → pay → confirm flow works end-to-end
- Stale PENDING bookings auto-expire after 5 minutes
- Cancel releases Redis locks immediately
- User can only see own bookings

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis down during lock | Booking fails | Return 503, user retries |
| Movie-service down | Can't get seat info | Feign timeout + error message |
| Lock expires before payment | Seats released, booking fails | 5-min TTL is generous; UI shows countdown |
| Race condition on lock rollback | Leaked locks | TTL ensures eventual cleanup |

## Security Considerations
- All endpoints require valid JWT (no public paths except actuator/health)
- User can only access own bookings (check userId from JWT principal)
- Confirm endpoint should validate caller (payment-service or system)
- Input validation: seatIds list max 10, showtimeId exists

## Next Steps
- Phase 4: Payment service creates Stripe PaymentIntent and handles webhooks
