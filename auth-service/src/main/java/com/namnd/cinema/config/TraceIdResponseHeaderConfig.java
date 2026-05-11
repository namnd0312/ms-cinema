package com.namnd.cinema.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Registers a servlet filter that emits the X-Trace-Id response header on every
 * request (success and error). Reads from SLF4J MDC (populated by Micrometer
 * Tracing) so the filter does NOT depend on servlet filter ordering.
 *
 * auth-service is the JWT issuer and does NOT depend on jwt-auth-autoconfigure,
 * so this is a local mirror of the same filter that ships from that module for
 * the other 5 services.
 */
@Configuration
public class TraceIdResponseHeaderConfig {

    private static final String HEADER_NAME = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> traceIdResponseHeaderFilter() {
        OncePerRequestFilter filter = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain)
                    throws ServletException, IOException {
                applyHeader(res);
                try {
                    chain.doFilter(req, res);
                } finally {
                    applyHeader(res);
                }
            }

            private void applyHeader(HttpServletResponse res) {
                if (res.isCommitted()) return;
                String traceId = MDC.get(MDC_KEY);
                if (traceId != null && !traceId.isEmpty()) {
                    res.setHeader(HEADER_NAME, traceId);
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(filter);
        // After Spring ServerHttpObservationFilter (populates MDC) but before
        // Spring Security (-100) which commits 401/403 responses early.
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.addUrlPatterns("/*");
        reg.setName("traceIdResponseHeaderFilter");
        return reg;
    }
}
