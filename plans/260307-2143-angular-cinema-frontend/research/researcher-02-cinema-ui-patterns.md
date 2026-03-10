# Cinema Seat Selection & Booking UI Patterns Research

**Date:** 2026-03-07 | **Researcher:** Cinema Frontend Team

---

## 1. Cinema Seat Selection Grid Pattern

### Grid Rendering Architecture
- **Tech Stack**: CSS Grid/Flexbox + DOM elements or Canvas for high-density layouts
- **Seat States**: `available` (clickable), `reserved` (disabled), `selected` (highlighted), `occupied` (crossed out)
- **Touch-Friendly**: Minimum 44x44px clickable areas; tap/click events toggle selection state
- **Accessibility**: Keyboard navigation (arrow keys move focus), Enter/Space to select, visible focus indicators

### Implementation Approach
1. **Array-Based Model**: Store seat grid as 2D array `seats[row][column]` with state enum per seat
2. **Dynamic Grid Rendering**: CSS Grid with `grid-template-columns: repeat(auto-fit, minmax(40px, 1fr))` for responsiveness
3. **Selection Logic**:
   - Click handler toggles seat.selected boolean
   - Validate contiguous seat selection (optional)
   - Calculate total price as seats accumulate
4. **Visual Feedback**: CSS classes for states (`:hover` for available, `:active` for selection, `:disabled` for unavailable)

### Example Data Structure
```
seats: Seat[] = [
  { id: 'A1', row: 'A', col: 1, state: 'available', price: 150 },
  { id: 'A2', row: 'A', col: 2, state: 'reserved', price: 150 }
]
```

---

## 2. Angular Material Components for Booking

### Recommended Component Stack
| Component | Purpose | Notes |
|-----------|---------|-------|
| **Stepper** | Multi-step workflow (movie → seats → payment → confirm) | Horizontal for desktop, vertical for mobile |
| **Card** | Display movie info, seat summary, pricing details | Elevation changes on hover |
| **Dialog** | Payment confirmation, seat details, error messages | Modal overlay with action buttons |
| **Snackbar** | Transaction status, "Payment processing", "Booking confirmed" | Auto-dismiss after 3-5s or require action |
| **Toolbar** | Header with cinema name, selected seat count, total price | Sticky during seat selection |
| **Table** | Show seat availability legend, booking history (optional) | Read-only display |
| **Chips** | Display selected seats as removable tags (e.g., "A1 ✕", "A2 ✕") | Click to deselect |

### Layout Pattern
```
Toolbar (movie title + selected count)
├─ Stepper (horizontal)
│  ├─ Step 1: Movie Selection (Card grid)
│  ├─ Step 2: Seat Selection (Grid + Legend)
│  ├─ Step 3: Payment (Form dialog)
│  └─ Step 4: Confirmation (Card summary)
└─ Bottom Sheet / Dialog (Order Summary)
   ├─ Chips (selected seats)
   ├─ Price breakdown
   └─ Confirm button
```

---

## 3. Responsive Design Patterns

### Desktop (1200px+)
- Seat grid: 8-12 columns, scrollable horizontally if needed
- Sidebar: Right panel shows price summary, payment button (sticky)
- Stepper: Horizontal orientation

### Tablet (768-1199px)
- Seat grid: 6-8 columns, center-aligned
- Summary panel: Below grid or collapsible
- Stepper: Horizontal or vertical (user preference)

### Mobile (< 768px)
- Seat grid: 4-6 columns (rows/columns auto-adjust)
- Summary: Sticky bottom sheet with "Review Order" button
- Stepper: Vertical orientation
- Touch: 48px+ tap targets for seats

### CSS Media Queries
```css
@media (max-width: 768px) {
  .seat-grid { grid-template-columns: repeat(4, 1fr); gap: 8px; }
  .summary { position: fixed; bottom: 0; width: 100%; }
}
```

---

## 4. Payment Flow UI Pattern

### State Management
- **Polling**: After payment submit, poll `/api/payment/status/{transactionId}` every 2-3s for 30s max
- **Status States**: `pending` → `processing` → `success` / `failed`
- **UI Reflection**: Snackbar shows real-time status; disable interact during polling

### Flow Diagram
```
User clicks "Pay Now"
  ↓
Dialog opens (payment form)
  ↓
Submit payment
  ↓
Snackbar: "Processing..." (indeterminate progress)
  ↓
Poll backend status
  ↓
Success → Show confirmation card (booking ref, QR code, seats)
          → Snackbar: "Booking confirmed!" (5s auto-dismiss)
          → Navigate to confirmation page
Fail → Snackbar: "Payment failed. Please retry." (action button)
       → Reopen payment dialog
```

### Error Handling
- Network timeout: Retry UI with exponential backoff
- Payment declined: Show reason + suggest alternative payment method
- Session timeout: Redirect to login with cart preservation

---

## 5. Movie Browsing UI Pattern

### Card Grid Layout
- **Grid**: `grid-template-columns: repeat(auto-fill, minmax(200px, 1fr))` for responsive movie cards
- **Card Content**: Movie poster (image), title, rating, genre chips, "Select Showtime" button
- **Filtering**: Filter toolbar above grid (genre, rating, showtime dropdowns)
- **Search**: Search bar in toolbar with debounce (300ms) + live results

### Navigation Pattern
```
Movie Grid (filtered)
  ↓ Click card
Movie Detail Page
  ├─ Poster + Title + Rating + Reviews
  ├─ Showtime selector (date picker + time radio buttons)
  ├─ Seat selection (redirect to stepper step 2)
  └─ Back button
```

### Card Styling
- Hover: Scale 1.05 + shadow increase (Material elevation)
- Click: Navigate to detail or open in dialog
- Skeleton loaders during fetch

---

## Key Implementation Decisions

1. **Use Angular Material Stepper** for booking workflow—native Material Design, accessible, responsive
2. **Grid-based Seat Rendering**: Use CSS Grid for simplicity; Canvas only if 1000+ seats
3. **Snackbar for Feedback**: Auto-dismiss for success, actionable for errors
4. **Bottom Sheet on Mobile**: Sticky summary panel for order review (better than fixed toolbar)
5. **Optimistic Updates**: Mark selected seats immediately; revert on backend validation failure
6. **State Management**: Use NgRx or simple service with BehaviorSubject for cart/booking state

---

## Unresolved Questions

- Should seat grid support SVG overlays for non-rectangular theater layouts (e.g., curved screens)?
- Payment confirmation: Use dialog modal or redirect to dedicated confirmation page?
- Real-time seat sync: WebSocket vs. polling interval + refresh button?
