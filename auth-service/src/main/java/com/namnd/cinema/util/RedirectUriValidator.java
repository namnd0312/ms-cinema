package com.namnd.cinema.util;

import com.namnd.cinema.exception.InvalidRedirectUriException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Strict OAuth2 redirect_uri validator — open-redirect mitigation.
 *
 * Rules (per RFC 6749 §3.1.2 + RFC 9700 §2.1):
 *   - Max 5 entries per client (avoids exhaustion + simplifies review)
 *   - Scheme must be `https`, OR `http` if host in {localhost, 127.0.0.1} (dev-only)
 *   - No fragments (RFC 6749 forbids)
 *   - No query strings (avoids redirect_uri-via-encoded-tricks)
 *   - No wildcards
 *   - Normalize scheme + host to lowercase before storage
 */
@Component
public class RedirectUriValidator {

    private static final int MAX_REDIRECT_URIS = 5;
    private static final Set<String> DEV_HOSTS = Set.of("localhost", "127.0.0.1");

    /** Validate + return a normalized copy. Throws {@link InvalidRedirectUriException} on failure. */
    public Set<String> validateAndNormalize(List<String> uris) {
        if (uris == null || uris.isEmpty()) {
            throw new InvalidRedirectUriException("At least one redirect_uri required");
        }
        if (uris.size() > MAX_REDIRECT_URIS) {
            throw new InvalidRedirectUriException(
                    "Max " + MAX_REDIRECT_URIS + " redirect_uris allowed; got " + uris.size());
        }
        Set<String> normalized = new HashSet<>();
        for (String raw : uris) {
            normalized.add(validateOne(raw));
        }
        return normalized;
    }

    /** Validate post_logout_redirect_uris (same rules, but list may be empty). */
    public Set<String> validateAndNormalizePostLogout(List<String> uris) {
        if (uris == null || uris.isEmpty()) {
            return Set.of();
        }
        if (uris.size() > MAX_REDIRECT_URIS) {
            throw new InvalidRedirectUriException(
                    "Max " + MAX_REDIRECT_URIS + " post_logout_redirect_uris allowed; got " + uris.size());
        }
        Set<String> normalized = new HashSet<>();
        for (String raw : uris) {
            normalized.add(validateOne(raw));
        }
        return normalized;
    }

    private String validateOne(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRedirectUriException("redirect_uri must not be blank");
        }
        if (raw.contains("*")) {
            throw new InvalidRedirectUriException("Wildcards not allowed: " + raw);
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new InvalidRedirectUriException("Malformed URI: " + raw);
        }
        if (uri.getFragment() != null) {
            throw new InvalidRedirectUriException("Fragment not allowed: " + raw);
        }
        if (uri.getRawQuery() != null) {
            throw new InvalidRedirectUriException("Query string not allowed: " + raw);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null || host.isBlank()) {
            throw new InvalidRedirectUriException("Scheme + host required: " + raw);
        }
        scheme = scheme.toLowerCase();
        host = host.toLowerCase();
        boolean httpsOk = "https".equals(scheme);
        boolean devHttpOk = "http".equals(scheme) && DEV_HOSTS.contains(host);
        if (!httpsOk && !devHttpOk) {
            throw new InvalidRedirectUriException(
                    "Only https (or http for localhost/127.0.0.1) allowed: " + raw);
        }
        // Rebuild normalized URI: scheme + host lowercased, path/port preserved.
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (uri.getPort() != -1) sb.append(':').append(uri.getPort());
        if (uri.getRawPath() != null) sb.append(uri.getRawPath());
        return sb.toString();
    }
}
