package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payload for creating or updating a showtime slot.
 */
public record CreateShowtimeRequest(
        @NotNull Long movieId,
        @NotNull Long theaterId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime,
        @NotNull BigDecimal basePrice
) {}
