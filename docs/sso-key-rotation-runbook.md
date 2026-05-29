# SSO Signing Key Rotation Runbook (Phase 06)

Operational procedure for rotating the RSA signing key used by the Spring Authorization Server. Designed for execution by a junior on-call w/ no tribal knowledge.

Target duration end-to-end: **< 10 min active + 1h grace wait**.

## Why rotate

- Quarterly hygiene (default cadence).
- Suspected private-key compromise (treat as emergency; see "Emergency rotation" below).
- KEK password rotation (re-encryption pre-requisite).

## Pre-flight checks

1. Confirm exactly **one** `ACTIVE` signing key + **zero** `RETIRED` keys in `signing_keys` table:
   ```sql
   SELECT kid, status, created_at, retired_at FROM signing_keys ORDER BY created_at DESC;
   ```
   Expect: 1 row w/ `status=ACTIVE`. Abort if multiple RETIRED rows already exist — clean up first.

2. Confirm JWKS endpoint serves the current ACTIVE key:
   ```bash
   curl -s https://auth.cinema.example/oauth2/jwks | jq '.keys | length'
   # Expect: 1
   ```

3. Confirm operator has ADMIN role bearer token ready:
   ```bash
   TOKEN=$(curl -s -X POST https://auth.cinema.example/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@cinema.example","password":"…"}' | jq -r .accessToken)
   ```

## Procedure

### Step 1: Trigger rotation

```bash
curl -s -X POST https://auth.cinema.example/api/admin/signing-keys/rotate \
  -H "Authorization: Bearer $TOKEN" | jq
```

Expected response (HTTP 200):

```json
{
  "kid": "k-20260529-01",
  "algorithm": "RS256",
  "status": "ACTIVE",
  "createdAt": "2026-05-29T09:30:11",
  "retiredAt": null
}
```

### Step 2: Confirm JWKS now publishes 2 keys

```bash
curl -s https://auth.cinema.example/oauth2/jwks | jq '.keys[] | {kid, alg}'
# Expect: 2 entries — new ACTIVE kid + previous (now RETIRED) kid
```

### Step 3: Mint a token + verify new kid

```bash
# Using a registered partner client (replace placeholders):
CODE=…  # complete auth-code+PKCE flow first
RES=$(curl -s -X POST https://auth.cinema.example/oauth2/token \
  -u "$CLIENT_ID:$CLIENT_SECRET" \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=$REDIRECT_URI&code_verifier=$VERIFIER")

ID_TOKEN=$(echo "$RES" | jq -r .id_token)
echo "$ID_TOKEN" | cut -d. -f1 | base64 -d | jq .kid
# Expect: matches the new ACTIVE kid from Step 1
```

### Step 4: Wait the grace window

Wait `max(access_token_ttl, id_token_ttl)` — default **1 hour** — so every live token signed by the RETIRED key has expired. Confirm via metrics:

```bash
# Grafana panel jwt_verify_alg_total{kid="<retired-kid>"} should plateau then drop to 0.
```

### Step 5: Hard-delete the RETIRED key

```bash
RETIRED_KID=…  # from Step 2
curl -s -X DELETE -o /dev/null -w "%{http_code}\n" \
  https://auth.cinema.example/api/admin/signing-keys/$RETIRED_KID \
  -H "Authorization: Bearer $TOKEN"
# Expect: 204
```

### Step 6: Post-checks

```bash
# JWKS back to 1 key:
curl -s https://auth.cinema.example/oauth2/jwks | jq '.keys | length'   # 1

# DB consistent:
psql -c "SELECT kid, status FROM signing_keys;"    # 1 row, ACTIVE
```

## Audit trail

Every step above emits an audit event consumed by `audit-service`:

| Step | event_type           | entityType  | action  |
| ---- | -------------------- | ----------- | ------- |
| 1    | `signing_key.rotate` | SigningKey  | UPDATE  |
| 5    | `signing_key.delete` | SigningKey  | DELETE  |

Verify in `audit_logs` table after the procedure.

## Rollback

If Step 3 reveals resource services are rejecting the new-kid tokens:

1. Stop accepting traffic on `/oauth2/token` (scale auth-service to 0 or block at ingress).
2. Manually promote the RETIRED key back to ACTIVE:
   ```sql
   BEGIN;
   UPDATE signing_keys SET status='RETIRED', retired_at=NOW() WHERE status='ACTIVE';
   UPDATE signing_keys SET status='ACTIVE',  retired_at=NULL  WHERE kid='<rolled-back-kid>';
   COMMIT;
   ```
3. Restart auth-service: `kubectl rollout restart deploy/auth-service -n ms-cinema`.
4. Open an incident; investigate why resource services failed to refresh JWKS.

## Emergency rotation (key compromise)

Skip the grace window. Immediately after Step 1:

1. Drop traffic to old kid via DB:
   ```sql
   DELETE FROM signing_keys WHERE status='RETIRED';
   ```
2. Force resource services to refresh JWKS by restarting them, or wait the configured cache TTL (default 1h — set lower if compromise is suspected often).
3. All tokens signed by the compromised key become unverifiable; users re-login.
4. File a postmortem.

## Schedule

- **Quarterly** as a calendar reminder (next: 2026-08-29).
- Owned by the on-call SRE rotation; auth-service team consulted for incidents.
