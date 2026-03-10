# Phase 04: Booking Module

## Context Links
- [Cinema UI Patterns](./research/researcher-02-cinema-ui-patterns.md) — seat grid, stepper, chips
- Backend: `POST /api/bookings/reserve`, `GET /api/showtimes/{id}/seats`
- [Phase 03](./phase-03-movie-module.md) — navigates here with showtimeId

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Seat selection grid, booking flow with Material Stepper, booking history. Core user journey.

## Key Insights
- Seat grid as 2D CSS Grid; states: available/selected/occupied (color-coded)
- Material Stepper for multi-step flow: seats → summary → payment
- Selected seats as removable MatChips
- Booking has expiry timer (backend-enforced); show countdown
- 44x44px minimum touch target for seats

## Requirements
### Functional
- Seat selection grid for chosen showtime
- Visual seat states: green (available), blue (selected), gray (occupied)
- Screen indicator at top of grid
- Selected seats shown as chips (removable)
- Booking summary with price calculation
- Booking confirmation/cancellation
- Booking history page (list user's bookings)
- Booking detail page

### Non-functional
- Auth guard on all booking routes
- Responsive seat grid (scales columns on mobile)
- Smooth selection animations
- Countdown timer for reservation expiry

## Architecture
```
features/booking/
├── booking.routes.ts
├── seat-selection/
│   └── seat-selection.component.ts    # Grid + stepper wrapper
├── seat-grid/
│   └── seat-grid.component.ts         # Reusable seat grid
├── booking-summary/
│   └── booking-summary.component.ts   # Price + seat list
├── booking-history/
│   └── booking-history.component.ts   # User's bookings
└── booking-detail/
    └── booking-detail.component.ts    # Single booking view

core/services/
└── booking.service.ts
```

## Related Code Files
- **Create:** `core/services/booking.service.ts`
- **Create:** `features/booking/booking.routes.ts`
- **Create:** `features/booking/seat-selection/seat-selection.component.ts`
- **Create:** `features/booking/seat-grid/seat-grid.component.ts`
- **Create:** `features/booking/booking-summary/booking-summary.component.ts`
- **Create:** `features/booking/booking-history/booking-history.component.ts`
- **Create:** `features/booking/booking-detail/booking-detail.component.ts`
- **Modify:** `app.routes.ts` — add booking lazy route

## Implementation Steps
1. Create `BookingService`:
   - `reserveSeats(showtimeId, seatIds): Observable<Booking>`
   - `getBooking(id): Observable<Booking>`
   - `getMyBookings(): Observable<Booking[]>`
   - `confirmBooking(id): Observable<Booking>`
   - `cancelBooking(id): Observable<void>`
2. Create `SeatGridComponent` (standalone, reusable):
   - Input: `seats: Seat[]` (flat array with row/col info)
   - Input: `selectedSeatIds: Set<number>`
   - Output: `seatToggled: EventEmitter<Seat>`
   - Render as CSS Grid grouped by rows
   - Seat states via CSS classes: `.available`, `.selected`, `.occupied`
   - Screen indicator div at top
   - Legend below grid (color key)
   - Responsive: `grid-template-columns: repeat(auto-fit, minmax(40px, 1fr))`
   - `@for (seat of rowSeats; track seat.id)` with `@switch (seat.state)`
3. Create `SeatSelectionComponent` (stepper wrapper):
   - Load seats via `MovieService.getShowtimeSeats(showtimeId)`
   - Track selected seats with `signal<Set<number>>`
   - Material Stepper with 3 steps:
     - Step 1: Seat selection (SeatGridComponent)
     - Step 2: Summary (BookingSummaryComponent)
     - Step 3: Payment (navigates to payment module)
   - On "Reserve": call `BookingService.reserveSeats()`
   - Start countdown timer from booking.expiresAt
4. Create `BookingSummaryComponent`:
   - Input: `selectedSeats: Seat[]`, `showtime: Showtime`
   - Display: movie title, showtime, theater, selected seats as chips
   - Price calculation: sum of seat prices
   - "Proceed to Payment" button
5. Create `BookingHistoryComponent`:
   - Fetch `BookingService.getMyBookings()`
   - Display as Material card list
   - Status chips: RESERVED (yellow), CONFIRMED (green), CANCELLED (red), EXPIRED (gray)
   - Click → navigate to booking detail
6. Create `BookingDetailComponent`:
   - Route param `:id` → fetch booking
   - Show full details: movie, showtime, seats, status, payment info
   - Actions: "Cancel Booking" (if RESERVED/CONFIRMED)
7. Define routes in `booking.routes.ts`:
   - `select/:showtimeId` → SeatSelectionComponent (auth guard)
   - `history` → BookingHistoryComponent (auth guard)
   - `:id` → BookingDetailComponent (auth guard)

## Todo List
- [ ] BookingService with API calls
- [ ] SeatGridComponent (visual grid with states)
- [ ] SeatSelectionComponent (stepper flow)
- [ ] BookingSummaryComponent (price + chips)
- [ ] BookingHistoryComponent (user's bookings)
- [ ] BookingDetailComponent
- [ ] Booking routes with auth guard
- [ ] Seat grid SCSS styling (colors, responsive)
- [ ] Reservation countdown timer

## Success Criteria
- Seat grid renders with correct states from API
- User can select/deselect seats; chips update
- Reserve creates booking on backend
- Stepper navigates through flow smoothly
- Booking history shows user's bookings with status
- Countdown timer displays remaining reservation time

## Risk Assessment
- Seat data format may vary — handle flat vs. nested arrays
- Concurrent seat selection conflicts — optimistic UI, handle 409 Conflict from backend
- Stepper state loss on page refresh — store in sessionStorage

## Security Considerations
- All booking routes behind auth guard
- Validate seat selection client-side before API call
- Don't expose other users' booking data

## Next Steps
- Phase 05: Payment Module (receives bookingId from stepper step 3)
