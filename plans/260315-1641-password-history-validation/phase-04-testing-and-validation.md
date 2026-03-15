# Phase 04 - Testing & Validation

## Context Links
- [Parent Plan](plan.md)
- [Phase 01](phase-01-backend-password-history-entity.md)
- [Phase 02](phase-02-backend-change-password-endpoint.md)
- [Phase 03](phase-03-frontend-change-password.md)

## Overview
- **Date:** 2026-03-15
- **Priority:** P2
- **Status:** in-progress — compile/build verified; manual tests pending
- **Review:** pending
- **Description:** Compile checks, build verification, and manual test scenarios.

## Key Insights
- Backend uses `mvn clean compile` for compile checks
- Frontend uses `ng build` for build verification
- No existing unit test infrastructure observed; focus on compile + manual validation

## Requirements

### Functional
- All code compiles without errors
- All manual test scenarios pass

### Non-Functional
- No regression in existing auth flows (login, register, reset-password)

## Implementation Steps

### Step 1: Backend compile check
```bash
cd auth-service && mvn clean compile
```
Fix any compilation errors.

### Step 2: Frontend build check
```bash
cd cinema-frontend && ng build
```
Fix any TypeScript/template errors.

### Step 3: Manual test scenarios

**Change Password - Happy Path:**
1. Login as existing user
2. Navigate to /profile
3. Click "Change Password"
4. Enter current password, new password, confirm password
5. Submit → expect "Password changed successfully" snackbar
6. Redirected to /profile
7. Login with new password → success

**Change Password - Wrong Current Password:**
1. Enter wrong current password
2. Submit → expect "Current password is incorrect" snackbar

**Change Password - Password Reuse:**
1. Change password from A to B (success)
2. Change password from B to A → expect "Cannot reuse your 3 most recent passwords" snackbar

**Change Password - Mismatch:**
1. Enter different newPassword and confirmPassword
2. Client-side: form shows "Passwords do not match" error, submit button disabled

**Reset Password - History Check:**
1. Request password reset via forgot-password
2. Open reset link
3. Enter a recently used password → expect error message

**Registration - Initial History:**
1. Register new user
2. Activate account
3. Login
4. Try to change password to same as registration password → expect reuse error

## Todo List
- [x] Backend compile check passes
- [x] Frontend build check passes
- [ ] Change password happy path
- [ ] Wrong current password error
- [ ] Password reuse error
- [ ] Password mismatch client-side validation
- [ ] Reset password history check
- [ ] Registration seeds initial history
- [ ] Existing login/register/reset flows still work

## Success Criteria
- Zero compile/build errors
- All manual scenarios produce expected results
- No regression in existing auth flows

## Risk Assessment
- **Low:** Existing tests may fail if they hit registration flow without PasswordHistoryService bean. Check test context.

## Security Considerations
- Verify error messages don't leak sensitive info
- Verify password history entries are BCrypt-encoded (not plaintext)

## Next Steps
- Feature complete. Update docs if needed.
