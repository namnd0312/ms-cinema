package com.namnd.movieservice.dto;

import java.time.LocalDateTime;

/**
 * Response for a comment with reaction counts. userReaction: "LIKE", "DISLIKE", or null.
 */
public record MovieCommentDto(
        Long id,
        Long movieId,
        Long userId,
        String content,
        Long likeCount,
        Long dislikeCount,
        String userReaction,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
