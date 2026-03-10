package com.namnd.paymentservice.exception;

/**
 * Runtime exception for payment business logic errors and Stripe API failures.
 */
public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
