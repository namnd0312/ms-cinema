# Phase 02 — HTTP Request/Response Logging Filter

## Context Links
- Parent plan: [plan.md](./plan.md)
- Phase 01: [phase-01-json-logging-config.md](./phase-01-json-logging-config.md)
- Code standards: `docs/code-standards.md`

## Overview

- **Date:** 2026-03-09
- **Priority:** P2
- **Status:** pending
- **Description:** Implement `HttpLoggingFilter` (OncePerRequestFilter) for all 5 HTTP services. Logs method, URL, status, duration, headers, masked body, client IP, User-Agent. Injects correlationId into MDC.

## Key Insights

- api-gateway uses `spring-cloud-starter-gateway-mvc` (servlet-based) — **NOT WebFlux** — so standard `OncePerRequestFilter` works identically
- `ContentCachingRequestWrapper` / `ContentCachingResponseWrapper` needed to read body without consuming it
- Body logging must be done AFTER filter chain executes (response body is only available post-processing)
- Sensitive fields to mask: `password`, `token`, `secret`, `Authorization` (header), `refreshToken`
- MDC is thread-local; must be cleared after request to prevent leakage to thread pool

## Requirements

**Log per request:**
1. Method + URL + status + duration (ms)
2. Request headers (mask Authorization value → `Bearer ***`)
3. Request body (JSON fields: mask `password`, `token`, `secret`, `refreshToken` values)
4. Response body (optional, only for errors — avoid logging large payloads)
5. Client IP (`X-Forwarded-For` or `RemoteAddr`)
6. User-Agent header

**MDC fields set per request:**
- `correlationId` — from `X-Correlation-ID` request header, or generate UUID if missing
- `method`, `url`, `clientIp` — for all log lines within the request

**Log levels:**
- `2xx/3xx` → INFO
- `4xx` → WARN
- `5xx` → ERROR

## Architecture

```
Request → HttpLoggingFilter
  1. Wrap request (ContentCachingRequestWrapper)
  2. Wrap response (ContentCachingResponseWrapper)
  3. Extract/generate correlationId → MDC.put("correlationId", ...)
  4. Set MDC: method, url, clientIp, userAgent
  5. Record startTime = System.currentTimeMillis()
  6. chain.doFilter(wrappedReq, wrappedResp)   ← actual processing
  7. Compute duration = now - startTime
  8. Read response status
  9. Log at appropriate level (INFO/WARN/ERROR)
 10. MDC.clear()
```

**File structure (per service, e.g. auth-service):**
```
config/
├── filter/
│   └── HttpLoggingFilter.java      ← OncePerRequestFilter implementation
└── HttpLoggingConfig.java          ← @Bean FilterRegistrationBean (order=-100)
```

