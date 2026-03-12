package com.namnd.movieservice.dto;

import java.time.LocalDateTime;

/**
 * Response for a single user rating.
 */
public record MovieRatingDto(
        Long id,
        Long movieId,
        Long userId,
        Integer rating,
        LocalDateTime createdAt
) {}
