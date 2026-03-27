# Phase 04: Frontend Setup Password Page

## Context Links

- [Plan Overview](plan.md)
- [activate-account.component.ts](../../cinema-frontend/src/app/features/auth/activate-account/activate-account.component.ts) (65 lines)
- [auth.service.ts](../../cinema-frontend/src/app/core/services/auth.service.ts) (143 lines)
- [auth.routes.ts](../../cinema-frontend/src/app/features/auth/auth.routes.ts) (17 lines)
- [user.model.ts](../../cinema-frontend/src/app/core/models/user.model.ts)

## Overview

- **Priority:** P1
- **Status:** done
- Create new setup-password component (or repurpose activate-account). User arrives via email link with token in URL query param. Shows password + confirm password form. Submits to POST /api/auth/activate-with-password. Redirects to login on success.

## Key Insights

- Current activate-account.component.ts: auto-calls GET /api/auth/activate on init (line 57). Must be replaced/supplemented with a form-based flow.
- Keep existing activate-account component for backward compatibility (old email links). Create new setup-password component.
- auth.routes.ts (line 14): `{ path: 'activate', component: ActivateAccountComponent }`. Add new route for `setup-password`.
- auth.service.ts activateAccount() (line 98-100): GET request. Need new method for POST.
- Pattern reference: reset-password component likely has similar form (password + confirm). Can mirror its structure.

## Requirements

### Functional
- Route: /auth/setup-password?token=uuid
- Form: password (min 6 chars) + confirm password (must match) + submit button
- On submit: POST /api/auth/activate-with-password {token, password, confirmPassword}
- On success: show success message + "Go to Login" link
- On error: show error message (invalid/expired token, validation errors)
- Loading spinner during submission

### Non-Functional
- Consistent styling with other auth pages (auth-container, auth-card pattern)
- Material Design components (MatCard, MatFormField, MatInput, MatButton)
- Reactive form with validators

## Architecture

```
/auth/setup-password?token=uuid
  -> SetupPasswordComponent
    -> reads token from query params
    -> if no token: show error
    -> renders password form
    -> on submit: authService.setupPassword(token, password, confirmPassword)
      -> POST /api/auth/activate-with-password
    -> on success: show success + login link
    -> on error: show error message
```

## Related Code Files

### Create
- `cinema-frontend/src/app/features/auth/setup-password/setup-password.component.ts` - New component

### Modify
- `cinema-frontend/src/app/core/services/auth.service.ts` - Add setupPassword() method
- `cinema-frontend/src/app/features/auth/auth.routes.ts` - Add setup-password route
- `cinema-frontend/src/app/core/models/user.model.ts` - (Optional) Add SetupPasswordRequest interface

## Implementation Steps

### 1. Add setupPassword() to AuthService (auth.service.ts)

Add after activateAccount() (after line 100):
```typescript
setupPassword(token: string, password: string, confirmPassword: string): Observable<any> {
  return this.http.post('/api/auth/activate-with-password', {
    token, password, confirmPassword
  }, { responseType: 'text' });
}
```

### 2. Create SetupPasswordComponent

File: `cinema-frontend/src/app/features/auth/setup-password/setup-password.component.ts`

Component structure:
- Standalone component with inline template + styles (consistent with register, activate-account patterns)
- Imports: ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule, MatIconModule, RouterLink
- Signals: loading, success, errorMessage, noToken
- Form group: password (required, minLength 6), confirmPassword (required)
- Custom validator or onSubmit check: password === confirmPassword
- OnInit: read token from ActivatedRoute.snapshot.queryParams['token']
- If no token: set noToken=true, show error
- onSubmit: call authService.setupPassword(token, password, confirmPassword)

Template structure:
```
<div class="auth-container">
  <mat-card class="auth-card">
    <mat-card-header>
      <mat-card-title>Set Up Your Password</mat-card-title>
    </mat-card-header>
    <mat-card-content>
      @if (noToken()) {
        <error: no token provided>
      } @else if (success()) {
        <success icon + "Account activated!" + Go to Login link>
      } @else {
        <form with password + confirmPassword fields>
        @if (errorMessage()) { <error message> }
        <submit button with loading spinner>
      }
    </mat-card-content>
  </mat-card>
</div>
```

### 3. Add route to auth.routes.ts

Import SetupPasswordComponent and add before the redirect:
```typescript
{ path: 'setup-password', component: SetupPasswordComponent },
```

Keep existing `{ path: 'activate', component: ActivateAccountComponent }` for backward compatibility.

### 4. (Optional) Add SetupPasswordRequest to user.model.ts

```typescript
export interface SetupPasswordRequest {
  token: string;
  password: string;
  confirmPassword: string;
}
```

## Todo List

- [x] Add `setupPassword()` method to auth.service.ts
- [x] Create setup-password.component.ts with form, validation, API call
- [x] Add `setup-password` route to auth.routes.ts
- [ ] (Optional) Add SetupPasswordRequest interface to user.model.ts
- [x] Verify Angular compiles: `cd cinema-frontend && ng build`
- [ ] Manual test: visit /auth/setup-password?token=test-uuid

## Success Criteria

- /auth/setup-password?token=valid-token shows password form
- /auth/setup-password without token shows error
- Submit with matching passwords >= 6 chars -> success, "Go to Login" shown
- Submit with mismatched passwords -> client-side error
- Submit with short password -> client-side error
- Submit with expired/invalid token -> server error displayed
- Consistent Material Design styling

## Risk Assessment

- **Low:** New route + component, no modification to existing activate-account
- **Low:** auth.service.ts gets one new method, no impact on existing methods
- **Medium:** Email link format change (Phase 02) means old emails point to old activate route (still works if user already has password from old flow)

## Security Considerations

- Token passed in URL query param (same pattern as reset-password, acceptable for HTTPS)
- Password validation both client-side (reactive form) and server-side (AuthController)
- No token stored in localStorage; used only during form submission
- Form should not pre-fill or cache password fields (autocomplete="new-password")

## Next Steps

- After all 4 phases: compile backend + frontend, integration test full flow
- Update API documentation (Swagger annotations already added in Phase 02)
- Consider updating codebase-summary.md and system-architecture.md docs
