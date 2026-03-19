---
title: "Google OAuth2 Login for Auth Service"
description: "Add Google OAuth2 login with auto-link by email, SPA-compatible JWT flow"
status: complete
priority: P1
effort: 8h
branch: kai/feat/google-oauth2-login
tags: [auth, oauth2, google, security]
created: 2026-03-16
---

# Google OAuth2 Login

## Goal
Enable Google OAuth2 login in auth-service. Auto-link by email when Google email_verified=true. Issue same JWT+refresh token as normal login. SPA flow: backend exchanges code, redirects to frontend with tokens.

## Architecture
- Spring Security OAuth2 Client handles code exchange + state/CSRF
- Custom success handler: find-or-create user, link provider, generate JWT, redirect to Angular SPA
- New `user_oauth_providers` table for multi-provider support
- OAuth-only users have NULL password, skip password-based login

## Flow
1. Frontend opens `{gateway}/oauth2/authorization/google`
2. Spring redirects to Google consent screen
3. Google redirects back to `{auth-service}/login/oauth2/code/google`
4. Custom handler: extract email/sub/name -> find/create user -> link provider -> generate JWT+refresh
5. Redirect to `{frontendUrl}/auth/oauth2/callback?token={jwt}&refreshToken={refresh}`

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Database Schema & Entity](./phase-01-database-schema-oauth-provider.md) | complete | 1h |
| 2 | [Spring Security OAuth2 Config](./phase-02-spring-security-oauth2-config.md) | complete | 1.5h |
| 3 | [OAuth2 Success Handler & User Service](./phase-03-oauth2-success-handler-user-service.md) | complete | 2h |
| 4 | [API Gateway Routes](./phase-04-api-gateway-routes.md) | complete | 0.5h |
| 5 | [Testing & Validation](./phase-05-testing-validation.md) | complete | 1h |
| 6 | [Frontend Angular Changes](./phase-06-frontend-angular-oauth2.md) | complete | 1h |
| 7 | [Google Cloud Console Setup](./phase-07-google-cloud-console-setup.md) | pending | 0.5h |

## Key Dependencies
- Google Cloud Console: OAuth2 credentials (client-id, client-secret)
- Environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- PostgreSQL: ddl-auto=update handles schema migration

## Key Decisions
- Use `spring-boot-starter-oauth2-client` (not manual token verification)
- Session policy: allow temporary session for OAuth2 flow only (default Spring repo, no custom cookie repo needed)
- No auth_type column on User; check `user_oauth_providers` table to determine if OAuth-only
- Frontend URL configurable via `namnd.app.oauth2CallbackUrl`
- Token passing: query params in redirect URL (frontend consumes + clears immediately)
- Auto-link: silent, no user confirmation needed (Google email_verified=true is sufficient)
- OAuth-only users can set password later via forgot-password flow

## Risks
- Gateway must route OAuth2 paths to auth-service correctly

## Validation Summary

**Validated:** 2026-03-16
**Questions asked:** 6

### Confirmed Decisions
- **Token passing:** Query params in redirect URL (simple, acceptable for 15min JWT)
- **Auto-link strategy:** Silent auto-link when Google email_verified=true (no confirmation page)
- **OAuth-only password:** Users can set password later via existing forgot-password flow (no new endpoint)
- **Session policy:** Allow temporary session for OAuth2 flow only (drop custom cookie repo, use Spring default)
- **Google credentials:** Include setup instructions in plan
- **Frontend scope:** Include Angular changes (login button + callback route)

### Action Items
- [x] Phase 2: Remove `HttpCookieOAuth2AuthorizationRequestRepository` — using Spring default session-based repo (IF_REQUIRED)
- [x] Phase 2: Session policy set to IF_REQUIRED (correct)
- [x] Add Phase 6: Frontend Angular changes (Google login button + callback route) — DONE
- [ ] Add Phase 7: Google Cloud Console setup instructions — pending

### Code Review (2026-03-16)
Report: `reports/code-review-260316-2221-google-oauth2-implementation.md`

Blocking issues before production:
- [ ] **[C2]** Replace default JWT secret fallback with invalid placeholder (fail fast)
- [ ] **[H1]** Handle `DataIntegrityViolationException` in `processOAuth2User` (concurrent login race)
- [ ] **[H2]** Null-check `email`/`sub` in success handler before calling user linking service
- [ ] **[M4]** Move `setTokens` inside `tap` success in `handleOAuth2Callback` (partial auth state bug)
- [ ] **[L2]** Add `gatewayUrl` to `environment.prod.ts`
