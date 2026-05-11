package com.namnd.jwt.autoconfigure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds X-Trace-Id header to all HTTP responses (success AND error).
 * Reads the trace ID from SLF4J MDC, which Micrometer Tracing populates
 * regardless of servlet filter ordering. The header is written before AND
 * after the chain so it survives early-committed error responses (e.g.
 * Spring Security 401) and async dispatches that populate MDC late.
 */
public class TraceIdResponseHeaderFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        applyHeader(response);
        try {
            filterChain.doFilter(request, response);
        } finally {
            applyHeader(response);
        }
    }

    private void applyHeader(HttpServletResponse response) {
        if (response.isCommitted()) return;
        String traceId = MDC.get(MDC_KEY);
        if (traceId != null && !traceId.isEmpty()) {
            response.setHeader(HEADER_NAME, traceId);
        }
    }
}
