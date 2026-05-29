package com.namnd.cinema.config.security;

import com.namnd.cinema.config.custom.CustomAccesDeniedHandler;
import com.namnd.cinema.config.filter.JwtAuthenticationFilter;
import com.namnd.cinema.config.oauth2.OAuth2CorsConfigurationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;

    /**
     * When true, every non-permitted request must arrive on HTTPS. Enabled via
     * {@code namnd.app.requireSsl=true} in {@code application-prod.yml}.
     * Defaults to false so local/dev profiles keep working over http.
     */
    @Value("${namnd.app.requireSsl:false}")
    private boolean requireSsl;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public CustomAccesDeniedHandler customAccesDeniedHandler() {
        return new CustomAccesDeniedHandler();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Phase 06: CORS source pulls allowed origins from registered partner clients'
     * redirect_uris (DB lookup per request). Denies wildcard origins by construction.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(JdbcTemplate jdbc) {
        return new OAuth2CorsConfigurationSource(jdbc);
    }

    /**
     * App security chain — runs AFTER the Spring AS chain (@Order(1)).
     * Order(2) ensures /oauth2/** is consumed by AS first; this chain catches
     * /api/**, /login form, Google OAuth callback, and everything else.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/change-password").authenticated()
                .requestMatchers(
                    "/api/auth/**",
                    "/oauth2/authorization/**",
                    "/login/oauth2/code/**",
                    // OIDC discovery + JWKS — public so partner relying parties can fetch.
                    "/oauth2/jwks",
                    "/.well-known/**",
                    "/actuator/health", "/actuator/info", "/actuator/prometheus",
                    "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html"
                ).permitAll()
                // Phase 04 consent endpoints — require an authenticated user session.
                // Spring AS funnels unauth users to /login first via the AS chain entry point.
                .requestMatchers("/oauth/consent", "/api/oauth/consent").authenticated()
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.disable())
            // IF_REQUIRED: allows temporary session for OAuth2 state param,
            // JWT-based requests won't create sessions
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2AuthenticationSuccessHandler)
            )
            .exceptionHandling(ex -> ex
                .accessDeniedHandler(customAccesDeniedHandler())
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .cors(Customizer.withDefaults());

        // Phase 06: enforce HTTPS at the app layer when running with the prod profile.
        if (requireSsl) {
            http.requiresChannel(c -> c.anyRequest().requiresSecure());
        }

        return http.build();
    }
}
