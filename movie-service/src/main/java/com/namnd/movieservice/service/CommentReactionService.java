package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CommentReactionDto;
import com.namnd.movieservice.dto.CommentReactionRequest;

/**
 * Reaction business logic: toggle like/dislike with create/switch/remove semantics.
 */
public interface CommentReactionService {
    CommentReactionDto toggleReaction(Long commentId, Long userId, CommentReactionRequest request);
    CommentReactionDto removeReaction(Long commentId, Long userId);
    CommentReactionDto getReactionSummary(Long commentId, Long userId);
}
