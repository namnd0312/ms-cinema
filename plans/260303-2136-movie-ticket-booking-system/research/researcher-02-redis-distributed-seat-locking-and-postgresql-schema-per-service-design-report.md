# Research Report: Seat Locking & DB Schema Design
Date: 2026-03-03 | Stack: Spring Boot 3.4.3 / Java 21 / Redis 7 / PostgreSQL 16

---

## Topic 1: Redis Distributed Locking for Seat Reservation

### Pattern: SET NX+TTL vs Redisson

| Approach | Pros | Cons |
|---|---|---|
| `SET key val NX PX ttl` (raw) | No extra dep, simple | Manual unlock, no watchdog, deadlock risk |
| Redisson `RLock` | Watchdog (auto-renew), pub/sub wait, Spring integration | Extra dependency (~4MB) |

**Recommendation**: Use **Redisson** via `redisson-spring-boot-starter`. Its watchdog auto-extends TTL while the thread holds the lock, preventing expiry during slow payment.

```xml
<dependency>
  <groupId>org.redisson</groupId>
  <artifactId>redisson-spring-boot-starter</artifactId>
  <version>3.27.x</version>
</dependency>
```

### Locking Multiple Seats Atomically

Two strategies:
1. **Individual locks in sorted order** — acquire `seat:lock:{showtime_id}:{seat_id}` per seat, always sorted by seat_id to prevent deadlocks.
2. **Lua script** — single atomic `SET NX` batch: check-and-set all seats in one script execution (Redis executes Lua atomically, no interleaving).

**Recommendation**: Lua script for ≤20 seats. Individual Redisson `RLock` (sorted) for simplicity in most cases.

```lua
-- Lua: lock N seats atomically
local keys = KEYS  -- seat lock keys
local ttl  = ARGV[1]
local owner = ARGV[2]
for i = 1, #keys do
  if redis.call('EXISTS', keys[i]) == 1 then return 0 end
end
for i = 1, #keys do
  redis.call('SET', keys[i], owner, 'PX', ttl, 'NX')
end
return 1
```

### TTL Strategy (5-min Reservation Window)

- Set initial lock TTL = **300 seconds** (5 min).
- Key: `seat:lock:{showtimeId}:{seatId}` → value: `userId:sessionId`.
- Redisson watchdog renews every `TTL/3` (100s) while thread is alive — prevents expiry mid-payment.
- Expose countdown to client via `TTL` command on lock key.

### Handling Lock Expiry During Payment

- Watchdog keeps lock alive as long as JVM thread is running.
- If JVM crashes: lock expires naturally (no deadlock).
- On payment service side: verify lock still held before final DB write; if expired → fail transaction, notify user to re-select.
- Store `reservation_expires_at` in booking row for DB-level validation fallback.

### Releasing Locks

| Event | Action |
|---|---|
| Booking confirmed | `rLock.unlock()` after DB commit |
| User cancels | Explicit `unlock()` + update booking status |
| Session timeout | Scheduled job scans `PENDING` bookings older than TTL, releases locks |
| Lock expired by TTL | Redis auto-clears; scheduled job marks booking `EXPIRED` |

### Concurrency: Preventing Double-Booking

Two-layer defense:
1. **Redis lock** (first gate): only one thread enters booking logic per seat.
2. **DB unique constraint** (second gate): `UNIQUE (showtime_id, seat_id)` on `booking_seats` where status != CANCELLED. Use partial unique index in PostgreSQL.

```sql
CREATE UNIQUE INDEX ux_active_booking_seat
  ON booking_seats (showtime_id, seat_id)
  WHERE status NOT IN ('CANCELLED', 'EXPIRED');
```

---

## Topic 2: Database Schema Design

### Database-per-Service Layout

```
movie-service-db     → movies, theaters, screens, seats, showtimes
booking-service-db   → bookings, booking_seats
payment-service-db   → payments
```

No cross-DB foreign keys. Services communicate via IDs only; consistency via events (Saga/outbox pattern).

### Movie Service Schema

