# MS Cinema SSO — Partner Integration Guide

OIDC integration guide for B2B partners. Get from "I have nothing" to "my users sign in via cinema" in one sitting. Copy-paste samples included.

## TL;DR

- **Protocol:** OAuth 2.1 + OpenID Connect 1.0 (Authorization Code + PKCE).
- **Issuer:** `https://auth.cinema.example/`
- **Discovery:** `https://auth.cinema.example/.well-known/openid-configuration`
- **JWKS:** `https://auth.cinema.example/oauth2/jwks`
- **Scopes:** `openid profile email` (no others granted today).
- **Token format:** RS256 JWT. Validate via JWKS.

## Step 1 — Request credentials

Email the MS Cinema admin team (`auth-admin@cinema.example`) with:

- Partner display name (shown verbatim on the user consent screen).
- Redirect URI(s) — full HTTPS URLs, exact match enforced, max 5 per client.
- Expected scopes (default: `openid profile email`).
- Estimated daily token rate (default ingress limit: 10 rps per client IP, burst 20).

You will receive (once, in encrypted email):

```text
client_id     = partner-foobar
client_secret = <high-entropy random>     # SHOWN ONCE — store immediately
redirect_uris = https://app.foobar.com/auth/cb
```

## Step 2 — Configure partner app

Pick the OIDC library that fits your stack. Configure with the values above.

### Node.js (`openid-client`)

```js
import { Issuer } from 'openid-client';

const cinema = await Issuer.discover('https://auth.cinema.example/');
const client = new cinema.Client({
  client_id: process.env.CINEMA_CLIENT_ID,
  client_secret: process.env.CINEMA_CLIENT_SECRET,
  redirect_uris: ['https://app.foobar.com/auth/cb'],
  response_types: ['code'],
  token_endpoint_auth_method: 'client_secret_basic',
});
```

### Java (`nimbus-jose-jwt` + Spring Security 6 OIDC client)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          cinema:
            client-id: ${CINEMA_CLIENT_ID}
            client-secret: ${CINEMA_CLIENT_SECRET}
            redirect-uri: https://app.foobar.com/auth/cb
            scope: openid,profile,email
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_basic
        provider:
          cinema:
            issuer-uri: https://auth.cinema.example/
```

## Step 3 — Authorization Code + PKCE (raw curl)

PKCE is **mandatory**. Token endpoint rejects requests without `code_verifier`.

```bash
# 1. Generate verifier + S256 challenge.
VERIFIER=$(openssl rand -base64 64 | tr -d '=+/\n' | cut -c1-128)
CHALLENGE=$(echo -n "$VERIFIER" | openssl dgst -sha256 -binary | openssl base64 | tr -d '=' | tr '/+' '_-')

# 2. Build the /authorize URL. State is your own anti-CSRF nonce.
STATE=$(uuidgen)
NONCE=$(uuidgen)
open "https://auth.cinema.example/oauth2/authorize?\
response_type=code\
&client_id=$CINEMA_CLIENT_ID\
&redirect_uri=https%3A%2F%2Fapp.foobar.com%2Fauth%2Fcb\
&scope=openid%20profile%20email\
&state=$STATE\
&nonce=$NONCE\
&code_challenge=$CHALLENGE\
&code_challenge_method=S256"

# 3. User authenticates + grants consent. Browser is redirected to:
#    https://app.foobar.com/auth/cb?code=AUTH_CODE&state=$STATE
#    Verify state matches your generated value before continuing.

# 4. Exchange code for tokens.
curl -X POST https://auth.cinema.example/oauth2/token \
  -u "$CINEMA_CLIENT_ID:$CINEMA_CLIENT_SECRET" \
  -d "grant_type=authorization_code" \
  -d "code=$AUTH_CODE" \
  -d "redirect_uri=https://app.foobar.com/auth/cb" \
  -d "code_verifier=$VERIFIER"
```

Response:

```json
{
  "access_token": "eyJraWQ...",
  "refresh_token": "eyJraWQ...",
  "id_token": "eyJraWQ...",
  "token_type": "Bearer",
  "expires_in": 900,
  "scope": "openid profile email"
}
```

## Step 4 — Verify the id_token

Always validate the signature against JWKS, the issuer, and the audience (`client_id`).

### Node.js (`jose`)

```js
import { jwtVerify, createRemoteJWKSet } from 'jose';

