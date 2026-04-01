# Phase 4: Verify Movie Form Consistency

## Context Links
- [Plan Overview](./plan.md)
- [movie-form-dialog.component.ts](../../cinema-frontend/src/app/features/admin/movie-management/movie-form-dialog.component.ts)

## Overview
- **Priority:** Low
- **Status:** pending
- **Description:** Movie form already uses MatDatepicker. Verify it's consistent with updated components. Minor improvements if needed.

## Key Insights
- Already uses `MatDatepickerModule` + `MatNativeDateModule`
- Uses `matSuffix` for toggle, `[matDatepicker]="dp"` binding
- Formats output via `val.releaseDate?.toISOString().split('T')[0]`
- Potential timezone issue with `toISOString()` (converts to UTC)

## Requirements
### Functional
- No functional changes needed
- Verify same datepicker UX as reconciliation dashboard

### Non-functional
- Fix timezone-safe formatting if inconsistent with Phase 2

## Related Code Files
- **Review:** `cinema-frontend/src/app/features/admin/movie-management/movie-form-dialog.component.ts`

## Implementation Steps

### 1. Review date formatting for timezone safety
Current code:
```typescript
releaseDate: val.releaseDate?.toISOString().split('T')[0] ?? ''
```

If Phase 2 uses local-date formatting (year/month/day parts), apply same here:
```typescript
private formatDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}
```

### 2. Consider extracting shared date util
If all 3 components use same `formatDate()` logic, extract to:
`cinema-frontend/src/app/core/utils/date-format.util.ts`

Only if pattern repeats 3+ times (DRY). Likely yes: reconciliation + showtime + movie.

### 3. Verify visual consistency
- Calendar popup styling matches across components
- Toggle icon placement consistent (matSuffix)

## Todo List
- [ ] Review movie form datepicker -- no changes needed if already correct
- [ ] Fix timezone-safe formatting if using toISOString()
- [ ] Extract shared formatDate util if 3+ components use it
- [ ] Verify visual consistency

## Success Criteria
- Movie form datepicker behaves same as reconciliation dashboard
- No timezone-related date shift bugs
- Shared util created if DRY applies

## Risk Assessment
- **Very low risk:** Minimal or no code changes

## Security Considerations
- None

## Next Steps
- Proceed to Phase 5 (testing)
