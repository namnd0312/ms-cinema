# Phase 06: Adjacent Seat Suggestion for Group Bookings

## Context Links
- Parent: [plan.md](plan.md)
- Depends on: Phase 1 (seat types), Phase 2 (aisle layout)
- Backend: `movie-service/src/main/java/com/namnd/movieservice/`
- Frontend: `cinema-frontend/src/app/features/booking/`

## Overview
- **Priority:** Medium
- **Status:** Pending
- **Effort:** 2.5h
- **Description:** Add "Find N seats together" feature — user enters group size, system suggests best adjacent available seat groups ranked by preference (center seats, same type, closest to screen).

## Key Insights
- Adjacency = same row, consecutive column numbers, no aisle gap between them
- Need to know which seats are booked (from booking-service) + seat layout (from movie-service)
- Algorithm runs on frontend using already-loaded seat data — no new backend endpoint needed
- Aisle positions (from Phase 2) must be respected when computing adjacency
- Suggestion should prefer: center of row > front rows > same seat type

## Requirements
### Functional
- User inputs group size (2-10) via number input or +/- buttons
- System highlights top 3 adjacent seat groups on the grid
- User clicks a suggestion to auto-select those seats
- "Clear suggestion" button to reset
- Works with real-time seat updates (Phase 5) — re-compute if seats become unavailable

### Non-Functional
- Algorithm runs client-side (seats already loaded in memory)
- Max computation time < 50ms for 300-seat theater
- Suggestions update when seat availability changes

## Architecture
```
[User enters groupSize=4]
  → SeatSuggestionService.findAdjacentGroups(seats, bookedIds, groupSize, aislePositions)
  → Returns top 3 groups sorted by score
  → SeatGridComponent highlights suggested groups
  → User clicks group → auto-selects all seats in group
```

### Frontend Components
- `seat-suggestion.service.ts` — pure algorithm service, finds adjacent groups, scores them
- `seat-suggestion-panel.component.ts` — UI: group size input + suggestion chips
- Update `seat-grid.component.ts` — highlight suggested seats with distinct style
- Update `seat-selection.component.ts` — orchestrate suggestion flow

## Related Code Files
### Frontend — Create
- `cinema-frontend/src/app/core/services/seat-suggestion.service.ts`
- `cinema-frontend/src/app/features/booking/seat-suggestion-panel/seat-suggestion-panel.component.ts`

### Frontend — Modify
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` — add `suggestedSeatIds` input, highlight style
- `cinema-frontend/src/app/features/booking/seat-selection/seat-selection.component.ts` — add suggestion panel, wire up auto-select

## Implementation Steps

### Algorithm Service (1h)
1. Create `seat-suggestion.service.ts`:
   - Method: `findAdjacentGroups(seats: Seat[], occupiedIds: Set<number>, groupSize: number, aisleColumns?: number[]): SeatGroup[]`
   - Group available seats by row
   - Within each row, find consecutive runs of available seats (skip aisle columns)
   - Extract all windows of `groupSize` from each run
   - Score each group:
     - **Center bonus**: seats closer to center column get higher score
     - **Row bonus**: prefer rows 3-7 (sweet spot for viewing)
     - **Type bonus**: prefer groups where all seats are same type
   - Return top 3 groups sorted by score descending
   - `SeatGroup` interface: `{ seats: Seat[], score: number, rowLabel: string }`

### Suggestion Panel UI (0.5h)
2. Create `seat-suggestion-panel.component.ts`:
   - Input: `groupSize` (number signal)
   - Output: `groupSizeChanged`, `suggestionSelected`, `suggestionsCleared`
   - Template: number input (min 2, max 10) + "Find seats" button
   - Show suggestion chips: "Row A: A3-A6 ($40)" — click to select
   - "Clear" button to reset

### Grid Integration (0.5h)
3. Update `seat-grid.component.ts`:
   - Add input: `suggestedSeatIds = input<Set<number>>(new Set())`
   - Add CSS class `.suggested` with pulsing border animation (e.g., gold/amber glow)
   - Update `getSeatState()` to return `'suggested'` when seat is in suggestedSeatIds and available

### Orchestration (0.5h)
4. Update `seat-selection.component.ts`:
   - Add `SeatSuggestionService` injection
   - Add `suggestedGroups` signal and `suggestedSeatIds` computed signal
   - On group size change: compute suggestions from current available seats
   - On suggestion selected: set `selectedSeatIds` to that group's seat IDs
   - On clear: reset suggestions
   - Re-compute suggestions when seat availability changes (WebSocket from Phase 5)
   - Place `<app-seat-suggestion-panel>` above the seat grid in Step 1

## Todo List
- [ ] Create `seat-suggestion.service.ts` with adjacency algorithm
- [ ] Create `seat-suggestion-panel.component.ts` UI
- [ ] Add `.suggested` style + `suggestedSeatIds` input to `seat-grid.component.ts`
- [ ] Wire suggestion flow in `seat-selection.component.ts`
- [ ] Handle edge case: fewer than 3 groups available
- [ ] Handle edge case: no adjacent group of requested size exists (show message)
- [ ] Re-compute on real-time seat changes (if Phase 5 implemented)
- [ ] Test with various theater sizes and group sizes

## Success Criteria
- "Find 4 seats together" returns adjacent seats in same row
- Suggestions respect aisle gaps (don't span across aisles)
- Clicking suggestion auto-selects all seats in group
- Algorithm runs < 50ms for 300 seats
- Compilation passes: `ng build`

## Risk Assessment
- **Large theaters** — 500+ seats could have many combinations; window-sliding approach is O(rows × cols), efficient enough
- **Aisle detection** — Depends on Phase 2's aisle column definitions; if Phase 2 not done yet, skip aisle-awareness initially
- **Real-time invalidation** — If a suggested seat gets taken via WebSocket, suggestion becomes stale; re-compute on WS events

## Security Considerations
- Pure frontend logic, no security concerns
- No user data exposed

## Next Steps
- Future: backend-optimized suggestion endpoint for very large theaters
- Future: preference for wheelchair-accessible seats
- Future: "best value" mode — suggest cheapest adjacent group
