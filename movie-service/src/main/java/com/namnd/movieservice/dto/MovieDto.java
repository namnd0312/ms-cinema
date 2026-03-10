package com.namnd.movieservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read model returned to clients for movie data.
 */
public record MovieDto(
        Long id,
        String title,
        String description,
        String genre,
        Integer durationMin,
        String rating,
        String posterUrl,
        LocalDate releaseDate,
        String status,
        LocalDateTime createdAt
) {}