**Note:** `notification-service` has NO HTTP logging filter (it's Kafka-only; Tomcat exists for actuator but no business HTTP traffic).

## Related Code Files

**Create (5 HTTP services):**
- `auth-service/src/main/java/com/namnd/springjwt/config/filter/HttpLoggingFilter.java`
- `auth-service/src/main/java/com/namnd/springjwt/config/HttpLoggingConfig.java`
- `api-gateway/src/main/java/com/namnd/apigateway/config/filter/HttpLoggingFilter.java`
- `api-gateway/src/main/java/com/namnd/apigateway/config/HttpLoggingConfig.java`
- `movie-service/src/main/java/com/namnd/movieservice/config/filter/HttpLoggingFilter.java`
- `movie-service/src/main/java/com/namnd/movieservice/config/HttpLoggingConfig.java`
- `booking-service/src/main/java/com/namnd/bookingservice/config/filter/HttpLoggingFilter.java`
- `booking-service/src/main/java/com/namnd/bookingservice/config/HttpLoggingConfig.java`
- `payment-service/src/main/java/com/namnd/paymentservice/config/filter/HttpLoggingFilter.java`
- `payment-service/src/main/java/com/namnd/paymentservice/config/HttpLoggingConfig.java`

## Implementation Steps

### Step 1 — HttpLoggingFilter.java (same logic for all 5 services, only package differs)

```java
package com.namnd.springjwt.config.filter; // adjust per service

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/**
 * Logs every HTTP request/response in structured JSON via MDC + SLF4J.
 * Masks sensitive fields (password, token, secret) in request body.
 * Sets correlationId in MDC so all downstream log lines share the same ID.
 */
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    // Regex to mask sensitive JSON field values
    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
        "\"(password|token|secret|refreshToken)\"\\s*:\\s*\"[^\"]*\"",
        Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_BODY_LENGTH = 2000; // cap logged body size

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        var wrappedReq = new ContentCachingRequestWrapper(request);
        var wrappedResp = new ContentCachingResponseWrapper(response);

        // Inject correlationId — use incoming header or generate
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-ID"))
                .filter(h -> !h.isBlank())
                .orElse(UUID.randomUUID().toString());

        MDC.put("correlationId", correlationId);
        MDC.put("method", request.getMethod());
        MDC.put("url", request.getRequestURI());
        MDC.put("clientIp", resolveClientIp(request));

        // Set correlation header on response for client tracing
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

            // Log request body (masked)
            String reqBody = maskSensitiveFields(readBody(wrappedReq.getContentAsByteArray()));
            MDC.put("requestBody", reqBody);

            // Log response body only on errors (avoid large payloads in success logs)
            if (status >= 400) {
                String respBody = readBody(wrappedResp.getContentAsByteArray());
                MDC.put("responseBody", respBody);
            }

            String msg = request.getMethod() + " " + request.getRequestURI() + " -> " + status + " in " + duration + "ms";

            if (status >= 500) {
                log.error(msg);
            } else if (status >= 400) {
                log.warn(msg);
            } else {
                log.info(msg);
            }

            // Copy body bytes back so client receives the response
            wrappedResp.copyBodyToResponse();

            MDC.clear();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskAuthHeader(String authHeader) {
        if (authHeader == null) return null;
        if (authHeader.toLowerCase().startsWith("bearer ")) return "Bearer ***";
        return "***";
    }

    private String maskSensitiveFields(String body) {
        if (body == null || body.isBlank()) return "";
        return SENSITIVE_PATTERN.matcher(body)
                .replaceAll(m -> "\"" + m.group(1) + "\":\"***\"");
    }

    private String readBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        String body = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        return body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) + "...[truncated]" : body;
    }
}
```

### Step 2 — HttpLoggingConfig.java (register filter with high priority)

```java
package com.namnd.springjwt.config; // adjust per service

import com.namnd.springjwt.config.filter.HttpLoggingFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers HttpLoggingFilter before all other filters so correlationId
 * is injected into MDC early and present in all subsequent log lines.
 */
@Configuration
public class HttpLoggingConfig {

    @Bean
    public FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter() {
        var registration = new FilterRegistrationBean<>(new HttpLoggingFilter());
        registration.setOrder(-100); // run before Spring Security filters
        registration.addUrlPatterns("/*");
        return registration;
    }
}
```

### Step 3 — Replicate to all 5 HTTP services

Same filter logic, only `package` declaration differs per service:
- `auth-service` → `com.namnd.springjwt.config.filter`
- `api-gateway` → `com.namnd.apigateway.config.filter`
- `movie-service` → `com.namnd.movieservice.config.filter`
- `booking-service` → `com.namnd.bookingservice.config.filter`
- `payment-service` → `com.namnd.paymentservice.config.filter`

## Todo List

- [ ] Create `HttpLoggingFilter.java` in auth-service (`config/filter/`)
- [ ] Create `HttpLoggingConfig.java` in auth-service (`config/`)
- [ ] Create `HttpLoggingFilter.java` in api-gateway (`config/filter/`)
- [ ] Create `HttpLoggingConfig.java` in api-gateway (`config/`)
- [ ] Create `HttpLoggingFilter.java` in movie-service (`config/filter/`)
- [ ] Create `HttpLoggingConfig.java` in movie-service (`config/`)
- [ ] Create `HttpLoggingFilter.java` in booking-service (`config/filter/`)
- [ ] Create `HttpLoggingConfig.java` in booking-service (`config/`)
- [ ] Create `HttpLoggingFilter.java` in payment-service (`config/filter/`)
- [ ] Create `HttpLoggingConfig.java` in payment-service (`config/`)
- [ ] Verify passwords are NOT logged in plain text (masked to `***`)
- [ ] Verify Authorization header is masked
- [ ] Verify `X-Correlation-ID` echoed on response
- [ ] Run `mvn clean compile` on all modules

## Success Criteria

- Every HTTP request produces a structured JSON log line with method, url, status, duration
- `password` field in request body appears as `***` in logs
- `Authorization: Bearer <token>` appears as `Bearer ***` in logs
- 4xx responses log at WARN, 5xx at ERROR, 2xx at INFO
- Each log line has `correlationId` field
- `X-Correlation-ID` response header present

## Risk Assessment

- **ContentCachingRequestWrapper**: body read after chain.doFilter(); if body was already read before filter (shouldn't happen), it may be empty — low risk
- **Response body logging for errors**: if response body is very large, `MAX_BODY_LENGTH=2000` truncates safely
- **Thread leakage**: MDC.clear() in `finally` block prevents MDC pollution across requests

## Security Considerations

- `SENSITIVE_PATTERN` masks `password`, `token`, `secret`, `refreshToken` in JSON body
- `Authorization` header always masked — never logged in full
- Response body only logged for 4xx/5xx (avoids leaking sensitive response data in success cases)
- `X-Forwarded-For` trusted for IP resolution — acceptable since api-gateway is the edge

## Next Steps

→ Phase 03: Error logging enhancements & MDC propagation verification
