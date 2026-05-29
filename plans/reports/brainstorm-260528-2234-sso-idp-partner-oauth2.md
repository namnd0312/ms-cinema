# Brainstorm — SSO Identity Provider for B2B Partner Apps

**Date:** 2026-05-28
**Author:** brainstorm session
**Status:** Decisions agreed, ready for `/plan`

---

## 1. Problem Statement

MS Cinema must let **external partner apps** authenticate end-users against cinema user accounts (cinema acts as **Identity Provider / IdP**).

**Locked decisions from interview:**

| Dimension | Choice | Reason |
|---|---|---|
| Direction | Provider/IdP (not Client) | Partners log in via cinema, not vice-versa |
| Audience | B2B partner apps (1-5 known) | Small, controlled, no public dev portal |
| Tech | Spring Authorization Server (build) | Stay on Java/Spring, own the code |
| Access model | OIDC only — authentication, no API scopes | KISS: id_token w/ email+name+sub, no delegated API access |
| JWT crypto | Migrate HS512 → RS256 + JWKS | Required for OIDC + asymmetric verification by partners |
| Timeline | 3-4 weeks | Real platform work, not a quick win |

---

## 2. Evaluated Approaches

### A. Spring Authorization Server inside `auth-service` ✅ CHOSEN
**Pros**
- Same JVM, same DB, same code style — no new service to operate
- Reuses existing `User`, `UserDetailsService`, `Role`, Google OAuth2 client login
- Official Spring project, actively maintained, OIDC-certified
- Existing JWT refresh + blacklist infra largely reusable

**Cons**
- Couples IdP lifecycle to auth-service deploys
- Auth-service surface grows (consent UI, client mgmt, JWKS)
- Requires breaking JWT crypto change → coordinated rollout across 5 resource services + frontend

### B. Standalone `sso-service` w/ Spring Authorization Server
**Pros**
- Separation of concerns: auth-service = user store, sso-service = IdP
- Independent deploys, independent scaling

**Cons**
- 9th microservice. Premature for 1-5 partners (YAGNI)
- Needs inter-service call from sso → auth for user lookup → latency + new contract
- Doubles ops surface

### C. Keycloak federated to auth-service
**Pros**
- Battle-tested, free admin console, built-in consent + client mgmt
- SAML + OIDC + social out of box

**Cons**
- New runtime (separate JVM, separate DB), new ops domain
- User Storage SPI plugin to delegate to auth-service is custom Java code anyway
- Heavy for 1-5 partners; learning curve eats the time-savings

### D. Managed (Auth0 / Cognito / Okta CIC)
**Pros**
- Zero code, fastest
**Cons**
- Per-MAU cost, vendor lock-in
- User migration out of auth-service (or sync) is non-trivial
- Data residency concerns for VN userbase

**Decision:** **Approach A** — Spring Authorization Server embedded in `auth-service`. Smallest blast radius for the scale, reuses existing user store, no new ops surface.

---

## 3. Recommended Architecture

### 3.1 High-level flow (Authorization Code + PKCE)

```
[Partner App]                                  [MS Cinema IdP]
    |                                                  |
    | 1. GET /oauth2/authorize?client_id=...&         |
    |    redirect_uri=...&scope=openid profile email  |
    |    &code_challenge=...&state=...                |
    | -----------------------------------------------> |
    |                                                  |
    |    2. Not logged in? Show /login page           |
    |    (existing email/password OR Google OAuth)    |
    |                                                  |
    |    3. Logged in? Show consent screen            |
    |    (or auto-approve for trusted partners)       |
    |                                                  |
    | 4. 302 redirect back: ?code=...&state=...        |
    | <----------------------------------------------- |
    |                                                  |
    | 5. POST /oauth2/token (code + code_verifier +   |
    |    client_id + client_secret)                   |
    | -----------------------------------------------> |
    |                                                  |
    | 6. { id_token (RS256), access_token,            |
    |      refresh_token, expires_in }                |
    | <----------------------------------------------- |
    |                                                  |
    | 7. Verify id_token via /.well-known/jwks.json   |
```

### 3.2 New endpoints exposed by auth-service

