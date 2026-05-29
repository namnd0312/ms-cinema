# Phase 01 — Crypto Foundation: RSA Keys, JWKS Endpoint, Dual-Mode JWT Validator

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md)
- Research — Spring AS: [research/researcher-01-spring-authorization-server.md](./research/researcher-01-spring-authorization-server.md) — sections 3 (RS256 JwtEncoder), 7 (version compat)
- Research — JWT migration: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section A (dual-mode), C-4 (JWKS cache policy)
- Scout — existing auth: [scout/scout-01-existing-auth-patterns.md](./scout/scout-01-existing-auth-patterns.md) — sections 1 (no migrations today), 2 (JwtService), 3 (shared lib)
- Related code files (existing):
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/pom.xml`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/resources/application.yml`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidator.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java`

## Overview

- Date: 2026-05-28
- Description: Establish RSA-2048 signing-key lifecycle (DB-backed, encrypted at rest), expose public JWKS endpoint, extend shared `jwt-auth-autoconfigure` lib to verify BOTH HS512 (legacy) and RS256 (new) tokens. No token-issuance behavior changes yet — this is a pure additive enabler.
- Priority: P1
- Implementation status: pending
- Review status: n/a

## Key Insights

- **Spring AS version corrected**: 1.3.x (not 1.4.x — research confirms 1.4.x not released).
- **Single-ACTIVE-key limitation**: NimbusJwtEncoder picks first RS256 ACTIVE key (Spring AS issue #1005). Workaround = enforce exactly 1 ACTIVE row at any time; expose ACTIVE + RETIRED in JWKS for verifier grace period.
- **Flyway must adopt now**: scout confirms `ddl-auto: update` only; no migration tool. Baseline existing schema first, then layer SSO migrations.
- **Dual-mode dispatcher = peek JWS header alg before decoding** (`SignedJWT.parse(token).getHeader().getAlgorithm()`). Avoids forcing one decoder to fail before falling back.
- **Backward compat is contract**: when `jwt.auth.dual-mode-enabled=false` OR `jwt.auth.jwks-uri` empty, the lib behaves exactly like today (HS512 only). Old prod stays safe even if lib upgrade lands before issuer flips.

## Requirements

### Functional

- New `signing_keys` table tracking RSA keypairs w/ status lifecycle.
- `SigningKeyService` auto-generates first ACTIVE RSA-2048 keypair on startup if none exists.
- `GET /oauth2/jwks` returns JWK Set: ACTIVE + RETIRED public keys, `use=sig`, `alg=RS256`, `kid` from DB.
- `jwt-auth-autoconfigure` v0.0.2 dispatches per JWS `alg` header: HS512 -> existing path, RS256 -> JWKS-backed `NimbusJwtDecoder`.
- All 5 resource services + auth-service redeploy on v0.0.2 (no behavior change).

### Non-functional

- Private RSA key encrypted at rest: AES-GCM (256-bit), KEK derived via PBKDF2-HMAC-SHA256 (100k iter, 16-byte salt) from `SIGNING_KEY_ENCRYPTION_PASSWORD` env.
- JWKS cache headers: `Cache-Control: public, max-age=3600`.
- NimbusJwtDecoder uses `JWKSourceBuilder.create(jwksUri).cache(true)` — 1h TTL, refresh on `kid` miss.
- Dual-mode dispatch latency overhead < 1ms (header peek is O(1)).
- Zero downtime when v0.0.2 deploys (HS512 path unchanged).

## Architecture

### `signing_keys` schema

```
signing_keys
  id                       BIGSERIAL PK
  kid                      VARCHAR(64) UNIQUE NOT NULL
  algorithm                VARCHAR(20) NOT NULL    -- 'RS256'
  public_key               TEXT NOT NULL           -- PEM
  private_key_encrypted    TEXT NOT NULL           -- base64(salt || iv || ciphertext || tag)
  status                   VARCHAR(16) NOT NULL    -- ACTIVE | RETIRED
  created_at               TIMESTAMP NOT NULL
  retired_at               TIMESTAMP NULL
  CONSTRAINT only_one_active CHECK (status IN ('ACTIVE','RETIRED'))
```
Add partial unique index `CREATE UNIQUE INDEX uniq_active_key ON signing_keys (status) WHERE status='ACTIVE';` to enforce single-ACTIVE.

### Dual-mode dispatcher (shared lib)

```
HTTP request --> JwtAuthenticationFilter --> JwtTokenValidatorDualMode.parse(token)
                                                    |
                                                    | peek SignedJWT header
                                                    v
                                          alg == RS256 && dualModeEnabled ?
                                            yes -> NimbusJwtDecoder (JWKS-backed)
                                            no  -> existing HS512 validator
                                                    |
                                                    v
                                              Claims (or null on fail)
