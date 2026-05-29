package com.namnd.cinema.dto.oauth2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * Admin request to register a new partner OAuth2 client.
 * Server generates clientId + clientSecret; caller cannot supply them.
 */
@Data
public class CreateClientRequest {

    @NotBlank
    @Size(max = 200)
    private String clientName;

    @NotEmpty
    private List<String> redirectUris;

    private List<String> postLogoutRedirectUris;

    /** Subset of {openid,profile,email}; null/empty defaults to all three. */
    private Set<String> scopes;

    /** Default true; rare to disable (would skip user consent screen). */
    private Boolean requireConsent;

    /** Per-client TTL overrides; null = use global default. */
    private Long accessTokenTtlSeconds;
    private Long refreshTokenTtlSeconds;
}
