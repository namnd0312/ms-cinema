# Phase 3: Update Showtime Form Dialog

## Context Links
- [Plan Overview](./plan.md)
- [showtime-form-dialog.component.ts](../../cinema-frontend/src/app/features/admin/showtime-management/showtime-form-dialog.component.ts)
- [Phase 1 - Dependencies](./phase-01-setup-dependencies.md)

## Overview
- **Priority:** High
- **Status:** pending
- **Description:** Replace native `<input type="datetime-local">` with Material datetime picker for Start Time and End Time fields. Two approaches depending on Phase 1 outcome.

## Key Insights
- Currently uses `type="datetime-local"` with string values (`YYYY-MM-DDTHH:mm`)
- API expects ISO datetime strings: `"2026-04-01T14:30:00"`
- Needs both date AND time selection
- Form uses ReactiveFormsModule (FormBuilder)

## Requirements
### Functional
- Start Time and End Time fields show Material-styled date+time picker
- Calendar popup for date, time input for hours/minutes
- API receives same ISO datetime string format

### Non-functional
- Consistent Material styling with other date fields
- Accessible keyboard navigation

## Architecture

### Approach A: `@angular-material-components/datetime-picker` (if Phase 1 succeeds)
- Use `NgxMatDatetimePickerModule` for combined date+time
- Single field with calendar + time spinners

### Approach B: MatDatepicker + separate time input (fallback)
- Split each datetime field into 2 sub-fields: date (MatDatepicker) + time (`type="time"`)
- Combine Date + time string on save
- **Recommended:** Simpler, no extra dep, still Material-consistent

## Related Code Files
- **Modify:** `cinema-frontend/src/app/features/admin/showtime-management/showtime-form-dialog.component.ts`

## Implementation Steps (Approach B - Recommended Fallback)

### 1. Add imports
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

### 3. Update form controls -- split datetime into date + time
**Before:**
```typescript
form = inject(FormBuilder).group({
  // ...
  startTime: [this.formatDatetime(this.data.showtime?.startTime), Validators.required],
  endTime: [this.formatDatetime(this.data.showtime?.endTime), Validators.required],
});
```

**After:**
```typescript
form = inject(FormBuilder).group({
  // ...
  startDate: [this.parseDate(this.data.showtime?.startTime), Validators.required],
  startTime: [this.parseTime(this.data.showtime?.startTime), Validators.required],
  endDate: [this.parseDate(this.data.showtime?.endTime), Validators.required],
  endTime: [this.parseTime(this.data.showtime?.endTime), Validators.required],
});
```

### 4. Add parse helper methods
```typescript
private parseDate(iso?: string): Date | null {
  return iso ? new Date(iso) : null;
}

private parseTime(iso?: string): string {
  if (!iso) return '';
  return iso.substring(11, 16); // "HH:mm"
}

private combineDatetime(date: Date, time: string): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}T${time}:00`;
}
```

### 5. Remove old `formatDatetime` method

### 6. Update template
**Before:**
```html
<mat-form-field><mat-label>Start Time</mat-label>
  <input matInput type="datetime-local" formControlName="startTime">
</mat-form-field>
<mat-form-field><mat-label>End Time</mat-label>
  <input matInput type="datetime-local" formControlName="endTime">
</mat-form-field>
```

**After:**
```html
<mat-form-field><mat-label>Start Date</mat-label>
  <input matInput [matDatepicker]="startDp" formControlName="startDate">
  <mat-datepicker-toggle matSuffix [for]="startDp"></mat-datepicker-toggle>
  <mat-datepicker #startDp></mat-datepicker></mat-form-field>
<mat-form-field><mat-label>Start Time</mat-label>
  <input matInput type="time" formControlName="startTime"></mat-form-field>
<mat-form-field><mat-label>End Date</mat-label>
  <input matInput [matDatepicker]="endDp" formControlName="endDate">
  <mat-datepicker-toggle matSuffix [for]="endDp"></mat-datepicker-toggle>
  <mat-datepicker #endDp></mat-datepicker></mat-form-field>
<mat-form-field><mat-label>End Time</mat-label>
  <input matInput type="time" formControlName="endTime"></mat-form-field>
```

### 7. Update `save()` -- combine date + time before API call
**Before:**
```typescript
const request: CreateShowtimeRequest = {
  movieId: val.movieId!,
  theaterId: val.theaterId!,
  startTime: val.startTime!,
  endTime: val.endTime!,
  basePrice: val.basePrice!
};
```

**After:**
```typescript
const request: CreateShowtimeRequest = {
  movieId: val.movieId!,
  theaterId: val.theaterId!,
  startTime: this.combineDatetime(val.startDate!, val.startTime!),
  endTime: this.combineDatetime(val.endDate!, val.endTime!),
  basePrice: val.basePrice!
};
```

## Implementation Steps (Approach A - If datetime-picker available)

### 1. Import NgxMatDatetimePickerModule
```typescript
import { NgxMatDatetimePickerModule, NgxMatTimepickerModule } from '@angular-material-components/datetime-picker';
```

### 2. Use `ngx-mat-datetime-picker` in template
```html
<mat-form-field><mat-label>Start Time</mat-label>
  <input matInput [ngxMatDatetimePicker]="startDtp" formControlName="startTime">
  <ngx-mat-datetime-picker #startDtp></ngx-mat-datetime-picker>
  <mat-datepicker-toggle matSuffix [for]="startDtp"></mat-datepicker-toggle>
</mat-form-field>
```

### 3. Form controls use Date objects, format in save()

## Todo List
- [ ] Check Phase 1 outcome (Approach A or B)
- [ ] Add MatDatepickerModule + MatNativeDateModule imports
- [ ] Split startTime/endTime into date + time controls (Approach B)
- [ ] Add parseDate, parseTime, combineDatetime helpers
- [ ] Remove old formatDatetime method
- [ ] Update template with MatDatepicker + time inputs
- [ ] Update save() to combine date+time
- [ ] Verify build compiles
- [ ] Test date picker opens calendar popup
- [ ] Test time input works
- [ ] Verify API receives correct ISO datetime format

## Success Criteria
- Date fields show Material calendar popup
- Time fields allow HH:mm input
- Combined datetime sent to API as ISO string (e.g., `2026-04-01T14:30:00`)
- Edit mode pre-populates date and time correctly
- Create mode works with empty defaults

## Risk Assessment
- **Medium risk:** Splitting 1 field into 2 changes form layout
- **Mitigation:** Keep fields side-by-side in form grid (each pair on one row)
- **Edge case:** User sets end date before start date
- **Mitigation:** Out of scope for this task (can add validation later -- YAGNI)

## Security Considerations
- No security impact -- purely UI change

## Next Steps
- Proceed to Phase 4 (verify movie form)
