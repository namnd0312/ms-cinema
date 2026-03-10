package com.namnd.bookingservice.dto;

import java.math.BigDecimal;

/**
 * DTO received from movie-service representing seat metadata.
 * priceMultiplier is applied to showtime basePrice to compute per-seat price.
 */
public record SeatInfoDto(
        Long id,
        String rowLabel,
        Integer seatNumber,
        String seatType,
        BigDecimal priceMultiplier
) {}
