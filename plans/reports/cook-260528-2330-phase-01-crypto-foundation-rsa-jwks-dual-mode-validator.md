# Cook Report — Phase 01: Crypto Foundation (RSA + JWKS + Dual-Mode Validator)

- Plan: `plans/260528-2240-sso-idp-partner-oauth2/phase-01-crypto-foundation-rsa-jwks-dual-mode-validator.md`
- Date: 2026-05-28 23:43 SGT
- Branch: `k8s`
- Status: implementation done, builds + unit tests green; **NOT yet deployed/smoke-tested in staging**.

## What landed

### auth-service
- `pom.xml`: added `flyway-core`, `flyway-database-postgresql`, `nimbus-jose-jwt 9.40`.
- `application.yml`: `spring.flyway.enabled=true`, `baseline-on-migrate=true`; new env keys `namnd.app.oauth2IssuerUri`, `namnd.app.signingKeyEncryptionPassword`.
- `db/migration/V202605290001__signing_keys.sql`: table + partial unique index `uniq_active_signing_key` enforcing single ACTIVE row.
- `model/KeyStatus.java`, `model/SigningKey.java` (Lombok `@ToString(exclude="privateKeyEncrypted")`).
- `repository/SigningKeyRepository.java`: `findFirstByStatus`, `findByStatusIn`, `findByKid`.
- `util/RsaKeyCryptoUtil.java`: RSA-2048 gen, PEM round-trip, AES-GCM 256-bit + PBKDF2-HMAC-SHA256 100k iter.
- `service/SigningKeyService.java` + `impl/SigningKeyServiceImpl.java`: bootstrap, JWKS list, private-key decrypt.
- `config/SigningKeyBootstrap.java`: `ApplicationRunner` — DB races caught via `DataIntegrityViolationException`.
- `controller/JwksController.java`: `GET /oauth2/jwks` → Nimbus `JWKSet` JSON, `Cache-Control: public, max-age=3600`.
- `config/security/SecurityConfig.java`: permitAll for `/oauth2/jwks` + `/.well-known/**`.

### jwt-auth-autoconfigure (shared lib)
- `pom.xml`: added `spring-security-oauth2-jose`, `spring-security-oauth2-resource-server`, `nimbus-jose-jwt 9.40`, test dep.
- `JwtAuthProperties.java`: added `jwksUri`, `issuerUri`, `audience`, `dualModeEnabled=true`.
- `RemoteJwksDecoderFactory.java`: `NimbusJwtDecoder.withJwkSetUri(...)` + JwtTimestampValidator + optional iss/aud.
- `JwtTokenValidatorDualMode.java`: peeks `alg` from `SignedJWT`, dispatches to RS256/HS512; jjwt-0.12.6 `Claims` bridge via `HashMap`+`Jwt` facade.
- `JwtAutoConfiguration.java`: `legacyHs512Validator` bean + `@Primary jwtTokenValidator` dual-mode bean; `rs256JwtDecoder` is `@ConditionalOnProperty(jwks-uri)`.

### 5 resource services (movie/booking/payment/notification/audit)
- `application.yml`: added `jwks-uri`, `issuer-uri`, `audience`, `dual-mode-enabled` keys with env-var defaults to empty so behavior is unchanged today.

## Deviations from plan

1. **No `V0__baseline_existing_schema.sql`** — plan called for `pg_dump --schema-only` of staging. I do not have staging access. Used Flyway `baseline-on-migrate=true` + `baseline-version=0` so existing DBs are baselined and skip directly to `V202605290001__signing_keys.sql`. **Kept `ddl-auto: update`** (not `validate`) — switching to `validate` without a verified baseline against actual prod schema is too risky for this session. Add baseline + flip to `validate` as Phase 1.5 follow-up before Phase 02.
2. **No shared-lib version bump to 0.0.2** — module versions are parent-tied (`${project.version}` = `0.0.1-SNAPSHOT`); consumer poms pick up changes via reactor. Behavior matches the plan (no behavior change for consumers without `jwks-uri`).
3. **`RemoteJwksDecoderFactory`** uses `NimbusJwtDecoder.withJwkSetUri(...)` rather than the Nimbus `JWKSourceBuilder.create(...).cache(...).retrying(...)` form spec'd in the plan — that API is not in Spring Security 6.4's public surface. Spring's default JWK source already caches + refreshes on `kid` miss; functional outcome matches.
4. **Tests written but not all in plan list** — wrote `RsaKeyCryptoUtilTest` (5 tests) and `JwtTokenValidatorDualModeTest` (4 tests). Skipped `SigningKeyServiceImplTest`, `JwksControllerTest`, and full `@SpringBootTest` integration because (a) auth-service has only a stub `contextLoads` test today, (b) test-context bootstrap pulls in Redis/Kafka mocks not configured for new beans, (c) Mockito + Nimbus mint were sufficient to prove the dispatcher logic. These tests should be added in Phase 02 work when full `@SpringBootTest` context is exercised.
5. **No staging smoke** — out of scope for this session (no cluster access).

## Verification

```
./mvnw clean install -DskipTests  → BUILD SUCCESS (8/8 modules)
./mvnw -pl auth-service test       → 6/6 tests pass (5 RsaKeyCryptoUtilTest + 1 contextLoads)
./mvnw -pl jwt-auth-autoconfigure test → 4/4 dual-mode tests pass
```

## Security notes

- Private RSA key: encrypted with AES-GCM 256-bit (128-bit tag) under a PBKDF2-HMAC-SHA256(100k iter) KEK derived from `SIGNING_KEY_ENCRYPTION_PASSWORD`. Salt + IV regenerated per encryption (ciphertext non-determinism verified by unit test).
- `signing_keys.private_key_encrypted` excluded from `@ToString`.
- JWKS endpoint exposes **only public material** (RSA modulus + exponent).
- Dev default KEK in `application.yml` is documented as "change in production" — operators MUST set `SIGNING_KEY_ENCRYPTION_PASSWORD` before first boot or the dev fallback will be used (and existing-key decryption will break later if the env var is set after the fact). Recommend Kubernetes Secret + `valueFrom.secretKeyRef`.

## Unresolved questions (require human input before Phase 02)

1. **Issuer URI** — should partners see `http://localhost:8081` (current placeholder) or `https://auth.cinema.example/`? Affects token `iss` claim + OIDC discovery doc. Cannot mint partner-facing tokens until decided.
2. **Baseline schema** — need a real `V0__baseline_existing_schema.sql` from `pg_dump --schema-only` of prod replica before switching `ddl-auto` to `validate`. Or accept the YAGNI position: keep `ddl-auto: update`, document Flyway as additive-only.
3. **K8s Secret for KEK** — confirm path/name conventions (e.g., `auth-service-signing-kek` secret with key `kek-password`) so deploy manifests can reference it.
4. **OIDC discovery `/.well-known/openid-configuration`** — plan permits the path in SecurityConfig but does not implement it in Phase 01. Confirm that Phase 02 (Spring AS) will provide it via the framework's `OidcProviderConfigurationEndpoint` (default behavior).

## Next

Phase 02 — Spring Authorization Server 1.3.x core (consumes `SigningKeyService.loadPrivateKey()` for `NimbusJwtEncoder`). Depends on Issuer URI decision above.
