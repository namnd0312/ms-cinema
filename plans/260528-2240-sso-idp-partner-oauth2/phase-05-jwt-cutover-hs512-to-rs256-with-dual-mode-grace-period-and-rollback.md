# Phase 05 — JWT Cutover: HS512 -> RS256 Issuance w/ Dual-Mode Grace Period + Rollback Plan

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md) — section 3.4 (migration strategy)
- Research — Spring AS: [research/researcher-01-spring-authorization-server.md](./research/researcher-01-spring-authorization-server.md) — section 3 (JwtEncoder)
- Research — JWT migration: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section A (dual-mode)
- Scout: [scout/scout-01-existing-auth-patterns.md](./scout/scout-01-existing-auth-patterns.md) — section 2 (existing `JwtService` line 39, 57, 68)
- Prereq: Phase 01 (dual-mode lib v0.0.2 already deployed everywhere) + Phase 02 (Spring AS + `JwtEncoder` bean ready)
- Related code files:
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/impl/JwtServiceImpl.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidatorDualMode.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java`

## Overview

- Date: 2026-05-28
- Description: Switch auth-service internal token issuance from HS512 (jjwt + shared secret) to RS256 via Spring AS `JwtEncoder` w/ DB-backed key. Existing HS512 tokens still verified during a 15-minute grace window via dual-mode shared lib (deployed Phase 01). After 1 week of zero HS512 traffic, remove HS512 path entirely + delete `JWT_SECRET` env var. Includes feature-flag rollback path.
- Priority: P1
- Implementation status: pending
- Review status: n/a

## Key Insights

- **Existing app tokens != partner OIDC tokens, but both must move to RS256** — keeping HS512 for internal tokens defeats the purpose; resource services would still need shared secret distribution.
- **Internal token claims unchanged from caller perspective** — `sub` (email), `jti`, `userId`, `roles`, `iat`, `exp` all preserved. Only JWS header changes (`alg=RS256`, `kid=...`).
- **Grace period = max access token TTL = 15min**. After cutover, no NEW HS512 token issued; in-flight ones expire within 15min. Conservative: wait 1 hour (4x TTL) before declaring grace complete; wait 1 WEEK of zero HS512 traffic before lib v0.1.0 cleanup.
- **Rollback is fast** because dual-mode lib accepts BOTH algs. If RS256 issuance breaks: flip feature flag, re-issue HS512, no resource-service redeploy needed.
- **Refresh tokens in DB (Phase 02 `OAuth2AuthorizationService`) opaque, not JWT** — for Spring AS-issued refreshes. Internal auth-service refresh-token path (`RefreshTokenService`) currently jjwt-signed; must also flip to `JwtEncoder` OR keep as opaque DB-tracked (decision: keep current refresh flow but re-sign access via `JwtEncoder`). Document choice in implementation.

## Requirements

### Functional

- Pre-cutover gate verified: dual-mode lib v0.0.2 deployed everywhere; JWKS reachable from each resource service.
- `JwtServiceImpl.generateTokenLogin`, `generateTokenFromEmail` (both variants), `generateRefreshToken` flipped to use Spring AS `JwtEncoder`.
- New `namnd.app.token-signing-algorithm` config (`HS512` | `RS256`, default `RS256` after cutover) — fast rollback switch.
- Monitoring: Micrometer counter `jwt_verify_alg_total{alg,service}` published from `JwtTokenValidatorDualMode`.
- Post-grace cleanup: HS512 path removed from shared lib, validator renamed `JwtTokenValidatorRs256`, `JWT_SECRET` env var deleted from all 6 services, shared lib v0.1.0 published.

### Non-functional

- Zero-downtime cutover (grace window absorbs in-flight tokens).
- Rollback time < 5min (flip flag + restart auth-service; resource services unchanged).
- Token issuance latency same order of magnitude as HS512 (<5ms typical; RSA signing on Java 21 ~1-2ms).

## Architecture

### Pre-cutover (state after Phase 01)

```
auth-service issues HS512 -> shared secret in env
resource services verify via dual-mode lib (HS512 path active, RS256 path armed but unused)
```

### During cutover (this phase, before grace ends)

```
auth-service issues RS256 (kid in header) via Spring AS JwtEncoder
resource services verify via dual-mode lib:
  - RS256 tokens -> NimbusJwtDecoder via JWKS
  - HS512 tokens (in-flight from before cutover) -> legacy validator
