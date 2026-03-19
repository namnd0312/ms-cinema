# Phase 6: Frontend Angular OAuth2 Changes

## Context Links
- [Plan overview](./plan.md)
- Frontend: `cinema-frontend/` (Angular 18, port 4200)
- Auth routes: `cinema-frontend/src/app/auth/`

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add "Sign in with Google" button to login page + `/auth/oauth2/callback` route to extract tokens from URL

## Key Insights
- OAuth2 flow redirects to `http://localhost:4200/auth/oauth2/callback?token=xxx&refreshToken=yyy`
- Frontend must extract tokens from query params, store in localStorage/sessionStorage, clear URL
- Reuse existing AuthService token storage logic
- No OAuth2 library needed on frontend — backend handles everything

## Requirements
### Functional
- "Sign in with Google" button on login page → navigates to `{gateway}/oauth2/authorization/google`
- New `/auth/oauth2/callback` route component that:
  1. Extracts `token` and `refreshToken` from URL query params
  2. Stores them using existing AuthService
  3. Redirects to home/dashboard
  4. Handles error cases (missing params)

### Non-Functional
- Consistent with existing Material UI styling
- Clear URL after extracting tokens (prevent leakage via browser history)

## Related Code Files

### Modify
- `cinema-frontend/src/app/auth/` — add callback route
- Login component — add Google button

### Create
- `cinema-frontend/src/app/auth/oauth2-callback/oauth2-callback.component.ts`

## Implementation Steps

### 1. Create OAuth2 Callback Component
Extracts tokens from URL, stores via AuthService, redirects to home.

### 2. Add Route
Add `/auth/oauth2/callback` to auth routes (lazy-loaded).

### 3. Add Google Login Button
On login page, add Material button that opens `window.location.href = '{gatewayUrl}/oauth2/authorization/google'`.

### 4. Environment Config
Add `gatewayUrl` to Angular environment config (default: `http://localhost:8080`).

## Todo List
- [ ] Create OAuth2CallbackComponent
- [ ] Add route `/auth/oauth2/callback`
- [ ] Add "Sign in with Google" button to login page
- [ ] Add gateway URL to environment config
- [ ] Test: callback extracts tokens and redirects
- [ ] Test: Google button navigates to OAuth2 flow

## Success Criteria
- Clicking "Sign in with Google" initiates OAuth2 flow
- After Google login, callback page extracts tokens + redirects to app
- Authenticated state works same as password login

## Risk Assessment
- **Low:** URL params visible briefly in browser history
- **Mitigation:** Use `replaceUrl: true` or `window.history.replaceState` after extraction

## Security Considerations
- Clear tokens from URL immediately after extraction
- Use `window.history.replaceState` to remove query params from browser history

## Next Steps
- Phase 7: Google Cloud Console setup
