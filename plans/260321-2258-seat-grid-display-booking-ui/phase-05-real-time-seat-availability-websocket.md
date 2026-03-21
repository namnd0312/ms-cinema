# Phase 05: Real-time Seat Availability via WebSocket

## Context Links
- Parent: [plan.md](plan.md)
- Depends on: Phase 1 (seat type colors), Phase 2 (theater layout)
- Backend: `booking-service/src/main/java/com/namnd/bookingservice/`
- Frontend: `cinema-frontend/src/app/features/booking/`

## Overview
- **Priority:** High
- **Status:** Pending
- **Effort:** 3.5h
- **Description:** Add WebSocket (STOMP over SockJS) to booking-service so the seat grid reflects real-time seat lock/unlock/reserve/cancel events from other users. Frontend subscribes per showtime.

## Key Insights
- No WebSocket infra exists — need `spring-boot-starter-websocket` + STOMP config
- Seat locks happen in `SeatLockServiceImpl` (Redis); reservations in `BookingServiceImpl`
- Events to broadcast: seat locked, seat unlocked, booking confirmed, booking cancelled/expired
- api-gateway needs WebSocket route for `/ws/**` to booking-service
- Frontend uses `@stomp/stompjs` + `sockjs-client` for Angular integration

## Requirements
### Functional
- When user A locks/reserves seats, user B's grid updates within 1-2s
- Expired/cancelled bookings release seats visually in real-time
- Reconnect automatically on connection drop

### Non-Functional
- WebSocket per-showtime topic to limit broadcast scope
- Graceful degradation: if WS fails, fall back to polling every 10s
- No auth on WS for now (showtime-scoped, read-only seat status)

## Architecture
```
[User A reserves] → BookingServiceImpl.reserve()
  → SeatLockService.lockSeats() (Redis)
  → SeatWebSocketPublisher.publishSeatUpdate(showtimeId, seatIds, status)
  → STOMP /topic/showtime/{id}/seats → [User B's SeatGridComponent updates]
```

### Backend Components
- `WebSocketConfig.java` — STOMP endpoint `/ws`, broker `/topic`
- `SeatWebSocketPublisher.java` — publishes `SeatStatusMessage` to `/topic/showtime/{id}/seats`
- `SeatStatusMessage.java` — DTO: `{seatIds: Long[], status: String, userId: Long}`

### Frontend Components
- `seat-websocket.service.ts` — STOMP client, subscribe/unsubscribe per showtime
- Update `seat-selection.component.ts` — inject WS service, merge real-time updates into seat state

## Related Code Files
### Backend — Modify
- `booking-service/pom.xml` — add `spring-boot-starter-websocket`
- `booking-service/.../service/impl/BookingServiceImpl.java` — inject publisher, call on reserve/confirm/cancel
- `booking-service/.../service/impl/BookingExpiryScheduler.java` — call publisher on expiry
- `api-gateway` route config — add WS route

### Backend — Create
- `booking-service/.../config/WebSocketConfig.java`
- `booking-service/.../websocket/SeatWebSocketPublisher.java`
- `booking-service/.../dto/SeatStatusMessage.java`

### Frontend — Modify
- `cinema-frontend/src/app/features/booking/seat-selection/seat-selection.component.ts` — subscribe to WS on init
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` — handle external seat status changes

### Frontend — Create
- `cinema-frontend/src/app/core/services/seat-websocket.service.ts`

## Implementation Steps

### Backend (2h)
1. Add `spring-boot-starter-websocket` to `booking-service/pom.xml`
2. Create `WebSocketConfig.java`:
   - Enable STOMP over SockJS at `/ws`
   - Message broker prefix: `/topic`
   - App destination prefix: `/app`
   - Set allowed origins for CORS
3. Create `SeatStatusMessage.java` record: `seatIds`, `status` (LOCKED/UNLOCKED/RESERVED/RELEASED), `timestamp`
4. Create `SeatWebSocketPublisher.java`:
   - Inject `SimpMessagingTemplate`
   - Method `publishSeatUpdate(Long showtimeId, List<Long> seatIds, String status)`
   - Sends to `/topic/showtime/{showtimeId}/seats`
5. Modify `BookingServiceImpl`:
   - Inject `SeatWebSocketPublisher`
   - Call `publishSeatUpdate` in `reserve()` with status RESERVED
   - Call in `confirmBooking()` with CONFIRMED
   - Call in `cancelBooking()` with RELEASED
6. Modify `BookingExpiryScheduler`:
   - Inject publisher, call with RELEASED on expiry
7. Add WebSocket route in api-gateway config:
   - Route `/ws/**` → `lb://booking-service`
   - Ensure WebSocket upgrade headers pass through

### Frontend (1.5h)
8. Install: `npm install @stomp/stompjs sockjs-client`
9. Create `seat-websocket.service.ts`:
   - STOMP client connecting to `/ws` via SockJS
   - `subscribe(showtimeId)` → returns Observable of SeatStatusMessage
   - `disconnect()` on destroy
   - Auto-reconnect with exponential backoff
10. Update `seat-selection.component.ts`:
    - Inject `SeatWebSocketService`
    - Subscribe on `ngOnInit` to showtime's topic
    - On message: update `seats` signal — mark seatIds as OCCUPIED (RESERVED/CONFIRMED) or AVAILABLE (RELEASED)
    - Filter out own user's events (don't re-apply own locks)
    - Unsubscribe on `ngOnDestroy`
11. Update `seat-grid.component.ts`:
    - Add visual transition animation when seat status changes externally (brief pulse effect)

## Todo List
- [ ] Add `spring-boot-starter-websocket` dependency
- [ ] Create `WebSocketConfig.java` with STOMP/SockJS
- [ ] Create `SeatStatusMessage.java` DTO
- [ ] Create `SeatWebSocketPublisher.java`
- [ ] Wire publisher into `BookingServiceImpl` (reserve/confirm/cancel)
- [ ] Wire publisher into `BookingExpiryScheduler`
- [ ] Add api-gateway WebSocket route
- [ ] Install `@stomp/stompjs` + `sockjs-client` on frontend
- [ ] Create `seat-websocket.service.ts`
- [ ] Update `seat-selection.component.ts` with WS subscription
- [ ] Add seat change animation in `seat-grid.component.ts`
- [ ] Test: open 2 browsers, reserve in one, verify grid updates in other

## Success Criteria
- Seat grid updates within 2s when another user reserves/cancels
- WebSocket reconnects after disconnect
- No memory leaks (proper unsubscribe on destroy)
- api-gateway correctly proxies WS traffic
- Compilation passes: `mvn clean compile -pl booking-service` + `ng build`

## Risk Assessment
- **api-gateway WS routing** — Spring Cloud Gateway WS support may need specific config; test early
- **CORS** — SockJS fallback uses HTTP; ensure CORS configured for gateway origin
- **Scale** — Single-instance STOMP broker is fine for MVP; for multi-instance, would need Redis/RabbitMQ broker relay (out of scope)

## Security Considerations
- WS topics are read-only (no client-to-server seat operations via WS)
- No sensitive data in WS messages (only seatIds + status)
- Future: add JWT auth to WS handshake if needed

## Next Steps
- Phase 6 (Adjacent Seat Suggestion) can run in parallel
- Future: Redis-backed STOMP broker for horizontal scaling
