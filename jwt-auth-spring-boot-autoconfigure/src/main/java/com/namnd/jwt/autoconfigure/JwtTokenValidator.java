package com.namnd.jwt.autoconfigure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Collections;
import java.util.List;

/**
 * Validates JWT tokens and extracts claims (email, userId, roles).
 * Claims-only validation -- no database or blacklist lookup.
 * Used by downstream microservices that share the auth-service secret.
 */
public class JwtTokenValidator {

    private final SecretKey signingKey;

    public JwtTokenValidator(String base64Secret) {
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Parse and validate token signature + expiry.
     * Returns null if token is invalid or expired (caller skips auth setup).
     */
    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        return roles != null ? roles : Collections.emptyList();
    }
}
