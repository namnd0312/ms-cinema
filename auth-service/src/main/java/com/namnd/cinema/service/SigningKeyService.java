package com.namnd.cinema.service;

import com.namnd.cinema.model.SigningKey;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;
import java.util.Optional;

/**
 * RSA signing-key lifecycle service.
 * Phase 01 surface: bootstrap, JWKS publication, on-demand private key decryption
 * for downstream {@code NimbusJwtEncoder} (Phase 02).
 */
public interface SigningKeyService {

    /** Currently ACTIVE key (used to sign new tokens). Empty before first bootstrap. */
    Optional<SigningKey> findActive();

    /** ACTIVE + RETIRED — both publish public material in /oauth2/jwks. */
    List<SigningKey> findActiveAndRetired();

    /** Generate fresh RSA-2048, encrypt private key, persist as ACTIVE. */
    SigningKey generateAndPersistActive();

    /** Decode PEM public key for JWK serialization. */
    PublicKey loadPublicKey(SigningKey key);

    /** Decrypt + decode private key. Used by signing path; never logged. */
    PrivateKey loadPrivateKey(SigningKey key);

    /**
     * Rotate: mark current ACTIVE -> RETIRED, generate fresh ACTIVE key.
     * Transactional — partial unique index from Phase 01 guarantees exactly one ACTIVE post-commit.
     * RETIRED key stays in JWKS so existing tokens minted under it keep verifying for their grace window.
     */
    SigningKey rotate();

    /** Hard-delete a RETIRED key. Caller MUST ensure no live tokens reference its kid. */
    void deleteRetired(String kid);
}
