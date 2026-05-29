package com.namnd.cinema.config.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Dynamic CORS source — allows only origins drawn from {@code redirect_uri}s of
 * currently-registered OAuth2 clients (Phase 06 hardening).
 *
 * Reads {@code oauth2_registered_client.redirect_uris} (comma-separated per row) on every
 * request, builds the origin set, and short-circuits with a 403 for any other origin.
 * For 1-5 partner clients the DB hit is negligible; for larger fleets cache the result.
 *
 * Never allow {@code "*"} or credentials with wildcard — that combo enables cross-origin
 * token theft from a malicious site.
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2CorsConfigurationSource implements CorsConfigurationSource {

    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "X-Requested-With");

    private final JdbcTemplate jdbc;

    @Override
    public CorsConfiguration getCorsConfiguration(@NonNull HttpServletRequest request) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(loadRegisteredOrigins());
        cfg.setAllowedMethods(ALLOWED_METHODS);
        cfg.setAllowedHeaders(ALLOWED_HEADERS);
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        return cfg;
    }

    /** Pull every redirect_uri across every client and reduce to its origin. */
    private List<String> loadRegisteredOrigins() {
        Set<String> origins = new LinkedHashSet<>();
        try {
            List<String> rows = jdbc.queryForList(
                    "SELECT redirect_uris FROM oauth2_registered_client", String.class);
            for (String csv : rows) {
                if (csv == null || csv.isBlank()) continue;
                for (String uri : csv.split(",")) {
                    String origin = toOrigin(uri.trim());
                    if (origin != null) origins.add(origin);
                }
            }
        } catch (Exception ex) {
            log.warn("CORS origin lookup failed; falling back to empty allowlist: {}", ex.getMessage());
        }
        return List.copyOf(origins);
    }

    /** scheme://host[:port] — strips path, query, fragment. */
    private String toOrigin(String redirectUri) {
        if (redirectUri.isEmpty()) return null;
        try {
            URI u = URI.create(redirectUri);
            if (u.getScheme() == null || u.getHost() == null) return null;
            StringBuilder sb = new StringBuilder()
                    .append(u.getScheme()).append("://").append(u.getHost());
            if (u.getPort() != -1) sb.append(":").append(u.getPort());
            return sb.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
