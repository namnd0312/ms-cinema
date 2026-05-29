package com.namnd.cinema.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * RSA signing keypair for OAuth2/OIDC token issuance.
 * Public key serialized to JWK and exposed via /oauth2/jwks for verifiers.
 * Private key is AES-GCM encrypted at rest (decrypted on demand to sign).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "privateKeyEncrypted")
@Entity
@Table(name = "signing_keys")
public class SigningKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWK Key ID — must be unique, embedded in JWT header for verifier key lookup. */
    @Column(nullable = false, unique = true, length = 64)
    private String kid;

    /** JWS algorithm — fixed to "RS256" today; future EdDSA possible. */
    @Column(nullable = false, length = 20)
    private String algorithm;

    /** PEM-encoded public key (X.509 SubjectPublicKeyInfo). */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /**
     * Base64 of {salt(16) || iv(12) || ciphertext || gcmTag(16)}.
     * Plaintext = PEM-encoded PKCS#8 private key bytes.
     * NEVER log or serialize; @ToString excludes this field.
     */
    @Column(name = "private_key_encrypted", nullable = false, columnDefinition = "TEXT")
    private String privateKeyEncrypted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KeyStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;
}
