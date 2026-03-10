package com.namnd.paymentservice.event;

/**
 * Spring application event fired when a payment is completed.
 * Published inside the transaction, consumed after commit to trigger Kafka.
 */
public record PaymentCompletedSpringEvent(
        Long bookingId,
        String paymentId,
        Long amount,
        String currency
) {}
