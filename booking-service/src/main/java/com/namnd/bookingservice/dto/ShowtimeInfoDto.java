package com.namnd.bookingservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO received from movie-service for showtime details.
 * Only basePrice is required; other fields are captured for completeness.
 * Unknown fields from nested objects (movie, theater) are ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShowtimeInfoDto(
        Long id,
        Object movie,
        Object theater,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal basePrice,
        String status,
        LocalDateTime createdAt
) {}
