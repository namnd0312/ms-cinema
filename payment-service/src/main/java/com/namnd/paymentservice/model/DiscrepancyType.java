package com.namnd.paymentservice.model;

/**
 * Classification of reconciliation comparison results between local and Stripe records.
 */
public enum DiscrepancyType {
    MATCHED,
    STATUS_MISMATCH,
    AMOUNT_MISMATCH,
    MISSING_LOCAL,
    MISSING_STRIPE
}
