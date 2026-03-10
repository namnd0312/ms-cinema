package com.namnd.jwt.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT auth starter.
 * Bound to prefix "jwt.auth" in consumer application properties.
 * No Lombok -- keeps starter dependency-free for consumers.
 */
@ConfigurationProperties(prefix = "jwt.auth")
public class JwtAuthProperties {

    /** Base64-encoded HS512 secret key (must match auth-service) */
    private String secret;

    /** Enable/disable JWT auto-configuration (default: true) */
    private boolean enabled = true;

    /** Ant-pattern paths to skip JWT validation (e.g. /actuator/health) */
    private String[] publicPaths = {};

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String[] getPublicPaths() { return publicPaths; }
    public void setPublicPaths(String[] publicPaths) { this.publicPaths = publicPaths; }
}
