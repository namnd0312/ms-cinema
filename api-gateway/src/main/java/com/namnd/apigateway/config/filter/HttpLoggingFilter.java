package com.namnd.apigateway.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
// ContentCachingRequestWrapper not needed directly — CorrelationIdRequestWrapper extends it
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Logs every HTTP request/response as structured JSON via MDC + SLF4J.
 * Sets correlationId in MDC so all log lines within a request share the same ID.
 * Masks sensitive fields (password, token, secret, refreshToken) in request body.
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    /** Masks sensitive JSON field values — case-insensitive match on field names */
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "\"(password|token|secret|refreshToken)\"\s*:\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    /** Cap logged body size to avoid enormous log entries */
    private static final int MAX_BODY_LENGTH = 2000;

    /** SSE/streaming endpoints must not be wrapped — response caching blocks flush */
    private static final List<String> STREAMING_PATHS = List.of("/api/notifications/stream");

    /** Skip logging for actuator endpoints (Prometheus scrapes every 15s) */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Skip response wrapping for SSE/streaming endpoints to avoid blocking
        if (STREAMING_PATHS.stream().anyMatch(p -> request.getRequestURI().startsWith(p))) {
            chain.doFilter(request, response);
            return;
        }

        var wrappedResp = new ContentCachingResponseWrapper(response);

        // Use incoming X-Correlation-ID or generate a new UUID
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-ID"))
                .filter(h -> !h.isBlank())
                .orElse(UUID.randomUUID().toString());

        // Inject correlationId into the forwarded request so downstream services share the same ID
        var wrappedReq = new CorrelationIdRequestWrapper(request, correlationId);

        MDC.put("correlationId", correlationId);
        MDC.put("method", request.getMethod());
        MDC.put("url", request.getRequestURI());
        MDC.put("clientIp", resolveClientIp(request));

        // Echo correlation ID back to client for distributed tracing
        wrappedResp.setHeader("X-Correlation-ID", correlationId);

        long start = System.currentTimeMillis();

        try {
            chain.doFilter(wrappedReq, wrappedResp);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = wrappedResp.getStatus();

            MDC.put("status", String.valueOf(status));
            MDC.put("durationMs", String.valueOf(duration));
            MDC.put("userAgent", request.getHeader("User-Agent"));
            MDC.put("authHeader", maskAuthHeader(request.getHeader("Authorization")));

            String reqBody = maskSensitiveFields(readBody(wrappedReq.getContentAsByteArray()));
            MDC.put("requestBody", reqBody);

            // Log response body only on errors to avoid large payload dumps
            if (status >= 400) {
                MDC.put("responseBody", readBody(wrappedResp.getContentAsByteArray()));
            }

            String msg = request.getMethod() + " " + request.getRequestURI() + " -> " + status + " in " + duration + "ms";

            if (status >= 500) {
                log.error(msg);
            } else if (status >= 400) {
                log.warn(msg);
            } else {
                log.info(msg);
            }

            // Copy cached response body back to actual response stream
            wrappedResp.copyBodyToResponse();

            MDC.clear();
        }
    }

    /** Prefer X-Forwarded-For (set by reverse proxy) over direct remote addr */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Always mask Authorization header — never log tokens in full */
    private String maskAuthHeader(String authHeader) {
        if (authHeader == null) return null;
        return authHeader.toLowerCase().startsWith("bearer ") ? "Bearer ***" : "***";
    }

    /** Replace sensitive JSON field values with *** */
    private String maskSensitiveFields(String body) {
        if (body == null || body.isBlank()) return "";
        return SENSITIVE_PATTERN.matcher(body)
                .replaceAll(m -> "\"" + m.group(1) + "\":\"***\"");
    }

    private String readBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String body = new String(bytes, StandardCharsets.UTF_8);
        return body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) + "...[truncated]" : body;
    }

    /**
     * Wraps an HttpServletRequest to inject X-Correlation-ID into headers
     * so Spring Cloud Gateway MVC forwards it to downstream services.
     */
    /** Extends ContentCachingRequestWrapper to preserve getContentAsByteArray() while injecting the header */
    private static class CorrelationIdRequestWrapper extends org.springframework.web.util.ContentCachingRequestWrapper {

        private static final String CORRELATION_HEADER = "X-Correlation-ID";
        private final String correlationId;

        CorrelationIdRequestWrapper(HttpServletRequest request, String correlationId) {
            super(request);
            this.correlationId = correlationId;
        }

        @Override
        public String getHeader(String name) {
            if (CORRELATION_HEADER.equalsIgnoreCase(name)) return correlationId;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (CORRELATION_HEADER.equalsIgnoreCase(name)) return Collections.enumeration(List.of(correlationId));
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (!names.contains(CORRELATION_HEADER)) names.add(CORRELATION_HEADER);
            return Collections.enumeration(names);
        }
    }
}
