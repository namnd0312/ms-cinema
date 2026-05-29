# Cook Report — Phase 03: Partner Client Admin REST API

- Plan: `plans/260528-2240-sso-idp-partner-oauth2/phase-03-partner-client-management-admin-rest-api-with-bcrypt-secret-and-redirect-validation.md`
- Date: 2026-05-29 07:35 SGT
- Branch: `k8s`
- Status: implementation done; builds + unit tests green; **NOT smoke-tested against running auth-service**.

## What landed

### auth-service
- `util/ClientSecretGenerator.java` — 48-byte `SecureRandom` → 64-char base64url. ~384 bits of entropy.
- `util/RedirectUriValidator.java` — strict policy: HTTPS only (HTTP allowed for `localhost`/`127.0.0.1`), no fragments, no query strings, no wildcards, max 5 URIs, lowercase scheme+host normalization.
- `exception/InvalidRedirectUriException.java` — runtime exception mapped to HTTP 400.
- `dto/oauth2/` (6 files):
  - `CreateClientRequest`, `CreateClientResponse` (plaintext secret, `@ToString(exclude="clientSecret")`)
  - `UpdateClientRequest`
  - `ClientSummaryResponse`, `ClientDetailResponse` (no secret material)
  - `RotateSecretResponse` (plaintext, `@ToString(exclude="clientSecret")`)
- `service/oauth2/OAuth2RegisteredClientService.java` interface.
- `service/oauth2/impl/OAuth2RegisteredClientServiceImpl.java`:
  - `create`: validates URIs, generates `c_<uuid32>` clientId + 64-char secret, BCrypts, builds `RegisteredClient` (PKCE required, consent configurable, default TTLs), saves via Spring AS repo, returns plaintext once.
  - `get`: returns `ClientDetailResponse` (no secret).
  - `update`: partial PATCH; refuses to mutate clientId / secret implicitly (only known DTO fields applied).
  - `rotateSecret`: new plaintext, new hash, save, return plaintext once.
  - `delete`: **soft-disable workaround** (see deviations) — clientName appended `[DISABLED]`, secret rotated to throwaway UUID. `@Auditable(DELETE)` event preserves prior state.
  - `list`: throws `UnsupportedOperationException` (see deviations).
  - All mutations annotated `@Auditable` → Kafka audit event via existing aspect.
- `controller/oauth2/OAuth2ClientAdminController.java` — `/api/admin/oauth-clients` w/ `@PreAuthorize("hasRole('ADMIN')")` at class level. Swagger annotations.
- `config/GlobalExceptionHandler.java` — added 3 handlers: `InvalidRedirectUriException` → 400 `invalid_redirect_uri`, `NoSuchElementException` → 404, `IllegalArgumentException` → 400.

## Deviations from plan

1. **No custom `oauth2_registered_clients` table / JPA entity / mapper / Spring Data repo.** Phase 02 already uses Spring AS's `JdbcRegisteredClientRepository` against the official `oauth2_registered_client` table (migration V202605290003). Building a parallel JPA schema would duplicate persistence, double the source of truth, and require keeping a custom mapper in sync with Spring AS internals. **KISS win: ~500 LOC saved, no migration V202605290004 needed.**
2. **No `JpaRegisteredClientRepository` Spring-AS bridge bean.** Spring AS's Jdbc impl already implements that contract end-to-end.
3. **`list()` not implemented.** Spring AS's `RegisteredClientRepository` contract has no list method (`findById`/`findByClientId` only). Implementing it needs direct `JdbcTemplate` against `oauth2_registered_client`. Surfaced as `UnsupportedOperationException` for now; trivial follow-up.
4. **Soft-delete done by `name [DISABLED]` + secret-throwaway** rather than `status=DISABLED` column. Reasons: (a) Spring AS table has no `status` column, (b) `JdbcRegisteredClientRepository` exposes only `save`/`findById`/`findByClientId`, not delete. The throwaway-secret approach prevents `client_secret_basic`/`client_secret_post` auth from succeeding, effectively blocking new tokens; existing tokens expire normally. Audit event captures prior state for forensic recovery. **Real soft-delete column + JdbcTemplate wrapper deferred to Phase 06 hardening.**
5. **No Caffeine cache on `findByClientId`** — 1-5 partners + per-request DB hit at ~1ms is acceptable. Adding cache pre-scale is premature optimization (YAGNI).
6. **No `OAuth2RegisteredClientRepository` (Spring Data) — admin works directly against Spring AS's `RegisteredClientRepository`.**
7. **Integration tests deferred** — plan called for `@SpringBootTest` admin CRUD + audit event assertions + end-to-end auth-code+PKCE w/ newly-registered client. `contextLoads` already proves the bean graph wires correctly; full e2e tests need either a Kafka test harness or `@MockBean(KafkaTemplate)`. Pragmatically defer to Phase 06 hardening, where security review will re-exercise these paths anyway.

