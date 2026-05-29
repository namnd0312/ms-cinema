---
title: "SSO Identity Provider (OAuth2/OIDC) for B2B Partners"
description: "Embed Spring Authorization Server in auth-service; OIDC-only for 1-5 partners; migrate HS512->RS256+JWKS across 5 resource services."
status: pending
priority: P1
effort: 3-4w
branch: k8s
tags: [sso, oauth2, oidc, security, jwt-migration, identity-provider]
created: 2026-05-28
---

# SSO Identity Provider (OAuth2/OIDC) for B2B Partners

## Overview

Embed Spring Authorization Server 1.3.x inside existing `auth-service` to turn MS Cinema into an OIDC Identity Provider for 1-5 known partner apps. Reuses existing `User` store, `UserDetailsService`, Google OAuth2 login. Migrates JWT signing from HS512 (shared secret) to RS256 (RSA + JWKS) via dual-mode validator in `jwt-auth-autoconfigure` shared lib, coordinated across 5 resource services with zero-downtime cutover. YAGNI: OIDC-only (openid+profile+email), no SAML, no DCR, no PAR, no public dev portal.

**Brainstorm:** [plans/reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md](../reports/brainstorm-260528-2234-sso-idp-partner-oauth2.md)

## Phases

| # | Name | Priority | Depends-on | Status |
|---|---|---|---|---|
| 01 | Crypto foundation (RSA + JWKS + dual-mode lib) | P1 | - | pending |
| 02 | Spring Authorization Server core | P1 | 01 | pending |
| 03 | Partner client management admin API | P1 | 02 | pending |
| 04 | Consent screen UI (Angular) | P1 | 02 | pending |
| 05 | JWT cutover HS512 -> RS256 | P1 | 01, 02 | pending |
| 06 | Hardening + partner onboarding docs | P1 | 03, 04, 05 | code+docs done; ops deferred |

## Dependency Graph

```
01 (crypto + dual-mode lib)
  -> 02 (Spring AS core)
       -> 03 (admin API)  -+
       -> 04 (consent UI) -+
                            -> 05 (JWT cutover)
                                 -> 06 (hardening + docs)
```
Phases 03 + 04 run in parallel after 02 completes.

## Plan Validation Questions — MUST-RESOLVE before Phase 02 starts

1. **Issuer URI:** what hostname will partners see? (`https://auth.cinema.example/`?) Affects OIDC discovery + token `iss` claim — must be decided before keys/tokens are minted (changing later forces partner-side reconfig).
2. **Logout semantics:** RP-initiated logout only, or also back-channel logout to partner apps? (Back-channel is harder; recommend skipping for v1.)
3. **Token TTLs:** id_token + access_token TTL? (Suggest: 15min access, 1h id_token, 14d refresh w/ rotation. Confirm with security policy if any.)
4. **Audit logging:** should `audit-service` receive `oauth2.token.issued`, `oauth2.client.registered`, `oauth2.consent.granted` events? (Recommended — fits existing `@Auditable` pattern.)
5. **Rate limiting on `/oauth2/token`:** any expected partner abuse? (Likely needed once >1 partner. K8s NGINX Ingress rate limit per client_ip or per client_id?)
6. **Existing Google OAuth2 client login on auth-service:** should it continue to work as a login *method* within the AS flow? (Recommend yes — user can log into cinema AS via Google, then cinema mints id_token for partner.)

## Key Risks

- **JWT crypto migration breaks 5 resource services** — mitigated by dual-mode lib (Phase 01) + grace period before cutover (Phase 05).
- **Private signing key leak** — encrypted at rest w/ AES-GCM + PBKDF2-derived KEK from env (Phase 01).
- **Open-redirect via lax `redirect_uri` matching** — strict exact match, HTTPS only, no wildcards, max 5 URIs, validated at registration (Phase 03).
- **Consent screen phishing** — partner display name pulled from DB only (never request params); redirect host shown prominently (Phase 04).

## Phase Files

- [phase-01-crypto-foundation-rsa-jwks-dual-mode-validator.md](./phase-01-crypto-foundation-rsa-jwks-dual-mode-validator.md)
- [phase-02-spring-authorization-server-core-oidc-endpoints-and-token-customizer.md](./phase-02-spring-authorization-server-core-oidc-endpoints-and-token-customizer.md)
- [phase-03-partner-client-management-admin-rest-api-with-bcrypt-secret-and-redirect-validation.md](./phase-03-partner-client-management-admin-rest-api-with-bcrypt-secret-and-redirect-validation.md)
- [phase-04-consent-screen-angular-material-with-auto-approve-and-anti-spoofing.md](./phase-04-consent-screen-angular-material-with-auto-approve-and-anti-spoofing.md)
- [phase-05-jwt-cutover-hs512-to-rs256-with-dual-mode-grace-period-and-rollback.md](./phase-05-jwt-cutover-hs512-to-rs256-with-dual-mode-grace-period-and-rollback.md)
- [phase-06-hardening-oidc-conformance-security-review-and-partner-onboarding-docs.md](./phase-06-hardening-oidc-conformance-security-review-and-partner-onboarding-docs.md)
