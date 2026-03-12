package com.namnd.movieservice.repository;

import com.namnd.movieservice.model.CommentReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Reaction data access. Count queries for like/dislike aggregation.
 */
public interface CommentReactionRepository extends JpaRepository<CommentReaction, Long> {

    Optional<CommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    @Query("SELECT COUNT(r) FROM CommentReaction r WHERE r.comment.id = :commentId AND r.isLike = true")
    Long countLikesByCommentId(@Param("commentId") Long commentId);

    @Query("SELECT COUNT(r) FROM CommentReaction r WHERE r.comment.id = :commentId AND r.isLike = false")
    Long countDislikesByCommentId(@Param("commentId") Long commentId);
}
