/cle# Phase 03 — Error Logging & MDC Verification

## Context Links
- Parent plan: [plan.md](./plan.md)
- Phase 02: [phase-02-http-logging-filter.md](./phase-02-http-logging-filter.md)

## Overview

- **Date:** 2026-03-09
- **Priority:** P2
- **Status:** pending
- **Description:** Add global exception handler to log unhandled exceptions with correlationId. Verify MDC fields appear in all log lines. Update `application.yml` logging config to remove redundant plain-text config.

## Key Insights

- `HttpLoggingFilter` (Phase 02) already handles 4xx/5xx response-level logging at WARN/ERROR
- Unhandled exceptions that bubble to Spring's DispatcherServlet may not pass through filter's `finally` block with correct status → need `@RestControllerAdvice` for structured error logging
- `notification-service` has no HTTP controllers but has Kafka listeners — exceptions there should log with context (topic, partition, offset) — already handled by `DefaultErrorHandler` in `KafkaConsumerConfig`
- MDC propagation to async contexts (Kafka threads) is NOT automatic — keep Kafka logging separate from HTTP MDC

## Requirements

- Unhandled exceptions produce an ERROR-level JSON log with `correlationId`, `errorType`, `errorMessage`, `stackTrace`
- Services already have `CustomAccesDeniedHandler` (auth-service) — no change needed there
- Remove `logging.level` from `application.yml` after `logback-spring.xml` takes over (avoid config conflict)

## Architecture

```
Exception thrown in controller/service
  ↓
@RestControllerAdvice GlobalExceptionHandler
  ├── log.error("Unhandled exception", ex)      ← MDC already set by HttpLoggingFilter
  └── return ResponseEntity(500, ErrorResponse)
```

**Existing auth-service exception handling:**
- `BadCredentialsException` — caught in AuthController directly
- `CustomAccesDeniedHandler` — returns 403 JSON (access denied)
- Unhandled exceptions from business services → currently no global handler → would return 500 with default Spring error

## Related Code Files

**Create:**
- `auth-service/src/main/java/com/namnd/springjwt/config/GlobalExceptionHandler.java`
- `api-gateway/src/main/java/com/namnd/apigateway/config/GlobalExceptionHandler.java`
- `movie-service/src/main/java/com/namnd/movieservice/config/GlobalExceptionHandler.java`
- `booking-service/src/main/java/com/namnd/bookingservice/config/GlobalExceptionHandler.java`
- `payment-service/src/main/java/com/namnd/paymentservice/config/GlobalExceptionHandler.java`

**Modify:**
- `*/src/main/resources/application.yml` — remove `logging.level.*` entries (moved to logback-spring.xml)

## Implementation Steps

### Step 1 — GlobalExceptionHandler.java (same for all HTTP services)

```java
package com.namnd.springjwt.config; // adjust per service

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Catches unhandled exceptions and logs them at ERROR level with full stack trace.
 * MDC correlationId is already set by HttpLoggingFilter at this point.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "status", 500,
                        "error", "Internal Server Error",
                        "message", "An unexpected error occurred"
                ));
    }
}
```

**Important:** Do NOT catch exceptions already handled by existing service-specific handlers. This catches only the "fallthrough" case.

### Step 2 — Remove conflicting logging config from application.yml

In each service's `application.yml`, remove or comment out any `logging:` block since `logback-spring.xml` now owns all log config:

```yaml
# REMOVE these if present:
# logging:
#   level:
#     com.namnd.springjwt: debug
#     org.hibernate: debug
```

These are now defined in `logback-spring.xml` per Phase 01.

### Step 3 — Verify MDC in logs

After implementation, trigger a request and verify JSON log line includes:
```json
{
  "correlationId": "...",
  "method": "POST",
  "url": "/api/auth/login",
  "status": "200",
  "durationMs": "145",
  "clientIp": "...",
  "userAgent": "..."
}
```

## Todo List

- [ ] Create `GlobalExceptionHandler.java` in auth-service
- [ ] Create `GlobalExceptionHandler.java` in api-gateway
- [ ] Create `GlobalExceptionHandler.java` in movie-service
- [ ] Create `GlobalExceptionHandler.java` in booking-service
- [ ] Create `GlobalExceptionHandler.java` in payment-service
- [ ] Remove `logging:` blocks from `application.yml` in all services (if present)
- [ ] Rebuild all services: `docker compose build --no-cache`
- [ ] Start services: `docker compose up -d`
- [ ] Send test request and verify JSON log output
- [ ] Send invalid request and verify WARN-level log
- [ ] Verify passwords not logged in plain text

## Success Criteria

- Unhandled 500 errors produce ERROR log with stack trace in JSON
- All JSON logs include `correlationId`
- No `logging:` config conflicts between `application.yml` and `logback-spring.xml`
- All 6 services produce structured JSON to stdout

## Risk Assessment

- **Existing exception handlers**: GlobalExceptionHandler must not conflict with existing auth-service handlers (BadCredentials, etc.). Using catch-all `Exception.class` is fine as Spring's @ExceptionHandler picks the most specific match first.
- **api-gateway**: Spring Cloud Gateway MVC may have its own error handling — test a 404 proxy miss to verify GlobalExceptionHandler triggers.

## Security Considerations

- GlobalExceptionHandler returns generic "unexpected error occurred" message — never leaks stack trace to client
- Stack trace logged server-side only (in structured JSON)
- No sensitive data included in error response body

## Next Steps

- Rebuild and redeploy via Docker: `docker compose build --no-cache && docker compose up -d`
- Monitor logs: `docker compose logs -f auth-service`
- Optional future: ship logs to Loki/ELK via Grafana agent (foundation is now ready)
