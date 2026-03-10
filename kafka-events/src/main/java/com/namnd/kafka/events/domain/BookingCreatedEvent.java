package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Event published when a new booking is created and seats are locked.
 * Consumed by payment-service to initiate payment processing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingCreatedEvent(
        Long bookingId,
        Long userId,
        Long showtimeId,
        List<Long> seatIds,
        Long totalAmount,
        String currency
) {}
