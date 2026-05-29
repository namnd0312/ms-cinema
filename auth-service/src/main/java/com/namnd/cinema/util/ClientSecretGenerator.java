package com.namnd.cinema.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates high-entropy OAuth2 client_secrets.
 * 48 random bytes -> ~64-char base64url -> ~384 bits of entropy (well above OWASP minimum).
 * Returned plaintext is for one-shot transmission to the admin caller; storage is BCrypt-hashed.
 */
@Component
public class ClientSecretGenerator {

    private static final int SECRET_BYTES = 48;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        byte[] buf = new byte[SECRET_BYTES];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
