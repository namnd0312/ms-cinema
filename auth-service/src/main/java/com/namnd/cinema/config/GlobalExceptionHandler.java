package com.namnd.cinema.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.namnd.cinema.exception.InvalidRedirectUriException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Catches unhandled exceptions and logs them at ERROR level.
 * MDC correlationId is already set by HttpLoggingFilter at this point,
 * so the log entry will include the request correlation ID automatically.
 * Returns a generic error response to avoid leaking stack traces to clients.
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

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "error", "Not Found",
                        "message", "No endpoint mapped for: " + ex.getResourcePath()));
    }

    /** Strict redirect_uri policy rejection (Phase 03 admin client management). */
    @ExceptionHandler(InvalidRedirectUriException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRedirectUri(InvalidRedirectUriException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "error", "invalid_redirect_uri",
                        "message", ex.getMessage()));
    }

    /** Missing OAuth2 client (or any other missing-element domain miss). */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "error", "not_found",
                        "message", ex.getMessage()));
    }

    /** Validation errors raised by service layer (e.g. scope subset checks). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("status", 400, "error", "bad_request",
                        "message", ex.getMessage()));
    }
}
