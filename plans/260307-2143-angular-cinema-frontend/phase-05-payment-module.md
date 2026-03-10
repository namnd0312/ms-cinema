# Phase 05: Payment Module

## Context Links
- [Cinema UI Patterns](./research/researcher-02-cinema-ui-patterns.md) — payment flow, polling
- Backend: `POST /api/payments/create-intent`, `POST /api/payments/fake-success`
- [Phase 04](./phase-04-booking-module.md) — passes bookingId

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Payment flow using fake payment endpoint for testing. Payment status polling, history, and refund.

## Key Insights
- Backend provides `/api/payments/fake-success` for testing — no real payment gateway needed
- Poll payment status after submit (2-3s interval, 30s max)
- Show processing spinner during polling
- Success → auto-confirm booking → navigate to confirmation

## Requirements
### Functional
- Payment page showing booking summary + amount
- "Pay Now" triggers fake payment
- Processing indicator during payment
- Success/failure result pages
- Payment history (user's payments)
- Refund request (if applicable)

### Non-functional
- Auth guard on all payment routes
- Graceful timeout handling (30s max poll)
- Auto-navigate on success

## Architecture
```
features/payment/
├── payment.routes.ts
├── payment-page/
│   └── payment-page.component.ts      # Main payment form
├── payment-status/
│   └── payment-status.component.ts    # Success/failure display
└── payment-history/
    └── payment-history.component.ts   # User's payments

core/services/
└── payment.service.ts
```

## Related Code Files
- **Create:** `core/services/payment.service.ts`
- **Create:** `features/payment/payment.routes.ts`
- **Create:** `features/payment/payment-page/payment-page.component.ts`
- **Create:** `features/payment/payment-status/payment-status.component.ts`
- **Create:** `features/payment/payment-history/payment-history.component.ts`
- **Modify:** `app.routes.ts` — add payment lazy route

## Implementation Steps
1. Create `PaymentService`:
   - `createPaymentIntent(bookingId, amount, currency): Observable<Payment>`
   - `fakePaymentSuccess(bookingId): Observable<any>`
   - `getPayment(id): Observable<Payment>`
   - `getMyPayments(): Observable<Payment[]>`
   - `refundPayment(id): Observable<any>`
2. Create `PaymentPageComponent`:
   - Route param: `bookingId` (from query or route)
   - On init: fetch booking details, display summary
   - Show: movie, showtime, seats, total amount
   - "Pay Now" button:
     a. Call `createPaymentIntent()` (optional, depends on backend flow)
     b. Call `fakePaymentSuccess(bookingId)`
     c. Show MatProgressSpinner during processing
     d. On success: navigate to payment-status with success
     e. On failure: show error snackbar, allow retry
   - Disable button during processing
3. Create `PaymentStatusComponent`:
   - Route: `/payment/status/:bookingId`
   - Query param or state: `status=success|failed`
   - Success view: checkmark icon, booking reference, "View Booking" button
   - Failure view: error icon, reason, "Try Again" button
   - Auto-confirm booking on success: call `BookingService.confirmBooking()`
4. Create `PaymentHistoryComponent`:
   - Fetch `PaymentService.getMyPayments()`
   - Material table or card list
   - Columns: date, movie, amount, status, actions
   - Refund button for eligible payments
5. Define routes:
   - `pay/:bookingId` → PaymentPageComponent (auth guard)
   - `status/:bookingId` → PaymentStatusComponent (auth guard)
   - `history` → PaymentHistoryComponent (auth guard)

## Todo List
- [ ] PaymentService with API calls
- [ ] PaymentPageComponent (summary + pay button)
- [ ] PaymentStatusComponent (success/failure)
- [ ] PaymentHistoryComponent
- [ ] Payment routes with auth guard
- [ ] Processing spinner/indicator
- [ ] Error handling and retry

## Success Criteria
- "Pay Now" calls fake-success endpoint successfully
- Processing indicator shown during payment
- Success page displays booking confirmation
- Payment history lists user's payments
- Failure shows actionable error message

## Risk Assessment
- Fake payment always succeeds — no failure testing path (acceptable for MVP)
- Payment and booking confirmation not atomic — handle partial failures gracefully

## Security Considerations
- Auth guard on all payment routes
- Validate bookingId belongs to current user
- Don't expose payment details of other users

## Next Steps
- Phase 06: Layout & Polish (app shell, error handling, admin pages)
