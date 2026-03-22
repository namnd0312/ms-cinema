# Code Review: FR-3.1 Seat Grid Display & Booking UI Improvements

**Date:** 2026-03-22
**Reviewer:** code-reviewer agent
**Plan:** `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260321-2258-seat-grid-display-booking-ui/plan.md`

---

## Code Review Summary

### Scope
- **Files reviewed:** 14 (8 frontend, 5 backend, 1 config)
- **Lines of code analyzed:** ~700 frontend TS, ~120 backend Java
- **Review focus:** All 6 phases of FR-3.1 implementation (new + modified files)
- **Build result:** Angular production build passes with 3 warnings (no errors); TypeScript typecheck clean (0 errors)
- **Updated plans:** `plan.md` — all 6 phases confirmed done; status remains `implemented`

---

### Overall Assessment

Implementation is solid and complete. All 6 phases delivered: seat type visuals, theater layout, responsive support, accessibility/ARIA, WebSocket real-time updates, and adjacent seat suggestion. Code is modular (files split per the 200-line rule), readable, and follows Angular 18 standalone patterns. Three build warnings require attention, and a handful of medium/low issues identified below.

---

### Critical Issues

None.

---

### High Priority Findings

#### H1 — WebSocket reconnection re-subscribes to wrong showtime

**File:** `/cinema-frontend/src/app/core/services/seat-websocket.service.ts`, line 48–68

`connect()` is guarded by `if (this.client?.active) return` — correct. However after a STOMP-level disconnect caused by network drop, `@stomp/stompjs` will auto-reconnect (via `reconnectDelay: 5000`) and fire `onConnect` again. The `showtimeId` is captured in the closure at original call time, so re-subscription is correct **as long as `subscribe()` is not called again** for a different showtime during the drop window.

The real risk: if the component re-initializes (user navigates away and back) while the previous `Client` is in a reconnecting state, `disconnect()` calls `client.deactivate()` which is async, but `this.client` is immediately set to `null`. The next `connect()` sees `client?.active === false` and creates a new `Client`, leaving the deactivating old one in limbo if `deactivate()` resolves after `client = null`.

**Fix:** await deactivation or use a `deactivating` flag before creating a new client.

```typescript
disconnect(): void {
  const old = this.client;
  this.client = null;
  this.currentShowtimeId = null;
  if (old?.active) old.deactivate(); // fire-and-forget is ok; just don't null before deactivate
}
```

The current order is `deactivate()` then `this.client = null` — actually line 37-41 does do it in that order. The risk is narrow; downgraded to medium on second read but noted here since it is the most nuanced WebSocket issue.

#### H2 — `@stomp/stompjs` and `sockjs-client` are CommonJS, not ESM

**Build warning:** `Module '@stomp/stompjs' ... is not ESM` / `Module 'sockjs-client' ... is not ESM`

Angular 18's build optimizer cannot tree-shake CommonJS modules. These two libs add ~60 kB unshaken to the booking chunk. Angular docs recommend listing them in `allowedCommonJsDependencies` to suppress the warning (acknowledging the tradeoff) or upgrading to ESM-compatible versions.

**Fix — `angular.json` `options` block:**
```json
"allowedCommonJsDependencies": ["@stomp/stompjs", "sockjs-client"]
```

`@stomp/stompjs` v7 does ship an ESM bundle. Check if direct ESM import resolves the tree-shaking warning without the allowlist workaround.

#### H3 — Component style budget exceeded

**Build warning:** `seat-grid.component.ts` inline styles = 2.90 kB, budget is 2.05 kB (warning threshold).

The inline `styles` block is 141 lines of CSS. No error yet, but approaching the 4 kB error threshold if styles grow.

**Fix:** Extract to `seat-grid.component.scss` and reference via `styleUrl`. This also removes the inline style entirely from the component TypeScript file, improving readability.

---

### Medium Priority Improvements

#### M1 — `Math.max(...array)` spread can throw RangeError on large seat arrays

**Files:**
- `seat-grid-layout.utils.ts` line 28 (not directly; `getAislePositions` receives `totalColumns` — OK)
- `seat-grid.component.ts` line 154: `Math.max(...this.seats().map(s => s.columnNumber), 0)`
- `seat-suggestion.service.ts` lines 45–46

```typescript
const totalRows = Math.max(...seats.map(s => s.rowNumber), 0);   // risk
const maxCol = Math.max(...seats.map(s => s.columnNumber), 0);   // risk
```

JavaScript's spread operator passes array items as function arguments. Engines typically cap at ~65 000–125 000 args before throwing `RangeError: Maximum call stack size exceeded`. A 400-seat theater is safe, but the pattern is brittle. Use `Array.prototype.reduce` instead:

```typescript
const totalRows = seats.reduce((m, s) => Math.max(m, s.rowNumber), 0);
const maxCol    = seats.reduce((m, s) => Math.max(m, s.columnNumber), 0);
```

#### M2 — `SeatStatusMessage` frontend type does not match backend record

