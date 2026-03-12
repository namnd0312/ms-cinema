package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CommentReactionDto;
import com.namnd.movieservice.dto.CommentReactionRequest;
import com.namnd.movieservice.model.CommentReaction;
import com.namnd.movieservice.model.CommentStatus;
import com.namnd.movieservice.model.MovieComment;
import com.namnd.movieservice.repository.CommentReactionRepository;
import com.namnd.movieservice.repository.MovieCommentRepository;
import com.namnd.movieservice.service.impl.CommentReactionServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CommentReactionServiceImpl.
 * Tests toggle reaction logic: create, switch type, and remove on same-click.
 */
@ExtendWith(MockitoExtension.class)
class CommentReactionServiceTest {

    @Mock
    private CommentReactionRepository reactionRepository;

    @Mock
    private MovieCommentRepository commentRepository;

    @InjectMocks
    private CommentReactionServiceImpl reactionService;

    private static final Long COMMENT_ID = 10L;
    private static final Long USER_ID = 100L;

    @Test
    void toggleReaction_noExisting_createsNewReaction() {
        // Arrange
        CommentReactionRequest request = new CommentReactionRequest(true);
        MovieComment comment = new MovieComment();
        comment.setId(COMMENT_ID);
        comment.setStatus(CommentStatus.ACTIVE);

        CommentReaction saved = new CommentReaction();
        saved.setId(1L);
        saved.setComment(comment);
        saved.setUserId(USER_ID);
        saved.setIsLike(true);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(saved));
        when(reactionRepository.save(any(CommentReaction.class))).thenReturn(saved);
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(1L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(0L);

        // Act
        CommentReactionDto result = reactionService.toggleReaction(COMMENT_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(COMMENT_ID, result.commentId());
        assertEquals(1L, result.likeCount());
        assertEquals(0L, result.dislikeCount());
        assertEquals("LIKE", result.userReaction());
        verify(reactionRepository).save(any(CommentReaction.class));
        verify(reactionRepository, never()).delete(any());
    }

    @Test
    void toggleReaction_sameType_removesReaction() {
        // Arrange
        CommentReactionRequest request = new CommentReactionRequest(true);
        MovieComment comment = new MovieComment();
        comment.setId(COMMENT_ID);
        comment.setStatus(CommentStatus.ACTIVE);

        CommentReaction existing = new CommentReaction();
        existing.setId(1L);
        existing.setComment(comment);
        existing.setUserId(USER_ID);
        existing.setIsLike(true);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.empty());
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(0L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(0L);

        // Act
        CommentReactionDto result = reactionService.toggleReaction(COMMENT_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(COMMENT_ID, result.commentId());
        assertEquals(0L, result.likeCount());
        assertEquals(0L, result.dislikeCount());
        assertNull(result.userReaction());
        verify(reactionRepository).delete(existing);
        verify(reactionRepository, never()).save(any());
    }

    @Test
    void toggleReaction_differentType_switchesReaction() {
        // Arrange
        CommentReactionRequest request = new CommentReactionRequest(false);
        MovieComment comment = new MovieComment();
        comment.setId(COMMENT_ID);
        comment.setStatus(CommentStatus.ACTIVE);

        CommentReaction existing = new CommentReaction();
        existing.setId(1L);
        existing.setComment(comment);
        existing.setUserId(USER_ID);
        existing.setIsLike(true);

        CommentReaction switched = new CommentReaction();
        switched.setId(1L);
        switched.setComment(comment);
        switched.setUserId(USER_ID);
        switched.setIsLike(false);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID))
                .thenReturn(Optional.of(existing))
                .thenReturn(Optional.of(switched));
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(0L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(1L);
        when(reactionRepository.save(any(CommentReaction.class))).thenReturn(switched);

        // Act
        CommentReactionDto result = reactionService.toggleReaction(COMMENT_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(COMMENT_ID, result.commentId());
        assertEquals(0L, result.likeCount());
        assertEquals(1L, result.dislikeCount());
        assertEquals("DISLIKE", result.userReaction());
        verify(reactionRepository).save(existing);
        verify(reactionRepository, never()).delete(any());
    }

    @Test
    void getReactionSummary_returnsCountsAndUserReaction() {
        // Arrange
        MovieComment comment = new MovieComment();
        comment.setId(COMMENT_ID);
        comment.setStatus(CommentStatus.ACTIVE);

        CommentReaction userReaction = new CommentReaction();
        userReaction.setId(1L);
        userReaction.setComment(comment);
        userReaction.setUserId(USER_ID);
        userReaction.setIsLike(true);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(10L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(2L);
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID))
                .thenReturn(Optional.of(userReaction));

        // Act
        CommentReactionDto result = reactionService.getReactionSummary(COMMENT_ID, USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals(COMMENT_ID, result.commentId());
        assertEquals(10L, result.likeCount());
        assertEquals(2L, result.dislikeCount());
        assertEquals("LIKE", result.userReaction());
    }

    @Test
    void toggleReaction_deletedComment_throwsEntityNotFoundException() {
        // Arrange
        CommentReactionRequest request = new CommentReactionRequest(true);
        MovieComment comment = new MovieComment();
        comment.setId(COMMENT_ID);
        comment.setStatus(CommentStatus.DELETED);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(comment));

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () ->
                reactionService.toggleReaction(COMMENT_ID, USER_ID, request)
        );
        verify(reactionRepository, never()).save(any());
        verify(reactionRepository, never()).delete(any());
    }
}
