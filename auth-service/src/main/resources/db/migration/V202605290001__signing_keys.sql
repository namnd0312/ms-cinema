-- Signing keys for OAuth2/OIDC token issuance (Phase 01 SSO IdP).
-- Stores RSA keypairs; private key is AES-GCM encrypted with PBKDF2-derived KEK.
-- ACTIVE = currently signs new tokens; RETIRED = published in JWKS for verifier grace period.
CREATE TABLE IF NOT EXISTS signing_keys (
    id                      BIGSERIAL PRIMARY KEY,
    kid                     VARCHAR(64)  NOT NULL UNIQUE,
    algorithm               VARCHAR(20)  NOT NULL,
    public_key              TEXT         NOT NULL,
    private_key_encrypted   TEXT         NOT NULL,
    status                  VARCHAR(16)  NOT NULL,
    created_at              TIMESTAMP    NOT NULL,
    retired_at              TIMESTAMP    NULL,
    CONSTRAINT signing_keys_status_chk CHECK (status IN ('ACTIVE','RETIRED'))
);

-- Enforce exactly one ACTIVE row across the cluster (Spring AS NimbusJwtEncoder
-- picks first ACTIVE RS256 key; races on pod startup are rejected at DB level).
CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_signing_key
    ON signing_keys (status)
    WHERE status = 'ACTIVE';
