# Phase 02: Auth Module

## Context Links
- [Angular Patterns Research](./research/researcher-01-angular-patterns.md) — JWT interceptor, guards
- [Backend Auth Endpoints](../../README.md) — `/api/auth/*`
- [Phase 01](./phase-01-project-setup.md)

## Overview
- **Priority:** P1 (required for protected routes)
- **Status:** pending
- **Description:** JWT authentication with login, register, password reset, account activation. Functional interceptor and guard.

## Key Insights
- `HttpInterceptorFn` — no class needed, uses `inject()` for DI
- 401 → auto-refresh token, retry original request
- Store tokens in localStorage; auth state via signals
- Backend returns `{id, token, refreshToken, email, username, roles}` on login

## Requirements
### Functional
- Login form (email + password)
- Register form (username, email, password, fullName)
- Forgot password flow (email → token → reset)
- Account activation page (reads token from URL)
- Auto-attach JWT to requests; auto-refresh on 401
- Route guard redirects unauthenticated users to login

### Non-functional
- Form validation with Material form fields
- Loading states on submit
- Error messages (snackbar for failures)
- Return URL preservation on guard redirect

## Architecture
```
features/auth/
├── auth.routes.ts
├── login/
│   └── login.component.ts
├── register/
│   └── register.component.ts
├── forgot-password/
│   └── forgot-password.component.ts
├── reset-password/
│   └── reset-password.component.ts
└── activate-account/
    └── activate-account.component.ts

core/
├── services/auth.service.ts
├── interceptors/auth.interceptor.ts
├── interceptors/error.interceptor.ts
└── guards/auth.guard.ts
```

## Related Code Files
- **Create:** `core/services/auth.service.ts`
- **Create:** `core/interceptors/auth.interceptor.ts`
- **Create:** `core/interceptors/error.interceptor.ts`
- **Create:** `core/guards/auth.guard.ts`
- **Create:** `features/auth/auth.routes.ts`
- **Create:** `features/auth/login/login.component.ts`
- **Create:** `features/auth/register/register.component.ts`
- **Create:** `features/auth/forgot-password/forgot-password.component.ts`
- **Create:** `features/auth/reset-password/reset-password.component.ts`
- **Create:** `features/auth/activate-account/activate-account.component.ts`
- **Modify:** `app.routes.ts` — add auth routes
- **Modify:** `app.config.ts` — register interceptors

## Implementation Steps
1. Create `AuthService` with signals-based state:
   - `currentUser = signal<User | null>(null)`
   - `isAuthenticated = computed(() => !!this.currentUser())`
   - Methods: `login()`, `register()`, `logout()`, `refreshToken()`, `forgotPassword()`, `resetPassword()`, `activateAccount()`, `resendActivation()`
   - `getToken()` / `setTokens()` — localStorage read/write
   - On init: check localStorage for existing token, validate expiry
2. Create `authInterceptor: HttpInterceptorFn`:
   - Skip public URLs (`/api/auth/login`, `/api/auth/register`, etc.)
   - Attach `Authorization: Bearer <token>` header
   - On 401: call `refreshToken()`, retry request; if refresh fails → logout + redirect to login
   - Use `BehaviorSubject<boolean>` to queue concurrent 401 retries
3. Create `errorInterceptor: HttpInterceptorFn`:
   - Catch HTTP errors, show user-friendly snackbar messages
   - Handle 423 (account locked), 403 (access denied), 500 (server error)
4. Create `authGuard: CanActivateFn`:
   - Check `AuthService.isAuthenticated()`
   - Redirect to `/auth/login?returnUrl=...` if not authenticated
5. Create `LoginComponent`:
   - Reactive form: email (required, email), password (required, min 6)
   - On submit: call `authService.login()`, navigate to returnUrl or `/movies`
   - Show errors via MatSnackBar
6. Create `RegisterComponent`:
   - Reactive form: username, email, password, fullName
   - Password strength indicator (optional)
   - On success: show "check email" message
7. Create `ForgotPasswordComponent`:
   - Email input → POST `/api/auth/forgot-password`
   - Show confirmation regardless of email existence
8. Create `ResetPasswordComponent`:
   - Read token from route params
   - New password + confirm password fields
   - POST `/api/auth/reset-password`
9. Create `ActivateAccountComponent`:
   - Read token from query params on init
   - Call GET `/api/auth/activate?token=...`
   - Show success/failure message
10. Register interceptors in `app.config.ts`: `withInterceptors([authInterceptor, errorInterceptor])`
11. Add auth routes to `app.routes.ts` with lazy loading

## Todo List
- [ ] AuthService with signal state + localStorage
- [ ] Auth interceptor (token attach + 401 refresh)
- [ ] Error interceptor (snackbar messages)
- [ ] Auth guard (functional canActivate)
- [ ] Login component + form
- [ ] Register component + form
- [ ] Forgot password component
- [ ] Reset password component
- [ ] Activate account component
- [ ] Wire routes and interceptors

## Success Criteria
- Login/register forms submit to backend and handle responses
- JWT attached to all protected requests
- 401 triggers silent token refresh
- Guard blocks unauthenticated access and redirects to login
- Account activation works from email link

## Risk Assessment
- Token refresh race condition — mitigate with `isRefreshing` flag + queue
- localStorage not available in SSR — acceptable (no SSR planned)
- CORS errors — backend CORS already enabled

## Security Considerations
- Never log tokens to console in production
- Clear tokens on logout
- Validate token expiry client-side before sending
- Sanitize error messages (don't leak server internals)

## Next Steps
- Phase 03: Movie Module (uses auth guard for protected actions)
