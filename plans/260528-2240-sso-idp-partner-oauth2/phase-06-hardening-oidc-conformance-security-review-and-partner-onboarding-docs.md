# Phase 06 — Hardening: OIDC Conformance Run, Security Review Checklist, Key Rotation Runbook, Partner Onboarding Docs

## Context Links

- Plan overview: [plan.md](./plan.md)
- Brainstorm: [../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md) — sections 5 (risks), 6 (success criteria)
- Research — hardening: [research/researcher-02-jwt-migration-consent-hardening.md](./research/researcher-02-jwt-migration-consent-hardening.md) — section C (full hardening checklist)
- Research — Spring AS: [research/researcher-01-spring-authorization-server.md](./research/researcher-01-spring-authorization-server.md)
- Prereq: Phases 01-05 complete + grace period observed for Phase 05.
- Related code files:
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuthorizationServerConfig.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationConsentService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationService.java`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/` (NGINX ingress + secret manifests for rate limiting + KEK)
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/system-architecture.md`
  - `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/api-documentation.md` (if exists; create otherwise)

## Overview

- Date: 2026-05-28
- Description: Final hardening + production readiness. Run OpenID Conformance Suite against staging; fix any non-conformance. Execute security review checklist (PKCE, redirect URI, secret hygiene, refresh rotation, JWKS cache, state/nonce, HTTPS, CORS). Add K8s NGINX rate limits on token + authorize endpoints. Wire audit events. Implement admin key-rotation endpoint + runbook. Author partner onboarding guide. Update system docs.
- Priority: P1
- Implementation status: code + docs complete (2026-05-29); operational items (conformance run, staging drill, load test, pilot) deferred
- Review status: n/a

## Key Insights

- **OIDC conformance suite is the production-readiness gate** — without a passing run we cannot tell partners we are spec-compliant.
- **Rate limiting at NGINX layer is sufficient for 1-5 partners** (Bucket4j in-app is YAGNI now). Annotations on the Ingress object are all that's needed.
- **Refresh-token reuse detection** is the highest-leverage security feature available — Spring AS handles it natively when `reuseRefreshTokens(false)` is set (Phase 02 — verify here).
- **Key rotation runbook MUST be exercised once in staging** during this phase to validate it actually works; otherwise it's documentation theater.
- **Partner integration guide should be copy-pasteable** — partners should not need to read Spring docs.

## Requirements

### Functional

- OpenID Conformance Suite "OIDC Basic Certification" profile passes against staging IdP (or non-conformance items triaged + fixed).
- Admin endpoint `POST /api/admin/signing-keys/rotate` issues new ACTIVE key, marks current ACTIVE -> RETIRED.
- Audit events emitted: `oauth2.token.issued`, `oauth2.token.revoked`, `oauth2.consent.granted`, `oauth2.consent.denied`, `oauth2.signing_key.rotated`.
- K8s NGINX Ingress rate limit annotations on `/oauth2/token` + `/oauth2/authorize`.
- Docs: `sso-key-rotation-runbook.md`, `sso-partner-integration-guide.md`, `sso-jwt-rollback-runbook.md` (from Phase 05).
- Updated `system-architecture.md` w/ IdP component + sequence diagram.
- Updated `api-documentation.md` listing all new SSO endpoints.

### Non-functional

- Conformance suite run reproducible (config saved as JSON).
- Rate limit values tunable via Helm values.
- Runbooks executable by a junior dev w/o tribal knowledge.

## Architecture

### Audit event taxonomy

| event_type | source | trigger |
|---|---|---|
| `oauth2.token.issued` | `AuthenticationSuccessEvent` listener | Successful `/oauth2/token` |
| `oauth2.token.revoked` | `/oauth2/revoke` endpoint + refresh rotation reuse detection | Token revocation |
| `oauth2.consent.granted` | `JpaOAuth2AuthorizationConsentService.save` | Consent persisted |
| `oauth2.consent.denied` | `OAuth2AuthorizationConsentService.remove` or denial submit | Consent rejected |
| `oauth2.client.created|updated|secret_rotated|disabled` | Phase 03 admin endpoints | Client mgmt |
| `oauth2.signing_key.rotated` | `/api/admin/signing-keys/rotate` | Key rotation |

### NGINX Ingress rate-limit annotations

```yaml
metadata:
  annotations:
    nginx.ingress.kubernetes.io/limit-rps: "10"
    nginx.ingress.kubernetes.io/limit-connections: "20"
    nginx.ingress.kubernetes.io/server-snippet: |
      location ~* /oauth2/(token|authorize) {
        limit_req zone=oauth_burst burst=20 nodelay;
      }