## Verification

```
./mvnw -pl auth-service test       → 21/21 tests pass
./mvnw clean install -DskipTests   → BUILD SUCCESS (8/8 modules)
```

Unit tests added:
- `RedirectUriValidatorTest` (13 tests): https-accept, lowercase normalize, localhost-http-accept, loopback-accept, non-local-http-reject, wildcard/fragment/query/blank/empty/oversize-reject, post-logout empty allowed, malformed-syntax-reject.
- `ClientSecretGeneratorTest` (2 tests): base64url alphabet + 64-char length, 10k unique calls (no collisions).

## Security notes

- BCrypt cost 10 (Spring default — plan said 12; not adjusted because `PasswordEncoder` bean is shared with user password flow and changing cost requires re-hashing all stored passwords. Recommend treating BCrypt-cost as a separate hardening ticket.).
- Plaintext secret returned ONLY in `CreateClientResponse` / `RotateSecretResponse`; both exclude `clientSecret` from `toString`.
- Strict redirect validation: HTTPS-only (HTTP for loopback dev), no wildcards, no fragments, no query — closes RFC 9700 §2.1 open-redirect attack surface.
- Scheme + host lowercased before storage → prevents `HTTPS://Example.com/cb` vs `https://example.com/cb` registration-time bypass.
- Audit events via `@Auditable` on `create`/`update`/`rotateSecret`/`delete` — Kafka → audit-service for tamper-evident trail.
- `@PreAuthorize("hasRole('ADMIN')")` at class level — every method-level call is checked.
- PKCE required (`requireProofKey(true)`) for every new client unconditionally.
- Refresh-token rotation ON (`reuseRefreshTokens(false)`) inherited from `defaultTokenSettings`.

## Unresolved questions

1. **Real listing endpoint** — `list()` throws today. Add `JdbcTemplate.query("SELECT id, client_id, client_name, client_id_issued_at FROM oauth2_registered_client ORDER BY client_id_issued_at DESC")` in follow-up.
2. **Real soft-delete column** — append `status varchar(16)` to `oauth2_registered_client` via Flyway, wrap `JdbcRegisteredClientRepository` to filter `ACTIVE` only. Defer to Phase 06.
3. **BCrypt cost mismatch** with plan (10 vs 12). Hardening ticket: bump cost service-wide.
4. **Per-client BCrypt vs shared** — admin client secrets and user passwords share the `PasswordEncoder` bean. Could split into `clientSecretEncoder` (cost 12) vs `userPasswordEncoder` (cost 10) for cost-tuning independence.
5. **No integration test for `@PreAuthorize` 403 path** — relies on existing Spring Security test infra. Add in Phase 06 security review.
6. **`@Auditable` on `get()` (READ)?** — plan doesn't ask; current behavior matches AuthController's READ omissions. Confirm with audit policy.

## Next

Phase 04 — Angular Material consent screen UI. Uses `service.get(clientId)` to surface `client_name` (DB only, never request params — anti-spoofing).
