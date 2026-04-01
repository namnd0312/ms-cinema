---
title: "Unify date inputs to Angular Material DatePicker"
description: "Replace native date/datetime-local inputs with MatDatepicker across all admin forms"
status: completed
priority: P2
effort: 3h
branch: master
tags: [frontend, angular-material, datepicker, ux-consistency]
created: 2026-04-01
---

# Unify Date Inputs to Angular Material DatePicker

## Problem

3 admin components use inconsistent date input approaches:
- **movie-form-dialog**: Already uses `MatDatepicker` (date-only) -- reference impl
- **reconciliation-dashboard**: Uses native `<input type="date">` with ngModel
- **showtime-form-dialog**: Uses native `<input type="datetime-local">` (needs date+time)

## Goal

All date fields use Angular Material DatePicker with consistent UX. Showtime form needs datetime (date+time) selection via `@angular-material-components/datetime-picker`.

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Setup & Dependencies](./phase-01-setup-dependencies.md) | skipped (used fallback) | - |
| 2 | [Update Reconciliation Dashboard](./phase-02-update-reconciliation-dashboard.md) | completed | 45m |
| 3 | [Update Showtime Form Dialog](./phase-03-update-showtime-form-dialog.md) | completed | 1h |
| 4 | [Verify Movie Form Consistency](./phase-04-verify-movie-form-consistency.md) | completed | 15m |
| 5 | [Testing & Validation](./phase-05-testing-validation.md) | completed | 30m |

## Key Dependencies

- Angular 18 / Angular Material 18 / Angular CDK 18
- `@angular-material-components/datetime-picker` v18 (for datetime fields)
- API expects: `YYYY-MM-DD` (reconciliation), ISO datetime string (showtime)

## Files Affected

- `cinema-frontend/package.json` (new dep)
- `cinema-frontend/src/app/features/admin/reconciliation/reconciliation-dashboard.component.ts`
- `cinema-frontend/src/app/features/admin/showtime-management/showtime-form-dialog.component.ts`
- `cinema-frontend/src/app/features/admin/movie-management/movie-form-dialog.component.ts` (verify only)
