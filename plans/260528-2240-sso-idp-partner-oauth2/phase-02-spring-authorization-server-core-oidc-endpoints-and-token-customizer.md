# Phase 02 — Spring Authorization Server Core: OIDC Endpoints + Token Customizer + JPA-Backed Services

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md) — sections 3.1, 3.2
- Research — Spring AS: [research/researcher-01-spring-authorization-server.md](./research/researcher-01-spring-authorization-server.md) — sections 1, 2, 3, 4, 5, 6
- Research — hardening: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section C
- Scout: [scout/scout-01-existing-auth-patterns.md](./scout/scout-01-existing-auth-patterns.md)
- Prereq: Phase 01 must complete (uses `SigningKeyService`, `signing_keys` table)
- Plan validation Qs MUST be resolved before this phase starts (issuer URI, TTLs, etc.)
- Related code files:
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/pom.xml`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/resources/application.yml`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/UserService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/security/OAuth2AuthenticationSuccessHandler.java`

## Overview

- Date: 2026-05-28
- Description: Embed Spring Authorization Server 1.3.x in `auth-service`. Two coexisting `SecurityFilterChain` beans (AS @Order(1), existing app @Order(2)). Wire JPA-backed `RegisteredClientRepository`, `OAuth2AuthorizationService`, `OAuth2AuthorizationConsentService`. Wire `JwtEncoder` to Phase 01's `SigningKeyService`. Customize id_token claims (sub/email/email_verified/name).
- Priority: P1
- Implementation status: pending
- Review status: n/a

## Key Insights

- **Filter ordering is load-bearing**: AS chain @Order(1) MUST come before existing chain @Order(2). Otherwise `/oauth2/**` hits form-login + JWT filter and breaks.
- **Existing `oauth2Login()` for Google must stay in @Order(2) chain** — research confirms no conflict if both chains share the same `UserDetailsService`. AS chain handles `/oauth2/authorize`, existing chain handles `/oauth2/code/google` callback.
- **Spring AS issuer URI is set at startup** via `AuthorizationServerSettings.builder().issuer(...)`. Changing later means breaking partner discovery — pin via `namnd.app.oauth2-issuer-uri` env (plan-validation Q1 MUST resolve first).
- **Phase-02 ships an in-memory `RegisteredClientRepository` stub** with 1 dev client so the full auth-code+PKCE flow can be tested. Phase 03 swaps to JPA-backed repo.
- **TokenCustomizer is the ONLY place** id_token gets `email`, `name`, etc. Default Spring AS id_token has just `sub`, `aud`, `iss`, `exp`, `iat`, `nonce`.

## Requirements

### Functional

- Add `spring-boot-starter-oauth2-authorization-server:1.3.x` to `auth-service`.
- Expose endpoints: `/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`, `/oauth2/introspect`, `/oauth2/jwks` (Phase 01 already), `/userinfo`, `/.well-known/openid-configuration`, `/connect/logout`.
- JPA-backed `OAuth2AuthorizationService` + `OAuth2AuthorizationConsentService` persisting to `oauth2_authorization` and `oauth2_authorization_consent` tables.
- `JwtEncoder` signs tokens with ACTIVE key from `signing_keys` (Phase 01).
- `OAuth2TokenCustomizer<JwtEncodingContext>` adds `email`, `email_verified`, `name`, `sub=user.id` to id_token; minimal claims to access_token.
- Consent endpoint stub `/oauth/consent` (Phase 04 implements real UI; Phase 02 uses Spring AS default page until then).
- Dev client (in-memory) enables end-to-end testing of auth-code+PKCE.

### Non-functional

- AS endpoints `permitAll` for unauthenticated discovery (`.well-known`, `/oauth2/jwks`) per OIDC spec.
- TTLs (subject to Q3 confirmation): access 15min, id_token 1h, refresh 14d w/ rotation, auth code 5min.
- Refresh rotation on (`reuseRefreshTokens(false)`).
- Existing Google OAuth2 client login + `/api/**` JWT filter unaffected.

## Architecture

### Filter chain layout

```
HttpSecurity ----------------+--- @Order(1) authorizationServerSecurityFilterChain
                              |     - matches /oauth2/**, /.well-known/**, /userinfo, /connect/logout
                              |     - OAuth2AuthorizationServerConfigurer + oidc(default)
                              |     - exceptionHandling -> redirect to /login
                              |
                              +--- @Order(2) defaultSecurityFilterChain (existing)
                                    - /api/** authenticated via JwtAuthenticationFilter (HS512 today)
                                    - /login form
                                    - oauth2Login (Google)
                                    - everything else permitAll
```

### Spring AS DB tables (managed by Spring AS schema)

- `oauth2_authorization` (id, registered_client_id, principal_name, authorization_grant_type, attributes, state, authorization_code_value, access_token_value, access_token_issued_at, access_token_expires_at, access_token_metadata, access_token_type, access_token_scopes, oidc_id_token_value, oidc_id_token_issued_at, oidc_id_token_expires_at, oidc_id_token_metadata, refresh_token_value, refresh_token_issued_at, refresh_token_expires_at, refresh_token_metadata).
- `oauth2_authorization_consent` (registered_client_id, principal_name, authorities).

(Use exact DDL from `org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql` resource — Spring AS ships it; copy into Flyway migration.)

### Sequence — partner login (auth code + PKCE)

```
Partner -> AS: GET /oauth2/authorize?client_id&redirect_uri&code_challenge&state&scope=openid
AS -> User: 302 /login (if no session)
User submits creds OR Google OAuth -> existing chain authenticates -> redirect back to /oauth2/authorize
AS -> User: 302 /oauth/consent (unless auto_approve)
User clicks Allow -> AS: POST /oauth2/authorize (consent submit)
AS -> Partner: 302 redirect_uri?code=...&state=...
Partner -> AS: POST /oauth2/token (code, code_verifier, client_id, client_secret)
AS -> Partner: {id_token (RS256, kid), access_token, refresh_token, expires_in}
Partner verifies id_token via /oauth2/jwks
```

## Related Code Files

### Create

- `auth-service/src/main/resources/db/migration/V202605290003__spring_authorization_server_schema.sql` — Spring AS official DDL for `oauth2_authorization` + `oauth2_authorization_consent`.
- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuthorizationServerConfig.java` — AS filter chain, settings, encoder, customizer beans.
- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/InMemoryDevRegisteredClientStub.java` — `@Profile("!prod")` stub w/ 1 dev client. Replaced in Phase 03.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JwksSourceFromDbService.java` — implements `JWKSource<SecurityContext>` reading `signing_keys`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationService.java` — implements `OAuth2AuthorizationService`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationConsentService.java` — implements `OAuth2AuthorizationConsentService`.
- `auth-service/src/main/java/com/namnd/cinema/repository/OAuth2AuthorizationRepository.java` — Spring Data JPA.
- `auth-service/src/main/java/com/namnd/cinema/repository/OAuth2AuthorizationConsentRepository.java`.
- `auth-service/src/main/java/com/namnd/cinema/model/oauth2/OAuth2AuthorizationEntity.java`.
- `auth-service/src/main/java/com/namnd/cinema/model/oauth2/OAuth2AuthorizationConsentEntity.java`.
- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/IdTokenCustomizer.java` — `OAuth2TokenCustomizer<JwtEncodingContext>` bean.
- `auth-service/src/main/java/com/namnd/cinema/controller/oauth2/ConsentStubController.java` — minimal `/oauth/consent` placeholder (Phase 04 replaces).

### Modify

- `auth-service/pom.xml` — add `spring-boot-starter-oauth2-authorization-server` v1.3.x.
- `auth-service/src/main/resources/application.yml` — set `namnd.app.oauth2-issuer-uri`, `namnd.app.access-token-ttl-seconds=900`, `namnd.app.id-token-ttl-seconds=3600`, `namnd.app.refresh-token-ttl-seconds=1209600`.
- `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` — annotate existing chain `@Order(2)`; ensure no path conflicts with AS chain.

### Delete

- None.

## Implementation Steps

1. **Add AS dependency** to `auth-service/pom.xml`:
   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
     <version>1.3.1</version>
   </dependency>
   ```
2. **Copy official Spring AS schema** (`oauth2-authorization-schema.sql`, `oauth2-authorization-consent-schema.sql`) into Flyway migration `V202605290003__spring_authorization_server_schema.sql`. Adapt types for PostgreSQL (BLOB -> BYTEA).
3. **Implement entities** `OAuth2AuthorizationEntity` (mirrors columns; tokens as `byte[]` via Jackson serialization) and `OAuth2AuthorizationConsentEntity` (composite key `@IdClass`).
4. **Implement repositories** w/ `findByState`, `findByAuthorizationCodeValue`, `findByAccessTokenValue`, `findByRefreshTokenValue`, `findByOidcIdTokenValue` derived queries; consent repo `findByRegisteredClientIdAndPrincipalName`.
5. **Implement `JpaOAuth2AuthorizationService`** — `save`, `remove`, `findById`, `findByToken(token, OAuth2TokenType)` dispatching to the right repo method. Use Jackson `ObjectMapper` configured with Spring Security's modules (`SecurityJackson2Modules.getModules(classLoader)`) for token (de)serialization.
6. **Implement `JpaOAuth2AuthorizationConsentService`** — `save`, `remove`, `findById(registeredClientId, principalName)`.
7. **Implement `JwksSourceFromDbService implements JWKSource<SecurityContext>`** — on each call, load `signingKeyService.findActiveAndRetired()`, build `RSAKey` list (ACTIVE + RETIRED), return matching set. Spring AS picks ACTIVE for signing automatically (single-ACTIVE constraint from Phase 01).
8. **Implement `IdTokenCustomizer`**:
   ```java
   @Bean OAuth2TokenCustomizer<JwtEncodingContext> idTokenCustomizer(UserRepository users) {
     return ctx -> {
       if (OidcParameterNames.ID_TOKEN.equals(ctx.getTokenType().getValue())) {
         String username = ctx.getPrincipal().getName();
         users.findByEmail(username).ifPresent(u -> ctx.getClaims().claims(c -> {
           c.put("sub", String.valueOf(u.getId()));
           c.put("email", u.getEmail());
           c.put("email_verified", u.isActivated());
           c.put("name", u.getFullName());
         }));
       }
     };
   }
   ```
   For ACCESS_TOKEN: leave default (no PII bloat).
8. **Implement `AuthorizationServerConfig`** bean wiring:
   ```java
   @Bean @Order(1) SecurityFilterChain asSecurityFilterChain(HttpSecurity http) throws Exception {
     OAuth2AuthorizationServerConfigurer cfg = OAuth2AuthorizationServerConfigurer.authorizationServer();
     http.securityMatcher(cfg.getEndpointsMatcher())
         .with(cfg, c -> c.oidc(Customizer.withDefaults())
                          .authorizationEndpoint(a -> a.consentPage("/oauth/consent")))
         .authorizeHttpRequests(a -> a.anyRequest().authenticated())
         .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
             new LoginUrlAuthenticationEntryPoint("/login"),
             new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
     return http.build();
   }

   @Bean AuthorizationServerSettings asSettings(@Value("${namnd.app.oauth2-issuer-uri}") String issuer) {
     return AuthorizationServerSettings.builder().issuer(issuer).build();
   }

   @Bean JwtEncoder jwtEncoder(JWKSource<SecurityContext> src) { return new NimbusJwtEncoder(src); }
   @Bean JWKSource<SecurityContext> jwkSource(JwksSourceFromDbService svc) { return svc; }
   ```
9. **Update `SecurityConfig.java`** — add `@Order(2)` to existing `SecurityFilterChain` bean. No other changes; existing form login + `oauth2Login` (Google) + JWT filter remain.
10. **Implement `InMemoryDevRegisteredClientStub`** (`@Profile("dev")`) — 1 client w/ id=`dev-test-client`, secret=`{noop}dev-secret`, redirect=`http://localhost:8080/callback`, scopes=`openid,profile,email`, grant_types=`authorization_code,refresh_token`, `requireProofKey(true)`. Bean type `RegisteredClientRepository`. Phase 03 replaces with JPA-backed `@Profile("!dev")` bean.
11. **Implement `ConsentStubController`** — temporary controller returning Spring AS's default consent HTML (or delegate to default by not overriding `consentPage` for now; if so, skip this controller entirely). Decision: omit controller; let Spring AS render default until Phase 04.
12. **Update `application.yml`**:
    ```yaml
    namnd:
      app:
        oauth2-issuer-uri: ${OAUTH_AS_ISSUER_URI:http://localhost:8080}
        access-token-ttl-seconds: 900
        id-token-ttl-seconds: 3600
        refresh-token-ttl-seconds: 1209600
    ```
13. **Configure default TTLs** in `AuthorizationServerConfig`:
    ```java
    @Bean TokenSettings defaultTokenSettings(...) {
      return TokenSettings.builder()
          .accessTokenTimeToLive(Duration.ofSeconds(accessTtl))
          .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
          .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
          .refreshTokenTimeToLive(Duration.ofSeconds(refreshTtl))
          .reuseRefreshTokens(false).build();
    }
    ```
    (Apply per-client via Phase 03's JPA mapper too.)
14. **Verify existing Google login chain** still operative: log into staging via Google after deploy; existing `/api/v1/auth/**` endpoints still respond.
15. **Integration test**: `@SpringBootTest(webEnvironment=RANDOM_PORT) @ActiveProfiles("dev")`:
    - Use `MockMvc` + manual PKCE pair (S256 challenge).
    - `GET /oauth2/authorize?...` -> follow redirects with a logged-in test user session.
    - Capture `code` from redirect.
    - `POST /oauth2/token` with `code_verifier`.
    - Assert response has `id_token`, `access_token`, `refresh_token`.
    - Parse id_token, verify `kid` matches a key in `/oauth2/jwks`, verify `email`, `name`, `sub`, `iss == issuer`, `aud == clientId`.
16. **Manual discovery test**: `curl https://{staging}/.well-known/openid-configuration` returns JSON w/ `issuer`, `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `userinfo_endpoint`, `response_types_supported=["code"]`, `grant_types_supported=["authorization_code","refresh_token"]`, `scopes_supported=["openid","profile","email"]`, `id_token_signing_alg_values_supported=["RS256"]`.
17. **Compile + boot smoke**: `./mvnw -pl auth-service clean test`; boot locally w/ `--spring.profiles.active=dev`; hit `/.well-known/openid-configuration`.

## Todo List

- [ ] Add Spring AS starter dep to `auth-service/pom.xml`
- [ ] Author `V202605290003__spring_authorization_server_schema.sql`
- [ ] Implement `OAuth2AuthorizationEntity` + repository
- [ ] Implement `OAuth2AuthorizationConsentEntity` + repository (composite key)
- [ ] Implement `JpaOAuth2AuthorizationService`
- [ ] Implement `JpaOAuth2AuthorizationConsentService`
- [ ] Implement `JwksSourceFromDbService`
- [ ] Implement `IdTokenCustomizer`
- [ ] Implement `AuthorizationServerConfig` w/ AS filter chain + settings + JwtEncoder
- [ ] Annotate existing `SecurityConfig` chain `@Order(2)`
- [ ] Implement `InMemoryDevRegisteredClientStub` (`@Profile("dev")`)
- [ ] Add issuer + TTL env keys to `application.yml`
- [ ] Configure default `TokenSettings` (refresh rotation on, RS256)
- [ ] Integration test: full auth-code+PKCE flow asserts id_token claims
- [ ] Discovery endpoint smoke test
- [ ] Verify existing Google login + `/api/**` unchanged in staging

## Success Criteria

- `.well-known/openid-configuration` returns valid OIDC discovery document w/ correct issuer.
- Dev client completes auth-code+PKCE flow in integration test; receives signed id_token.
- id_token verified against `/oauth2/jwks` w/ matching `kid`.
- id_token contains `sub`, `email`, `email_verified`, `name`, `iss`, `aud`, `exp`, `iat`.
- Existing `/api/v1/auth/login` (form) + Google OAuth still work end-to-end.
- Refresh token rotates on use (old token reuse -> 400).
- `oauth2_authorization` + `oauth2_authorization_consent` rows persist in DB.

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| AS filter chain order misconfig hides `/oauth2/**` behind JWT filter | HIGH | Explicit `@Order(1)`/`@Order(2)`; integration test asserts `/oauth2/jwks` unauthenticated |
| Token (de)serialization fails (Jackson missing modules) | HIGH | Register `SecurityJackson2Modules` in ObjectMapper used by JPA service; covered by integration test |
| Issuer URI mismatch between config and ingress hostname | HIGH | Plan-validation Q1 resolved before phase; document in runbook |
| Google login regression after second filter chain added | MED | Manual staging smoke; add `@WithMockUser` integration test for `/api/v1/auth/me` |
| Refresh-token rotation breaks long-lived clients | LOW | Documented behavior; only 1-5 partners, all OIDC-clients support rotation |

## Security Considerations

- RS256 id_token signatures (NOT HS256).
- `requireProofKey(true)` enforced on dev client (PKCE mandatory) — same in JPA defaults Phase 03.
- AS endpoints behind HTTPS in staging/prod (NGINX TLS).
- `OidcUserInfo` endpoint returns claims from JPA `User` lookup; no extra PII unless mapped.
- Access tokens self-contained JWT (signed RS256) — verifiable offline by partners; revocation via `/oauth2/revoke` invalidates server-side authorization record.
- Default Spring AS authorization code TTL = 5min (no need to override).
- Refresh-token rotation on (theft detection).
- All AS-managed tables in same DB; row-level access via existing service-account DB user only.

## Next Steps

- Phase 03 (admin API) replaces `InMemoryDevRegisteredClientStub` with JPA-backed repository + REST CRUD.
- Phase 04 (consent UI) replaces Spring AS default consent page with Angular component.
- Phase 05 (cutover) flips existing JwtService to delegate to Spring AS `JwtEncoder` so internal app tokens also go RS256.
