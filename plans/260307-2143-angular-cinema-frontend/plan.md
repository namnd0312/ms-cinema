---
title: "Angular Cinema Frontend"
description: "Angular 17/18 frontend for movie ticket booking microservices"
status: pending
priority: P2
effort: 12h
branch: master
tags: [angular, frontend, material, cinema]
created: 2026-03-07
---

# Angular Cinema Frontend

## Overview

Standalone Angular 17/18 app with Material UI for movie browsing, seat booking, and payment. Connects to backend microservices via API Gateway at `localhost:8080`.

## Tech Stack

- Angular 17/18 (standalone components, new control flow)
- Angular Material (Material 3 theming)
- SCSS, CSS Grid for responsive layouts
- JWT auth with HttpInterceptorFn
- Signals for reactive state

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | Project Setup | 1.5h | pending | [phase-01](./phase-01-project-setup.md) |
| 2 | Auth Module | 2.5h | pending | [phase-02](./phase-02-auth-module.md) |
| 3 | Movie Module | 2h | pending | [phase-03](./phase-03-movie-module.md) |
| 4 | Booking Module | 2.5h | pending | [phase-04](./phase-04-booking-module.md) |
| 5 | Payment Module | 1.5h | pending | [phase-05](./phase-05-payment-module.md) |
| 6 | Layout & Polish | 2h | pending | [phase-06](./phase-06-layout-and-polish.md) |

## Dependencies

- Backend services running (auth, movie, booking, payment) via API Gateway
- Node.js 18+ / npm 9+
- Angular CLI 17+

## Key Decisions

- Signals over NgRx (simpler state; YAGNI)
- Feature-based folder structure with lazy loading
- CSS Grid for seat selection (no Canvas; <1000 seats)
- Fake payment endpoint for testing
- localStorage for JWT token storage

## Validation Summary

**Validated:** 2026-03-07
**Questions asked:** 4

### Confirmed Decisions
- **Token storage:** localStorage (simple, acceptable for dev/learning project)
- **Admin scope:** Include basic admin pages (theater + showtime CRUD) in Phase 6
- **Seat color scheme:** Cinema dark theme (dark bg, green=available, cyan=selected, red=occupied)
- **Booking flow:** Material Stepper (3 steps: seats → summary → payment)

### Action Items
- [ ] Update Phase 04 seat grid SCSS to use cinema dark theme colors (green/cyan/red)
- [ ] Ensure Phase 06 admin pages remain in scope

## Research Reports

- [Angular Patterns](./research/researcher-01-angular-patterns.md)
- [Cinema UI Patterns](./research/researcher-02-cinema-ui-patterns.md)
