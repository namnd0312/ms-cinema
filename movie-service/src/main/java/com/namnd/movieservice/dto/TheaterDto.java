package com.namnd.movieservice.dto;

import java.time.LocalDateTime;

/**
 * Read model returned to clients for theater data.
 */
public record TheaterDto(
        Long id,
        String name,
        String location,
        Integer totalRows,
        Integer totalColumns,
        LocalDateTime createdAt
) {}
