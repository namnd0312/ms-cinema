package com.namnd.cinema.config.oauth2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Seeds a single dev client into oauth2_registered_client so the end-to-end
 * auth-code+PKCE flow can be exercised before Phase 03's admin API ships.
 *
 * Active only with profile=dev. Idempotent: re-checks repo on every boot.
 *
 * Dev client:
 *   client_id     = dev-test-client
 *   client_secret = dev-secret (BCrypt at rest)
 *   grant_types   = authorization_code, refresh_token
 *   redirect_uris = http://localhost:8080/callback, http://127.0.0.1:8080/callback
 *   scopes        = openid, profile, email
 *   PKCE required (no implicit; no client_secret_basic without PKCE).
 */
@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevRegisteredClientBootstrap implements ApplicationRunner {

    private static final String DEV_CLIENT_ID = "dev-test-client";

    private final RegisteredClientRepository registeredClientRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final TokenSettings defaultTokenSettings;

    @Override
    public void run(ApplicationArguments args) {
        if (registeredClientRepository.findByClientId(DEV_CLIENT_ID) != null) {
            log.info("Dev OAuth2 client '{}' already present; skipping bootstrap.", DEV_CLIENT_ID);
            return;
        }
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(DEV_CLIENT_ID)
                .clientSecret(passwordEncoder.encode("dev-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/callback")
                .redirectUri("http://127.0.0.1:8080/callback")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .scope(OidcScopes.EMAIL)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)        // PKCE mandatory
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(defaultTokenSettings)
                .build();
        registeredClientRepository.save(client);
        log.info("Seeded dev OAuth2 client '{}' (PKCE required, consent required).", DEV_CLIENT_ID);
    }
}