```

### Key rotation sequence

```
1. ops calls POST /api/admin/signing-keys/rotate
2. SigningKeyService.rotate():
   - generate new RSA-2048 keypair
   - mark current ACTIVE row -> RETIRED (retired_at=now)
   - insert new row w/ status=ACTIVE
3. JwkSourceFromDbService picks up both on next call (cache miss in resource services -> JWKS refresh)
4. Wait max(access_token_ttl, id_token_ttl) = 1h for RETIRED-signed tokens to expire
5. ops calls DELETE /api/admin/signing-keys/{kid} on RETIRED row (manual confirmation)
```

## Related Code Files

### Create

- `auth-service/src/main/java/com/namnd/cinema/controller/oauth2/SigningKeyAdminController.java`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/SigningKeyRotationService.java` (interface + impl).
- `auth-service/src/main/java/com/namnd/cinema/config/oauth2/AuditEventListenerForAuthorizationServer.java` — Spring `ApplicationListener` for AS events bridging to `AuditEventPublisher`.
- `docs/sso-key-rotation-runbook.md`.
- `docs/sso-partner-integration-guide.md`.
- `docs/sso-jwt-rollback-runbook.md` (from Phase 05 notes).
- `k8s/auth-service-ingress.yaml` (if not exists) or modify existing to add annotations.
- `plans/260528-2240-sso-idp-partner-oauth2/oidc-conformance-config.json` — saved suite config + last results.

### Modify

- `auth-service/src/main/java/com/namnd/cinema/service/impl/SigningKeyServiceImpl.java` — add `rotate()` method.
- `auth-service/src/main/java/com/namnd/cinema/repository/SigningKeyRepository.java` — add `findByKid`, `deleteByKid`.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationConsentService.java` — emit audit events on save/remove.
- `auth-service/src/main/java/com/namnd/cinema/service/oauth2/JpaOAuth2AuthorizationService.java` — emit `oauth2.token.issued` audit event on save (when new token).
- `auth-service/src/main/resources/application.yml` (`prod` profile) — `server.servlet.session.cookie.secure=true`, `server.forward-headers-strategy=framework`, `requireSsl=true` enforced.
- `docs/system-architecture.md` — add IdP component + sequence diagram.
- `docs/api-documentation.md` — list new endpoints.

### Delete

- None.

## Implementation Steps

### Security review checklist (each item has a test + evidence)

1. **PKCE mandatory**: POST `/oauth2/token` w/o `code_verifier` -> 400. Integration test asserts.
2. **Redirect URI strict match**: Register `https://a.com/cb`; AS rejects `https://a.com/cb?x=1`, `https://a.com/cb/`, `https://A.COM/cb`. Test cases in `RedirectUriValidatorTest` + AS integration test.
3. **Client secret never logged**: Grep staging logs for last 24h:
   ```
   kubectl logs -n cinema deploy/auth-service --since=24h | grep -iE "client_secret|secret\s*[:=]" | grep -v "hash"
   ```
   Expected: 0 hits. Add assertion + checklist item.
4. **Refresh-token rotation + reuse detection**:
   - Use refresh token1 -> get token2. Reuse token1 -> 400 + entire chain revoked.
   - Verify `TokenSettings.reuseRefreshTokens(false)` set (Phase 02). Integration test asserts.
5. **JWKS `Cache-Control: public, max-age=3600`** present on `GET /oauth2/jwks`. (Phase 01.) Re-verify.
6. **State + nonce validation**: Submit auth-code w/ mismatched `state` -> Spring AS rejects (native). Test asserts.
7. **HTTPS-only token endpoint**: in `prod` profile add `http.requiresChannel(c -> c.anyRequest().requiresSecure())`. Local/dev profile leaves http allowed.
8. **CORS on AS endpoints**: configure `CorsConfigurationSource` allowing only origins matching registered redirect URIs (DB query at startup) — deny `*`. Integration test: cross-origin from unregistered origin -> CORS error.

### Audit wiring

9. **`AuditEventListenerForAuthorizationServer`** — listens to `AuthenticationSuccessEvent`, `OAuth2AuthorizationCodeRequestAuthenticationToken`, `OAuth2RefreshTokenAuthenticationToken`; publishes `AuditEvent` w/ event_type `oauth2.token.issued` to existing `AuditEventPublisher`.
10. **`JpaOAuth2AuthorizationConsentService.save`** -> publish `oauth2.consent.granted` (after-commit hook).
11. **Token revoke endpoint hook** — Spring AS exposes `OAuth2TokenRevocationAuthenticationProvider`; add custom `successHandler` publishing `oauth2.token.revoked`.

