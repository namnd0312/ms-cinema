package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a comment on a movie.
 */
public record CreateCommentRequest(
        @NotBlank @Size(max = 2000) String content
) {}
