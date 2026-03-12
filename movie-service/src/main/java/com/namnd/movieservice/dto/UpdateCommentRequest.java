package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an existing comment.
 */
public record UpdateCommentRequest(
        @NotBlank @Size(max = 2000) String content
) {}