### Key rotation feature

12. **`SigningKeyRotationService.rotate()`**:
    ```java
    @Transactional public SigningKey rotate() {
      SigningKey active = repo.findByStatus(ACTIVE)
          .stream().findFirst().orElseThrow();
      active.setStatus(RETIRED);
      active.setRetiredAt(Instant.now());
      repo.save(active);
      SigningKey fresh = signingKeyService.generateAndPersistActive();
      auditPublisher.publish(new AuditEvent(..., "oauth2.signing_key.rotated", fresh.getKid(), ...));
      return fresh;
    }
    ```
13. **`SigningKeyAdminController`**:
    ```java
    @RestController @RequestMapping("/api/admin/signing-keys") @PreAuthorize("hasRole('ADMIN')")
    class SigningKeyAdminController {
      @PostMapping("/rotate") public SigningKeyResponse rotate() { return mapper.toResp(rotationService.rotate()); }
      @GetMapping public List<SigningKeyResponse> list() { ... }
      @DeleteMapping("/{kid}") public ResponseEntity<Void> delete(@PathVariable String kid) {
        // only allowed on RETIRED keys past grace
        ... return 204;
      }
    }
    ```
14. **Exercise rotation in staging once** — invoke endpoint; verify JWKS now exposes 2 keys; mint new token (RS256, new kid); verify resource service accepts (NimbusJwtDecoder cache-miss + refresh); after 1h delete RETIRED row.

### Rate limiting

15. **Modify `k8s/auth-service-ingress.yaml`** w/ annotations above. Apply to staging; load-test confirms 10rps cap.

### OIDC conformance suite

