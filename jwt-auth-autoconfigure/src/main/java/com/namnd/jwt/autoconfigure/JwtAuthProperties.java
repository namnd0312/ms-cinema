package com.namnd.jwt.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT auth starter.
 * Bound to prefix "jwt.auth" in consumer application properties.
 * No Lombok -- keeps starter dependency-free for consumers.
 *
 * Dual-mode (HS512 legacy + RS256 JWKS-backed) lives on this same prefix:
 *   jwt.auth.secret         - legacy HS512 base64 secret (unchanged contract)
 *   jwt.auth.jwks-uri       - RS256 verifier source (e.g. https://auth/oauth2/jwks)
 *   jwt.auth.issuer-uri     - optional `iss` claim validation
 *   jwt.auth.audience       - optional `aud` claim validation
 *   jwt.auth.dual-mode-enabled - if false, RS256 tokens are rejected (legacy-only mode)
 */
@ConfigurationProperties(prefix = "jwt.auth")
public class JwtAuthProperties {

    /** Base64-encoded HS512 secret key (must match auth-service). Required for legacy path. */
    private String secret;

    /** Enable/disable JWT auto-configuration (default: true) */
    private boolean enabled = true;

    /** Ant-pattern paths to skip JWT validation (e.g. /actuator/health) */
    private String[] publicPaths = {};

    /** OIDC JWKS URI; if blank, RS256 path is disabled and lib stays HS512-only. */
    private String jwksUri;

    /** Expected `iss` claim for RS256 tokens; null = skip issuer validation. */
    private String issuerUri;

    /** Expected `aud` claim for RS256 tokens; null = skip audience validation. */
    private String audience;

    /** Master switch for the dual-mode dispatcher. Default true so a deployed lib upgrade
     * automatically accepts RS256 once jwks-uri is set; HS512 path is unaffected either way. */
    private boolean dualModeEnabled = true;

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String[] getPublicPaths() { return publicPaths; }
    public void setPublicPaths(String[] publicPaths) { this.publicPaths = publicPaths; }

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }

    public boolean isDualModeEnabled() { return dualModeEnabled; }
    public void setDualModeEnabled(boolean dualModeEnabled) { this.dualModeEnabled = dualModeEnabled; }
}
