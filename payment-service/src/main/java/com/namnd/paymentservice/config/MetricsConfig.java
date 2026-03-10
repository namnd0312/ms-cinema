package com.namnd.paymentservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer counters for payment business metrics.
 * Exposed via /actuator/prometheus for Grafana dashboards.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter paymentInitiatedCounter(MeterRegistry registry) {
        return Counter.builder("payment.initiated")
                .description("Payment intents created")
                .register(registry);
    }

    @Bean
    public Counter paymentCompletedCounter(MeterRegistry registry) {
        return Counter.builder("payment.completed")
                .description("Payments completed successfully")
                .register(registry);
    }

    @Bean
    public Counter paymentFailedCounter(MeterRegistry registry) {
        return Counter.builder("payment.failed")
                .description("Payments failed")
                .register(registry);
    }
}
