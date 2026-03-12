package com.namnd.movieservice.dto;

/**
 * Aggregated rating summary for a movie. userRating is null for unauthenticated users.
 */
public record MovieRatingSummaryDto(
        Double averageRating,
        Long totalRatings,
        Integer userRating
) {}
