package com.namnd.bookingservice.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Custom Micrometer counters for booking business metrics.
 * Exposed via /actuator/prometheus for Grafana dashboards.
 */
@Configuration
public class MetricsConfig {

    @Bean
    public Counter bookingCreatedCounter(MeterRegistry registry) {
        return Counter.builder("booking.created")
                .description("Bookings created")
                .register(registry);
    }

    @Bean
    public Counter bookingConfirmedCounter(MeterRegistry registry) {
        return Counter.builder("booking.confirmed")
                .description("Bookings confirmed")
                .register(registry);
    }

    @Bean
    public Counter bookingCancelledCounter(MeterRegistry registry) {
        return Counter.builder("booking.cancelled")
                .description("Bookings cancelled")
                .register(registry);
    }
}