| Endpoint | Purpose |
|---|---|
| `GET /.well-known/openid-configuration` | OIDC discovery doc |
| `GET /oauth2/jwks` | Public keys (JWKS) for partners |
| `GET /oauth2/authorize` | Auth code request |
| `POST /oauth2/token` | Token exchange |
| `GET /userinfo` | OIDC UserInfo (email, name, sub) |
| `POST /oauth2/revoke` | Revoke access/refresh token |
| `POST /oauth2/introspect` | Token introspection (optional, internal) |
| `POST /connect/logout` | OIDC RP-initiated logout (optional) |

### 3.3 New entities

```
oauth2_registered_clients
  id, client_id (unique), client_secret_hash (BCrypt),
  client_name, redirect_uris (CSV/JSON),
  post_logout_redirect_uris, scopes (CSV: openid,profile,email),
  grant_types (authorization_code,refresh_token),
  require_pkce (true), token_ttl_seconds,
  refresh_ttl_seconds, auto_approve (bool for trusted partners),
  created_at, created_by

oauth2_authorization  (Spring AS manages — code, state, tokens)
oauth2_authorization_consent  (Spring AS manages)

signing_keys
  id, kid (unique), algorithm (RS256), public_key, private_key_encrypted,
  status (ACTIVE|RETIRED), created_at, retired_at
```

### 3.4 JWT migration strategy (HS512 → RS256)

Coordinated rollout to avoid breaking 5 resource services:

**Phase 0 — prep**
- Generate RSA-2048 keypair, persist in `signing_keys` (private key encrypted at rest using k8s Secret or env-derived KEK)
- Expose JWKS endpoint

**Phase 1 — dual-issuance**
- auth-service issues new tokens signed RS256 (kid in header)
- `jwt-auth-autoconfigure` shared lib upgraded to support BOTH:
  - if `alg=HS512` → verify with shared secret (legacy)
  - if `alg=RS256` → verify via JWKS (fetch + cache 1h)
- Roll out updated lib to all 5 resource services + auth-service. No behavior change yet — both still work.

**Phase 2 — cutover**
- auth-service stops issuing HS512. All new tokens RS256.
- Wait for old HS512 tokens to expire (max access TTL).

**Phase 3 — cleanup**
- Remove HS512 verification path from shared lib
- Remove `JWT_SECRET` env var
- Document key rotation runbook (rotate every 90d; keep old key in JWKS until tokens expire)

### 3.5 Partner onboarding (manual, fits 1-5 partners)

- Admin-only REST endpoint `POST /api/admin/oauth-clients` (RBAC: ROLE_ADMIN)
- Generates `client_id` (UUID), `client_secret` (random 64 chars, returned ONCE, stored as BCrypt hash)
- Admin sets redirect URIs (strict-match, no wildcards), partner name
- `auto_approve=true` for known partners → skip consent UI

**No** self-service developer portal, **no** dynamic client registration. YAGNI for 5 partners.

### 3.6 Consent UI

- Minimal Angular page on cinema-frontend: "Partner X wants access to: email, profile. [Allow] [Deny]"
- Skip entirely when `client.auto_approve=true`
- Persist decision in `oauth2_authorization_consent` so user not re-prompted

---

## 4. What we explicitly are NOT building (YAGNI)

- ❌ API scopes beyond `openid profile email` — partners only auth, no delegated API access
- ❌ Client credentials grant (no machine-to-machine yet)
- ❌ Device code flow (no smart-TV partners)
- ❌ SAML — only OIDC
- ❌ Dynamic Client Registration (RFC 7591)
- ❌ Pushed Authorization Requests (PAR)
- ❌ DPoP / mTLS client auth
- ❌ Multi-tenancy (single cinema brand)
- ❌ Public developer portal w/ API keys self-service
- ❌ Per-partner branding on login page

These can be added later **if** scale demands.

---