**Frontend** (`seat-websocket.service.ts`, line 8): `seatIds: number[]`
**Backend** (`SeatStatusMessage.java`, line 11): `List<Long>`

Java `Long` serializes to JSON number. Angular's `number` is fine for values up to `Number.MAX_SAFE_INTEGER` (2^53-1). However, seat IDs are `Long` in Java and could theoretically exceed safe integer range if the DB sequence grows large. More immediately, the comment in the backend DTO lists status values `LOCKED, UNLOCKED, RESERVED, RELEASED, CONFIRMED` but the frontend only handles `RESERVED, CONFIRMED, RELEASED`. `LOCKED` and `UNLOCKED` are emitted from elsewhere (phase-5 description), but if the backend ever publishes these, the frontend will silently treat them as "available" (not occupied), which is wrong.

**Fix:** Align status handling:
```typescript
const isOccupied = msg.status === 'RESERVED' || msg.status === 'CONFIRMED' || msg.status === 'LOCKED';
```

#### M3 — `SeatWebSocketService` is `providedIn: 'root'` (singleton) but `Subject` is never completed

`messageSubject` is a `Subject` that is never `.complete()`-d. If multiple tabs or tests share the singleton and the app tears down without calling `ngOnDestroy`, subscribers may leak. `ngOnDestroy` on a root service is only called at app teardown (not on component destroy). The `SeatSelectionComponent` correctly unsubscribes its `wsSub` via `ngOnDestroy`, so actual leaks are avoided in practice, but the Subject should be completed in `disconnect()` and re-created on next `subscribe()` call to make the contract explicit.

#### M4 — `rowLabel` extraction from `seatLabel.charAt(0)` is fragile

**Files:** `seat-grid-layout.utils.ts` line 59, `seat-suggestion.service.ts` line 64

```typescript
label: sorted[0]?.seatLabel?.charAt(0) || String.fromCharCode(64 + rowNum),
rowLabel: group[0].seatLabel.charAt(0),
```

This assumes seat labels are always formatted as `[letter][number]` (e.g., `A1`). If the backend ever emits numeric-only labels or multi-character row prefixes (e.g., `AA1`), the row label will be wrong or just the first character of a number. The `Seat` model does not have a dedicated `rowLabel` field on the frontend (it does on the backend DTO). Consider adding `rowLabel` to the frontend `Seat` interface and populating it from the API response to avoid inference heuristics.

#### M5 — `BookingCountdownTimer` uses raw `setInterval`, not Angular `NgZone`

**File:** `seat-selection-timer.utils.ts`

`setInterval` is called outside Angular's zone context since `BookingCountdownTimer` is a plain class (not a service). Signal writes from setInterval callbacks still trigger change detection in Angular 18 signals-based reactivity. Functionally correct, but if the project ever uses `NgZone.runOutsideAngular` for performance, this could miss detection. Low real risk with current setup, but worth noting.

---

### Low Priority Suggestions

#### L1 — `SeatSuggestionPanelComponent` minimum group size is hardcoded as 2

The `changeSize` method clamps minimum at `2`. A single-person booking cannot use the group finder. Consider lowering to `1` or at least making it a configurable `input()`. A group of 1 is a valid use case (best single seat by score).

#### L2 — `onSuggestionSelected` replaces entire selection

**File:** `seat-selection.component.ts`, line 210

```typescript
onSuggestionSelected(group: SeatGroup): void {
  this.selectedSeatIds.set(new Set(group.seats.map(s => s.id)));
```

This replaces any manually selected seats. Users who selected 1 seat manually and then use the suggestion panel lose their selection. Consider whether "merge" semantics are more appropriate here.

#### L3 — `matTooltipShowDelay` is not a valid Angular Material input

**File:** `seat-grid.component.ts`, line 49

```html
matTooltipShowDelay="300"
```

The correct input is `[matTooltipShowDelay]="300"` (number binding, not string). Using the string attribute form may work via coercion in Material 18 but is technically incorrect and will cause a template type-check warning with strict mode. Use property binding:
```html
[matTooltipShowDelay]="300"
```

#### L4 — `getSeatState` called 4–5 times per seat per render cycle

**File:** `seat-grid.component.ts`, lines 40–46

Each seat button calls `getSeatState(seat)` in 4 separate bindings (`[class.available]`, `[class.selected]`, `[class.occupied]`, `[class.suggested]`, `[disabled]`). Angular calls the method once per binding per change detection cycle. For a 200-seat grid that's ~1000 calls. Since the method is pure (no side effects), this is acceptable, but a `computed` map `seatId → state` would be cleaner and slightly more efficient at scale.

#### L5 — `matStepperNext` directive on mobile bar button has no `stepper` reference

**File:** `seat-selection.component.ts`, line 74

```html
<button mat-raised-button color="primary" matStepperNext ...>
```