```sql
-- movies
CREATE TABLE movies (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title       VARCHAR(255) NOT NULL,
  duration_min INT NOT NULL,
  genre       VARCHAR(100),
  status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE|INACTIVE
  created_at  TIMESTAMPTZ DEFAULT now()
);

-- theaters
CREATE TABLE theaters (
  id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name    VARCHAR(255) NOT NULL,
  city    VARCHAR(100) NOT NULL
);

-- screens (within a theater)
CREATE TABLE screens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  theater_id  UUID NOT NULL REFERENCES theaters(id),
  name        VARCHAR(50) NOT NULL,
  total_seats INT NOT NULL
);

-- seats (physical layout, static)
CREATE TABLE seats (
  id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  screen_id UUID NOT NULL REFERENCES screens(id),
  row_label VARCHAR(5) NOT NULL,
  number    INT NOT NULL,
  type      VARCHAR(20) NOT NULL DEFAULT 'STANDARD', -- STANDARD|PREMIUM|VIP
  UNIQUE (screen_id, row_label, number)
);

-- showtimes
CREATE TABLE showtimes (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  movie_id    UUID NOT NULL REFERENCES movies(id),
  screen_id   UUID NOT NULL REFERENCES screens(id),
  start_time  TIMESTAMPTZ NOT NULL,
  end_time    TIMESTAMPTZ NOT NULL,
  base_price  NUMERIC(10,2) NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' -- SCHEDULED|CANCELLED|COMPLETED
);

CREATE INDEX idx_showtimes_start ON showtimes (start_time);
CREATE INDEX idx_showtimes_movie  ON showtimes (movie_id, start_time);
CREATE INDEX idx_showtimes_screen ON showtimes (screen_id, start_time);
```

### Booking Service Schema

```sql
-- bookings
CREATE TABLE bookings (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id      UUID NOT NULL,          -- ref to user-service (ID only)
  showtime_id  UUID NOT NULL,          -- ref to movie-service (ID only)
  status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  -- PENDING|CONFIRMED|CANCELLED|EXPIRED
  total_amount NUMERIC(10,2) NOT NULL,
  reserved_at  TIMESTAMPTZ DEFAULT now(),
  expires_at   TIMESTAMPTZ NOT NULL,   -- reserved_at + 5min
  confirmed_at TIMESTAMPTZ
);

CREATE INDEX idx_bookings_user     ON bookings (user_id);
CREATE INDEX idx_bookings_showtime ON bookings (showtime_id, status);
CREATE INDEX idx_bookings_expires  ON bookings (expires_at) WHERE status = 'PENDING';

-- booking_seats (join: which seats belong to a booking)
CREATE TABLE booking_seats (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id  UUID NOT NULL REFERENCES bookings(id),
  showtime_id UUID NOT NULL,   -- denormalized for index
  seat_id     UUID NOT NULL,   -- ref to movie-service seat (ID only)
  seat_type   VARCHAR(20) NOT NULL,  -- snapshot at booking time
  price       NUMERIC(10,2) NOT NULL,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
);

-- Partial unique index prevents double-booking at DB level
CREATE UNIQUE INDEX ux_active_booking_seat
  ON booking_seats (showtime_id, seat_id)
  WHERE status NOT IN ('CANCELLED', 'EXPIRED');

CREATE INDEX idx_booking_seats_booking ON booking_seats (booking_id);
```

### Payment Service Schema

```sql
CREATE TABLE payments (
  id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  booking_id     UUID NOT NULL UNIQUE,  -- ref to booking-service (ID only)
  user_id        UUID NOT NULL,
  amount         NUMERIC(10,2) NOT NULL,
  currency       CHAR(3) NOT NULL DEFAULT 'USD',
  status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  -- PENDING|COMPLETED|FAILED|REFUNDED
  provider       VARCHAR(50),           -- STRIPE|PAYPAL etc.
  provider_tx_id VARCHAR(255),
  paid_at        TIMESTAMPTZ,
  created_at     TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX idx_payments_booking ON payments (booking_id);
CREATE INDEX idx_payments_user    ON payments (user_id, status);
```

### Enum Strategy

Use `VARCHAR(20)` columns with `CHECK` constraints (not PostgreSQL `ENUM` type) for flexibility:

```sql
-- Easier to add values without ALTER TYPE; works across services; Hibernate maps to Java enum
ALTER TABLE bookings ADD CONSTRAINT chk_booking_status
  CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','EXPIRED'));
```

In Java: `@Enumerated(EnumType.STRING)` on entity fields.

### Cross-Service ID References

- Store only UUIDs from foreign services — no FK constraints.
- Validate existence via API call or async event.
- Denormalize critical read-time data (e.g., `seat_type`, `price`) into `booking_seats` at write time (snapshot pattern) to avoid cross-service joins.

---

## Unresolved Questions

1. Redisson watchdog behavior if payment takes >5 min — needs explicit max-lease-time cap or UX flow decision.
2. Saga vs 2-phase commit for booking+payment atomicity across services — not decided.
3. Should `showtimes` store seat-level pricing or flat `base_price` only? Premium/VIP multiplier strategy TBD.
4. Redis Cluster vs single-node for lock HA — Redlock algorithm needed if cluster used.
