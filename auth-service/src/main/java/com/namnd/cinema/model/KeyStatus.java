package com.namnd.cinema.model;

/**
 * Lifecycle status of a {@link SigningKey}.
 * ACTIVE = used to sign newly-issued tokens (exactly one row per cluster).
 * RETIRED = no longer signs, but public key still published in JWKS so existing
 * tokens minted under it continue to verify until they expire.
 */
public enum KeyStatus {
    ACTIVE,
    RETIRED
}
