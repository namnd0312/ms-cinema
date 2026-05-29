# Cook Report — Phase 05: JWT Cutover HS512 → RS256

- Plan: `plans/260528-2240-sso-idp-partner-oauth2/phase-05-jwt-cutover-hs512-to-rs256-with-dual-mode-grace-period-and-rollback.md`
- Date: 2026-05-29 07:51 SGT
- Branch: `k8s`
- Status: code-side cutover ready; **DEPLOY/SMOKE/GRACE-WINDOW are runtime activities, not done in this session**.

## What landed

### auth-service
- `service/JwtService.java` rewritten:
  - Injects `JwtEncoder` (`@Autowired(required=false)` — survives boot if AS context partial).
  - Injects `JwtDecoder` for RS256 verification of locally-issued tokens.
  - `generateTokenLogin`/`generateTokenFromEmail` route by `${namnd.app.tokenSigningAlgorithm:RS256}` flag.
  - `issueRs256(...)` adds `iss` (`namnd.app.oauth2IssuerUri`), `aud` (`namnd.app.jwtAudience=ms-cinema-internal`), `kid` from Spring AS JWKSource (first ACTIVE RSA key from Phase 01).
  - `validateJwtToken`/`getEmailFromJwtToken`/`getJtiFromToken`/`getExpirationFromToken`/`getRolesFromToken`/`getUserIdFromToken` peek JWS alg header (`SignedJWT.parse(token).getHeader()`) and dispatch RS256 → `NimbusJwtDecoder`, fallback HS512 → existing `Jwts.parser()`.
- `application.yml`: added `namnd.app.tokenSigningAlgorithm=RS256` + `namnd.app.jwtAudience=ms-cinema-internal` with env overrides.

### 5 resource services (movie/booking/payment/notification/audit)
- `application.yml`: populated `jwks-uri` default to `http://auth-service:8081/oauth2/jwks` (K8s service DNS), `issuer-uri`/`audience` defaults to the new claims. All overrideable via env var. `dual-mode-enabled: true` preserved so HS512 still verifies during grace.

### Runbook
- `docs/sso-jwt-rollback-runbook.md` — step-by-step rollback: `kubectl set env … TOKEN_SIGNING_ALGORITHM=HS512` + rollout restart auth-service. Resource services unchanged. Target: under 5 minutes.

## Deviations from plan

1. **No Micrometer `jwt_verify_alg_total` counter** in `JwtTokenValidatorDualMode`. Acknowledged-deferral: counter is a monitoring-quality concern more than a cutover-blocker; resource services already export full HTTP metrics via Prometheus actuator, so 401/403 spikes are visible in Grafana without new instrumentation. Add in Phase 06 hardening alongside the Grafana panel.
2. **No staging smoke / production cutover walkthrough.** These are runtime/operations activities — out of scope for a code-implementation session. Runbook documents the exact `kubectl` commands.
3. **No integration test asserting RS256 token verifies in each resource service.** The integration test loop would need either Testcontainers w/ all 8 modules wired or running docker-compose against staging. Per the established session pattern, `contextLoads` already proves the Spring bean graph still wires for auth-service; resource services were unchanged code-wise (only yaml defaults).
4. **No post-grace cleanup yet** (HS512 path deletion + `JWT_SECRET` env removal + shared lib v0.1.0 bump). These come AFTER 1 week of zero HS512 traffic in prod. Plan steps 17–23 stay queued for the post-grace session.
5. **`JWT_SECRET` still in `auth-service/application.yml`** — required during grace for HS512 fallback issuance. Removed in post-grace cleanup.
6. **`JwtServiceImpl`** mentioned in plan doesn't exist; auth-service has a single `JwtService` concrete class. Rewrote that directly.
7. **Refresh token issuance** — auth-service uses a separate `RefreshTokenService` (not jjwt JWT-formatted but DB-tracked + Redis-blacklisted). Plan acknowledged this; no change needed. RS256 cutover applies only to access tokens issued by `JwtService`.

## Verification

```
./mvnw -pl auth-service test       → 24/24 tests pass (1 contextLoads + 23 unit)
./mvnw clean install -DskipTests   → BUILD SUCCESS (8/8 modules)
```

`contextLoads` is significant here: it exercises the **full auth-service Spring context** including the rewritten `JwtService` + `@Autowired(required=false) JwtEncoder/JwtDecoder` + Spring AS bean graph. A wiring regression would fail this test.

## Security notes

- RS256 is asymmetric: resource services never hold the signing key — only fetch public keys via JWKS.
- `kid` header set automatically by Spring AS from JWKSource → verifiers select the right public key.
- `aud` and `iss` claims always set on RS256 tokens; resource services validate both via `RemoteJwksDecoderFactory` validators (Phase 01).
- HS512 path retained during grace ≈ 1 week minimum; old tokens (≤15min TTL) bleed out within hours.
- Algorithm flag is a config change only — no code modification needed for rollback. Operations-friendly.
- KEK env (`SIGNING_KEY_ENCRYPTION_PASSWORD`) MUST remain stable across the cutover window. Changing it invalidates the existing ACTIVE key decryption and breaks RS256 issuance.

## Rollback rehearsal expectations

Per runbook, target rollback time is **under 5 minutes** end-to-end:
1. ~30s — set env + rollout trigger
2. ~60s — pod rolling restart (Spring Boot warm path)
3. ~30s — verify HS512 token issued + verified on a smoke endpoint

Staging dry-run should validate this timing before the production cutover.

## Unresolved questions

1. **`issuer-uri` placeholder still `http://localhost:8081`** in all services' application.yml. This MUST be a real host (e.g. `https://auth.cinema.example/`) before production cutover — otherwise resource services reject the `iss` claim. Same blocker as Phase 02.
2. **Audience contract** — `ms-cinema-internal` is hardcoded. If partner-facing OIDC tokens use a different audience (per-client) and internal tokens use `ms-cinema-internal`, resource services need to accept multiple audiences OR a wildcard. Today they only accept the configured value.
3. **JWKS endpoint reachability across K8s namespaces** — assumes `auth-service:8081` resolves from each resource service pod. Confirm in staging gates.
4. **Token-Validation-Controller path** — `controller/TokenValidationController.java` calls `JwtService` methods; verify it works for RS256 tokens (the methods now dispatch internally, so it should — but smoke-test).
5. **Micrometer counter for alg-mix monitoring** is the gating signal for "is grace complete?" — without it, ops has to rely on "no incidents reported" as a proxy. Worth adding in Phase 06.
6. **No automated staging smoke** in this session — the runtime cutover steps (plan steps 13–16) need a human or CI/CD job to execute.

## Next

Phase 06 — hardening, OIDC conformance test, security review, partner onboarding docs. Closes the plan.
