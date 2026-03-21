# Phase 3: Responsive & Mobile Support

## Context Links
- [plan.md](plan.md)
- [Phase 1](phase-01-seat-type-differentiation-row-labels.md), [Phase 2](phase-02-theater-layout-realism.md) (prerequisites)
- [seat-grid.component.ts](../../cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts)
- [seat-selection.component.ts](../../cinema-frontend/src/app/features/booking/seat-selection/seat-selection.component.ts)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Mobile-friendly seat grid with horizontal scroll, touch-optimized buttons, pinch-to-zoom, responsive stepper

## Key Insights
- Current seat grid uses fixed 44px buttons -- too small for touch on small screens
- Grid doesn't scroll horizontally; large theaters overflow on mobile
- Mat-stepper is already responsive but step labels could be condensed on mobile
- `max-width: 900px` on selection container may limit usability on tablets
- Pinch-to-zoom can be achieved with CSS `touch-action: manipulation` + JS gesture handling (or simpler: let browser native zoom work on grid container)

## Requirements

### Functional
- FR1: Horizontal scroll on seat grid when theater wider than viewport
- FR2: Larger touch targets on mobile (min 48px per WCAG)
- FR3: Pinch-to-zoom support for large theaters (optional: CSS transform scale)
- FR4: Responsive stepper -- icon-only labels on small screens
- FR5: Floating selection summary on mobile (bottom bar showing count + total)

### Non-Functional
- NFR1: Breakpoints: mobile <600px, tablet 600-960px, desktop >960px
- NFR2: No layout jumps on orientation change
- NFR3: Smooth scroll behavior

## Architecture

### Horizontal Scroll
```css
.seat-grid-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scroll-snap-type: x proximity;
}
```

### Responsive Seat Sizing
```css
/* Mobile: larger for touch */
@media (max-width: 600px) {
  .seat { width: 36px; height: 36px; font-size: 0.6rem; }
  .seat-grid { gap: 4px; }
}
/* Default remains 44px */
```

### Pinch-to-Zoom (Simple Approach)
- Wrap grid in container with `touch-action: pan-x pan-y pinch-zoom`
- Let browser native pinch-zoom handle it within the scroll container
- Avoid custom JS gesture handlers (YAGNI -- native zoom sufficient for MVP)

### Mobile Bottom Summary Bar
```html
<!-- Fixed bottom bar on mobile, showing seat count + total -->
<div class="mobile-summary-bar">
  <span>{{ count }} seats</span>
  <span>{{ total | currency }}</span>
  <button mat-raised-button>Next</button>
</div>
```

## Related Code Files

### Modify
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` -- add scroll wrapper, responsive seat sizes
- `cinema-frontend/src/app/features/booking/seat-selection/seat-selection.component.ts` -- add mobile summary bar, responsive stepper

## Implementation Steps

1. **Wrap seat grid in scroll container** -- add `.seat-grid-wrapper` div with `overflow-x: auto` around the grid
2. **Add responsive media queries** for seat sizes:
   - Mobile (<600px): 36px seats, 4px gap
   - Tablet (600-960px): 40px seats, 5px gap
   - Desktop (>960px): 44px seats, 6px gap (current)
3. **Enable native pinch-to-zoom** -- set `touch-action: pan-x pan-y pinch-zoom` on grid wrapper
4. **Add scroll indicators** -- CSS gradient fade on left/right edges when content overflows (`::-webkit-scrollbar` styling or `scroll-padding`)
5. **Add mobile summary bar** in seat-selection component -- `@if (isMobile())` fixed bottom bar with seat count, total, Next button
6. **Detect mobile** via `window.matchMedia('(max-width: 600px)')` or Angular CDK BreakpointObserver
7. **Responsive stepper labels** -- on mobile, use `<mat-step label="">` with icon-only display, or use `[labelPosition]="'bottom'"` with shorter text
8. **Remove `max-width: 900px`** constraint on mobile -- use `100%` width with padding
9. **Test orientation change** -- verify layout adapts on portrait/landscape switch

## Todo List
- [ ] Add scroll wrapper with overflow-x: auto
- [ ] Implement responsive seat size media queries
- [ ] Enable native pinch-to-zoom on grid wrapper
- [ ] Add scroll edge fade indicators
- [ ] Create mobile summary bar in seat-selection
- [ ] Add mobile detection (BreakpointObserver)
- [ ] Responsive stepper label adaptation
- [ ] Remove width constraint on mobile
- [ ] Test on various screen sizes (375px, 768px, 1024px, 1440px)

## Success Criteria
- Seat grid horizontally scrollable on small screens
- Touch targets at least 36px (ideally 48px) on mobile
- Pinch-to-zoom works natively within grid area
- Stepper readable on mobile
- No content overflow or clipping

## Risk Assessment
- **Medium:** Horizontal scroll may confuse users who expect vertical layout. Mitigation: add visual scroll hint (arrow indicator)
- **Low:** BreakpointObserver adds CDK dependency. Mitigation: CDK is already a Material dependency, zero extra weight

## Security Considerations
- No security impact

## Next Steps
- Phase 4 adds keyboard navigation and screen reader support
