# Phase 2: Theater Layout Realism

## Context Links
- [plan.md](plan.md)
- [Phase 1](phase-01-seat-type-differentiation-row-labels.md) (prerequisite)
- [seat-grid.component.ts](../../cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Curved row perspective, aisle gaps, enhanced screen element, VIP section visual separation

## Key Insights
- Current screen element is flat text with gradient -- needs perspective/curve
- Theater column count available from `Theater.totalColumns`; typical layout: 3 sections (left, center, right)
- Aisle positions can be computed: e.g., after column 3 and before column (totalColumns-2) for 3-section split
- VIP rows typically at back; PREMIUM in middle; STANDARD at front -- order by seatType group
- CSS `perspective` + `transform: rotateX()` can simulate curved row effect

## Requirements

### Functional
- FR1: Screen element with curved/perspective effect (CSS transform)
- FR2: Aisle gaps between seat sections (left/center/right)
- FR3: VIP section visually separated (extra spacing + subtle divider)
- FR4: Rows closer to screen appear slightly smaller (perspective depth)

### Non-Functional
- NFR1: Purely CSS -- no canvas, no JS layout calculations beyond gap positions
- NFR2: Must degrade gracefully on browsers without `perspective` support

## Architecture

### Screen Element
```css
.screen {
  perspective: 400px;
  transform: rotateX(-25deg);
  border-radius: 50% / 10%;
  /* gradient + box-shadow for glow */
}
```

### Aisle Gap Strategy
- Compute aisle positions from `totalColumns`: e.g., `aisleAfter = [Math.floor(totalColumns/3), Math.floor(2*totalColumns/3)]`
- In template, insert empty spacer `<div class="aisle">` after aisle column positions
- Alternative: use CSS `column-gap` on specific grid columns via `grid-template-columns` with wider gaps

### Curved Row Effect
- Wrap seat grid in container with `perspective: 800px`
- Apply `transform: rotateX(2deg)` on grid -- slight tilt gives depth
- OR per-row padding that increases toward front rows (simpler, more compatible)

### VIP Separation
- Detect row boundary where seatType changes (e.g., STANDARD→PREMIUM or PREMIUM→VIP)
- Insert `<div class="section-divider">` between row groups
- Extra `margin-top` on first VIP row

## Related Code Files

### Modify
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid.component.ts` -- add aisle logic, screen styling, section dividers

### Create (if needed for 200-line limit)
- `cinema-frontend/src/app/features/booking/seat-grid/seat-grid-layout.utils.ts` -- utility functions for aisle positions, section grouping

## Implementation Steps

1. **Enhance screen element CSS** -- add `perspective`, curved border-radius, gradient glow, box-shadow
2. **Compute aisle columns** -- create utility function `getAislePositions(totalColumns: number): number[]` returning column indices where gaps go
3. **Update grid template columns** -- modify `gridColumns` computed to insert wider gaps at aisle positions: e.g., `"40px repeat(3, 44px) 20px repeat(4, 44px) 20px repeat(3, 44px)"`
4. **Insert aisle spacers** in template -- when iterating seats, check if current `columnNumber` is at aisle boundary, render empty `<div class="aisle-gap">`
5. **Group rows by seat type section** -- compute sections: STANDARD rows, PREMIUM rows, VIP rows (use sorted unique seatType from each row)
6. **Add section dividers** -- render `<div class="section-divider">VIP</div>` between type groups
7. **Add VIP row extra spacing** -- CSS `margin-top: 16px` on first row of each new section
8. **Apply subtle perspective** on grid container -- `perspective: 1000px` + slight `rotateX`; keep it subtle (2-3deg max)
9. **Extract layout utils** into `seat-grid-layout.utils.ts` if seat-grid component approaches 200 lines

## Todo List
- [ ] Redesign screen element with CSS perspective + glow
- [ ] Implement aisle position computation utility
- [ ] Update grid-template-columns for aisle gaps
- [ ] Insert aisle spacer elements in template
- [ ] Group rows by seat type section
- [ ] Add section dividers between type groups
- [ ] Apply perspective transform on grid container
- [ ] Extract layout utils if needed
- [ ] Test with theaters of different sizes (small 5x5, large 15x20)

## Success Criteria
- Screen has curved/glowing appearance
- Visible aisle gaps divide seats into sections
- VIP rows visually separated from other types
- Subtle 3D perspective effect visible
- All seat selection functionality preserved

## Risk Assessment
- **Medium:** Aisle computation may break for theaters with odd column counts. Mitigation: use floor division, test edge cases
- **Low:** CSS perspective may cause click target misalignment. Mitigation: keep rotation angle minimal (<3deg), test click accuracy

## Security Considerations
- No security impact

## Next Steps
- Phase 3 makes the enhanced layout responsive for mobile
