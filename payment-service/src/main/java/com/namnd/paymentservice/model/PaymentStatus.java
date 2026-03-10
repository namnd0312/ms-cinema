package com.namnd.paymentservice.model;

/**
 * Lifecycle states for a payment record.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
