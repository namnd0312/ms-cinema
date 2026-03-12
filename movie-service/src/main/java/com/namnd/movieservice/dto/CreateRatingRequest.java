package com.namnd.movieservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for rating a movie (1-5 stars).
 */
public record CreateRatingRequest(
        @NotNull @Min(1) @Max(5) Integer rating
) {}
