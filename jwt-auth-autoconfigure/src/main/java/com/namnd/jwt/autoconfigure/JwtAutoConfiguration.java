package com.namnd.jwt.autoconfigure;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Auto-configuration entry point for the JWT auth starter.
 * Activated only when Spring Security and a servlet web app are on the classpath,
 * and jwt.auth.enabled is true (default).
 * Consumer can override any bean with @ConditionalOnMissingBean or disable entirely
 * via jwt.auth.enabled=false.
 */
@AutoConfiguration(before = org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class)
@ConditionalOnClass(SecurityFilterChain.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "jwt.auth", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtAuthProperties.class)
public class JwtAutoConfiguration {

    /**
     * Legacy HS512 validator -- always created so the v0.0.1 contract is preserved
     * regardless of whether jwks-uri is configured.
     */
    @Bean(name = "legacyHs512Validator")
    @ConditionalOnMissingBean(name = "legacyHs512Validator")
    public JwtTokenValidator legacyHs512Validator(JwtAuthProperties properties) {
        return new JwtTokenValidator(properties.getSecret());
    }

    /**
     * RS256 verifier — only present when {@code jwt.auth.jwks-uri} is set.
     * Without it the dual-mode dispatcher behaves like pure HS512.
     */
    @Bean
    @ConditionalOnMissingBean(NimbusJwtDecoder.class)
    @ConditionalOnProperty(prefix = "jwt.auth", name = "jwks-uri")
    public NimbusJwtDecoder rs256JwtDecoder(JwtAuthProperties properties) {
        return RemoteJwksDecoderFactory.build(
                properties.getJwksUri(),
                properties.getIssuerUri(),
                properties.getAudience());
    }

    /**
     * Primary {@link JwtTokenValidator} bean is the dual-mode dispatcher.
     * Marked {@code @Primary} so the filter receives it (not the legacy delegate)
     * when both beans coexist in the context.
     */
    @Bean(name = "jwtTokenValidator")
    @org.springframework.context.annotation.Primary
    @ConditionalOnMissingBean(name = "jwtTokenValidator")
    public JwtTokenValidator jwtTokenValidator(
            JwtAuthProperties properties,
            JwtTokenValidator legacyHs512Validator,
            org.springframework.beans.factory.ObjectProvider<NimbusJwtDecoder> rs256DecoderProvider) {
        NimbusJwtDecoder rs256 = rs256DecoderProvider.getIfAvailable();
        return new JwtTokenValidatorDualMode(
                legacyHs512Validator,
                rs256,
                properties.isDualModeEnabled(),
                properties.getSecret());
    }

    @Bean
    @ConditionalOnMissingBean(name = "jwtAuthenticationFilter")
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTokenValidator tokenValidator) {
        return new JwtAuthenticationFilter(tokenValidator);
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain jwtSecurityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtFilter,
            JwtAuthProperties properties) throws Exception {

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            )
            .authorizeHttpRequests(auth -> {
                for (String path : properties.getPublicPaths()) {
                    auth.requestMatchers(path).permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(name = "serviceNameHeaderFilterRegistration")
    public FilterRegistrationBean<ServiceNameHeaderFilter> serviceNameHeaderFilterRegistration(
            @Value("${spring.application.name:unknown}") String applicationName) {
        FilterRegistrationBean<ServiceNameHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ServiceNameHeaderFilter(applicationName));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("serviceNameHeaderFilter");
        return registration;
    }

    /**
     * Adds X-Trace-Id response header on every request so clients can correlate
     * with the trace in Grafana/Tempo. Reads from SLF4J MDC (populated by
     * Micrometer Tracing) so the filter does NOT need to be ordered after the
     * tracing observation filter.
     */
    @Bean
    @ConditionalOnMissingBean(name = "traceIdResponseHeaderFilterRegistration")
    public FilterRegistrationBean<TraceIdResponseHeaderFilter> traceIdResponseHeaderFilterRegistration() {
        FilterRegistrationBean<TraceIdResponseHeaderFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceIdResponseHeaderFilter());
        registration.addUrlPatterns("/*");
        // Run AFTER Spring's ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1)
        // which populates MDC, but BEFORE Spring Security (default order -100)
        // which commits 401/403 responses early.
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("traceIdResponseHeaderFilter");
        return registration;
    }
}
