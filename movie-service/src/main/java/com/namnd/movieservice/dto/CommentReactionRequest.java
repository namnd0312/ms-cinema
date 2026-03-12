package com.namnd.movieservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for toggling a like/dislike reaction.
 */
public record CommentReactionRequest(
        @NotNull Boolean isLike
) {}
