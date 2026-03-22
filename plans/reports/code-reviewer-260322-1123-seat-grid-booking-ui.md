# Code Review: Seat Grid Display & Booking UI Improvements

## Scope
- **Files reviewed**: 16 (8 frontend, 6 backend Java, 2 YAML)
- **Lines analyzed**: ~1,100
- **Review focus**: New feature implementation — WebSocket real-time seat updates, seat grid UI, keyboard navigation, group seat suggestions
- **Plan**: none provided; reviewed against stated 6-phase criteria

---

## Overall Assessment

Solid, well-structured implementation. Clean separation of concerns (layout utils, keyboard nav utils, timer utils extracted from components). Angular signal usage is idiomatic. Spring STOMP setup is straightforward and correct. The main concerns are a **security misconfiguration** (WS CORS wildcard), a **type mismatch** between backend and frontend seat ID types, a **potential memory leak** in the WS service, and several medium-priority issues.

---

## Critical Issues

### 1. WebSocket CORS Wildcard in Production Code
**File**: `WebSocketConfig.java:26`
```java
.setAllowedOriginPatterns("*")
```
- Accepts connections from any origin. For a production cinema booking service this is a data integrity risk — malicious pages can subscribe to all showtime topics and receive real-time occupancy data (competitive intelligence, OSINT).
- **Fix**: restrict to the actual frontend origin via env var:
```java
.setAllowedOriginPatterns("${app.allowed-origins:http://localhost:4200}")
```

### 2. Backend `seatIds` are `Long`, Frontend expects `number` — silent truncation risk
**Backend** `SeatStatusMessage.java:12`: `List<Long> seatIds`
**Frontend** `SeatStatusMessage` interface (`seat-websocket.service.ts:8`): `seatIds: number[]`
**Seat model** (`movie.model.ts:51`): `id: number`

JavaScript `number` is IEEE-754 double, safe only up to 2^53-1. At very high seat IDs (DB bigserial hitting billions+) this silently truncates. The mismatch is also a serialization documentation gap. Not a bug today, but a time-bomb.
- **Fix**: align either by bounding seat IDs to 32-bit on the DB, or use `string` IDs in the WS message and parse on the client.

---

## High Priority

### 3. `SeatWebSocketService` is `providedIn: 'root'` — single shared Subject, multiple showtimes
**File**: `seat-websocket.service.ts:17,20-26`

`messageSubject` is a single `Subject`. If a user navigates between two different showtime pages without a full reload, `subscribe(showtimeId)` will create a second STOMP subscription on top of the existing one **but still emit on the same subject**. The old STOMP subscription (`/topic/showtime/1/seats`) keeps running and pollutes messages for the new showtime.

`disconnect()` is called in `ngOnDestroy` of `SeatSelectionComponent`, which is correct — but only if the component is actually destroyed before re-initialising (Angular router reuses routes). Race conditions possible.

- **Fix**: Track the active `showtimeId`; if the same service is reused, unsubscribe the old STOMP subscription before creating the new one:
```ts
private stompSub: StompSubscription | null = null;

private connect(showtimeId: number): void {
  this.disconnect(); // always reset
  ...
  onConnect: () => {
    this.stompSub = this.client!.subscribe(...);
  }
}
```

### 4. No error recovery shown in `reserve()` — user left in broken state
**File**: `seat-selection.component.ts:187`
```ts
error: () => this.reserving.set(false)
```
Sets spinner off but shows nothing to the user. If reservation fails (seat already taken by race condition, network error) the user sees no feedback.
- **Fix**: add `this.snackBar.open('Reservation failed: ...', 'Dismiss')` in error handler. Same applies to `ngOnInit` error handler (line 158): `loadingSeats` is set false but no error message shown — user sees a blank screen.

### 5. `onSeatClick` announcement is inverted
**File**: `seat-grid.component.ts:187-188`
```ts
const action = this.selectedSeatIds().has(seat.id) ? 'deselected' : 'selected';
```
`seatToggled.emit(seat)` is called first; the parent `toggleSeat()` mutates `selectedSeatIds`. But `this.selectedSeatIds()` is read **before** the parent has processed the event (signals are synchronous — output emit propagates synchronously in Angular). The seat is already toggled by the time the ternary runs, so:
- clicking to **select** → `has()` returns `true` → announces "deselected" ❌
- clicking to **deselect** → `has()` returns `false` → announces "selected" ❌

