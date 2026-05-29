# Phase 03 — Partner Client Management: Admin REST API w/ BCrypt Secret + Strict Redirect Validation

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md) — sections 3.3, 3.5
- Research — Spring AS: [research/researcher-01-spring-authorization-server.md](./research/researcher-01-spring-authorization-server.md) — section 2 (JPA repos)
- Research — hardening: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section C-2 (redirect URI), C-3 (refresh rotation)
- Scout: [scout/scout-01-existing-auth-patterns.md](./scout/scout-01-existing-auth-patterns.md) — section 4 (`@Auditable`)
- Prereq: Phase 02 complete (AS filter chain + token customizer + JpaOAuth2AuthorizationService)
- Related code files:
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java` (pattern reference for `@Auditable` usage)
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/kafka-events/src/main/java/com/namnd/kafka/events/audit/Auditable.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` (ROLE_ADMIN pattern)
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/oauth2/InMemoryDevRegisteredClientStub.java` (to delete/swap)

## Overview

- Date: 2026-05-28
- Description: JPA-backed `RegisteredClientRepository` replacing Phase 02 in-memory stub. Admin REST API `/api/admin/oauth-clients` for CRUD + secret rotation. Strict redirect-URI validation (HTTPS only, no wildcards, no fragments, exact match, max 5). BCrypt secret storage; plaintext returned only on create/rotate. `@Auditable` events emitted to audit-service.
- Priority: P1
- Implementation status: pending
- Review status: n/a

## Key Insights

- **Plaintext secret returned ONCE** — admin must copy on create/rotate. After that only BCrypt hash exists in DB.
- **Redirect-URI strict match is the open-redirect mitigation** — must reject query params, fragments, wildcards, non-HTTPS (except localhost dev).
- **Mapping JPA entity -> Spring AS `RegisteredClient`** is the hot path called on every `/oauth2/token` request — keep `JpaRegisteredClientRepository` lean (cache `findByClientId` per client_id, eviction on update).
- **Soft delete via `status=DISABLED`** — Spring AS calls `findByClientId` and we return `null` when DISABLED so token requests get `invalid_client`.
- **`@Auditable` already wires async Kafka publishing** (scout §4) — annotate controller methods only; AuditAspect handles the rest.

## Requirements

### Functional

- Table `oauth2_registered_clients` w/ all client metadata.
- REST endpoints under `/api/admin/oauth-clients` (ROLE_ADMIN):
  - `POST /` create — returns plaintext secret once.
  - `GET /` list paginated (no secrets).
  - `GET /{clientId}` detail (no secret).
  - `PATCH /{clientId}` update metadata (NOT secret, NOT clientId).
  - `POST /{clientId}/rotate-secret` rotate — returns new plaintext.
  - `DELETE /{clientId}` soft delete (sets `status=DISABLED`).
- `JpaRegisteredClientRepository` implements Spring AS contract; maps entity <-> `RegisteredClient`.
- Validation rejects bad redirect URIs with HTTP 400.
- All mutations emit audit events (`oauth2.client.created|updated|secret_rotated|disabled`).

### Non-functional

- Secret generation: 48 random bytes -> base64url ~64 chars (`SecureRandom`).
- BCrypt cost 12 (matches existing user-password hashing in `auth-service`).
- Per-client TTL fields optional; null = use global defaults.
- Plaintext secret never logged at any level (DEBUG/INFO/WARN/ERROR).
- Controller secret-redaction filter on responses (DTOs already omit `clientSecretHash`).

## Architecture

### `oauth2_registered_clients` schema

```
oauth2_registered_clients
  id                            BIGSERIAL PK
  client_id                     VARCHAR(100) UNIQUE NOT NULL
  client_secret_hash            VARCHAR(255) NOT NULL    -- BCrypt
  client_name                   VARCHAR(200) NOT NULL
  redirect_uris                 JSONB NOT NULL           -- array of strings, max 5
  post_logout_redirect_uris     JSONB NOT NULL DEFAULT '[]'
  scopes                        VARCHAR(255) NOT NULL DEFAULT 'openid,profile,email'
  grant_types                   VARCHAR(255) NOT NULL DEFAULT 'authorization_code,refresh_token'
  require_pkce                  BOOLEAN NOT NULL DEFAULT TRUE
  access_token_ttl_seconds      INTEGER NULL              -- null => global default
  refresh_token_ttl_seconds     INTEGER NULL
  id_token_ttl_seconds          INTEGER NULL
  auto_approve                  BOOLEAN NOT NULL DEFAULT FALSE
  status                        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
  created_at                    TIMESTAMP NOT NULL
  created_by                    VARCHAR(255) NOT NULL
  updated_at                    TIMESTAMP NULL
