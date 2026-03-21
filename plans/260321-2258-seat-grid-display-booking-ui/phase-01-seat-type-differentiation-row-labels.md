# Phase 1: Seat Type Visual Differentiation & Row Labels

## Context Links
- [plan.md](plan.md)
- [seat-grid.component.ts](../../cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts)
- [movie.model.ts](../../cinema-frontend/src/app/core/models/movie.model.ts)
- Backend: `SeatType.java` enum (STANDARD, VIP, PREMIUM)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Add color-coded seat types, row letter labels on left side, price-per-type legend

## Key Insights
- Backend Seat entity has `seatType` (STANDARD/VIP/PREMIUM) and `priceMultiplier`
- Frontend Seat interface already has `seatType`, `rowNumber`, `columnNumber`, `price`
- Current grid uses flat green/cyan/gray for available/selected/occupied -- no type distinction
- Seats arrive with `seatLabel` (e.g., "A1") and `rowNumber` (numeric) -- derive row letter from seatLabel

## Requirements

### Functional
- FR1: Each seat type has distinct color: STANDARD (green), PREMIUM (blue), VIP (gold/amber)
- FR2: Row labels (A, B, C...) displayed on left side of each row
- FR3: Legend shows seat type + color + price range
- FR4: Selected state overlays seat type color (cyan border + glow)
- FR5: Seat tooltip shows: "A1 - VIP - $15.00"

### Non-Functional
- NFR1: Colors must pass WCAG AA contrast ratio (4.5:1 on dark bg)
- NFR2: No layout shift when row labels added

## Architecture

### Color Map (CSS custom properties)
```css
--seat-standard: #2e7d32;
--seat-premium: #1565c0;
--seat-vip: #ff8f00;
--seat-occupied: #424242;
--seat-selected-border: #00bcd4;
```

### Row Label Strategy
- Group seats by `rowNumber`, extract row letter from first seat's `seatLabel[0]`
- Render row label as first element in each grid row (not a seat button)
- Use CSS grid with extra column for labels: `gridColumns = "40px repeat(maxCol, 44px)"`

## Related Code Files

### Modify
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` -- add type classes, row labels, updated legend
- `cinema-frontend/src/app/core/models/movie.model.ts` -- no changes needed (seatType already present)

### Create (if seat-grid exceeds 200 lines after changes)
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid-legend.component.ts` -- extracted legend sub-component

## Implementation Steps

1. **Define seat type color map** in seat-grid styles using CSS classes `.seat.standard`, `.seat.premium`, `.seat.vip`
2. **Update `getSeatState()` method** to return compound state: include seat type for CSS class binding
3. **Add `[class]` bindings** on seat button: `[class.standard]="seat.seatType === 'STANDARD'"` etc. Keep state classes (available/selected/occupied) separate
4. **Compute row-grouped seats** -- create `computed()` that groups seats by `rowNumber`, sorts rows ascending, extracts row letter
5. **Update template** to iterate rows, render row label `<span>` + seat buttons per row
6. **Update gridColumns computed** to prepend label column: `"40px repeat(maxCol, 44px)"`
7. **Enhance legend** to show seat types with prices: "STANDARD - $10", "PREMIUM - $13", "VIP - $18" (derive from unique seatType+price combos in seats data)
8. **Update title attribute** to include type: `[title]="seat.seatLabel + ' - ' + seat.seatType + ' - $' + seat.price"`
9. **If component exceeds 200 lines**, extract legend into `seat-grid-legend.component.ts`

## Todo List
- [ ] Define CSS color classes per seat type
- [ ] Update seat button class bindings for type + state
- [ ] Compute row-grouped seat layout
- [ ] Render row labels in grid
- [ ] Update grid columns to include label column
- [ ] Enhance legend with seat type + price
- [ ] Update tooltip/title text
- [ ] Extract legend component if >200 lines
- [ ] Verify no regression on seat selection/toggle

## Success Criteria
- Seats visually distinguishable by type (3 distinct colors)
- Row labels visible on left side
- Legend shows all seat types with price
- Existing seat selection + booking flow unchanged
- Component files stay under 200 lines

## Risk Assessment
- **Low:** Color choices may not be accessible. Mitigation: verify contrast ratios
- **Low:** Grid layout may break with row labels. Mitigation: test with various theater sizes

## Security Considerations
- No security impact (frontend display only, no new API calls)

## Next Steps
- Phase 2 builds on this layout to add aisle gaps and curved rows
