package com.namnd.movieservice.dto;

import java.math.BigDecimal;

/**
 * Read model for an individual seat within a theater.
 */
public record SeatDto(
        Long id,
        String rowLabel,
        Integer seatNumber,
        String seatType,
        BigDecimal priceMultiplier
) {}
