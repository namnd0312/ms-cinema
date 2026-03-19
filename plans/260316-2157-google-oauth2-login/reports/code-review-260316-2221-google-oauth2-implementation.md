## Code Review Summary

### Scope
- Files reviewed: 15 (5 new, 8 modified backend + 2 new frontend)
- Lines of code analyzed: ~600
- Review focus: New Google OAuth2 login implementation
- Updated plans: `plans/260316-2157-google-oauth2-login/plan.md`

---

### Overall Assessment
Implementation is functionally sound and follows project patterns. The OAuth2 flow is correctly designed: Spring Security handles code exchange, custom handler issues JWTs, frontend callback clears tokens from URL. Two security issues need attention before production.

---

### Critical Issues

**[C1] Tokens in redirect URL query params — partial mitigation**
`OAuth2AuthenticationSuccessHandler` places `token` and `refreshToken` as query params in the redirect URL. Frontend does clear the URL via `history.replaceState` immediately, but tokens are still visible in:
- Server-side access logs (gateway, auth-service)
- Browser history (race condition before replaceState)
- Referrer headers if any third-party JS triggers a navigation

The 15-min JWT window limits exposure but the refresh token (7-day) in a URL is a real risk. Accepted per plan validation — acceptable for this project stage, but log scrubbing or a fragment/cookie approach should be tracked as a follow-up.

**[C2] Default JWT secret committed in `application.yml`**
```yaml
jwtSecret: ${JWT_SECRET:kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+...}
```
A real secret is the fallback default — it ships in source. Anyone cloning the repo can forge JWTs without setting the env var. This existed before this PR but the OAuth2 work makes it more significant (wider login surface). The default should be an empty/invalid placeholder that fails fast.

---

### High Priority Findings

**[H1] Race condition in `OAuth2UserLinkingServiceImpl.processOAuth2User` — missing UPSERT atomicity**
Steps 2→3→4 are not atomic. Two concurrent first-time logins with the same Google `sub` (e.g., tab opened twice) can both pass the `existingLink.isPresent()` check (both false) and both hit `userService.save(user)`, creating duplicate users or violating the `provider_user_id` unique constraint with an unhandled `DataIntegrityViolationException`. Add a `try/catch DataIntegrityViolationException` around the save+link block with a retry-read fallback, or use a `SELECT ... FOR UPDATE` / `saveOrUpdate` approach.

**[H2] `email` attribute can be null — null user created silently**
In `createOAuth2User`, if Google does not return an email (uncommon but possible with `openid` scope only), `user.setEmail(null)` and `user.setUsername(null)` are set. The user is saved with NULL email which will likely violate a DB constraint — but the exception propagates as a 500 with no clear message. Add a null/blank check before `processOAuth2User` in the handler and return an error redirect.

**[H3] `@CrossOrigin(origins = "*")` on `AuthController`**
This was pre-existing, but the OAuth2 implementation adds a new attack surface. With `"*"` CORS on auth endpoints (login, refresh-token, logout), any origin can make credentialed requests. Should be restricted to the known frontend origin.

---

### Medium Priority Improvements

**[M1] `SecurityConfig` — `change-password` ordering may be shadowed**
```java
.requestMatchers("/api/auth/change-password").authenticated()
.requestMatchers("/api/auth/**", ...).permitAll()
```
The more-specific `change-password` rule is declared first, which is correct for Spring Security (first match wins). This is fine — but it's fragile if rules are reordered. A comment noting the ordering dependency would prevent regression.

**[M2] `createOAuth2User` duplicates role-resolution logic from `AuthController.registerUser`**
Same "find or create ROLE_USER" block exists in both places. Extract to a `RoleService.getOrCreateDefaultRole()` method to satisfy DRY.

**[M3] `AuthController` uses field injection (`@Autowired`) while new OAuth2 code uses constructor injection**
New classes (`OAuth2UserLinkingServiceImpl`, `OAuth2AuthenticationSuccessHandler`, `SecurityConfig`) correctly use `@RequiredArgsConstructor`. `AuthController` should be migrated to constructor injection for consistency and testability, but this is pre-existing technical debt.

**[M4] `handleOAuth2Callback` stores tokens before profile fetch succeeds**
```typescript
this.setTokens(token, refreshToken);  // stored first
return this.http.get<any>('/api/users/me').pipe(...)  // then validated
```
If `/api/users/me` fails, tokens are already in localStorage but `currentUser` is null — partially authenticated state. Store tokens only inside the `tap` on success, or clear them in the `error` handler.

**[M5] `linkedAt` uses `LocalDateTime.now()` (server local time) instead of UTC**
`@PrePersist` sets `linkedAt = LocalDateTime.now()` without timezone. In a multi-region or containerised deployment this will produce inconsistent timestamps. Use `LocalDateTime.now(ZoneOffset.UTC)` or `Instant`.

---

### Low Priority Suggestions

**[L1] `login.component.ts` Google button uses `login` mat-icon — not a Google logo**
Cosmetic: using a generic "login" icon for "Sign in with Google" may confuse users. Consider an SVG Google logo or at minimum `account_circle`.

**[L2] `environment.ts` — `gatewayUrl` is hardcoded `http://localhost:8080`**
`signInWithGoogle()` uses `environment.gatewayUrl` which is only defined in `environment.ts` (dev), not `environment.prod.ts`. Production build will fail to find the property or use the dev URL. Verify `environment.prod.ts` has the correct value.

**[L3] `oauth2-callback.component.ts` — error path shows `setTimeout` redirect**
Using `setTimeout(..., 2000)` for navigation is fragile. Prefer an Observable timer or a state-driven template approach, but acceptable at this scale.

---

### Positive Observations
- OAuth2 state/CSRF handled correctly by Spring Security (no custom implementation needed, `IF_REQUIRED` session policy is the right call)
- Auto-link gated on `email_verified=true` — correct security decision
- Provider link stored by immutable `sub` claim (not email), future-proof for email changes
- URL cleared via `history.replaceState` immediately in callback — good
- `NULL` password for OAuth-only users with explicit guard in `AuthController.login` and `changePassword` — clean
- Gateway routing for both `/oauth2/authorization/**` and `/login/oauth2/code/**` is correct
- `@Transactional` on `processOAuth2User` — correct scope
- No sensitive data logged (email logged, no tokens)

---

### Recommended Actions
1. **[C2] Replace default JWT secret with empty/invalid placeholder** — fail fast if env var not set
2. **[H1] Add `DataIntegrityViolationException` catch** in `processOAuth2User` for concurrent login race
3. **[H2] Null-check `email` and `sub`** in success handler before calling `processOAuth2User`, redirect to error page on null
4. **[M4] Move `setTokens` inside `tap`** in `handleOAuth2Callback`, clear on error
5. **[L2] Add `gatewayUrl` to `environment.prod.ts`**
6. **[M2] Extract `getOrCreateDefaultRole()`** to `RoleService` (DRY)
7. **[C1]** Track refresh-token-in-URL as a tech debt item for future PKCE+cookie approach

---

### Metrics
- Type Coverage: N/A (Java/TypeScript — no type errors observed)
- Test Coverage: Phase 5 (testing) not yet completed per plan
- Linting Issues: 0 critical, 1 style (`@Autowired` in AuthController — pre-existing)

---

### Unresolved Questions
- `environment.prod.ts` — does it exist and does it define `gatewayUrl`? (See L2)
- Is `ddl-auto: update` intentional for production? `validate` or migrations (Flyway/Liquibase) would be safer for the `user_oauth_providers` table.
- Phase 5 (testing) shows no test files added — are integration tests planned or deferred?
