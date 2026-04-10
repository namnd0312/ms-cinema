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

    @Bean
    @ConditionalOnMissingBean
    public JwtTokenValidator jwtTokenValidator(JwtAuthProperties properties) {
        return new JwtTokenValidator(properties.getSecret());
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
}
