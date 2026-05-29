package com.namnd.cinema.exception;

/**
 * Thrown by {@link com.namnd.cinema.util.RedirectUriValidator} when a registered
 * client submits a redirect_uri that violates strict-match policy.
 * Handled in GlobalExceptionHandler -> HTTP 400 invalid_redirect_uri.
 */
public class InvalidRedirectUriException extends RuntimeException {
    public InvalidRedirectUriException(String message) {
        super(message);
    }
}
