package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Payload for creating or updating a movie entry.
 */
public record CreateMovieRequest(
        @NotBlank String title,
        String description,
        String genre,
        @NotNull Integer durationMin,
        String rating,
        String posterUrl,
        LocalDate releaseDate
) {}