- **Fix**: read state before emitting, or flip the ternary:
```ts
const wasSelected = this.selectedSeatIds().has(seat.id);
this.seatToggled.emit(seat);
const action = wasSelected ? 'deselected' : 'selected';
```

---

## Medium Priority

### 6. `SeatSuggestionService.findAdjacentGroups` — `aisleColumns` param unused in actual call
**File**: `seat-selection.component.ts:196`
```ts
this.suggestionService.findAdjacentGroups(this.seats(), occupied, groupSize);
// aisleColumns defaults to new Set()
```
The component has access to `aislePositions` (computed in `SeatGridComponent` but not exposed). The suggestion algorithm will therefore allow groups that span aisles — contradicting the algorithm's own comment. Minor UX inconsistency but misleading.
- **Fix**: expose `aislePositions` as a computed signal on `SeatGridComponent` or move `getAislePositions()` call into `SeatSelectionComponent`.

### 7. `bookingExpiryScheduler` publishes WS after expiry loop — no per-batch failure handling
**File**: `BookingExpiryScheduler.java:41-48`

If `seatWebSocketPublisher.publishSeatUpdate()` throws (messaging infrastructure down), the whole `@Transactional` method would rollback including the DB status updates, or swallow the exception silently. At minimum, the WS publish should be done outside the transaction or wrapped with try-catch to ensure DB state is committed regardless of WS availability.

### 8. `seat-websocket.service.ts` — hardcoded `/ws` path
**File**: `seat-websocket.service.ts:44`
```ts
webSocketFactory: () => new SockJS('/ws'),
```
Works when served on same origin, but in development (proxy divergence) or when connecting through the API gateway with a path prefix this could break. Should use an environment-injected URL like other services presumably do.

### 9. `SeatSuggestionPanelComponent` — `groupSize` is mutable plain property, not a signal
**File**: `seat-suggestion-panel.component.ts:74`
```ts
groupSize = 2;
```
Mixed signal/non-signal pattern within the same component block. Not a bug (template binding works fine), but inconsistent with rest of codebase using signals. Low risk.

### 10. `buildSeatRows` sorts `rowSeats` in-place
**File**: `seat-grid-layout.utils.ts:50`
```ts
const sorted = rowSeats.sort((a, b) => a.columnNumber - b.columnNumber);
```
`Array.sort` mutates in-place. Since `rowSeats` is retrieved from `rowMap`, this is fine (newly built arrays), but if `seats` input itself ever shares array references this would mutate the original data. Low risk today, but `toSorted()` (ES2023) or `[...rowSeats].sort(...)` is safer.

### 11. `matTooltipShowDelay` — non-standard attribute
**File**: `seat-grid.component.ts:49`
```html
matTooltipShowDelay="300"
```
The correct Angular Material directive is `[matTooltipShowDelay]="300"` (binding) or the input is `matTooltipShowDelay` — check Angular Material version. If the attribute is not recognised it is silently ignored and there is no delay. Not a runtime error.

---

## Low Priority

### 12. `application.yml` — `/ws/**` added to `public-paths` without auth for WS
This is intentional (WS clients may not pass JWT in headers with SockJS), but the implication is **any unauthenticated user can subscribe to seat updates for any showtime**. Acceptable for read-only seat availability, but worth noting this is a deliberate design choice. Consider adding a comment explaining why.

### 13. `SeatStatusMessage` Java record status field is `String`, not enum
**File**: `SeatStatusMessage.java:12`
```java
String status, // LOCKED, UNLOCKED, RESERVED, RELEASED, CONFIRMED
```
Using a plain String over an enum loses compile-time safety. The frontend also uses string comparison (`msg.status === 'RESERVED' || msg.status === 'CONFIRMED'`). A typo in either side silently breaks update logic.

