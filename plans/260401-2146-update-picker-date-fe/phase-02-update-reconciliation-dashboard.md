# Phase 2: Update Reconciliation Dashboard

## Context Links
- [Plan Overview](./plan.md)
- [reconciliation-dashboard.component.ts](../../cinema-frontend/src/app/features/admin/reconciliation/reconciliation-dashboard.component.ts)
- [movie-form-dialog.component.ts](../../cinema-frontend/src/app/features/admin/movie-management/movie-form-dialog.component.ts) (reference impl)

## Overview
- **Priority:** Medium
- **Status:** pending
- **Description:** Replace native `<input type="date">` with MatDatepicker for Start Date and End Date fields in reconciliation dashboard.

## Key Insights
- Currently uses `FormsModule` + `ngModel` with string dates (`YYYY-MM-DD`)
- API expects `{ startDate: "YYYY-MM-DD", endDate: "YYYY-MM-DD" }` as strings
- MatDatepicker outputs `Date` objects -- need to format to string before API call
- Movie form already shows the pattern: `val.releaseDate?.toISOString().split('T')[0]`

## Requirements
### Functional
- Start Date and End Date use MatDatepicker with calendar popup
- Date format sent to API unchanged (`YYYY-MM-DD`)
- Default dates (yesterday) still set on init

### Non-functional
- Consistent look with movie-form-dialog datepicker
- No breaking changes to reconciliation trigger API

## Architecture
- Switch from `ngModel` string binding to `ngModel` Date object binding
- Format Date->string in `trigger()` method before API call
- Keep using `FormsModule` + `ngModel` (no need to switch to reactive forms -- YAGNI)

## Related Code Files
- **Modify:** `cinema-frontend/src/app/features/admin/reconciliation/reconciliation-dashboard.component.ts`

## Implementation Steps

### 1. Add imports to component
```typescript
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
```

### 2. Add to `imports` array
```typescript
imports: [
  // ... existing
  MatDatepickerModule, MatNativeDateModule
],
```

### 3. Update template -- replace native date inputs
**Before:**
```html
<mat-form-field><mat-label>Start Date</mat-label>
  <input matInput type="date" [(ngModel)]="startDate"></mat-form-field>
<mat-form-field><mat-label>End Date</mat-label>
  <input matInput type="date" [(ngModel)]="endDate"></mat-form-field>
```

**After:**
```html
<mat-form-field><mat-label>Start Date</mat-label>
  <input matInput [matDatepicker]="startDp" [(ngModel)]="startDate">
  <mat-datepicker-toggle matSuffix [for]="startDp"></mat-datepicker-toggle>
  <mat-datepicker #startDp></mat-datepicker></mat-form-field>
<mat-form-field><mat-label>End Date</mat-label>
  <input matInput [matDatepicker]="endDp" [(ngModel)]="endDate">
  <mat-datepicker-toggle matSuffix [for]="endDp"></mat-datepicker-toggle>
  <mat-datepicker #endDp></mat-datepicker></mat-form-field>
```

### 4. Change property types from string to Date
**Before:**
```typescript
startDate = '';
endDate = '';
```

**After:**
```typescript
startDate: Date | null = null;
endDate: Date | null = null;
```

### 5. Update `ngOnInit` default date initialization
**Before:**
```typescript
this.startDate = this.endDate = yesterday.toISOString().split('T')[0];
```

**After:**
```typescript
this.startDate = yesterday;
this.endDate = yesterday;
```

### 6. Update `trigger()` -- format Date to string for API
**Before:**
```typescript
if (!this.startDate || !this.endDate) return;
this.service.triggerReconciliation(this.startDate, this.endDate).subscribe({
```

**After:**
```typescript
if (!this.startDate || !this.endDate) return;
const fmt = (d: Date) => d.toISOString().split('T')[0];
this.service.triggerReconciliation(fmt(this.startDate), fmt(this.endDate)).subscribe({
```

## Todo List
- [ ] Add MatDatepickerModule + MatNativeDateModule imports
- [ ] Replace template `type="date"` with MatDatepicker markup
- [ ] Change startDate/endDate from string to Date|null
- [ ] Update ngOnInit default date logic
- [ ] Update trigger() to format Date->string
- [ ] Verify build compiles
- [ ] Test calendar popup opens for both fields
- [ ] Verify API receives correct date format

## Success Criteria
- Both date fields show calendar popup icon
- Clicking icon opens Material calendar
- Selected dates format correctly as `YYYY-MM-DD` to API
- Default date (yesterday) still pre-populated

## Risk Assessment
- **Low risk:** Straightforward replacement, movie form proves pattern works
- **Edge case:** Timezone offset in `toISOString()` could shift date by 1 day
- **Mitigation:** Consider using `DatePipe` or manual formatting: `${y}-${m}-${d}` from Date parts to avoid timezone issues

## Security Considerations
- No security impact -- purely UI change

## Next Steps
- Proceed to Phase 3 (showtime form)
