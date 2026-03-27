# Phase 03: Frontend Registration Changes

## Context Links

- [Plan Overview](plan.md)
- [register.component.ts](../../cinema-frontend/src/app/features/auth/register/register.component.ts) (116 lines)
- [user.model.ts](../../cinema-frontend/src/app/core/models/user.model.ts) (34 lines)
- [auth.service.ts](../../cinema-frontend/src/app/core/services/auth.service.ts) (143 lines)

## Overview

- **Priority:** P1
- **Status:** done
- Remove password field from registration form, RegisterRequest interface, and update success message to mention activation email with password setup.

## Key Insights

- register.component.ts: inline template (lines 17-81), form group defined at line 98-103 with `password` field
- Template lines 55-61: password mat-form-field block to remove
- RegisterRequest (user.model.ts line 14-19): has `password: string` to remove
- auth.service.ts register() (line 67-69): sends full object, no change needed (just sends fewer fields)

## Requirements

### Functional
- Registration form: username, fullName, email only (no password)
- Success message updated to mention "set your password" via email link
- RegisterRequest interface: remove password field

### Non-Functional
- Form validation still requires all 3 fields

## Architecture

No structural changes. Field removal only.

## Related Code Files

### Modify
- `cinema-frontend/src/app/features/auth/register/register.component.ts` - Remove password from form + template
- `cinema-frontend/src/app/core/models/user.model.ts` - Remove password from RegisterRequest

## Implementation Steps

### 1. Update RegisterRequest (user.model.ts)

Line 14-19: Remove `password: string;`
```typescript
export interface RegisterRequest {
  username: string;
  email: string;
  fullName: string;
}
```

### 2. Update register.component.ts form group

Line 98-103: Remove password from form group:
```typescript
form = this.fb.nonNullable.group({
  username: ['', [Validators.required]],
  fullName: ['', [Validators.required]],
  email: ['', [Validators.required, Validators.email]]
});
```

### 3. Remove password field from template

Remove lines 55-61 (the password mat-form-field block):
```html
<mat-form-field appearance="outline" class="full-width">
  <mat-label>Password</mat-label>
  <input matInput formControlName="password" type="password">
  @if (form.controls.password.hasError('minlength')) {
    <mat-error>Password must be at least 6 characters</mat-error>
  }
</mat-form-field>
```

### 4. Update success message

Line 26: Change to:
```html
<p>Registration successful! Please check your email to set up your password and activate your account.</p>
```

## Todo List

- [x] Remove `password` from RegisterRequest in user.model.ts
- [x] Remove `password` from form group in register.component.ts
- [x] Remove password mat-form-field from inline template
- [x] Update success message text
- [x] Verify Angular compiles: `cd cinema-frontend && ng build`

## Success Criteria

- Registration form shows only username, fullName, email
- Form submits without password field
- No TypeScript compile errors
- Success message mentions email for password setup

## Risk Assessment

- **Low:** auth.service.ts register() sends whatever object is passed; removing password from form means it won't be sent

## Security Considerations

- No password transmitted over wire during registration = reduced attack surface

## Next Steps

Phase 04: Create setup-password page on frontend
