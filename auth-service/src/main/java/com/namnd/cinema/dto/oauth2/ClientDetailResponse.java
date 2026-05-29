package com.namnd.cinema.dto.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;

/**
 * GET /api/admin/oauth-clients/{clientId} — full client metadata.
 * NEVER includes secret material (plaintext or hash).
 */
@Data
@Builder
@AllArgsConstructor
public class ClientDetailResponse {
    private String clientId;
    private String clientName;
    private Instant clientIdIssuedAt;
    private Set<String> redirectUris;
    private Set<String> postLogoutRedirectUris;
    private Set<String> scopes;
    private Set<String> grantTypes;
    private Boolean requireConsent;
    private Boolean requireProofKey;
    private Long accessTokenTtlSeconds;
    private Long refreshTokenTtlSeconds;
}