```
Index: `CREATE INDEX idx_oauth2_clients_status ON oauth2_registered_clients (status);`.

### Entity <-> RegisteredClient mapping

```
OAuth2RegisteredClientEntity --mapper--> RegisteredClient.Builder
  - clientId, clientSecret (BCrypt hash; verified via BCryptPasswordEncoder elsewhere)
  - clientName
  - redirectUris (Set<String> from JSONB)
  - postLogoutRedirectUris (Set<String>)
  - scopes (Set<String> from CSV)
  - authorizationGrantTypes (Set<AuthorizationGrantType>)
  - clientAuthenticationMethods = [CLIENT_SECRET_BASIC, CLIENT_SECRET_POST]
  - clientSettings: requireProofKey(requirePkce), requireAuthorizationConsent(!autoApprove)
  - tokenSettings: per-client TTL overrides; reuseRefreshTokens(false)
```

## Related Code Files

### Create

- `auth-service/src/main/resources/db/migration/V202605290004__oauth2_registered_clients.sql`.
- `auth-service/src/main/java/com/namnd/cinema/model/oauth2/OAuth2RegisteredClientEntity.java`.
- `auth-service/src/main/java/com/namnd/cinema/model/oauth2/ClientStatus.java` — enum `ACTIVE`, `DISABLED`.
- `auth-service/src/main/java/com/namnd/cinema/repository/OAuth2RegisteredClientRepository.java` (Spring Data, named distinct from Spring AS contract).
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/OAuth2RegisteredClientService.java` (interface).
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/impl/OAuth2RegisteredClientServiceImpl.java`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaRegisteredClientRepository.java` — implements `org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository`.
- `auth-service/src/main/java/com/namnd/cinema/util/RedirectUriValidator.java` — validation logic.
- `auth-service/src/main/java/com/namnd/cinema/util/ClientSecretGenerator.java`.
- `auth-service/src/main/java/com/namnd/cinema/controller/oauth2/OAuth2ClientAdminController.java`.
- DTOs (one file each under `auth-service/src/main/java/com/namnd/cinema/dto/oauth2/`):
  - `CreateClientRequest.java`
  - `CreateClientResponse.java` (includes `clientSecret` plaintext)
  - `UpdateClientRequest.java`
  - `ClientSummaryResponse.java` (no secret)
  - `ClientDetailResponse.java` (no secret)
  - `RotateSecretResponse.java`

### Modify

- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuthorizationServerConfig.java` — remove dev stub `@Bean RegisteredClientRepository`; replace with `JpaRegisteredClientRepository`. Keep dev stub `@Profile("dev")` only as fallback for `mvn test`.
- `auth-service/src/main/java/com/namnd/cinema/config/security/SecurityConfig.java` — ensure `/api/admin/**` requires `ROLE_ADMIN` (existing pattern; verify rule covers new path).

### Delete

- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/InMemoryDevRegisteredClientStub.java` — once JPA-backed repo proven; keep one round if needed for dev profile.

## Implementation Steps

1. **Flyway migration** `V202605290004__oauth2_registered_clients.sql` with schema above + index.
2. **`ClientStatus` enum** + **`OAuth2RegisteredClientEntity`** (Lombok `@Data @Builder`, `redirectUris` as `@Type(JsonType.class) List<String>` via Hypersistence Utils or `@Convert(converter=JsonListConverter.class)` — pick one and use across project).
3. **Spring Data repo** `OAuth2RegisteredClientRepository extends JpaRepository<...,Long>` w/ `Optional<...> findByClientIdAndStatus(String, ClientStatus)`, `Optional<...> findByClientId(String)`, `Page<...> findAllByStatus(ClientStatus, Pageable)`.
4. **`ClientSecretGenerator`**:
   ```java
   public String generate() {
     byte[] buf = new byte[48];
     new SecureRandom().nextBytes(buf);
     return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
   }
   ```
5. **`RedirectUriValidator.validate(List<String> uris)`** — for each:
   - `URI uri = URI.create(s)` (throws on bad syntax).
   - Reject if `uri.getFragment() != null`.
   - Reject if `uri.getQuery() != null`.
   - Reject if `s.contains("*")`.
   - Scheme must be `https`, OR `http` if host in `{"localhost","127.0.0.1"}`.
   - Host must be non-null, non-empty.
   - List size ≤ 5.
   - On failure throw `InvalidRedirectUriException` mapped to HTTP 400 w/ message including the offending URI.
6. **`OAuth2RegisteredClientServiceImpl`**:
   - `CreateClientResponse create(CreateClientRequest req)`:
     - Validate redirect URIs + post-logout URIs.
     - Generate clientId (`UUID.randomUUID().toString()` w/o dashes).
     - Generate plaintext secret via generator.
     - BCrypt hash.
     - Persist entity (`createdBy` from `SecurityContext`).
     - Emit `oauth2.client.created` audit (via `@Auditable(action=CREATE, entityType="OAuth2RegisteredClient")`).
     - Return response w/ plaintext secret.
   - `ClientDetailResponse get(String clientId)`.
   - `Page<ClientSummaryResponse> list(Pageable)` w/ `status=ACTIVE` filter (default) or all if `includeDisabled=true` query.
   - `ClientDetailResponse update(String clientId, UpdateClientRequest req)` — partial update; reject if `clientId` or secret-related fields present; cache evict.
   - `RotateSecretResponse rotateSecret(String clientId)` — regen plaintext, store new hash, audit `oauth2.client.secret_rotated`.
   - `void disable(String clientId)` — set `status=DISABLED`, audit `oauth2.client.disabled`, cache evict.
7. **Cache** `findByClientId` lookups via `@Cacheable("registered-clients-by-client-id")` on the JPA-backed Spring AS repo; evict on update/rotate/disable. Use Caffeine (already in shared deps from existing Redis blacklist? if not, add Caffeine).
8. **`JpaRegisteredClientRepository`**:
   ```java
   @Override public void save(RegisteredClient client) { /* not used by admin path; only Spring AS internal save during persisting authorizations - no-op or throw */ }
   @Override public RegisteredClient findById(String id) { return repo.findById(Long.parseLong(id)).filter(e -> e.getStatus()==ACTIVE).map(mapper::toRegisteredClient).orElse(null); }
   @Override public RegisteredClient findByClientId(String clientId) { return repo.findByClientIdAndStatus(clientId, ACTIVE).map(mapper::toRegisteredClient).orElse(null); }
   ```
9. **`OAuth2ClientAdminController`**:
   ```java
   @RestController @RequestMapping("/api/admin/oauth-clients") @PreAuthorize("hasRole('ADMIN')")
   public class OAuth2ClientAdminController {
     @PostMapping @Auditable(action=AuditAction.CREATE, entityType="OAuth2RegisteredClient")
     public ResponseEntity<CreateClientResponse> create(@Valid @RequestBody CreateClientRequest r) { ... return 201; }
     @GetMapping public Page<ClientSummaryResponse> list(Pageable p, @RequestParam(defaultValue="false") boolean includeDisabled) { ... }
     @GetMapping("/{clientId}") public ClientDetailResponse get(@PathVariable String clientId) { ... }
     @PatchMapping("/{clientId}") @Auditable(action=AuditAction.UPDATE, entityType="OAuth2RegisteredClient")
     public ClientDetailResponse update(@PathVariable String clientId, @Valid @RequestBody UpdateClientRequest r) { ... }
     @PostMapping("/{clientId}/rotate-secret") @Auditable(action=AuditAction.UPDATE, entityType="OAuth2RegisteredClient")
     public RotateSecretResponse rotateSecret(@PathVariable String clientId) { ... }
     @DeleteMapping("/{clientId}") @Auditable(action=AuditAction.DELETE, entityType="OAuth2RegisteredClient")
     public ResponseEntity<Void> disable(@PathVariable String clientId) { ... return 204; }
   }
   ```
10. **Add `InvalidRedirectUriException` handler** to `GlobalExceptionHandler` -> 400 w/ `{error: "invalid_redirect_uri", message: "..."}`.
11. **Unit tests**:
    - `RedirectUriValidatorTest` — table of valid/invalid URIs (with/without scheme, fragment, wildcard, query, localhost-http).
    - `OAuth2RegisteredClientServiceImplTest` — create returns plaintext once, rotate changes hash, disable sets status, update rejects forbidden fields.
    - `JpaRegisteredClientRepositoryTest` — DISABLED clients return null on `findByClientId`.
    - `ClientSecretGeneratorTest` — entropy / length / uniqueness across N invocations.
12. **Integration tests** (`@SpringBootTest` + `@AutoConfigureMockMvc`):
    - Non-admin user gets 403 on every endpoint.
    - Admin can complete CRUD lifecycle: create -> get -> update -> rotate -> disable -> list shows DISABLED only when `includeDisabled=true`.
    - Audit events received by Kafka test listener (assert event_type strings).
    - End-to-end: register partner client via API, immediately run auth-code+PKCE flow w/ that client -> succeeds.
13. **Replace in-memory dev stub** in `AuthorizationServerConfig` — comment out or scope to `@Profile("dev")` only.
14. **Compile + boot smoke**: `./mvnw -pl auth-service clean test`; manual curl against staging:
    ```
    curl -u admin:... -XPOST .../api/admin/oauth-clients -d '{...}'  # returns secret
    curl -u admin:... .../api/admin/oauth-clients/{clientId}        # no secret in response
    ```

## Todo List

- [ ] Author `V202605290004__oauth2_registered_clients.sql`
- [ ] Implement `ClientStatus` enum + `OAuth2RegisteredClientEntity`
- [ ] Implement Spring Data `OAuth2RegisteredClientRepository`
- [ ] Implement `ClientSecretGenerator`
- [ ] Implement `RedirectUriValidator` w/ exhaustive rule set
- [ ] Implement `OAuth2RegisteredClientServiceImpl` w/ `@Auditable` on mutations
- [ ] Implement entity <-> `RegisteredClient` mapper
- [ ] Implement `JpaRegisteredClientRepository` (Spring AS contract) w/ caching
- [ ] Implement `OAuth2ClientAdminController` w/ `@PreAuthorize("hasRole('ADMIN')")`
- [ ] Define all 6 DTOs under `dto/oauth2/`
- [ ] Add `InvalidRedirectUriException` -> 400 handler
- [ ] Replace in-memory dev stub w/ JPA-backed repo bean (non-dev profile)
- [ ] Unit tests for validator, generator, service
- [ ] Integration tests for controller (auth, CRUD, audit)
- [ ] End-to-end test: register client -> run auth-code+PKCE flow
- [ ] Staging manual smoke via curl

## Success Criteria

- Admin can register a partner via REST; receives plaintext secret in response body exactly once.
- Plaintext secret never appears in DB, never logged.
- Listing/get endpoints never return secret.
- Update endpoint rejects attempts to mutate `clientId` or secret.
- Rotate produces new plaintext + new hash; old secret fails on `/oauth2/token`.
- Soft-deleted (DISABLED) clients receive `invalid_client` on token endpoint.
- Strict redirect-URI validation rejects: wildcards, fragments, query params, plain http (except localhost), >5 URIs.
- Audit events `oauth2.client.created|updated|secret_rotated|disabled` produced on Kafka.
- Non-admin user receives 403 on every admin endpoint.

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Cache eviction race after rotate -> stale secret hash accepted briefly | MED | Synchronous cache evict in same tx; integration test asserts new secret active <1s after rotate |
| Plaintext secret accidentally logged via DTO toString | HIGH | `CreateClientResponse.toString()` overridden to mask; unit test asserts mask |
| Admin endpoint exposed via misconfig | HIGH | Integration test asserts non-admin 403; security test in Phase 06 reruns |
| JSONB redirect URI deserialization fails on migration | MED | Use single conversion lib consistently; document choice in `code-standards.md` |
| Per-client TTL overrides not honored by Spring AS | LOW | Map to `TokenSettings.builder()` in entity mapper; assert via integration test |

## Security Considerations

- BCrypt cost 12 for client secrets (matches user password policy).
- Secret entropy: 48 bytes = 384 bits, well over OWASP minimum for client_secret.
- `@PreAuthorize("hasRole('ADMIN')")` checked at method level (defense in depth on top of URL `requestMatchers("/api/admin/**").hasRole("ADMIN")`).
- Audit captures `createdBy` from `SecurityContext`; trace_id propagated via existing audit pipeline.
- Redirect URI normalization: lower-case scheme + host before storage to prevent registration-time bypass (`HTTPS://Example.com/cb` vs `https://example.com/cb`).
- Disable (not hard-delete) preserves audit trail.

## Next Steps

- Phase 04 (consent UI) uses `OAuth2RegisteredClientService.findByClientId` to load `client_name` for the consent screen (DB only, never from request params).
- Phase 06 (hardening) re-verifies admin endpoint authz, secret never logged, redirect strict-match via security review.
