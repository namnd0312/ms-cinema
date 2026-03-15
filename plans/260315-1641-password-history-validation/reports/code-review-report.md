# Code Review Report - Password History Validation

**Date:** 2026-03-15
**Reviewer:** code-review skill

---

## Code Review Summary

### Scope
- Files reviewed: 11 files (6 new, 5 modified)
- Lines of code analyzed: ~881 total
- Review focus: password history validation feature — security, correctness, consistency

### Overall Assessment

Implementation is functionally correct, follows existing project patterns, and both compiles clean. Core logic (BCrypt history check, endpoint validation sequence, frontend form) is sound. Three issues need attention: missing `@Transactional` on the change-password controller method, no history table pruning (unbounded growth), and a minor UX bug with the `submitting` signal not being reset on success.

---

### Critical Issues

None.

---

### High Priority Findings

**1. Missing `@Transactional` on `changePassword()` in `AuthController` (line 330)**

The method performs two writes: `userService.save(user)` and `passwordHistoryService.savePasswordToHistory(user, encoded)`. If the second call fails after the first succeeds, the user's password is updated but no history entry is written. The two writes must be atomic.

File: `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java`

Fix: Add `@Transactional` to the `changePassword()` method, or extract both writes into a transactional service method.

```java
@Transactional
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDto dto) {
    // ... existing body unchanged
}
```

`PasswordResetServiceImpl.resetPassword()` already has `@Transactional` correctly — this is the only place it was missed.

---

**2. Unbounded `password_history` table growth**

`savePasswordToHistory()` only inserts — it never prunes old entries. `findTop3ByUserOrderByCreatedAtDesc` limits the read to 3, but over time the table accumulates an unlimited number of rows per user. For a user who changes password frequently this is a slow leak.

File: `auth-service/src/main/java/com/namnd/cinema/service/impl/PasswordHistoryServiceImpl.java`

Fix: After insert, delete entries beyond the 3 most recent. One approach — add a repository query:

```java
// PasswordHistoryRepository
@Modifying
@Query("DELETE FROM PasswordHistory ph WHERE ph.user = :user AND ph.id NOT IN " +
       "(SELECT ph2.id FROM PasswordHistory ph2 WHERE ph2.user = :user ORDER BY ph2.createdAt DESC LIMIT 3)")
void deleteOldEntries(@Param("user") User user);
```

Or more portable: after save, query all entries ordered DESC, skip first 3, delete the rest.

---

### Medium Priority Improvements

**3. `submitting` signal not reset on success in `ChangePasswordComponent`**

`onSubmit()` sets `submitting.set(false)` only in the `error` callback. On success the method calls `router.navigate()`, which navigates away — so this usually does not cause a visible bug. However if navigation fails or is deferred, the button stays disabled. Reset it before navigating.

File: `cinema-frontend/src/app/features/profile/change-password/change-password.component.ts`, line 106-111

```typescript
next: () => {
  this.submitting.set(false);  // add this
  this.snackBar.open('Password changed successfully', 'Close', { duration: 3000 });
  this.router.navigate(['/profile']);
},
```

---

**4. `ChangePasswordDto` — `confirmPassword` not validated server-side (only min-length on `newPassword`)**

The DTO has `@NotBlank` on `confirmPassword` but no `@Size`. The controller validates the match manually which is correct. The only gap: there is no `@Size(min=6)` on `confirmPassword`, meaning a payload with `newPassword=abcdef` (valid) and `confirmPassword=x` (invalid size) would pass DTO validation and reach the equality check, returning a generic mismatch error rather than the size error. Low impact but inconsistent.

Fix: add `@Size(min = 6)` to `confirmPassword` in `ChangePasswordDto`.

---

**5. `PasswordHistory` entity missing database index on `(user_id, created_at DESC)`**

The plan's phase-01 non-functional requirement explicitly calls for this index but it was not added to the entity. With `ddl-auto: update`, Hibernate only creates it if declared in `@Table(indexes = ...)`.

File: `auth-service/src/main/java/com/namnd/cinema/model/PasswordHistory.java`

```java
@Table(name = "password_history",
       indexes = @Index(name = "idx_ph_user_created", columnList = "user_id, created_at DESC"))
```