16. **Spin up conformance suite** locally or use [openid.net/certification/](https://openid.net/certification/) hosted runner. Profile: "OIDC Basic Certification (Authorization Code)".
17. **Config** w/ staging issuer + dev client credentials.
18. **Run suite**; capture report. Triage failures:
    - common fixes: missing claim in discovery doc; userinfo response shape; nonce echo in id_token; error response shape.
19. **Save passing config** to `plans/260528-2240-sso-idp-partner-oauth2/oidc-conformance-config.json` + report PDF.

### Documentation

20. **`docs/sso-key-rotation-runbook.md`** sections:
    - Pre-check (RETIRED window empty).
    - Step 1: `POST /api/admin/signing-keys/rotate`.
    - Step 2: monitor JWKS endpoint shows 2 keys.
    - Step 3: wait 1h (or `max(access_ttl, id_ttl)`).
    - Step 4: `DELETE /api/admin/signing-keys/{retired_kid}`.
    - Rollback: re-promote RETIRED to ACTIVE via DB script (documented + reviewed).
21. **`docs/sso-partner-integration-guide.md`** sections:
    - OIDC discovery URL: `https://{issuer}/.well-known/openid-configuration`.
    - Step 1: request client credentials from cinema admin (link to admin contact).
    - Step 2: configure partner app w/ `client_id`, `client_secret`, `redirect_uri`.
    - Step 3: auth-code+PKCE flow w/ curl sample (generate code_verifier + code_challenge S256).
    - Step 4: id_token verification via JWKS (sample for Node `jose` lib + Java `nimbus-jose-jwt`).
    - Step 5: token refresh via `grant_type=refresh_token`.
    - Sample decoded id_token payload.
    - Troubleshooting (invalid_redirect_uri, invalid_client, PKCE failures).
22. **`docs/sso-jwt-rollback-runbook.md`** — codify Phase 05 rollback steps.
23. **Update `docs/system-architecture.md`** — add section "Identity Provider (SSO)" w/ Mermaid sequence diagram for partner login + component diagram showing auth-service hosting Spring AS.
24. **Update `docs/api-documentation.md`** — table of all new endpoints (admin + AS + JWKS + consent).
25. **Update `docs/development-roadmap.md`** — mark SSO phase complete.
26. **Update `docs/project-changelog.md`** — new entry summarizing feature + breaking-change marker (shared lib v0.1.0).

### Production readiness checks

27. **Final smoke** against staging:
    - All 6 hardening items pass.
    - Conformance suite passes.
    - Rate limit triggers on >10rps to `/oauth2/token`.
    - Key rotation runbook executed end-to-end.
    - Partner sample app completes flow using only the guide.
28. **Production rollout** — Helm release w/ all env vars; sanity-test w/ one trusted partner.

## Todo List

### Security review

- [ ] PKCE absence -> 400 (test + evidence)
- [ ] Redirect URI strict-match cases pass tests
- [ ] Log scan: zero client_secret leaks in 24h staging logs
- [ ] Refresh-token reuse -> chain revoked (test)
- [ ] JWKS Cache-Control header verified
- [ ] Mismatched state -> rejected (test)
- [ ] `requiresSecure()` enforced in `prod` profile
- [ ] CORS allows only registered redirect URI origins

### Audit wiring

- [ ] `AuditEventListenerForAuthorizationServer` listens + publishes `oauth2.token.issued`
- [ ] Consent service emits `oauth2.consent.granted|denied`
- [ ] Revoke endpoint emits `oauth2.token.revoked`

### Key rotation

- [x] `SigningKeyServiceImpl.rotate()` implemented (Phase 01) + audit-annotated (Phase 06)
- [x] `SigningKeyAdminController` w/ rotate + list + delete
- [ ] Rotation exercised end-to-end in staging
- [ ] Resource services accept tokens signed by NEW kid

### Rate limiting

- [x] K8s ingress annotations applied
- [ ] Load test confirms 10rps cap on `/oauth2/token`

### OIDC conformance

- [ ] Conformance suite config saved
- [ ] Suite run passes (or non-conformance triaged + fixed)
- [ ] Report archived in plan dir

### Documentation

- [x] `docs/sso-key-rotation-runbook.md` authored
- [x] `docs/sso-partner-integration-guide.md` authored w/ curl samples
- [x] `docs/sso-jwt-rollback-runbook.md` authored (Phase 05)
- [x] `docs/system-architecture.md` updated w/ IdP section + sequence diagram
- [x] `docs/api-documentation.md` updated w/ new endpoints
- [x] `docs/project-roadmap.md` + `docs/project-changelog.md` updated

### Production

- [ ] Final staging smoke
- [ ] Production rollout
- [ ] Trusted partner pilot completes flow

## Success Criteria

- OIDC Basic Certification suite passes against staging IdP.
- Security review checklist 100% green w/ evidence captured per item.
- Key rotation runbook executed in staging without resource-service downtime.
- Partner integration guide enables a partner to complete flow end-to-end w/o asking questions.
- All audit events visible in audit-service Kafka stream.
- NGINX rate limit observable at >10rps load-test.
- All 6 docs updated; changelog mentions feature + breaking shared lib v0.1.0.

## Risk Assessment

| Risk | Severity | Mitigation |
|---|---|---|
| Conformance suite finds non-trivial non-conformance | MED | Allocate 3-5 day buffer; common issues fixable via TokenCustomizer + discovery overrides |
| Key rotation accidentally leaves zero ACTIVE keys | HIGH | Tx wraps rotate(); partial unique index from Phase 01 prevents 2 ACTIVE; integration test asserts always exactly 1 ACTIVE post-rotate |
| Rate limit too aggressive blocks legitimate traffic | MED | Tune via Helm values; start at 10rps + burst 20 per Ingress IP; adjust on partner-by-partner basis |
| Audit event volume balloons w/ token traffic | LOW | Token events sampled (1/10) if volume >1k/min; configurable |
| Partner doc drift after future code changes | MED | Add `docs:update` workflow as recurring quarterly task |

## Security Considerations

- Final RFC 9700 (OAuth 2.0 Security BCP, Jan 2025) compliance check.
- OWASP OAuth2 Cheat Sheet items: PKCE, state, nonce, redirect URI exact match, refresh rotation, client auth, JWKS cache, HTTPS-only — all evidenced above.
- Rate limiting partially mitigates credential stuffing / replay attacks at edge.
- Key rotation runbook tested = real defense, not paper defense.
- Audit trail covers every token issuance + every client lifecycle change -> forensic capability if partner key compromised.
- CORS denies `*` -> no cross-origin token theft via browser-based partner sites.
- Production profile enforces TLS at app layer (`requiresSecure()`) in addition to ingress.

## Next Steps

- Post-launch: schedule first quarterly key rotation via runbook.
- Monitor `jwt_verify_alg_total` panel; verify HS512 count remains 0.
- Onboard partners one at a time using `sso-partner-integration-guide.md`.
- Quarterly review: revisit YAGNI exclusions list — only add SAML/DCR/PAR/etc. if a concrete partner demands it.
