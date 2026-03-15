# Phase 03 - Frontend: Change Password Component & Integration

## Context Links
- [Parent Plan](plan.md)
- [Phase 02](phase-02-backend-change-password-endpoint.md) (dependency)
- [auth.service.ts](../../cinema-frontend/src/app/core/services/auth.service.ts)
- [profile-page.component.ts](../../cinema-frontend/src/app/features/profile/profile-page/profile-page.component.ts)
- [profile.routes.ts](../../cinema-frontend/src/app/features/profile/profile.routes.ts)
- [error.interceptor.ts](../../cinema-frontend/src/app/core/interceptors/error.interceptor.ts) (auto-handles 400 snackbar)

## Overview
- **Date:** 2026-03-15
- **Priority:** P2
- **Status:** complete
- **Review:** complete — medium: reset submitting signal before navigate on success; low: mat-error message improvement
- **Description:** Create standalone change-password component with Angular Material form. Add route under /profile/change-password. Add button on profile page.

## Key Insights
- Error interceptor already handles 400 responses and shows snackbar with server message. No custom error handling needed in component for server-side validation errors.
- Use standalone component pattern (project standard for Angular 18)
- Use `inject()` pattern (consistent with auth.service.ts, profile-page.component.ts)
- Profile page uses inline template+styles (follow same pattern)

## Requirements

### Functional
- Form: currentPassword, newPassword, confirmPassword fields
- Client-side validation: required, min length 6, passwords match
- Call POST /api/auth/change-password
- On success: show snackbar "Password changed successfully", navigate to /profile
- On error: error interceptor auto-shows snackbar with server message

### Non-Functional
- Standalone component with Angular Material
- Responsive layout, max-width 500px (match profile page)
- Password visibility toggle (mat-icon suffix)

## Architecture
```
/profile/change-password → ChangePasswordComponent
  ├── ReactiveFormsModule (FormGroup with 3 controls)
  ├── Calls authService.changePassword()
  ├── Success → MatSnackBar + router.navigate(['/profile'])
  └── Error → handled by error interceptor (automatic)
```

## Related Code Files

### Create
- `cinema-frontend/src/app/features/profile/change-password/change-password.component.ts`

### Modify
- `cinema-frontend/src/app/core/services/auth.service.ts` - Add changePassword() method
- `cinema-frontend/src/app/features/profile/profile-page/profile-page.component.ts` - Add "Change Password" button
- `cinema-frontend/src/app/features/profile/profile.routes.ts` - Add change-password route

### Delete
- None

## Implementation Steps

### Step 1: Add changePassword() to auth.service.ts

Add method after `resetPassword()`:
```typescript
changePassword(currentPassword: string, newPassword: string, confirmPassword: string): Observable<any> {
  return this.http.post('/api/auth/change-password', {
    currentPassword, newPassword, confirmPassword
  }, { responseType: 'text' });
}
```

### Step 2: Create change-password.component.ts

Standalone component with inline template and styles (~120 lines total):
- Imports: MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, ReactiveFormsModule, MatSnackBarModule
- Form with 3 password fields, each with visibility toggle (mat-icon-button suffix)
- Custom validator: newPassword === confirmPassword
- Submit button disabled when form invalid or submitting
- On success: snackbar + navigate to /profile
- On error: error interceptor handles it (no manual error handling)

```typescript
@Component({
  selector: 'app-change-password',
  standalone: true,
  imports: [
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, ReactiveFormsModule
  ],
  template: `
    <div class="container">
      <mat-card>
        <mat-card-header>
          <mat-card-title>Change Password</mat-card-title>
        </mat-card-header>
        <mat-card-content>
          <form [formGroup]="form" (ngSubmit)="onSubmit()">
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Current Password</mat-label>
              <input matInput [type]="hideCurrentPw() ? 'password' : 'text'"
                     formControlName="currentPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideCurrentPw.set(!hideCurrentPw())">
                <mat-icon>{{hideCurrentPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              <mat-error>Required</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>New Password</mat-label>
              <input matInput [type]="hideNewPw() ? 'password' : 'text'"
                     formControlName="newPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideNewPw.set(!hideNewPw())">
                <mat-icon>{{hideNewPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              <mat-error>Min 6 characters</mat-error>
            </mat-form-field>

            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Confirm Password</mat-label>
              <input matInput [type]="hideConfirmPw() ? 'password' : 'text'"
                     formControlName="confirmPassword">
              <button mat-icon-button matSuffix type="button"
                      (click)="hideConfirmPw.set(!hideConfirmPw())">
                <mat-icon>{{hideConfirmPw() ? 'visibility_off' : 'visibility'}}</mat-icon>
              </button>
              @if (form.hasError('passwordMismatch')) {
                <mat-error>Passwords do not match</mat-error>
              }
            </mat-form-field>

            <button mat-raised-button color="primary" type="submit"
                    [disabled]="form.invalid || submitting()">
              {{ submitting() ? 'Changing...' : 'Change Password' }}
            </button>
          </form>
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: [`
    .container { padding: 24px; max-width: 500px; margin: 0 auto; }
    .full-width { width: 100%; margin-bottom: 8px; }
    button[type="submit"] { width: 100%; }
  `]
})
```

Component class logic:
- `form` FormGroup with currentPassword (required), newPassword (required, minLength 6), confirmPassword (required)
- Custom validator on form level checking newPassword === confirmPassword
- `hideCurrentPw`, `hideNewPw`, `hideConfirmPw` signals (default true)
- `submitting` signal (default false)
- `onSubmit()`: set submitting true, call authService.changePassword(), on success snackbar + navigate, on error set submitting false

### Step 3: Add route to profile.routes.ts

```typescript
import { ChangePasswordComponent } from './change-password/change-password.component';

export const PROFILE_ROUTES: Routes = [
  { path: '', component: ProfilePageComponent, canActivate: [authGuard] },
  { path: 'change-password', component: ChangePasswordComponent, canActivate: [authGuard] }
];
```

### Step 4: Add button to profile-page.component.ts

Add `MatButtonModule` and `RouterModule` to imports array.

Add after `</mat-card-content>`:
```html
<mat-card-actions>
  <a mat-raised-button color="primary" routerLink="/profile/change-password">
    <mat-icon>lock</mat-icon> Change Password
  </a>
</mat-card-actions>
```

## Todo List
- [x] Add changePassword() to auth.service.ts
- [x] Create change-password.component.ts
- [x] Add route to profile.routes.ts
- [x] Add button to profile-page.component.ts
- [x] Verify ng build compiles
- [ ] Reset submitting.set(false) before router.navigate() in onSubmit() — medium priority fix
- [ ] Improve mat-error for newPassword to distinguish required vs min-length — low priority

## Success Criteria
- Change password form renders at /profile/change-password
- Form validates client-side (required, min length, match)
- Successful change shows snackbar and redirects to /profile
- Server error messages displayed via error interceptor snackbar
- "Change Password" button visible on profile page

## Risk Assessment
- **Low:** Inline template may approach 200-line limit. If so, extract to separate .html file.
- **Low:** Mat-icon-button in mat-suffix may need MatIconButton import. Check Angular Material 18 API.

## Security Considerations
- Current password required (prevents unauthorized changes)
- Password fields use type="password" with optional toggle
- No password logging in console

## Next Steps
- Phase 04: Testing and validation