### 14. Legend price displays `đ` suffix via `number` pipe — currency format
**File**: `seat-grid.component.ts:69`
```html
{{ lt.price | number:'1.0-0' }}đ
```
Using `number` pipe + hardcoded `đ` instead of `CurrencyPipe`. Inconsistent with the rest of the template that uses `| currency`. Minor.

### 15. `aislePositions` computed on every `seats()` change includes `Math.max(...spread)` on potentially large array
**File**: `seat-grid.component.ts:153-156`
```ts
const maxCol = Math.max(...this.seats().map(s => s.columnNumber), 0);
```
Spread into `Math.max` on a large array can cause stack overflow. Use `reduce` or `Math.max(...cols)` with a reasonable bound. Typical cinema theater is fine (100-200 seats), but defensive coding is better.

---

## Positive Observations

- **Signal hygiene**: consistent use of `signal()`, `computed()`, `input()`, `output()` throughout — no `EventEmitter` used where `output()` suffices.
- **Utility extraction**: splitting `seat-grid-layout.utils.ts` and `seat-grid-keyboard-navigation.utils.ts` keeps the component under the 200-line rule (212 lines — borderline but acceptable given inline template+styles).
- **Roving tabindex** keyboard navigation is correctly implemented per ARIA grid pattern: `tabindex=0` on focused cell, `-1` on rest, arrow key handling delegated to pure utility.
- **ARIA**: `role="grid"`, `role="row"`, `role="rowheader"`, `role="gridcell"`, `aria-pressed`, `aria-label`, and `aria-live` region all present — WCAG AA grid pattern well-covered.
- **`BookingCountdownTimer`** is a plain class (not `Injectable`), keeping it instantiatable and testable without DI.
- **`ngOnDestroy`** cleanup is complete: timer stopped, WS subscription unsubscribed, service disconnected.
- **Spring WS**: constructor injection (not field injection), proper `@Component`/`@Service` separation, `SimpMessagingTemplate` used correctly.
- **Scheduler transaction**: `@Transactional` on `expireStaleBookings()` ensures DB atomicity for batch expiry.
- **Sliding window seat suggestion algorithm** is clean and well-commented; scoring weights are clearly explained.

---

## Recommended Actions

1. **[Critical]** Restrict `setAllowedOriginPatterns("*")` to env-configured origins.
2. **[High]** Fix inverted ARIA live announcement in `onSeatClick` (pre-read state before emit).
3. **[High]** Add user-facing error messages in `reserve()` and `ngOnInit` error handlers.
4. **[High]** Fix `SeatWebSocketService` multi-showtime reuse: track and unsubscribe old STOMP subscription on reconnect.
5. **[Medium]** Wrap `seatWebSocketPublisher.publishSeatUpdate()` in try-catch inside `BookingExpiryScheduler` to prevent transaction rollback on WS failure.
6. **[Medium]** Pass `aislePositions` to `findAdjacentGroups()` from `SeatSelectionComponent` or expose from grid.
7. **[Medium]** Replace `/ws` hardcoded path with environment variable.
8. **[Low]** Change `SeatStatusMessage.status` from `String` to an enum for compile-time safety, align frontend string literals accordingly.
9. **[Low]** Use `toSorted()` / `[...arr].sort()` in `buildSeatRows` to avoid in-place mutation.

---

## Metrics
- **Type Coverage**: Good — all public APIs typed; `status` fields as `string` are the main gap
- **Test Coverage**: Not assessed (no test files in scope)
- **Linting Issues**: 0 syntax errors; ~2 template attribute warnings likely (`matTooltipShowDelay`)
- **Security Issues**: 1 Critical (CORS), 1 Low (unauthenticated WS read)

---

## Unresolved Questions

1. Does Spring Cloud Gateway MVC (`spring-cloud-starter-gateway-mvc`) correctly upgrade WebSocket connections for STOMP/SockJS? Standard Spring Cloud Gateway (Netty-based) handles WS upgrade well; the MVC variant (servlet-based) has known limitations with WS proxying. The route `booking-service-ws` in `api-gateway/application.yml` should be verified at runtime.
2. Is `booking-service` behind gateway auth filter that would block the STOMP handshake? The `public-paths` list now includes `/ws/**`, but gateway-level auth filters were not in scope — confirm gateway does not re-block it.
