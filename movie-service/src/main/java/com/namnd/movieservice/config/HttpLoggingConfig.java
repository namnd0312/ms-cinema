package com.namnd.movieservice.config;

import com.namnd.movieservice.config.filter.HttpLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers HttpLoggingFilter with highest priority so correlationId is
 * injected into MDC before Spring Security and other filters run.
 */
@Configuration
public class HttpLoggingConfig {

    @Bean
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter() {
        var registration = new FilterRegistrationBean<>(new HttpLoggingFilter());
        registration.setOrder(-100); // run before Spring Security filters (order -100)
        registration.addUrlPatterns("/*");
        return registration;
    }
}
