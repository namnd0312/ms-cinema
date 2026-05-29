# SSO/JWT Cutover Rollback Runbook (Phase 05)

Recovery procedure if RS256 token issuance breaks production after the HS512 → RS256 cutover.
The dual-mode validator (Phase 01) means **no resource service redeploy is required** for rollback.

## When to invoke

- Spike in 401/403 on `/api/**` endpoints across multiple resource services within 30 minutes of auth-service deploy.
- `jwt_verify_alg_total{alg="RS256"}` near zero AND user-visible login failures.
- Any error in auth-service logs mentioning `NimbusJwtEncoder`, `JWKSource`, or `signing_keys`.

## Step-by-step (target: under 5 minutes)

1. **Set the rollback env on auth-service:**
   ```bash
   kubectl set env deploy/auth-service TOKEN_SIGNING_ALGORITHM=HS512 -n ms-cinema
   ```
2. **Rolling restart auth-service pods:**
   ```bash
   kubectl rollout restart deploy/auth-service -n ms-cinema
   kubectl rollout status  deploy/auth-service -n ms-cinema --timeout=180s
   ```
3. **Verify:** new logins return HS512-signed tokens.
   ```bash
   TOKEN=$(curl -s -X POST https://auth.cinema.example/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"smoke@cinema.example","password":"…"}' | jq -r .accessToken)
   echo "$TOKEN" | cut -d. -f1 | base64 -d | jq .alg
   # Expect: "HS512"
   ```
4. **Verify resource services still accept HS512:**
   ```bash
   curl -sf -H "Authorization: Bearer $TOKEN" https://api.cinema.example/movies | head -c 80
   # Expect: 200 OK
   ```
5. **Investigate RS256 issue offline.** Common causes:
   - ACTIVE row missing in `signing_keys` (Phase 01 bootstrap failed).
   - KEK env var (`SIGNING_KEY_ENCRYPTION_PASSWORD`) changed → existing key won't decrypt.
   - `JWT_AUDIENCE` mismatch between auth-service issuance and resource-service verification.

## Re-attempting cutover

Once root cause fixed and validated in staging:

1. **Confirm green on staging:** `jwt_verify_alg_total{alg="HS512",service=~"...service"}=0` for ≥ 30 min after flip.
2. **Production flip:**
   ```bash
   kubectl set env deploy/auth-service TOKEN_SIGNING_ALGORITHM=RS256 -n ms-cinema
   kubectl rollout restart deploy/auth-service -n ms-cinema
   ```
3. **Watch:** `jwt_verify_alg_total{alg="HS512"}` decay toward zero within 30 min.

## Key facts

- HS512 secret (`JWT_SECRET`) **must remain provisioned** in K8s Secret store until the post-grace cleanup deploy (Phase 05 step 17+, expected 1 week after green RS256 in prod).
- Resource services do not need an env change or restart for rollback — `jwks-uri` stays populated; the dual-mode lib routes by JWS header alg.
- A change in `signing_keys.ACTIVE` (Phase 01 key rotation) does **not** require this runbook; verifiers refresh JWKS on `kid` miss within 60s.

## Authorship

- Owner: auth/identity team
- Last validated: 2026-05-29 (staging dry-run pending)
