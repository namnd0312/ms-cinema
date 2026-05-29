package com.namnd.cinema.dto.oauth2;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * PATCH /api/admin/oauth-clients/{clientId}.
 * Server rejects clientId/clientSecret fields here — those go through dedicated endpoints.
 * Any field left null is unchanged.
 */
@Data
public class UpdateClientRequest {

    @Size(max = 200)
    private String clientName;

    private List<String> redirectUris;

    private List<String> postLogoutRedirectUris;

    private Set<String> scopes;

    private Boolean requireConsent;

    private Long accessTokenTtlSeconds;

    private Long refreshTokenTtlSeconds;
}
