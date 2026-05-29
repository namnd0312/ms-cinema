package com.namnd.cinema.config.oauth2;

import com.namnd.kafka.events.audit.AuditEventPublisher;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.time.Duration;

/**
 * Spring Authorization Server filter chain + persistence + token settings.
 * Order(1) — must come before the existing app SecurityFilterChain so /oauth2/**
 * (authorize, token, jwks, etc.) is not consumed by the JWT filter / form login.
 *
 * Wiring:
 *   - JdbcRegisteredClientRepository   <- oauth2_registered_client table
 *   - JdbcOAuth2AuthorizationService   <- oauth2_authorization table
 *   - JdbcOAuth2AuthorizationConsentService <- oauth2_authorization_consent table
 *   - NimbusJwtEncoder                 <- JwksSourceFromDbService (Phase 01 keys)
 *
 * Defaults applied to every new client:
 *   access TTL    = namnd.app.access-token-ttl-seconds   (default 900s / 15min)
 *   id_token TTL  = namnd.app.id-token-ttl-seconds       (default 3600s / 1h)
 *   refresh TTL   = namnd.app.refresh-token-ttl-seconds  (default 1209600s / 14d)
 *   refresh-token rotation = ON (reuseRefreshTokens=false)
 */
@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfig {

    @Value("${namnd.app.oauth2IssuerUri:http://localhost:8081}")
    private String issuerUri;

    @Value("${namnd.app.accessTokenTtlSeconds:900}")
    private long accessTtl;

    @Value("${namnd.app.idTokenTtlSeconds:3600}")
    private long idTokenTtl;

    @Value("${namnd.app.refreshTokenTtlSeconds:1209600}")
    private long refreshTtl;

    /**
     * AS filter chain — matches all Spring AS endpoints. Falls back to /login for
     * unauthenticated HTML requests (Google login + form login both handled by Order(2) chain).
     */
    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer cfg = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http.securityMatcher(cfg.getEndpointsMatcher())
                .with(cfg, c -> c
                        .oidc(Customizer.withDefaults())
                        // Custom Angular consent screen (Phase 04). Spring AS will 302 here
                        // when user-consent is required and not yet granted.
                        .authorizationEndpoint(a -> a.consentPage("/oauth/consent")))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(cfg.getEndpointsMatcher()))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(issuerUri)
                .build();
    }

    /**
     * Default token TTL settings applied during client registration (Phase 03 admin API
     * builds clients via {@code RegisteredClient.withId(...).tokenSettings(defaultTokenSettings())}).
     */
    @Bean
    public TokenSettings defaultTokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofSeconds(accessTtl))
                .refreshTokenTimeToLive(Duration.ofSeconds(refreshTtl))
                .reuseRefreshTokens(false)  // rotation on -> theft detection
                .build();
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    /**
     * RS256 JwtDecoder for AS endpoints that need to introspect tokens (e.g. /oauth2/revoke).
     * Uses the same JWK source so newly-rotated keys are picked up without restart.
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationService(jdbc, clients);
    }

    /**
     * Consent service decorated with an audit emitter (Phase 06).
     * Persistence still goes to the JDBC-backed store; save/remove additionally publish
     * {@code oauth2.consent.granted} / {@code oauth2.consent.denied} via the shared
     * {@link AuditEventPublisher}.
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbc,
            RegisteredClientRepository clients,
            AuditEventPublisher auditPublisher,
            @Autowired(required = false) Tracer tracer,
            @Value("${spring.application.name:auth-service}") String serviceName) {
        OAuth2AuthorizationConsentService delegate = new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
        return new AuditingOAuth2AuthorizationConsentService(delegate, auditPublisher, tracer, serviceName);
    }
}
