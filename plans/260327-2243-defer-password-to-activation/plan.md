---
title: "Defer Password Setup to Activation Step"
description: "Remove password from registration, add password setup during account activation"
status: implemented
priority: P2
effort: 4h
branch: master
tags: [auth, registration, activation, security]
created: 2026-03-27
---

# Defer Password Setup to Activation Step

## Summary

Change registration flow: user registers with username/fullName/email only (no password). Password is set during account activation via a new "Setup Password" page. Backend gets new POST endpoint; frontend activation page becomes a password form.

## Current Flow

1. Register with username, fullName, email, **password** -> user created (active=false, password hashed)
2. Click email link -> GET /api/auth/activate?token=uuid -> sets active=true
3. User goes to login manually

## New Flow

1. Register with username, fullName, email (no password) -> user created (active=false, password=null)
2. Email link -> frontend /auth/setup-password?token=uuid
3. User enters password + confirm -> POST /api/auth/activate-with-password
4. Backend validates token, hashes password, activates, saves password history
5. Redirect to login

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Backend Registration Changes](phase-01-backend-registration-changes.md) | done | 1h |
| 2 | [Backend Activation with Password](phase-02-backend-activation-with-password.md) | done | 1.5h |
| 3 | [Frontend Registration Changes](phase-03-frontend-registration-changes.md) | done | 0.5h |
| 4 | [Frontend Setup Password Page](phase-04-frontend-setup-password-page.md) | done | 1h |

## Dependencies

- Phase 2 depends on Phase 1 (User.password nullable)
- Phase 4 depends on Phase 2 (new backend endpoint)
- Phase 3 independent of Phase 4

## Key Decisions

- Reuse `activate` route path as `setup-password` (new route, keep old for backward compat)
- Old GET /api/auth/activate kept but optional (can remove later)
- Google OAuth flow unchanged (already creates users with password=null)

## Risk

- Existing unactivated users with passwords in DB: no migration needed, they still work with old GET activate endpoint
- Password=null between register and activate: login blocked by active=false check anyway
