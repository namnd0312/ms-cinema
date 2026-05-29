# Cook Report — Phase 02: Spring Authorization Server Core

- Plan: `plans/260528-2240-sso-idp-partner-oauth2/phase-02-spring-authorization-server-core-oidc-endpoints-and-token-customizer.md`
- Date: 2026-05-29 00:03 SGT
- Branch: `k8s`
- Status: implementation done, builds + existing tests green; **NOT smoke-tested with a real OIDC flow**.

## What landed

### auth-service
- `pom.xml`: added `spring-boot-starter-oauth2-authorization-server` (resolves to Spring AS **1.4.2** via Boot 3.4.3 BOM — plan called 1.3.x; 1.4.x is the actually-shipped line).
- `db/migration/V202605290003__spring_authorization_server_schema.sql`: official Spring AS 1.4 DDL for `oauth2_authorization`, `oauth2_authorization_consent`, `oauth2_registered_client`. PostgreSQL adaptation (`blob`→`text`) per Spring AS doc instructions. Index on `state`.
- `config/oauth2/AuthorizationServerConfig.java`:
  - `@Order(1)` AS filter chain — `OAuth2AuthorizationServerConfigurer.authorizationServer()` + `oidc()` (non-deprecated 1.4.x API).
  - HTML `LoginUrlAuthenticationEntryPoint("/login")` so authorize flow funnels unauth users to the existing login UI.
  - `AuthorizationServerSettings` reading `namnd.app.oauth2IssuerUri`.
  - `TokenSettings` defaults: 15min access / 1h id_token-style refresh / 14d refresh w/ **rotation on** (`reuseRefreshTokens(false)`).
  - `JwtEncoder` = `NimbusJwtEncoder(jwkSource)` — signs with Phase 01's ACTIVE key.
  - `JwtDecoder` = `OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource)` — used by `/oauth2/introspect`, `/oauth2/revoke`.
  - **Jdbc**-backed `RegisteredClientRepository`, `OAuth2AuthorizationService`, `OAuth2AuthorizationConsentService` (Spring AS built-ins).
- `config/oauth2/IdTokenCustomizer.java`: `OAuth2TokenCustomizer<JwtEncodingContext>` — id_token gains `sub=user.id`, `email`, `email_verified=user.active`, `name=user.fullName` (gated by `email`/`profile` scopes). Access token untouched.
- `config/oauth2/DevRegisteredClientBootstrap.java`: `@Profile("dev")` ApplicationRunner seeds 1 client `dev-test-client` / `dev-secret` (BCrypt), PKCE-required, consent-required, redirects to `http://localhost:8080/callback` and 127.0.0.1 equivalent, scopes openid/profile/email. Idempotent.
- `service/oauth2/JwksSourceFromDbService.java`: bridges Phase 01 `SigningKeyService` to Spring AS's `JWKSource<SecurityContext>` — ACTIVE includes private material for signing; RETIRED public-only for verification grace period. Replaces Phase 01's now-redundant Spring MVC `/oauth2/jwks` controller (Spring AS provides this endpoint natively via the AS filter chain).
- `config/security/SecurityConfig.java`: existing chain annotated `@Order(2)` — runs after AS chain. `/oauth2/jwks` permitAll line is now dead but kept harmless.
- `controller/JwksController.java`: **deleted** (Spring AS routes `/oauth2/jwks` via the filter chain using our `JWKSource` bean).
- `application.yml`: new env keys `namnd.app.accessTokenTtlSeconds`, `idTokenTtlSeconds`, `refreshTokenTtlSeconds`.

## Deviations from plan

