---
title: "Admin CRUD Frontend with MatDialog Forms"
description: "Build all admin CRUD features in Angular 18 frontend with MatDialog popup forms for movies, theaters, showtimes, and payment refunds"
status: pending
priority: P1
effort: 7h
branch: master
tags: [angular, admin, crud, mat-dialog, frontend]
created: 2026-03-12
---

# Admin CRUD Frontend Implementation Plan

## Context
Backend exposes 7 admin endpoints (movie CRUD, theater CRUD, showtime CRUD, payment refund). Frontend has 2 read-only admin pages (theaters, showtimes). Goal: full admin CRUD UI with MatDialog forms.

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | [Admin Services & Models](./phase-01-admin-services-and-models.md) | 1h | pending | Services, interfaces, model updates |
| 2 | [Movie Management](./phase-02-movie-management-page-and-dialog.md) | 2h | pending | New page + dialog form |
| 3 | [Theater Management Enhancement](./phase-03-theater-management-enhancement-and-dialog.md) | 1.5h | pending | Enhance existing + dialog form |
| 4 | [Showtime Management Enhancement](./phase-04-showtime-management-enhancement-and-dialog.md) | 1.5h | pending | Enhance existing + dialog form |
| 5 | [Payment Refund & Admin Nav](./phase-05-payment-refund-management-and-admin-nav.md) | 1h | pending | New page + refund + nav tabs |

## Key Dependencies
- All existing services (MovieService, PaymentService, AuthService) already have CRUD methods
- No TheaterService exists yet (Phase 1 creates it)
- No ShowtimeService for admin create/update (Phase 1 creates it)
- **Backend gap:** No `GET /api/payments` admin list-all endpoint (Phase 5 risk)

## Architecture Decisions
- MatDialog for create/edit forms (first MatDialog usage in codebase)
- MatTable for data lists (replaces mat-card lists)
- `window.confirm()` for delete confirmation (KISS, no custom dialog)
- MatSnackBar for success/error feedback (existing pattern)
- Inline templates/styles (existing convention, keep files <200 LOC)
- Standalone components with signals (Angular 18 pattern)

## Files Overview
- **New services:** `theater.service.ts`, `showtime-admin.service.ts`
- **New components:** 3 dialog forms + 2 new management pages (movie, payment) + 1 admin-nav layout
- **Modified:** 2 existing management components, `movie.model.ts`, `payment.service.ts`, `admin.routes.ts`

## Validation Summary

**Validated:** 2026-03-12
**Questions asked:** 4

### Confirmed Decisions
- **Backend gap:** Add `GET /api/payments` admin endpoint NOW (before Phase 5 frontend)
- **No delete for theaters/showtimes:** Correct, matches backend capability
- **Admin nav layout:** MatTabNav with tab bar wrapping all admin pages via `<router-outlet>`
- **Movie rating field:** MatSelect with preset values (G, PG, PG-13, R, NC-17)

### Action Items
- [ ] Add `GET /api/payments` + `getAllPayments()` to PaymentController/PaymentService in backend (pre-Phase 5)
