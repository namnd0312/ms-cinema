package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CommentReactionDto;
import com.namnd.movieservice.dto.CommentReactionRequest;
import com.namnd.movieservice.model.CommentReaction;
import com.namnd.movieservice.model.CommentStatus;
import com.namnd.movieservice.model.MovieComment;
import com.namnd.movieservice.repository.CommentReactionRepository;
import com.namnd.movieservice.repository.MovieCommentRepository;
import com.namnd.movieservice.service.CommentReactionService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Toggle reaction logic: create new, switch type, or remove on same-click.
 */
@Service
@RequiredArgsConstructor
public class CommentReactionServiceImpl implements CommentReactionService {

    private final CommentReactionRepository reactionRepository;
    private final MovieCommentRepository commentRepository;

    @Override
    @Transactional
    public CommentReactionDto toggleReaction(Long commentId, Long userId, CommentReactionRequest request) {
        MovieComment comment = findActiveCommentOrThrow(commentId);

        Optional<CommentReaction> existing = reactionRepository.findByCommentIdAndUserId(commentId, userId);

        if (existing.isPresent()) {
            CommentReaction reaction = existing.get();
            if (reaction.getIsLike().equals(request.isLike())) {
                // Same type clicked again -> toggle off (remove)
                reactionRepository.delete(reaction);
            } else {
                // Different type -> switch
                reaction.setIsLike(request.isLike());
                reactionRepository.save(reaction);
            }
        } else {
            // New reaction
            CommentReaction reaction = new CommentReaction();
            reaction.setComment(comment);
            reaction.setUserId(userId);
            reaction.setIsLike(request.isLike());
            reactionRepository.save(reaction);
        }

        return buildReactionDto(commentId, userId);
    }

    @Override
    @Transactional
    public CommentReactionDto removeReaction(Long commentId, Long userId) {
        findActiveCommentOrThrow(commentId);
        reactionRepository.findByCommentIdAndUserId(commentId, userId)
                .ifPresent(reactionRepository::delete);
        return buildReactionDto(commentId, userId);
    }

    @Override
    public CommentReactionDto getReactionSummary(Long commentId, Long userId) {
        findActiveCommentOrThrow(commentId);
        return buildReactionDto(commentId, userId);
    }

    private MovieComment findActiveCommentOrThrow(Long commentId) {
        MovieComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new EntityNotFoundException("Comment not found: " + commentId);
        }
        return comment;
    }

    private CommentReactionDto buildReactionDto(Long commentId, Long userId) {
        Long likes = reactionRepository.countLikesByCommentId(commentId);
        Long dislikes = reactionRepository.countDislikesByCommentId(commentId);
        String userReaction = null;
        if (userId != null) {
            userReaction = reactionRepository.findByCommentIdAndUserId(commentId, userId)
                    .map(r -> r.getIsLike() ? "LIKE" : "DISLIKE")
                    .orElse(null);
        }
        return new CommentReactionDto(commentId, likes, dislikes, userReaction);
    }
}
