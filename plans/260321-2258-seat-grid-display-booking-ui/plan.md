---
title: "FR-3.1: Seat Grid Display & Booking UI Improvements"
description: "Seat grid UI/UX overhaul: type colors, theater realism, responsive, a11y, real-time WebSocket updates, adjacent seat suggestions"
status: reviewed
priority: P2
effort: 14h
branch: master
tags: [frontend, backend, ui-ux, booking, seat-grid, accessibility, websocket, real-time]
created: 2026-03-21
---

# FR-3.1: Seat Grid Display & Booking UI Improvements

## Summary

Enhance the seat booking experience with visual seat type differentiation, realistic theater layout, responsive mobile support, full accessibility compliance, real-time WebSocket seat updates, and adjacent seat suggestions for group bookings. Phases 1-4 are frontend-only; phases 5-6 require backend changes (booking-service WebSocket + suggestion endpoint).

## Current State

- `SeatGridComponent` renders flat grid, no seat type colors, no row labels, no aisle gaps
- `SeatSelectionComponent` uses Material stepper, loads seats+booked IDs, countdown timer works
- `BookingSummaryComponent` shows chips with seatType+price, total price
- Seat model (frontend): `id, seatLabel, seatType, rowNumber, columnNumber, price, status`
- Backend SeatType enum: STANDARD, VIP, PREMIUM with priceMultiplier

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Seat Type Visual Differentiation & Row Labels | done | 2h | [phase-01](phase-01-seat-type-differentiation-row-labels.md) |
| 2 | Theater Layout Realism | done | 2.5h | [phase-02](phase-02-theater-layout-realism.md) |
| 3 | Responsive & Mobile Support | done | 2h | [phase-03](phase-03-responsive-mobile-support.md) |
| 4 | Accessibility & Tooltips | done | 1.5h | [phase-04](phase-04-accessibility-tooltips.md) |
| 5 | Real-time Seat Availability (WebSocket) | done | 3.5h | [phase-05](phase-05-real-time-seat-availability-websocket.md) |
| 6 | Adjacent Seat Suggestion for Groups | done | 2.5h | [phase-06](phase-06-adjacent-seat-suggestion-groups.md) |

## Key Dependencies

- Backend Seat model: `seatType` (STANDARD/VIP/PREMIUM), `rowLabel`, `seatNumber`, `priceMultiplier`
- Frontend Seat interface: `seatType, rowNumber, columnNumber, price, status`
- Angular Material 18 (MatTooltip, MatStepper, MatChips) already in project
- Redis seat locking in `SeatLockServiceImpl` — Phase 5 publishes lock/unlock events
- No WebSocket infra exists yet — Phase 5 adds `spring-boot-starter-websocket` + STOMP to booking-service

## Code Review

**Report:** `plans/reports/code-reviewer-260322-1333-seat-grid-booking-ui-final.md`
**Date:** 2026-03-22
**Result:** Passed — 0 critical, 3 high, 5 medium, 6 low findings.

**Required fixes before close:**
- [ ] Add `allowedCommonJsDependencies` to `angular.json` for `@stomp/stompjs` and `sockjs-client`
- [ ] Extract `seat-grid.component.ts` inline styles to `.scss` (style budget exceeded)
- [ ] Treat `LOCKED` status as occupied in `subscribeToSeatUpdates`
- [ ] Replace `Math.max(...arr)` spreads with `reduce` (M1)
- [ ] Fix `matTooltipShowDelay` to property binding (L3)
- [ ] Verify `matStepperNext` works in mobile bar outside stepper tree (L5)

**Open questions:**
- Spring Cloud Gateway MVC WebSocket proxying needs end-to-end verification
- No unit tests for new utils/services

## Constraints

- Update existing files; create new only for modularization (200-line limit) or new backend classes
- Use kebab-case naming (frontend), standard Java naming (backend)
- Angular 18 standalone component patterns
- Preserve existing seat selection, booking flow, countdown functionality
- WebSocket must pass through api-gateway (route config needed)
