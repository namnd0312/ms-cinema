# Phase 5: Testing & Validation

## Context Links
- [Plan Overview](./plan.md)
- All phase files in this directory

## Overview
- **Priority:** High
- **Status:** pending
- **Description:** Verify all datepicker changes compile, render, and send correct data to APIs.

## Key Insights
- No existing unit tests for these components (standalone dialog components)
- Primary validation: build + manual UI testing
- API contract must remain unchanged

## Requirements
### Functional
- All 3 components compile and render without errors
- Date formats sent to APIs are unchanged
- Calendar popups open and close correctly

### Non-functional
- No console errors in browser
- Responsive layout preserved

## Related Code Files
- All files modified in Phases 1-4

## Implementation Steps

### 1. Build verification
```bash
cd cinema-frontend && ng build
```
Must complete with 0 errors.

### 2. Manual UI testing checklist

**Movie Form Dialog:**
- [ ] Open Add Movie dialog
- [ ] Click Release Date field -- calendar popup opens
- [ ] Select a date -- field populates
- [ ] Save -- API receives `YYYY-MM-DD` format

**Reconciliation Dashboard:**
- [ ] Navigate to reconciliation page
- [ ] Both Start/End Date show calendar toggle icon
- [ ] Click toggle -- calendar popup opens
- [ ] Default dates = yesterday
- [ ] Click "Run Reconciliation" -- API receives `YYYY-MM-DD` strings

**Showtime Form Dialog:**
- [ ] Open Add Showtime dialog
- [ ] Start Date shows calendar popup
- [ ] Start Time accepts HH:mm input
- [ ] End Date shows calendar popup
- [ ] End Time accepts HH:mm input
- [ ] Save -- API receives ISO datetime `YYYY-MM-DDTHH:mm:00`
- [ ] Edit existing showtime -- date and time pre-populated

### 3. API payload verification
- Use browser DevTools Network tab
- Verify request payloads match expected formats
- Reconciliation: `{ "startDate": "2026-04-01", "endDate": "2026-04-01" }`
- Showtime: `{ "startTime": "2026-04-01T14:30:00", "endTime": "2026-04-01T16:30:00", ... }`

### 4. Edge cases
- [ ] Select date in different timezone -- verify no day shift
- [ ] Clear date field -- form validation catches required
- [ ] Very old/future dates -- calendar navigation works

## Todo List
- [ ] Run `ng build` -- 0 errors
- [ ] Test movie form datepicker
- [ ] Test reconciliation dashboard datepickers
- [ ] Test showtime form date+time fields
- [ ] Verify API payloads via DevTools
- [ ] Check timezone edge case

## Success Criteria
- Build passes
- All 3 forms use Material DatePicker
- API contracts unchanged
- No console errors
- Calendar popups render correctly

## Risk Assessment
- **Low risk:** Testing phase, no code changes

## Security Considerations
- Verify no date injection via picker (Material sanitizes input)

## Next Steps
- Mark plan as completed
- Update project changelog if needed
