package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Event published when a new showtime is scheduled.
 * Consumed by booking-service to update available seat inventory.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowtimeCreatedEvent(
        Long showtimeId,
        Long movieId,
        Long theaterId,
        LocalDateTime startTime,
        Integer availableSeats
) {}