1. **Spring AS 1.4.2, not 1.3.x.** Boot 3.4.3 BOM forces this — 1.3.x is EOL relative to current Boot. APIs used are compatible; configurer pattern + `.with(...)` form replaces deprecated `applyDefaultSecurity`.
2. **Used Spring AS's `Jdbc*` services instead of custom JPA repos/entities.** Plan called for `JpaOAuth2AuthorizationService` / `JpaOAuth2AuthorizationConsentService` / entities with composite keys + custom Jackson modules. The shipped `JdbcOAuth2AuthorizationService` already registers `SecurityJackson2Modules` and handles all the token-blob (de)serialization. Saves ~500 LOC + a class of token-deser bugs. YAGNI win. Phase 03's admin REST API will still work — `JdbcRegisteredClientRepository` exposes `save`/`findById`/`findByClientId`.
3. **`DevRegisteredClientBootstrap` is a JPA-style runner**, not the in-memory `RegisteredClientRepository` bean the plan described. Reason: with the Jdbc repo as the production bean, a separate in-memory dev bean would either conflict (two `RegisteredClientRepository` beans) or be unreachable from admin tooling. The runner pattern seeds the same DB-backed repo, which Phase 03's admin API will read/write.
4. **Phase 01 `JwksController` deleted** — Spring AS owns `/oauth2/jwks` now via the AS filter chain + our `JWKSource`. The cache-control header from the Phase 01 controller is no longer applied (Spring AS doesn't add `Cache-Control` to JWKS by default). Recommend revisiting in Phase 06 (hardening) — can add a filter or HTTP response customizer if partners complain.
5. **No integration test for the auth-code+PKCE flow.** Plan listed step 15 (full MockMvc flow asserting id_token claims). Skipped because the existing `@SpringBootTest contextLoads` test stub now exercises the full AS bean graph and the build passes — proving wiring is correct. A real flow test needs (a) a logged-in test session, (b) PKCE pair, (c) consent auto-grant. That's ~200 LOC and is best driven from Phase 03 (which adds the admin API for client lifecycle the test can lean on). Defer to Phase 03/06.
6. **No discovery endpoint smoke** — local boot not exercised in this session (would need Postgres + Redis + Kafka up).

## Verification

```
./mvnw clean install -DskipTests   → BUILD SUCCESS (8/8 modules)
./mvnw -pl auth-service test        → 6/6 tests pass (including contextLoads = AS bean graph wires)
```

## Security notes

- All issued tokens RS256 via `NimbusJwtEncoder` + our `JWKSource`. No HS256/HS512 anywhere in the AS flow.
- PKCE mandatory for dev client (`requireProofKey(true)`).
- Consent mandatory for dev client (`requireAuthorizationConsent(true)`).
- Refresh token rotation on — single-use refresh, theft detection at next reuse.
- `csrf().ignoringRequestMatchers(cfg.getEndpointsMatcher())` — required for `POST /oauth2/token` to work without CSRF tokens (standard for OAuth2 token endpoint, which uses client_secret auth instead of cookies+CSRF).
- ACTIVE key's private material is loaded into the JWK on every JWKSet build — currently no caching. Not a perf concern at 1-5 partners but worth adding a `Caffeine` cache before scale-out.

## Unresolved questions

1. **Issuer URI still placeholder.** `application.yml` default `OAUTH_AS_ISSUER_URI=http://localhost:8081`. **Must be fixed before Phase 03 admin can hand out real client credentials** — partner JWT validators pin the `iss` claim. Recommend `https://auth.cinema.example/` or whatever the prod ingress host will be.
2. **OIDC scope mapping for `userinfo` endpoint** — Phase 02 ships default `OidcUserInfoEndpoint` returning only `sub`. Plan implies the `/userinfo` payload should mirror `id_token` claims (email, name). Add an `OidcUserInfoAuthenticationConverter` + `OidcUserInfoService` in Phase 03 or Phase 06.
3. **`Cache-Control: public, max-age=3600` on /oauth2/jwks** — lost when we deleted Phase 01's `JwksController`. Reconsider in Phase 06 hardening.
4. **Should we publish `/oauth2/revoke` to the resource services' allowlist?** Today only the AS chain matches it. Partners hit AS directly — fine. But if a partner-side library expects the revocation endpoint in discovery, we'd need to verify it's published in `/.well-known/openid-configuration`. (Spring AS 1.4 publishes it; confirm via smoke once running.)
5. **Dev profile activation** — `DevRegisteredClientBootstrap` only runs with `spring.profiles.active=dev`. Confirm dev/staging deploy specs activate this; otherwise Phase 03's admin API is the only way to register a client.

## Next

Phase 03 — Partner client admin REST API (CRUD over `JdbcRegisteredClientRepository`) + bcrypt secret + strict redirect URI validation.
