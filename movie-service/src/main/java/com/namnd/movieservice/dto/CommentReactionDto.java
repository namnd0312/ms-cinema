package com.namnd.movieservice.dto;

/**
 * Response for reaction state of a comment. userReaction: "LIKE", "DISLIKE", or null.
 */
public record CommentReactionDto(
        Long commentId,
        Long likeCount,
        Long dislikeCount,
        String userReaction
) {}
