package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event published when a payment attempt fails.
 * Consumed by booking-service to release locked seats and cancel the booking.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        Long bookingId,
        String reason
) {}