const JWKS = createRemoteJWKSet(new URL('https://auth.cinema.example/oauth2/jwks'));

const { payload } = await jwtVerify(idToken, JWKS, {
  issuer: 'https://auth.cinema.example/',
  audience: process.env.CINEMA_CLIENT_ID,
});
// payload.sub  -> stable user id at cinema
// payload.email, payload.name -> profile claims (scope-gated)
```

### Java (`nimbus-jose-jwt`)

```java
JWKSource<SecurityContext> keySource = JWKSourceBuilder
    .create(new URL("https://auth.cinema.example/oauth2/jwks"))
    .build();
JWSKeySelector<SecurityContext> sel = new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keySource);
ConfigurableJWTProcessor<SecurityContext> proc = new DefaultJWTProcessor<>();
proc.setJWSKeySelector(sel);
JWTClaimsSet claims = proc.process(idToken, null);
// validate issuer + audience explicitly
require(claims.getIssuer().equals("https://auth.cinema.example/"));
require(claims.getAudience().contains(System.getenv("CINEMA_CLIENT_ID")));
```

### Sample decoded id_token payload

```json
{
  "iss": "https://auth.cinema.example/",
  "sub": "11042",
  "aud": "partner-foobar",
  "exp": 1748505000,
  "iat": 1748501400,
  "nonce": "1e9a14b0-...",
  "email": "alice@foobar.com",
  "email_verified": true,
  "name": "Alice Nguyen"
}
```

## Step 5 — Refresh tokens

Refresh tokens **rotate on every use**. Reusing a consumed refresh token revokes the entire chain (theft detection).

```bash
curl -X POST https://auth.cinema.example/oauth2/token \
  -u "$CINEMA_CLIENT_ID:$CINEMA_CLIENT_SECRET" \
  -d "grant_type=refresh_token" \
  -d "refresh_token=$CURRENT_REFRESH"
```

Always store the **newest** refresh token and discard the previous one immediately.

## Step 6 — Logout (RP-initiated, optional)

```text
https://auth.cinema.example/connect/logout?\
id_token_hint=$ID_TOKEN&post_logout_redirect_uri=https%3A%2F%2Fapp.foobar.com%2F
```

Back-channel logout is **not supported** in v1.

## Token / scope reference

| Scope     | Claims released                  |
| --------- | -------------------------------- |
| `openid`  | `sub`                            |
| `profile` | `name`                           |
| `email`   | `email`, `email_verified`        |

| Token         | TTL (default) | Notes                                  |
| ------------- | ------------- | -------------------------------------- |
| `access_token`  | 15 min       | RS256 JWT, validate via JWKS           |
| `id_token`      | 1 hour       | RS256 JWT, validate aud + nonce        |
| `refresh_token` | 14 days      | Opaque-looking JWT, rotates each use   |

## Troubleshooting

| Error                                                   | Likely cause                                                                  | Fix                                                                |
| ------------------------------------------------------- | ----------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| `invalid_redirect_uri` (400 on /authorize)              | URI sent does not exactly match registered value (case, trailing slash, port) | Re-check whitelist; URIs are exact match, case-sensitive           |
| `invalid_client` (401 on /token)                        | Wrong secret OR wrong client_id OR client disabled                            | Verify credentials; rotate via admin if leak suspected             |
| `invalid_grant` (400 on /token, "PKCE")                 | `code_verifier` missing or does not derive to `code_challenge`                | Re-derive S256 challenge from the SAME verifier used at /authorize |
| `invalid_grant` (400, refresh)                          | Refresh token already consumed → chain revoked                                | User must re-authenticate from scratch                             |
| `429 Too Many Requests`                                 | NGINX ingress rate limit (default 10 rps / IP)                                | Back off; if persistent, ask admin to raise the limit per-client   |
| `id_token` signature fails                              | Cached JWKS stale (post-rotation)                                             | Force JWKS refresh in your client; honour `Cache-Control: max-age` |

## Support

- Issues: open in the partner portal or email `auth-admin@cinema.example`.
- Status: https://status.cinema.example
- Audit trail: cinema's audit-service captures every token issued against your client_id and every consent grant/revoke; ask admin if you need a forensic export.
