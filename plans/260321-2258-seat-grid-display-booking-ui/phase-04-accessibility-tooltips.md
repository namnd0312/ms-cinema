# Phase 4: Accessibility & Tooltips

## Context Links
- [plan.md](plan.md)
- [Phase 1](phase-01-seat-type-differentiation-row-labels.md) (prerequisite for type info)
- [seat-grid.component.ts](../../cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** ARIA labels, keyboard navigation, screen reader support, hover tooltips, focus indicators

## Key Insights
- Current seat buttons have `[title]` but no ARIA labels
- No keyboard navigation between seats (arrow keys)
- Angular Material's `MatTooltip` can replace raw `[title]` for richer tooltips
- Focus indicators absent -- default browser outline likely overridden by custom styles
- Grid structure suits `role="grid"` with `role="row"` and `role="gridcell"` for screen readers

## Requirements

### Functional
- FR1: ARIA labels on each seat: "Seat A1, Standard, $10, Available"
- FR2: Keyboard navigation: Arrow keys move focus between seats, Enter/Space toggles selection
- FR3: Screen reader announces seat state changes on selection
- FR4: MatTooltip on hover showing: seat label, type, price, status
- FR5: Visible focus ring on keyboard navigation

### Non-Functional
- NFR1: WCAG 2.1 AA compliance
- NFR2: Focus ring 2px solid with offset, visible on dark background
- NFR3: Tooltip delay: 300ms show, 100ms hide

## Architecture

### ARIA Grid Pattern
```html
<div role="grid" aria-label="Theater seat map">
  <div role="row" aria-label="Row A">
    <span role="rowheader">A</span>
    <button role="gridcell" aria-label="Seat A1, Standard, $10, Available"
            [attr.aria-pressed]="isSelected(seat)">
      A1
    </button>
  </div>
</div>
```

### Keyboard Navigation
- Use `@HostListener('keydown')` on grid container
- Arrow keys: move focus to adjacent seat (up/down = row, left/right = column)
- Home/End: first/last seat in row
- Enter/Space: toggle selection
- Track focused seat index via signal

### Focus Indicator
```css
.seat:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 2px;
  box-shadow: 0 0 0 4px rgba(255,255,255,0.3);
}
```

## Related Code Files

### Modify
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` -- add ARIA attrs, keyboard handler, tooltips, focus styles

### Add Import
- `MatTooltipModule` from `@angular/material/tooltip`

## Implementation Steps

1. **Add ARIA roles** to grid structure:
   - Grid wrapper: `role="grid"` `aria-label="Theater seat map"`
   - Each row div: `role="row"` `aria-label="Row A"`
   - Row label span: `role="rowheader"`
   - Each seat button: `role="gridcell"` + `[attr.aria-pressed]` + `[attr.aria-label]`
2. **Create `getAriaLabel(seat)` method** returning: `"Seat {label}, {type}, ${price}, {state}"`
3. **Add MatTooltip** on seat buttons: `[matTooltip]="getTooltipText(seat)"` with `matTooltipShowDelay="300"`
4. **Create `getTooltipText(seat)` method** returning: `"{label} - {type} - ${price}"`
5. **Implement keyboard navigation**:
   - Track `focusedRow` and `focusedCol` signals
   - On ArrowRight/Left: increment/decrement column, wrap at edges
   - On ArrowUp/Down: increment/decrement row
   - On Enter/Space: call `onSeatClick()` for focused seat
   - Use `ViewChildren` to query seat buttons, call `.focus()` on target
6. **Add `tabindex` management** -- focused seat gets `tabindex="0"`, others get `tabindex="-1"` (roving tabindex pattern)
7. **Add focus-visible styles** -- visible ring that doesn't appear on mouse click
8. **Add live region** for selection feedback: `<div aria-live="polite" class="sr-only">` announcing "Seat A1 selected" / "Seat A1 deselected"
9. **Add `.sr-only` utility class** for screen-reader-only content

## Todo List
- [ ] Add ARIA roles (grid, row, gridcell, rowheader)
- [ ] Implement getAriaLabel() method
- [ ] Add MatTooltip with tooltip text
- [ ] Implement keyboard navigation (arrow keys, Enter/Space)
- [ ] Implement roving tabindex pattern
- [ ] Add focus-visible CSS styles
- [ ] Add aria-live region for selection announcements
- [ ] Add sr-only utility class
- [ ] Test with screen reader (VoiceOver on macOS)
- [ ] Test keyboard-only navigation flow

## Success Criteria
- All seats have descriptive ARIA labels
- Arrow keys navigate between seats, Enter/Space toggles
- Screen reader announces seat info on focus and selection changes
- MatTooltip shows on hover with seat details
- Focus ring clearly visible on keyboard navigation
- VoiceOver reads correct seat info

## Risk Assessment
- **Medium:** Keyboard navigation may conflict with Mat-stepper keyboard shortcuts. Mitigation: only handle arrow keys when grid is focused (check `event.target` ancestry)
- **Low:** Roving tabindex adds complexity. Mitigation: well-tested pattern, keep implementation simple

## Security Considerations
- No security impact

## Next Steps
- All 4 phases complete the FR-3.1 scope
- Follow with code review and testing