## 5. Risks & Mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| JWT crypto migration breaks resource services | HIGH | Phase 1 dual-mode in shared lib; coordinated deploy; canary in staging |
| Open-redirect via lax `redirect_uri` matching | HIGH | Strict exact-match, HTTPS only, no wildcards, validated at registration |
| Stolen client_secret abuse | HIGH | BCrypt at rest, never logged, rotation endpoint, IP allowlist optional |
| Auth code interception | MED | **PKCE mandatory** even for confidential clients (defense in depth) |
| Private signing key leak | HIGH | Encrypt at rest (k8s Secret KEK), restrict DB access, plan rotation |
| Consent screen phishing (cinema UI spoofed by partner) | MED | Always show partner name + redirect URI host on consent page |
| Refresh-token replay | MED | Rotation on every refresh, store hash, detect reuse → revoke chain |
| OIDC spec drift / non-conformance | MED | Run [OpenID Conformance Suite](https://openid.net/certification/) in staging before partner go-live |
| Auth-service becomes too big | LOW | Accept for now; revisit splitting to `sso-service` only if AS code > 30% of module |

---

## 6. Success Criteria

- [ ] Partner app can complete full auth-code+PKCE flow end-to-end against staging IdP
- [ ] OIDC discovery doc validates against [OpenID Conformance Suite](https://openid.net/certification/)
- [ ] All 5 existing resource services accept new RS256 tokens via JWKS (verified by integration tests)
- [ ] Zero downtime during HS512→RS256 cutover (dual-mode period proven in staging)
- [ ] Admin can register a new partner client + rotate secret via REST API
- [ ] id_token contains `sub`, `email`, `email_verified`, `name`, `iss`, `aud`, `exp`, `iat`
- [ ] Token revocation works (revoked refresh token cannot mint new access tokens)
- [ ] Security review: redirect-URI matching, PKCE enforcement, client-secret hashing all confirmed

---

## 7. Implementation Phases (high-level — for `/plan` to expand)

1. **Phase 1 — Crypto foundation:** RSA keypair + `signing_keys` table + JWKS endpoint + dual-mode verification in `jwt-auth-autoconfigure`
2. **Phase 2 — Spring Authorization Server core:** Add dependency, register `RegisteredClientRepository` (JPA-backed), wire to existing `UserDetailsService`, expose `/oauth2/authorize`, `/oauth2/token`, `/userinfo`, OIDC discovery
3. **Phase 3 — Partner client management:** Admin REST API + entity + BCrypt secret hashing + redirect-URI validation
4. **Phase 4 — Consent screen:** Angular consent page + auto-approve path + persistent consent record
5. **Phase 5 — JWT cutover:** Switch issuance to RS256, monitor, drop HS512 after grace period
6. **Phase 6 — Hardening + onboarding:** OIDC conformance suite run, security review, partner onboarding doc, key-rotation runbook

---

## 8. Dependencies

- `org.springframework.boot:spring-boot-starter-oauth2-authorization-server` (Spring Boot 3.4.x compatible)
- `com.nimbusds:nimbus-jose-jwt` (already transitive — used for RS256 + JWKS)
- DB migration: 4 new tables in auth-service `testdb`
- Angular routes + 1 new component on cinema-frontend (consent page)
- Update `jwt-auth-autoconfigure` shared lib → all 5 resource services must rebuild
- New env vars: `OAUTH_AS_ISSUER_URI`, `SIGNING_KEY_ENCRYPTION_PASSWORD`

---

## 9. Open Questions

1. **Issuer URI:** what hostname will partners see? (`https://auth.cinema.example/`?) Affects OIDC discovery + token `iss` claim — must be decided before keys/tokens are minted (changing later forces partner-side reconfig).
2. **Logout semantics:** RP-initiated logout only, or also back-channel logout to partner apps? (Back-channel is harder; recommend skipping for v1.)
3. **Token TTLs:** id_token + access_token TTL? (Suggest: 15min access, 1h id_token, 14d refresh w/ rotation. Confirm with security policy if any.)
4. **Audit logging:** should `audit-service` receive `oauth2.token.issued`, `oauth2.client.registered`, `oauth2.consent.granted` events? (Recommended — fits existing `@Auditable` pattern.)
5. **Rate limiting on `/oauth2/token`:** any expected partner abuse? (Likely needed once >1 partner. K8s NGINX Ingress rate limit per client_ip or per client_id?)
6. **Existing Google OAuth2 client login on auth-service:** should it continue to work as a login *method* within the AS flow? (Recommend yes — user can log into cinema AS via Google, then cinema mints id_token for partner.)

---

**Next step:** run `/plan` with this report as context to produce the phased implementation plan.
