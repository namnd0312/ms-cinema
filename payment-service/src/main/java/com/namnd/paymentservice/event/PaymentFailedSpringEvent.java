package com.namnd.paymentservice.event;

/**
 * Spring application event fired when a payment fails.
 * Published inside the transaction, consumed after commit to trigger Kafka.
 */
public record PaymentFailedSpringEvent(
        Long bookingId,
        String reason
) {}
