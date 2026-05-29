package com.namnd.jwt.autoconfigure;

import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dual-mode JWT validator: peeks the JWS `alg` header to route legacy HS512 tokens
 * to the existing {@link JwtTokenValidator} (no behavior change) and RS256 tokens
 * to a JWKS-backed {@link NimbusJwtDecoder}.
 *
 * Bridges Nimbus {@link Jwt} back to jjwt {@link Claims} so call-sites in
 * {@link JwtAuthenticationFilter} stay unchanged.
 *
 * Safety: when {@code dualModeEnabled=false} or {@code rs256Decoder=null} (no jwks-uri
 * configured), the validator falls through to legacy HS512 — preserving the v0.0.1 contract.
 */
public class JwtTokenValidatorDualMode extends JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidatorDualMode.class);

    private final JwtTokenValidator legacyHs512;
    private final NimbusJwtDecoder rs256Decoder;
    private final boolean dualModeEnabled;

    public JwtTokenValidatorDualMode(JwtTokenValidator legacyHs512,
                                     NimbusJwtDecoder rs256Decoder,
                                     boolean dualModeEnabled,
                                     String legacySecret) {
        // Parent ctor still needs the HS512 secret for direct calls; defensively wired.
        super(legacySecret);
        this.legacyHs512 = legacyHs512;
        this.rs256Decoder = rs256Decoder;
        this.dualModeEnabled = dualModeEnabled;
    }

    @Override
    public Claims parseClaims(String token) {
        try {
            String alg = SignedJWT.parse(token).getHeader().getAlgorithm().getName();
            if ("RS256".equals(alg) && dualModeEnabled && rs256Decoder != null) {
                Jwt decoded = rs256Decoder.decode(token);
                return toClaims(decoded);
            }
        } catch (Exception e) {
            // RS256 parse/decode failed -> fall through to legacy attempt (one of them is the truth).
            log.debug("RS256 path failed, attempting HS512 fallback: {}", e.getMessage());
        }
        return legacyHs512.parseClaims(token);
    }

    /** Bridge Nimbus claims to the jjwt {@link Claims} contract used by downstream filters. */
    private Claims toClaims(Jwt jwt) {
        return new BridgedClaims(new HashMap<>(jwt.getClaims()), jwt);
    }

    /**
     * Minimal {@link Claims} facade over a Nimbus-decoded JWT. Only the fields
     * consumed by {@link JwtAuthenticationFilter} (sub/userId/roles) need to work.
     * jjwt 0.12.x Claims is read-only — extends Map + Identifiable.
     */
    private static final class BridgedClaims extends HashMap<String, Object> implements Claims {
        private final Jwt jwt;

        BridgedClaims(Map<String, Object> m, Jwt jwt) { super(m); this.jwt = jwt; }

        @Override public String getIssuer() {
            return jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        }
        @Override public String getSubject() { return jwt.getSubject(); }
        @Override public java.util.Set<String> getAudience() {
            List<String> aud = jwt.getAudience();
            return aud == null ? Collections.emptySet() : new java.util.LinkedHashSet<>(aud);
        }
        @Override public java.util.Date getExpiration() {
            return jwt.getExpiresAt() == null ? null : java.util.Date.from(jwt.getExpiresAt());
        }
        @Override public java.util.Date getNotBefore() {
            return jwt.getNotBefore() == null ? null : java.util.Date.from(jwt.getNotBefore());
        }
        @Override public java.util.Date getIssuedAt() {
            return jwt.getIssuedAt() == null ? null : java.util.Date.from(jwt.getIssuedAt());
        }
        @Override public String getId() { return jwt.getId(); }

        @SuppressWarnings("unchecked")
        @Override public <T> T get(String name, Class<T> requiredType) {
            Object v = get(name);
            if (v == null) return null;
            if (requiredType.isInstance(v)) return (T) v;
            if (requiredType == Long.class && v instanceof Number n) return (T) Long.valueOf(n.longValue());
            if (requiredType == Integer.class && v instanceof Number n) return (T) Integer.valueOf(n.intValue());
            if (requiredType == String.class) return (T) v.toString();
            return (T) v;
        }
    }
}
