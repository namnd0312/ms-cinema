package com.namnd.cinema.service;

import com.namnd.cinema.model.UserPrinciple;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Phase 05 cutover: issuance switched from HS512 (jjwt) -> RS256 (Spring AS JwtEncoder).
 *
 * Algorithm flag {@code namnd.app.tokenSigningAlgorithm} (default RS256) controls issuance;
 * emergency rollback is `TOKEN_SIGNING_ALGORITHM=HS512` env override + auth-service restart.
 * Resource services already accept both algs via the dual-mode shared lib (Phase 01).
 *
 * Verification (validate / getX methods) auto-routes by JWS `alg` header — same dispatcher
 * shape as the shared {@code JwtTokenValidatorDualMode} — so both algs work during grace.
 */
@Component
public class JwtService {

    private static final Logger logger = LoggerFactory.getLogger(JwtService.class);

    @Value("${namnd.app.jwtSecret}")
    private String SECRET_KEY;

    @Value("${namnd.app.jwtExpiration}")
    private long EXPIRE_TIME;

    @Value("${namnd.app.tokenSigningAlgorithm:RS256}")
    private String signingAlgorithm;

    @Value("${namnd.app.oauth2IssuerUri:http://localhost:8081}")
    private String issuerUri;

    @Value("${namnd.app.jwtAudience:ms-cinema-internal}")
    private String audience;

    /** Spring AS-provided encoder (Phase 02). Lazy/autowired so HS512 rollback boot stays clean
     *  even if Spring AS context is partially broken. */
    @Autowired(required = false)
    private JwtEncoder rsJwtEncoder;

    /** Spring AS-provided decoder (Phase 02). Used for RS256 verification of locally-issued tokens. */
    @Autowired(required = false)
    private JwtDecoder rsJwtDecoder;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateTokenLogin(Authentication authentication) {
        UserPrinciple principal = (UserPrinciple) authentication.getPrincipal();
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList();
        return generateTokenFromEmail(principal.getUsername(), principal.getId(), roles);
    }

    /** Legacy overload — generates token without roles/userId (kept for backward compat). */
    public String generateTokenFromEmail(String email) {
        return generateTokenFromEmail(email, null, List.of());
    }

    /** Issues an access token w/ roles + userId. RS256 by default; HS512 if flag set. */
    public String generateTokenFromEmail(String email, Long userId, List<String> roles) {
        if ("HS512".equalsIgnoreCase(signingAlgorithm) || rsJwtEncoder == null) {
            return issueHs512(email, userId, roles);
        }
        return issueRs256(email, userId, roles);
    }

    private String issueHs512(String email, Long userId, List<String> roles) {
        var b = Jwts.builder()
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(getSigningKey());
        if (userId != null) b.claim("userId", userId);
        if (roles != null && !roles.isEmpty()) b.claim("roles", roles);
        return b.compact();
    }

    private String issueRs256(String email, Long userId, List<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuerUri)
                .audience(List.of(audience))
                .subject(email)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusMillis(EXPIRE_TIME));
        if (userId != null) claims.claim("userId", userId);
        if (roles != null && !roles.isEmpty()) claims.claim("roles", roles);
        // kid populated by Spring AS from the JWKSource (first ACTIVE RS256 key).
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return rsJwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            if (isRs256(authToken) && rsJwtDecoder != null) {
                rsJwtDecoder.decode(authToken);
                return true;
            }
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return true;
        } catch (SecurityException e) {
            logger.error("Invalid JWT signature -> Message: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token -> Message: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Expired JWT token -> Message: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Unsupported JWT token -> Message: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT claims string is empty -> Message: {}", e.getMessage());
        } catch (org.springframework.security.oauth2.jwt.JwtException e) {
            logger.error("RS256 JWT validation failed -> Message: {}", e.getMessage());
        }
        return false;
    }

    public String getEmailFromJwtToken(String token) {
        if (isRs256(token) && rsJwtDecoder != null) {
            return rsJwtDecoder.decode(token).getSubject();
        }
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public String getJtiFromToken(String token) {
        if (isRs256(token) && rsJwtDecoder != null) {
            return rsJwtDecoder.decode(token).getId();
        }
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().getId();
    }

    public Date getExpirationFromToken(String token) {
        if (isRs256(token) && rsJwtDecoder != null) {
            Instant exp = rsJwtDecoder.decode(token).getExpiresAt();
            return exp == null ? null : Date.from(exp);
        }
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().getExpiration();
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        if (isRs256(token) && rsJwtDecoder != null) {
            Object roles = rsJwtDecoder.decode(token).getClaim("roles");
            return roles instanceof List ? (List<String>) roles : List.of();
        }
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get("roles", List.class);
    }

    public Long getUserIdFromToken(String token) {
        if (isRs256(token) && rsJwtDecoder != null) {
            Object uid = rsJwtDecoder.decode(token).getClaim("userId");
            if (uid instanceof Number n) return n.longValue();
            return null;
        }
        return Jwts.parser().verifyWith(getSigningKey()).build()
                .parseSignedClaims(token).getPayload().get("userId", Long.class);
    }

    /** Peek JWS header without verifying signature — cheap routing decision (O(1) parse). */
    private boolean isRs256(String token) {
        try {
            return "RS256".equals(SignedJWT.parse(token).getHeader().getAlgorithm().getName());
        } catch (Exception e) {
            return false;
        }
    }
}
