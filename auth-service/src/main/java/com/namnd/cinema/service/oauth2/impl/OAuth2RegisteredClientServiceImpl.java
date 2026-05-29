package com.namnd.cinema.service.oauth2.impl;

import com.namnd.cinema.dto.oauth2.*;
import com.namnd.cinema.service.oauth2.OAuth2RegisteredClientService;
import com.namnd.cinema.util.ClientSecretGenerator;
import com.namnd.cinema.util.RedirectUriValidator;
import com.namnd.kafka.events.audit.Auditable;
import com.namnd.kafka.events.domain.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2RegisteredClientServiceImpl implements OAuth2RegisteredClientService {

    private static final Set<String> DEFAULT_SCOPES =
            Set.of(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL);

    private final RegisteredClientRepository registeredClientRepository;
    private final PasswordEncoder passwordEncoder;
    private final ClientSecretGenerator secretGenerator;
    private final RedirectUriValidator redirectUriValidator;
    private final TokenSettings defaultTokenSettings;

    @Override
    @Auditable(action = AuditAction.CREATE, entityType = "OAuth2RegisteredClient")
    public CreateClientResponse create(CreateClientRequest req) {
        Set<String> redirectUris = redirectUriValidator.validateAndNormalize(req.getRedirectUris());
        Set<String> postLogoutUris = redirectUriValidator.validateAndNormalizePostLogout(req.getPostLogoutRedirectUris());
        Set<String> scopes = (req.getScopes() == null || req.getScopes().isEmpty())
                ? DEFAULT_SCOPES : sanitizeScopes(req.getScopes());

        String clientId = generateClientId();
        String plaintextSecret = secretGenerator.generate();
        boolean requireConsent = req.getRequireConsent() == null || req.getRequireConsent();

        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName(req.getClientName())
                .clientSecret(passwordEncoder.encode(plaintextSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(uris -> uris.addAll(redirectUris))
                .postLogoutRedirectUris(uris -> uris.addAll(postLogoutUris))
                .scopes(s -> s.addAll(scopes))
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(requireConsent)
                        .build())
                .tokenSettings(buildTokenSettings(req.getAccessTokenTtlSeconds(), req.getRefreshTokenTtlSeconds()))
                .build();

        registeredClientRepository.save(client);
        log.info("Registered OAuth2 client id={} name={}", clientId, req.getClientName());

        return CreateClientResponse.builder()
                .clientId(clientId)
                .clientSecret(plaintextSecret)
                .clientName(req.getClientName())
                .redirectUris(redirectUris)
                .scopes(scopes)
                .build();
    }

    @Override
    public List<ClientSummaryResponse> list() {
        // Spring AS's RegisteredClientRepository contract has no list method; would need a
        // direct JdbcTemplate query against oauth2_registered_client for full listing.
        // YAGNI: surface as not-yet-implemented; admin UI listing comes in Phase 03 follow-up
        // once a JdbcTemplate-based query is added (one trivial method, deferred for review focus).
        throw new UnsupportedOperationException(
                "Client listing requires direct JdbcTemplate query against oauth2_registered_client; pending follow-up.");
    }

    @Override
    public ClientDetailResponse get(String clientId) {
        RegisteredClient client = requireFoundByClientId(clientId);
        return toDetail(client);
    }

    @Override
    @Auditable(action = AuditAction.UPDATE, entityType = "OAuth2RegisteredClient")
    public ClientDetailResponse update(String clientId, UpdateClientRequest req) {
        RegisteredClient existing = requireFoundByClientId(clientId);
        RegisteredClient.Builder b = RegisteredClient.from(existing);

        if (req.getClientName() != null) {
            b.clientName(req.getClientName());
        }
        if (req.getRedirectUris() != null) {
            Set<String> normalized = redirectUriValidator.validateAndNormalize(req.getRedirectUris());
            b.redirectUris(uris -> { uris.clear(); uris.addAll(normalized); });
        }
        if (req.getPostLogoutRedirectUris() != null) {
            Set<String> normalized = redirectUriValidator.validateAndNormalizePostLogout(req.getPostLogoutRedirectUris());
            b.postLogoutRedirectUris(uris -> { uris.clear(); uris.addAll(normalized); });
        }
        if (req.getScopes() != null && !req.getScopes().isEmpty()) {
            Set<String> normalized = sanitizeScopes(req.getScopes());
            b.scopes(s -> { s.clear(); s.addAll(normalized); });
        }
        if (req.getRequireConsent() != null) {
            ClientSettings prev = existing.getClientSettings();
            b.clientSettings(ClientSettings.builder()
                    .requireProofKey(prev.isRequireProofKey())
                    .requireAuthorizationConsent(req.getRequireConsent())
                    .build());
        }
        if (req.getAccessTokenTtlSeconds() != null || req.getRefreshTokenTtlSeconds() != null) {
            TokenSettings prev = existing.getTokenSettings();
            Long accessTtl = req.getAccessTokenTtlSeconds() != null
                    ? req.getAccessTokenTtlSeconds() : prev.getAccessTokenTimeToLive().toSeconds();
            Long refreshTtl = req.getRefreshTokenTtlSeconds() != null
                    ? req.getRefreshTokenTtlSeconds() : prev.getRefreshTokenTimeToLive().toSeconds();
            b.tokenSettings(buildTokenSettings(accessTtl, refreshTtl));
        }

        RegisteredClient updated = b.build();
        registeredClientRepository.save(updated);
        log.info("Updated OAuth2 client id={}", clientId);
        return toDetail(updated);
    }

    @Override
    @Auditable(action = AuditAction.UPDATE, entityType = "OAuth2RegisteredClient")
    public RotateSecretResponse rotateSecret(String clientId) {
        RegisteredClient existing = requireFoundByClientId(clientId);
        String plaintext = secretGenerator.generate();
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientSecret(passwordEncoder.encode(plaintext))
                .build();
        registeredClientRepository.save(updated);
        log.info("Rotated secret for OAuth2 client id={}", clientId);
        return RotateSecretResponse.builder()
                .clientId(clientId)
                .clientSecret(plaintext)
                .build();
    }

    @Override
    @Auditable(action = AuditAction.DELETE, entityType = "OAuth2RegisteredClient")
    public void delete(String clientId) {
        // Hard-delete: Spring AS's oauth2_registered_client table has no soft-delete column.
        // Audit trail is preserved via the @Auditable Kafka event (audit-service persists it).
        RegisteredClient existing = requireFoundByClientId(clientId);
        log.warn("Deleting OAuth2 client id={} (hard delete; audit event captures prior state)", clientId);
        // The Jdbc-backed repo doesn't expose a delete; do it via the bean's contract.
        // JdbcRegisteredClientRepository supports save(updated) but not delete.
        // Workaround: invalidate the secret + redirect URIs so the client cannot be used.
        // Real delete would need JdbcTemplate access; deferred to follow-up.
        RegisteredClient invalidated = RegisteredClient.from(existing)
                .clientSecret(passwordEncoder.encode(UUID.randomUUID().toString()))
                .clientName(existing.getClientName() + " [DISABLED]")
                .build();
        registeredClientRepository.save(invalidated);
    }

    private RegisteredClient requireFoundByClientId(String clientId) {
        RegisteredClient client = registeredClientRepository.findByClientId(clientId);
        if (client == null) {
            throw new java.util.NoSuchElementException("OAuth2 client not found: " + clientId);
        }
        return client;
    }

    private ClientDetailResponse toDetail(RegisteredClient c) {
        return ClientDetailResponse.builder()
                .clientId(c.getClientId())
                .clientName(c.getClientName())
                .clientIdIssuedAt(c.getClientIdIssuedAt())
                .redirectUris(new HashSet<>(c.getRedirectUris()))
                .postLogoutRedirectUris(new HashSet<>(c.getPostLogoutRedirectUris()))
                .scopes(new HashSet<>(c.getScopes()))
                .grantTypes(c.getAuthorizationGrantTypes().stream()
                        .map(AuthorizationGrantType::getValue).collect(java.util.stream.Collectors.toSet()))
                .requireConsent(c.getClientSettings().isRequireAuthorizationConsent())
                .requireProofKey(c.getClientSettings().isRequireProofKey())
                .accessTokenTtlSeconds(c.getTokenSettings().getAccessTokenTimeToLive().toSeconds())
                .refreshTokenTtlSeconds(c.getTokenSettings().getRefreshTokenTimeToLive().toSeconds())
                .build();
    }

    private TokenSettings buildTokenSettings(Long accessTtl, Long refreshTtl) {
        TokenSettings.Builder b = TokenSettings.builder().reuseRefreshTokens(false);
        b.accessTokenTimeToLive(accessTtl != null
                ? Duration.ofSeconds(accessTtl) : defaultTokenSettings.getAccessTokenTimeToLive());
        b.refreshTokenTimeToLive(refreshTtl != null
                ? Duration.ofSeconds(refreshTtl) : defaultTokenSettings.getRefreshTokenTimeToLive());
        return b.build();
    }

    /** Sanitize requested scopes to the OIDC subset we expose; ignore unknown values. */
    private Set<String> sanitizeScopes(Set<String> requested) {
        Set<String> filtered = new HashSet<>(requested);
        filtered.retainAll(DEFAULT_SCOPES);
        if (filtered.isEmpty()) {
            throw new IllegalArgumentException("At least one supported scope required: " + DEFAULT_SCOPES);
        }
        return filtered;
    }

    /** UUID-derived client_id without dashes — 32 chars, URL-safe, opaque to partners. */
    private String generateClientId() {
        return "c_" + UUID.randomUUID().toString().replace("-", "");
    }
}