```

### Post-grace cleanup (after 1 week zero HS512 traffic)

```
auth-service issues RS256 only
resource services lib v0.1.0: only NimbusJwtDecoder (HS512 path deleted)
JWT_SECRET env var removed everywhere
```

### Algorithm switch flag

```
namnd:
  app:
    token-signing-algorithm: RS256   # or HS512 for rollback
```
`JwtServiceImpl` reads this and branches.

## Related Code Files

### Modify (cutover - this phase)

- `auth-service/src/main/java/com/namnd/cinema/service/JwtService.java` — no signature changes; behavior swap in impl.
- `auth-service/src/main/java/com/namnd/cinema/service/impl/JwtServiceImpl.java` — inject `JwtEncoder` (RS256 path), add algorithm flag branch.
- `auth-service/src/main/resources/application.yml` — add `namnd.app.token-signing-algorithm: RS256`.
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidatorDualMode.java` — emit Micrometer counter on each verify.
- 6 services `application.yml` — uncomment `jwt.auth.jwks-uri: http://auth-service/oauth2/jwks` (or external host), `jwt.auth.issuer-uri: ${OAUTH_AS_ISSUER_URI}`, `jwt.auth.audience: ${JWT_AUDIENCE}`.

### Modify (cleanup - after 1 week grace)

- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidatorDualMode.java` -> rename to `JwtTokenValidatorRs256.java`; delete HS512 branch.
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java` (legacy HS512) -> delete.
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java` — remove `secret` field.
- `jwt-auth-autoconfigure/pom.xml` — bump version to `0.1.0`.
- 6 consumer poms — bump to `0.1.0`.
- 6 services `application.yml` — delete `jwt.auth.secret` key.
- K8s/Helm secret manifests — delete `JWT_SECRET` env.

## Implementation Steps

### Pre-cutover gate (verification checklist)

1. **Confirm dual-mode lib v0.0.2 deployed** in staging AND prod across all 6 services. Check `/actuator/info` or pom version exposed via `BuildProperties`.
2. **Confirm JWKS reachability** from each resource service:
   ```
   kubectl exec -it deploy/booking-service -- curl -fsS http://auth-service:8080/oauth2/jwks
   ```
   Repeat for movie, notification, payment, audit.
3. **Smoke test HS512 still works** — log in as test user in staging, hit a protected endpoint in each resource service; assert 200.
4. **Verify ACTIVE signing key present** in `signing_keys` table (Phase 01 bootstrap).

### Cutover steps

5. **Inject `JwtEncoder` into `JwtServiceImpl`**:
   ```java
   private final JwtEncoder rsJwtEncoder; // Spring AS bean from Phase 02
   private final String algoFlag;         // @Value("${namnd.app.token-signing-algorithm:RS256}")
   ```
6. **Refactor `generateTokenFromEmail(email, userId, roles)`** to algorithm-branch:
   ```java
   public String generateTokenFromEmail(String email, Long userId, List<String> roles) {
     if ("HS512".equals(algoFlag)) {
       return legacyHs512Generate(email, userId, roles); // current code path
     }
     return rs256Generate(email, userId, roles);
   }
   private String rs256Generate(String email, Long userId, List<String> roles) {
     Instant now = Instant.now();
     JwtClaimsSet claims = JwtClaimsSet.builder()
         .issuer(issuerUri)
         .subject(email)
         .id(UUID.randomUUID().toString())
         .issuedAt(now)
         .expiresAt(now.plusMillis(jwtExpirationMs))
         .claim("userId", userId)
         .claim("roles", roles)
         .build();
     JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build(); // kid auto-set by JwkSource
     return rsJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
   }
   ```
7. **Refactor `generateRefreshToken`** identically; ensure expiration uses `jwtRefreshExpirationMs`.
8. **Refactor `generateTokenLogin(Authentication)`** to call new method.
9. **Add `namnd.app.token-signing-algorithm: RS256`** to `auth-service/application.yml`. Document env override: `TOKEN_SIGNING_ALGORITHM=HS512` for emergency rollback.
10. **Uncomment JWKS keys in all 6 service `application.yml`**:
    ```yaml
    jwt:
      auth:
        jwks-uri: ${JWKS_URI:http://auth-service:8080/oauth2/jwks}
        issuer-uri: ${OAUTH_AS_ISSUER_URI:http://auth-service:8080}
        audience: ${JWT_AUDIENCE:ms-cinema-internal}
        dual-mode-enabled: true
    ```
11. **Add `audience` claim** to issued tokens in RS256 path:
    ```java
    .audience(List.of(audience)) // pulled from @Value("${namnd.app.jwt-audience}")
    ```
12. **Add Micrometer counter** in `JwtTokenValidatorDualMode`:
    ```java
    private final MeterRegistry meterRegistry;
    private final String serviceName; // @Value("${spring.application.name}")
    ...
    meterRegistry.counter("jwt_verify_alg_total","alg",alg,"service",serviceName).increment();
    ```
13. **Deploy auth-service first** with `TOKEN_SIGNING_ALGORITHM=RS256` to staging. Run smoke: login -> protected booking endpoint succeeds; check token header has `alg=RS256`, `kid=k-...`.
14. **Roll resource services with JWKS URI populated** (they were already on v0.0.2 from Phase 01 — only env var change + restart).
15. **Production cutover** during low-traffic window:
    - Deploy auth-service w/ RS256 flag.
    - Monitor `jwt_verify_alg_total{alg="HS512"}` count in Grafana — should fall toward 0 over 15-30 min.
    - Watch error rate on 5 resource services; rollback at threshold breach.
16. **Grace observation** — keep RS256 active for 1 week minimum; verify `jwt_verify_alg_total{alg="HS512"}` stays at 0 (or near 0; ignore stragglers from very long sessions).

### Rollback runbook (if RS256 issuance breaks prod)

1. Set `TOKEN_SIGNING_ALGORITHM=HS512` in auth-service deployment.
2. Restart auth-service pods (rolling).
3. New tokens HS512-signed; dual-mode lib accepts both. No resource-service restart needed.
4. Investigate RS256 issue offline; re-attempt cutover when fixed.

### Post-grace cleanup steps (after 1 week of zero HS512)

17. **Bump shared lib version** to `0.1.0` (breaking change marker — HS512 removed).
18. **Rename `JwtTokenValidatorDualMode` -> `JwtTokenValidatorRs256`**; delete HS512 branch + legacy `JwtTokenValidator` class.
19. **Delete `secret` field from `JwtAuthProperties`** + remove from all 6 service `application.yml`.
20. **Delete `JWT_SECRET` env var** from K8s deployments + Helm values.
21. **Bump all 6 consumer poms to `0.1.0`** + redeploy.
22. **Remove `algoFlag` branch** from `JwtServiceImpl` (only RS256 path remains).
23. **Update docs** `system-architecture.md` to reflect RS256-only state.

### Tests

24. **Integration test per resource service** — `@SpringBootTest` w/ real auth-service + resource service (Testcontainers Postgres + Wiremock JWKS OR docker-compose staging):
    - Login -> get RS256 token -> hit protected endpoint -> assert 200.
    - Same w/ HS512-pre-cutover-style token (manually generated) -> still works during grace.
25. **Rollback drill in staging** — flip flag to HS512, restart, verify endpoints stay green. Document timing.

## Todo List

### Pre-cutover

- [ ] Verify v0.0.2 lib deployed across 6 services in staging + prod
- [ ] Verify JWKS reachable from each resource service
- [ ] Smoke test HS512 path end-to-end in all 5 resource services
- [ ] Confirm ACTIVE signing key in DB

### Cutover

- [ ] Inject `JwtEncoder` into `JwtServiceImpl`
- [ ] Refactor `generateTokenFromEmail` w/ algorithm branch + RS256 path
- [ ] Refactor `generateRefreshToken` to RS256
- [ ] Refactor `generateTokenLogin` -> delegate to new method
- [ ] Add audience claim to RS256 tokens
- [ ] Add `namnd.app.token-signing-algorithm` config (default RS256)
- [ ] Uncomment JWKS / issuer / audience in all 6 service `application.yml`
- [ ] Add Micrometer `jwt_verify_alg_total` counter in dual-mode validator
- [ ] Deploy auth-service to staging w/ RS256
- [ ] Deploy 5 resource services w/ JWKS env populated
- [ ] Grafana panel for `jwt_verify_alg_total` per alg per service
- [ ] Production cutover during low-traffic window
- [ ] Monitor 1h post-cutover

### Rollback runbook validated

- [ ] Staging drill: flip flag to HS512, verify <5min recovery
- [ ] Document runbook steps in `docs/sso-jwt-rollback-runbook.md`

### Post-grace cleanup (1 week later)

- [ ] Confirm `jwt_verify_alg_total{alg=HS512}` = 0 for 1 week
- [ ] Bump shared lib to v0.1.0; remove HS512 path
- [ ] Rename `JwtTokenValidatorDualMode` -> `JwtTokenValidatorRs256`
- [ ] Delete `secret` from `JwtAuthProperties`
- [ ] Delete `JWT_SECRET` env from K8s/Helm
- [ ] Bump 6 consumer poms to v0.1.0; redeploy
- [ ] Remove algorithm flag branch from `JwtServiceImpl`
- [ ] Update `docs/system-architecture.md`

### Tests

- [ ] Per-resource-service integration test for RS256 token
- [ ] Manually generated HS512 token still verifies during grace
- [ ] Rollback drill timed in staging

## Success Criteria

- After cutover, all newly issued tokens have JWS header `alg=RS256` w/ matching `kid` in JWKS.
- All 5 resource services successfully verify RS256 tokens via JWKS in production.
- `jwt_verify_alg_total{alg=HS512}` drops to 0 within 1h of cutover.
- Rollback drill completes in <5min in staging w/o resource-service redeploy.
- Post-grace: `JWT_SECRET` removed from all 6 K8s deployments; HS512 path deleted from shared lib.
- Zero user-facing 401/403 spike during cutover (verified via Grafana panels).

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Resource service can't reach JWKS endpoint | HIGH | Pre-cutover gate verifies; NimbusJwtDecoder caches JWKS so brief outage tolerated |
| In-flight HS512 token rejected after cutover (timing race) | HIGH | Dual-mode lib accepts both during grace; 1h conservative wait before declaring done |
| RS256 signing slower than expected -> latency spike | MED | Benchmark in staging; Java 21 RSA-2048 sign ~1ms; acceptable |
| Audience mismatch breaks internal tokens | MED | Set sensible default `ms-cinema-internal`; validators tolerate null audience initially |
| JWKS cache stale after key rotation | LOW | Cache TTL 1h; NimbusJwtDecoder refreshes on `kid` miss |
| Refresh token format change breaks active sessions | MED | Keep refresh token path's claim shape identical; only signature changes |
| Forgot to populate JWKS env in one service | HIGH | Deployment checklist + automated script asserts env vars present pre-restart |

## Security Considerations

- RS256 = asymmetric: resource services NEVER hold signing key; only public verification via JWKS.
- `kid` header always set so verifiers pick right public key; mandatory after cutover.
- Audience claim validated by all resource services -> token issued for one audience can't be replayed at another (defense in depth).
- Issuer claim validated -> token from rogue issuer rejected.
- `JWT_SECRET` env removal eliminates shared-secret blast radius.
- Algorithm switch flag covered by audit log (config change is operational event).
- Refresh tokens still server-side tracked via existing `RefreshTokenService` + Redis blacklist for revocation.

## Next Steps

- Phase 06 (hardening) runs OIDC conformance + security review on the RS256-only state.
- Once Phase 06 signs off, partner onboarding can start (production-ready).
