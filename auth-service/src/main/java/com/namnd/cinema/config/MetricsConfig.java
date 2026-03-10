package com.namnd.cinema.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer counters for auth business metrics.
 * Exposed via /actuator/prometheus for Grafana dashboards.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter authLoginSuccessCounter(MeterRegistry registry) {
        return Counter.builder("auth.login.success")
                .description("Successful login attempts")
                .register(registry);
    }

    @Bean
    public Counter authLoginFailureCounter(MeterRegistry registry) {
        return Counter.builder("auth.login.failure")
                .description("Failed login attempts")
                .register(registry);
    }

    @Bean
    public Counter authRegisterCounter(MeterRegistry registry) {
        return Counter.builder("auth.register")
                .description("User registrations")
                .register(registry);
    }

    @Bean
    public Counter authTokenRefreshCounter(MeterRegistry registry) {
        return Counter.builder("auth.token.refresh")
                .description("Token refresh operations")
                .register(registry);
    }

    @Bean
    public Counter authLogoutCounter(MeterRegistry registry) {
        return Counter.builder("auth.logout")
                .description("User logouts")
                .register(registry);
    }
}
