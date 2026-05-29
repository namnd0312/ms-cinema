package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.Set;

/**
 * Response from POST /api/admin/oauth-clients.
 *
 * CRITICAL: this is the ONLY time {@code clientSecret} is ever returned in plaintext.
 * Admin must record it client-side; server only retains the BCrypt hash.
 *
 * {@code @ToString(exclude="clientSecret")} prevents accidental log emission.
 */
@Data
@Builder
@AllArgsConstructor
@ToString(exclude = "clientSecret")
public class CreateClientResponse {
    private String clientId;
    /** Plaintext secret — return-once. */
    private String clientSecret;
    private String clientName;
    private Set<String> redirectUris;
    private Set<String> scopes;
}
