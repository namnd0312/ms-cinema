package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for creating or updating a theater, including seat grid dimensions.
 */
public record CreateTheaterRequest(
        @NotBlank String name,
        @NotBlank String location,
        @NotNull Integer totalRows,
        @NotNull Integer totalColumns
) {}