For a password-change feature this is low-traffic, so impact is minor. But it was a stated requirement.

---

**6. `AuthController.changePassword()` does not return 401 for unauthenticated calls — it throws `RuntimeException`**

If `SecurityContextHolder.getContext().getAuthentication()` returns null or an anonymous token (e.g. due to misconfigured security filter), `authentication.getName()` will succeed but `userService.findByEmail(...)` will throw `RuntimeException("User not found")` which propagates as a 500. Should be guarded.

This is low-risk if the endpoint is properly secured by `SecurityConfig`, but worth verifying that `/api/auth/change-password` requires authentication in the security filter chain. If it does, the guard is redundant. If it doesn't, it should.

Action: Confirm `SecurityConfig` includes `POST /api/auth/change-password` in the authenticated-required matcher. No code change needed if already covered.

---

### Low Priority Suggestions

**7. `mat-error` for `newPassword` shows "Min 6 characters" regardless of actual error**

The template shows a static "Min 6 characters" error for all `newPassword` errors, including the `required` error on first touch. Minor UX inconsistency.

```html
<mat-error>
  @if (form.get('newPassword')?.hasError('required')) { Required }
  @else { Min 6 characters }
</mat-error>
```

**8. `AuthController` — 362 lines, approaching maintainability boundary**

Not a new issue from this feature (the controller was already long), but the addition of `changePassword()` makes it more evident. Consider extracting password-related operations (`changePassword`, `forgotPassword`, `resetPassword`) to a `PasswordController`. Low urgency.

---

### Positive Observations

- BCrypt comparison order is correct: `rawPassword` as first arg, stored hash as second.
- History check happens **before** encoding the new password — correct and consistent in both `changePassword` and `resetPassword`.
- Registration seeds initial history so the first change works correctly.
- Frontend uses `authGuard` on the new route consistently.
- Error interceptor integration is clean — component only handles success, server 400 messages surface automatically.
- `passwordMatchValidator` is a group-level validator (not field-level) — correct approach for cross-field validation.
- `@Data` + `@PrePersist` pattern in entity matches the existing codebase convention.
- No passwords are logged anywhere in the new code.
- `PasswordHistoryService` interface properly abstracts the implementation — consistent with other services.

---

### Recommended Actions

1. **[High]** Add `@Transactional` to `AuthController.changePassword()` — prevents partial write on failure.
2. **[High]** Add history pruning in `savePasswordToHistory()` — prevent unbounded table growth.
3. **[Medium]** Reset `submitting.set(false)` before `router.navigate()` in `onSubmit()`.
4. **[Medium]** Verify `SecurityConfig` requires auth for `POST /api/auth/change-password`.
5. **[Medium]** Add `@Size(min=6)` to `confirmPassword` field in `ChangePasswordDto`.
6. **[Low]** Add `@Index` annotation to `PasswordHistory` entity (stated requirement in phase-01).
7. **[Low]** Improve `mat-error` for `newPassword` to distinguish required vs min-length errors.

---

### Metrics

- Type Coverage: N/A (no TypeScript strict mode issues observed; Angular template compilation passes)
- Test Coverage: 0% unit tests (plan phase-04 identified no existing test infrastructure; compile + manual tests only)
- Linting Issues: 0 critical; items above are logic/design concerns, not lint violations
- Build: Both `mvn clean compile` and `ng build` pass (per user-confirmed pre-conditions)

---

### Task Completeness Verification

All implementation tasks from phases 01-03 are complete. Phase 04 (testing) compile steps are confirmed passing; manual scenarios are not verifiable in this review.

| Phase | Status |
|-------|--------|
| 01 - PasswordHistory entity & repository | Complete |
| 02 - Change password endpoint & history service | Complete (1 @Transactional gap) |
| 03 - Frontend change password component | Complete |
| 04 - Testing & validation | Compile/build verified; manual tests not run in this review |

---

### Unresolved Questions

- Is `POST /api/auth/change-password` explicitly secured in `SecurityConfig`? If not, an unauthenticated request reaches the controller and throws a 500 instead of 401.
- Is there an existing cleanup job or migration strategy for the `password_history` table? If user volume is low, the pruning issue is deferred-acceptable, but should be tracked.
