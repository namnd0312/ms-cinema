package com.namnd.movieservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Read model for a showtime, embedding movie and theater summaries.
 */
public record ShowtimeDto(
        Long id,
        MovieDto movie,
        TheaterDto theater,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal basePrice,
        String status,
        LocalDateTime createdAt
) {}
