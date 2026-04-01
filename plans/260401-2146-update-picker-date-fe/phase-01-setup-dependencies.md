# Phase 1: Setup & Dependencies

## Context Links
- [Plan Overview](./plan.md)
- [package.json](../../cinema-frontend/package.json)
- [Angular Material Datepicker docs](https://material.angular.io/components/datepicker)

## Overview
- **Priority:** High (blocker for other phases)
- **Status:** pending
- **Description:** Install `@angular-material-components/datetime-picker` for datetime fields in showtime form. Verify MatDatepicker + MatNativeDateModule already available.

## Key Insights
- Movie form already imports `MatDatepickerModule` + `MatNativeDateModule` -- proven pattern
- Angular 18 requires `@angular-material-components/datetime-picker` v18.x
- `@angular-material-components/datetime-picker` provides `NgxMatDatetimePickerModule` for combined date+time selection
- Uses same `MatNativeDateModule` date adapter, no extra adapter needed

## Requirements
### Functional
- `@angular-material-components/datetime-picker` installed and importable
- No version conflicts with existing Angular Material 18

### Non-functional
- Zero impact on existing movie-form-dialog behavior

## Architecture
- Standalone component imports -- each component imports what it needs
- No shared module needed (YAGNI)

## Related Code Files
- **Modify:** `cinema-frontend/package.json` (add dependency)

## Implementation Steps
1. Run `cd cinema-frontend && npm install @angular-material-components/datetime-picker@18`
2. Verify no peer dependency warnings for Angular 18 / Material 18
3. If v18 unavailable, check latest compatible version: `npm view @angular-material-components/datetime-picker versions`
4. Run `ng build` to confirm no compilation errors
5. **Fallback:** If `@angular-material-components/datetime-picker` has no Angular 18 support, use separate MatDatepicker + manual time input (MatFormField with `type="time"`) for showtime form -- simpler, no extra dep

## Todo List
- [ ] Install datetime-picker package
- [ ] Verify peer dependency compatibility
- [ ] Confirm build succeeds
- [ ] Document fallback if incompatible

## Success Criteria
- `npm install` succeeds without errors
- `ng build` compiles cleanly
- No version conflicts in node_modules

## Risk Assessment
- **High risk:** `@angular-material-components/datetime-picker` may not support Angular 18 yet (last published for Angular 16/17)
- **Mitigation:** Fallback to MatDatepicker + separate `<input type="time">` wrapped in MatFormField -- still consistent Material look, no extra deps

## Security Considerations
- Vet npm package for known vulnerabilities: `npm audit`

## Next Steps
- If install succeeds: proceed to Phase 2 + Phase 3
- If incompatible: use fallback (MatDatepicker + time input) and skip datetime-picker dep