```

### JWKS document sample

```json
{
  "keys": [
    {"kty":"RSA","kid":"k-2026-05-29-01","use":"sig","alg":"RS256","n":"...","e":"AQAB"},
    {"kty":"RSA","kid":"k-2026-02-28-01","use":"sig","alg":"RS256","n":"...","e":"AQAB"}
  ]
}
```

## Related Code Files

### Create (auth-service)

- `auth-service/src/main/resources/db/migration/V202605290001__baseline_existing_schema.sql` — Flyway baseline (current schema dump via `pg_dump --schema-only`).
- `auth-service/src/main/resources/db/migration/V202605290002__signing_keys.sql` — `signing_keys` table + partial unique index.
- `auth-service/src/main/java/com/namnd/cinema/model/SigningKey.java` — JPA entity.
- `auth-service/src/main/java/com/namnd/cinema/repository/SigningKeyRepository.java` — Spring Data repo, `findByStatus(KeyStatus)`.
- `auth-service/src/main/java/com/namnd/cinema/model/KeyStatus.java` — enum `ACTIVE`, `RETIRED`.
- `auth-service/src/main/java/com/namnd/cinema/service/SigningKeyService.java` — interface.
- `auth-service/src/main/java/com/namnd/cinema/service/impl/SigningKeyServiceImpl.java` — keygen, encrypt, persist, list.
- `auth-service/src/main/java/com/namnd/cinema/util/RsaKeyCryptoUtil.java` — AES-GCM encrypt/decrypt + PEM helpers.
- `auth-service/src/main/java/com/namnd/cinema/config/SigningKeyBootstrap.java` — `ApplicationRunner` ensures ACTIVE exists on startup.
- `auth-service/src/main/java/com/namnd/cinema/controller/JwksController.java` — `GET /oauth2/jwks`.

### Create (jwt-auth-autoconfigure)

- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtTokenValidatorDualMode.java` — dispatcher.
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/RemoteJwksDecoderFactory.java` — builds cached `NimbusJwtDecoder`.

### Modify

- `auth-service/pom.xml` — add `org.flywaydb:flyway-core`.
- `auth-service/src/main/resources/application.yml` — `spring.flyway.enabled=true`, change `spring.jpa.hibernate.ddl-auto: update` -> `validate`; new env keys `namnd.app.oauth2-issuer-uri` (unused yet, set for later phases), `namnd.app.signing-key-encryption-password`.
- `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` — add `/oauth2/jwks` to public paths.
- `jwt-auth-autoconfigure/pom.xml` — add `com.nimbusds:nimbus-jose-jwt` (already transitive, declare to be explicit), `org.springframework.security:spring-security-oauth2-jose`; bump version to `0.0.2`.
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthProperties.java` — add `issuerUri`, `audience`, `jwksUri`, `dualModeEnabled` (default `true`).
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java` — wire `JwtTokenValidatorDualMode` in place of `JwtTokenValidator` (keep HS512 validator as collaborator).
- `jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAuthenticationFilter.java` — call dual-mode validator (no behavior change when only HS512 tokens flow).
- `audit-service/pom.xml`, `booking-service/pom.xml`, `movie-service/pom.xml`, `notification-service/pom.xml`, `payment-service/pom.xml` — bump shared lib version to `0.0.2`.
- All 5 resource services + auth-service `application.yml` — add (commented-out for now) `jwt.auth.jwks-uri`, `jwt.auth.issuer-uri`, `jwt.auth.audience`, `jwt.auth.dual-mode-enabled: true` keys ready for Phase 05.

### Delete

- None.

## Implementation Steps

1. **Add Flyway to auth-service** (`pom.xml` after existing `<dependency>` block). Set `spring.flyway.enabled=true`, `spring.flyway.baseline-on-migrate=true`, `spring.flyway.baseline-version=0` in `application.yml`.
2. **Dump existing schema** via `pg_dump --schema-only --no-owner` on staging auth DB. Strip to DDL. Save as `V202605290001__baseline_existing_schema.sql`. Verify `ddl-auto: validate` boots clean against the baseline.
3. **Create `V202605290002__signing_keys.sql`** with table + partial unique index `uniq_active_key` (see Architecture).
4. **Implement `KeyStatus` enum, `SigningKey` entity, `SigningKeyRepository`** (Lombok `@Data`/`@Builder`, `@Enumerated(EnumType.STRING)`).
5. **Implement `RsaKeyCryptoUtil`**:
   - `KeyPair generateRsa2048()` -> `KeyPairGenerator.getInstance("RSA"); init(2048)`.
   - `String toPemPublic(PublicKey)` / `PublicKey fromPemPublic(String)`.
   - `String encryptPrivatePem(PrivateKey, char[] password)` — generate 16-byte salt + 12-byte IV; derive 256-bit key via `PBKDF2WithHmacSHA256` (100k iter); `Cipher.getInstance("AES/GCM/NoPadding")` w/ 128-bit tag; output `base64(salt || iv || ciphertext)`.
   - `PrivateKey decryptPrivatePem(String, char[] password)` — inverse.
6. **Implement `SigningKeyServiceImpl`**:
   - `Optional<SigningKey> findActive()`.
   - `SigningKey generateAndPersistActive()` — generate keypair, derive `kid = "k-" + LocalDate.now() + "-" + nextSeq()`, encrypt private key, save with `status=ACTIVE`.
   - `List<SigningKey> findActiveAndRetired()` — for JWKS endpoint.
   - `PrivateKey loadPrivateKey(SigningKey)` — decrypt on demand (used in Phase 02 by `JwkSource`).
7. **Implement `SigningKeyBootstrap`** as `@Component implements ApplicationRunner`: if `signingKeyService.findActive().isEmpty()` -> `generateAndPersistActive()` + log `kid`.
8. **Implement `JwksController`** — `@RestController @RequestMapping("/oauth2") class`:
   - `@GetMapping("/jwks") public ResponseEntity<Map<String,Object>>` -> build JWK list via `new RSAKey.Builder(rsaPub).keyID(kid).algorithm(JWSAlgorithm.RS256).keyUse(KeyUse.SIGNATURE).build().toJSONObject()`; wrap as `{"keys": [...]}`; add header `Cache-Control: public, max-age=3600`.
9. **Update `SecurityConfig.java`** — add `/oauth2/jwks` and `/.well-known/**` to `publicPaths`/`permitAll` so unauthenticated partners can fetch.
10. **Extend `JwtAuthProperties`** — add fields `String issuerUri`, `String audience`, `String jwksUri`, `boolean dualModeEnabled = true`. Lombok `@Data`.
11. **Implement `RemoteJwksDecoderFactory`**:
    ```java
    NimbusJwtDecoder build(String jwksUri, String issuerUri, String audience) {
      JWKSource<SecurityContext> src = JWKSourceBuilder
          .create(new URL(jwksUri))
          .retrying(true)
          .cache(3600_000, 60_000)
          .build();
      NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSource(src)
          .jwsAlgorithm(SignatureAlgorithm.RS256).build();
      List<OAuth2TokenValidator<Jwt>> vals = new ArrayList<>();
      vals.add(new JwtTimestampValidator());
      if (issuerUri != null) vals.add(new JwtIssuerValidator(issuerUri));
      if (audience != null) vals.add(audValidator(audience));
      d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(vals));
      return d;
    }
    ```
12. **Implement `JwtTokenValidatorDualMode`**:
    ```java
    public Claims parseClaims(String token) {
      try {
        SignedJWT jwt = SignedJWT.parse(token);
        String alg = jwt.getHeader().getAlgorithm().getName();
        if ("RS256".equals(alg) && props.isDualModeEnabled() && rs256Decoder != null) {
          Jwt decoded = rs256Decoder.decode(token);
          return toJjwtClaims(decoded); // bridge to existing Claims contract used downstream
        }
        return legacyHs512Validator.parseClaims(token);
      } catch (Exception e) { return null; }
    }
    ```
    Bridge `toJjwtClaims` builds a `Claims` map preserving keys `sub`, `userId`, `roles`, `jti`, `iat`, `exp` so call-sites in `JwtAuthenticationFilter` need zero change.
13. **Update `JwtAutoConfiguration`**:
    - Keep existing `JwtTokenValidator` bean (HS512).
    - Conditional bean: if `props.getJwksUri()` non-blank -> instantiate `NimbusJwtDecoder` via factory.
    - Always instantiate `JwtTokenValidatorDualMode(legacy, rs256OrNull, props)` and inject into the existing `JwtAuthenticationFilter` (replace direct `JwtTokenValidator` injection).
14. **Bump shared lib `pom.xml` to `0.0.2`**. Install locally (`./mvnw install -pl jwt-auth-autoconfigure`).
15. **Bump consumer poms** (audit/booking/movie/notification/payment + auth-service) to depend on `0.0.2`. Add `jwt.auth.jwks-uri`, `issuer-uri`, `audience` keys to each `application.yml` but leave commented or empty for now (so dual-mode falls through to HS512 only).
16. **Unit tests**:
    - `RsaKeyCryptoUtilTest` — encrypt -> decrypt roundtrip; wrong password fails.
    - `SigningKeyServiceImplTest` — bootstrap creates ACTIVE, subsequent boot is no-op.
    - `JwksControllerTest` — `@WebMvcTest`, asserts JWK structure + Cache-Control.
    - `JwtTokenValidatorDualModeTest` — fake HS512 token routes to legacy; fake RS256 token routes to Nimbus decoder; unknown alg returns null.
17. **Integration test** in auth-service — `@SpringBootTest`: hit `/oauth2/jwks`, parse via `JWKSet.parse(...)`, assert 1 key, alg=RS256.
18. **Local smoke test** — boot one resource service (e.g., movie-service) w/ shared lib v0.0.2; verify existing HS512 token from auth-service still authenticates GET endpoint.
19. **Compile gate** — `./mvnw clean install -DskipTests` from repo root must succeed; then `./mvnw test`.
20. **Staging deploy** — roll out v0.0.2 to all 6 services in staging; manual smoke (login + booking + payment) before declaring phase done.

## Todo List

- [ ] Add `flyway-core` to `auth-service/pom.xml`
- [ ] Generate `V202605290001__baseline_existing_schema.sql` from staging schema dump
- [ ] Switch `ddl-auto` to `validate`, enable Flyway baseline-on-migrate
- [ ] Author `V202605290002__signing_keys.sql` w/ partial unique index
- [ ] Implement `KeyStatus`, `SigningKey`, `SigningKeyRepository`
- [ ] Implement `RsaKeyCryptoUtil` (AES-GCM + PBKDF2 + PEM)
- [ ] Implement `SigningKeyServiceImpl`
- [ ] Implement `SigningKeyBootstrap` runner
- [ ] Implement `JwksController` w/ cache header
- [ ] Update `SecurityConfig` permit `/oauth2/jwks` + `/.well-known/**`
- [ ] Extend `JwtAuthProperties` (issuerUri, audience, jwksUri, dualModeEnabled)
- [ ] Implement `RemoteJwksDecoderFactory`
- [ ] Implement `JwtTokenValidatorDualMode` + Claims bridge
- [ ] Update `JwtAutoConfiguration` wiring
- [ ] Bump shared lib version to `0.0.2`
- [ ] Bump 6 consumer POMs to depend on `0.0.2`
- [ ] Add placeholder JWKS/issuer/audience keys to all 6 `application.yml`
- [ ] Write unit tests (crypto, service, controller, dual-mode)
- [ ] Write integration test for JWKS endpoint
- [ ] Staging smoke: existing HS512 tokens still verify in all 5 resource services

## Success Criteria

- `signing_keys` table exists in staging w/ exactly 1 ACTIVE row containing valid RSA-2048 keypair.
- `GET https://{staging}/oauth2/jwks` returns valid JWK Set; passes `JWKSet.parse()` lib validation.
- Cache-Control header present and = `public, max-age=3600`.
- All 5 resource services + auth-service run shared lib v0.0.2 in staging.
- Existing user login flow + protected endpoints in all resource services still work (HS512 verification path intact).
- Unit + integration tests pass; `mvn install` clean.
- Wrong-password decrypt throws (encryption integrity proven).

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Schema baseline mismatch (existing prod schema differs from staging) | HIGH | Generate baseline from prod replica; diff staging vs prod first |
| ddl-auto=validate fails on boot due to missed entity | HIGH | Run full integration test suite post-baseline before merge; have rollback PR ready |
| KEK env var leak | HIGH | Store in K8s Secret only; never in git; rotate by re-encrypting all private keys |
| Shared lib v0.0.2 regression breaks a resource service | HIGH | Smoke-test each service in staging individually before phase sign-off |
| Nimbus JWKS network call latency on first miss | MED | Pre-warm cache w/ startup probe in resource services; 30s remote timeout |
| Single-ACTIVE constraint races (two pods bootstrap simultaneously) | LOW | Partial unique index = DB rejects 2nd insert; one pod logs and continues |

## Security Considerations

- Private RSA key never logged. `SigningKey.toString()` overridden (Lombok `@ToString(exclude="privateKeyEncrypted")`).
- AES-GCM 256-bit w/ 128-bit auth tag (RFC 5116).
- PBKDF2-HMAC-SHA256 100k iterations (OWASP 2025 minimum).
- KEK env var passed as char[] (mutable), zeroed after use.
- JWKS endpoint is public (by design) — only PUBLIC keys exposed; private key path is server-side only.
- `Cache-Control: public, max-age=3600` matches RFC 9700 guidance; no `must-revalidate` so verifiers can use stale on outage.
- Issuer + audience claim validators added in RS256 decoder when configured (RFC 7519 §4.1).
- Dual-mode dispatcher never echoes token contents in logs.

## Next Steps

- Phase 02 (Spring AS core) consumes `SigningKeyService` to build `JWKSource` for `NimbusJwtEncoder` and reads from same `signing_keys` table.
- Phase 05 (cutover) flips auth-service to issue RS256 — at that point the dual-mode lib already deployed everywhere will start seeing RS256 tokens.
