package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event published when a payment is successfully processed.
 * Consumed by booking-service to confirm the booking.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
        Long bookingId,
        String paymentId,
        Long amount,
        String currency
) {}