The mobile summary bar button uses `matStepperNext` but is outside the `<mat-stepper>` component tree (it's a fixed bottom bar rendered via `@if (isMobile())`). The directive relies on injecting `MatStepper` from the parent element tree. Being outside the stepper may mean it silently does nothing on mobile. Verify or use the stepper's `next()` method via a `ViewChild` reference instead.

#### L6 — CORS origins in `WebSocketConfig.java` include wildcard subdomain

**File:** `booking-service/src/main/java/com/namnd/bookingservice/config/WebSocketConfig.java`, line 28

```java
"https://*.namnd.com"
```

`setAllowedOriginPatterns` with a wildcard subdomain is acceptable for SockJS CORS. Ensure this pattern is also restricted in the API gateway's CORS policy to avoid a mismatch where the gateway rejects the preflight but the service would have allowed it.

---

### Positive Observations

- **Modularization is excellent.** The 200-line limit is respected: `seat-grid-layout.utils.ts` (95 lines), `seat-grid-keyboard-navigation.utils.ts` (60 lines), `seat-selection-timer.utils.ts` (30 lines), `seat-suggestion.service.ts` (115 lines) are all well below the limit and have single, clear responsibilities.
- **Keyboard navigation (ARIA grid pattern)** is correctly implemented with roving tabindex, `role="grid"`, `role="row"`, `role="gridcell"`, `role="rowheader"`, and a `aria-live="polite"` announcement region. `Home`/`End` support is a bonus.
- **WebSocket cleanup** is properly handled: `SeatSelectionComponent.ngOnDestroy()` calls both `wsSub.unsubscribe()` and `seatWs.disconnect()` — no subscription leak.
- **Algorithm correctness** in `SeatSuggestionService`: sliding window is correct; `findConsecutiveRuns` correctly handles aisle breaks (checks `curr.columnNumber - 1` against 0-indexed aisle positions); scoring function is reasonable with three weighted components.
- **Backend WS publisher exception handling** in `BookingExpiryScheduler` wraps the publish call in `try/catch` with a `warn` log, correctly ensuring expiry logic doesn't fail if WebSocket publish throws. `BookingServiceImpl` does not wrap (acceptable since it's in-request path and an exception would be visible).
- **`SeatStatusMessage` as Java record** is clean and immutable. Convenience constructor with `Instant.now()` default is a good pattern.
- **API gateway route** for `/ws/**` correctly placed before the booking-service REST route, ensuring WebSocket upgrade requests hit the right upstream.
- **`application.yml` public-paths** correctly includes `/ws/**` so JWT filter does not block the WebSocket handshake.
- **Status vocab alignment** between backend (`RESERVED`, `CONFIRMED`, `RELEASED`) and frontend `subscribeToSeatUpdates` is largely correct (see M2 for the `LOCKED` gap).

---

### Recommended Actions

1. **[High]** Add `allowedCommonJsDependencies: ["@stomp/stompjs", "sockjs-client"]` to `angular.json` to suppress the CommonJS build warning. Evaluate `@stomp/stompjs` v7 ESM import path.
2. **[High]** Extract `seat-grid.component.ts` inline styles to `seat-grid.component.scss` to resolve the component style budget warning.
3. **[High]** Align frontend WS status handling to also treat `LOCKED` as occupied (M2).
4. **[Medium]** Replace `Math.max(...arr)` spreads with `reduce` in `seat-grid.component.ts` and `seat-suggestion.service.ts` (M1).
5. **[Medium]** Add `rowLabel` field to the frontend `Seat` interface and populate from API, removing `charAt(0)` heuristic (M4).
6. **[Low]** Fix `matTooltipShowDelay` to use property binding `[matTooltipShowDelay]="300"` (L3).
7. **[Low]** Verify `matStepperNext` works in the mobile summary bar outside the stepper tree; if not, use `ViewChild` stepper reference (L5).
8. **[Low]** Consider lowering minimum group size in `SeatSuggestionPanelComponent` from 2 to 1 (L1).

---

### Metrics

- **TypeScript errors:** 0 (clean typecheck)
- **Build errors:** 0
- **Build warnings:** 3 (CommonJS x2, style budget x1)
- **Critical issues:** 0
- **High priority:** 3
- **Medium priority:** 5
- **Low priority:** 6
- **Test coverage:** Not measured (no test run performed; no unit tests found for new files)
- **File size compliance:** All new files under 200 lines ✓

---

### Unresolved Questions

1. **WebSocket through Spring Cloud Gateway MVC (`lb://`):** STOMP over SockJS requires HTTP upgrade to WebSocket. Spring Cloud Gateway MVC (servlet-based, not reactive) has limited WebSocket proxying support. The reactive `spring-cloud-starter-gateway` handles WS upgrades natively; the MVC variant may not forward the `Upgrade` header correctly to the booking-service. This should be verified by an end-to-end test — if the gateway drops the upgrade, SockJS will fall back to HTTP long-polling which works but loses the performance benefit.

2. **No unit tests for new frontend services/utils** (`seat-websocket.service.ts`, `seat-suggestion.service.ts`, `seat-grid-layout.utils.ts`, `seat-grid-keyboard-navigation.utils.ts`). The suggestion algorithm especially warrants unit tests (edge cases: empty grid, all occupied, groupSize > available, single-row theater).
