package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateCommentRequest;
import com.namnd.movieservice.dto.MovieCommentDto;
import com.namnd.movieservice.dto.UpdateCommentRequest;
import com.namnd.movieservice.model.CommentStatus;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.MovieComment;
import com.namnd.movieservice.repository.CommentReactionRepository;
import com.namnd.movieservice.repository.MovieCommentRepository;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.service.impl.MovieCommentServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovieCommentServiceImpl.
 * Tests CRUD operations with ownership checks and soft-delete.
 */
@ExtendWith(MockitoExtension.class)
class MovieCommentServiceTest {

    @Mock
    private MovieCommentRepository commentRepository;

    @Mock
    private MovieRepository movieRepository;

    @Mock
    private CommentReactionRepository reactionRepository;

    @InjectMocks
    private MovieCommentServiceImpl commentService;

    private static final Long MOVIE_ID = 1L;
    private static final Long COMMENT_ID = 10L;
    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 101L;

    @Test
    void createComment_validInput_createsSuccessfully() {
        // Arrange
        CreateCommentRequest request = new CreateCommentRequest("Great movie!");
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);

        MovieComment saved = new MovieComment();
        saved.setId(COMMENT_ID);
        saved.setMovie(movie);
        saved.setUserId(USER_ID);
        saved.setContent("Great movie!");
        saved.setStatus(CommentStatus.ACTIVE);
        saved.setCreatedAt(LocalDateTime.now());
        saved.setUpdatedAt(LocalDateTime.now());

        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        when(commentRepository.save(any(MovieComment.class))).thenReturn(saved);
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(0L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(0L);
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        MovieCommentDto result = commentService.createComment(MOVIE_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(COMMENT_ID, result.id());
        assertEquals(MOVIE_ID, result.movieId());
        assertEquals(USER_ID, result.userId());
        assertEquals("Great movie!", result.content());
        assertEquals(0L, result.likeCount());
        assertEquals(0L, result.dislikeCount());
        assertNull(result.userReaction());
        verify(commentRepository).save(any(MovieComment.class));
    }

    @Test
    void updateComment_ownerUpdates_updatesContent() {
        // Arrange
        UpdateCommentRequest request = new UpdateCommentRequest("Updated content");
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);

        MovieComment existing = new MovieComment();
        existing.setId(COMMENT_ID);
        existing.setMovie(movie);
        existing.setUserId(USER_ID);
        existing.setContent("Old content");
        existing.setStatus(CommentStatus.ACTIVE);
        existing.setCreatedAt(LocalDateTime.now().minusHours(1));
        existing.setUpdatedAt(LocalDateTime.now().minusHours(1));

        MovieComment updated = new MovieComment();
        updated.setId(COMMENT_ID);
        updated.setMovie(movie);
        updated.setUserId(USER_ID);
        updated.setContent("Updated content");
        updated.setStatus(CommentStatus.ACTIVE);
        updated.setCreatedAt(existing.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(MovieComment.class))).thenReturn(updated);
        when(reactionRepository.countLikesByCommentId(COMMENT_ID)).thenReturn(2L);
        when(reactionRepository.countDislikesByCommentId(COMMENT_ID)).thenReturn(1L);
        when(reactionRepository.findByCommentIdAndUserId(COMMENT_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        MovieCommentDto result = commentService.updateComment(COMMENT_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals("Updated content", result.content());
        assertEquals(2L, result.likeCount());
        assertEquals(1L, result.dislikeCount());
        verify(commentRepository).save(existing);
    }

    @Test
    void updateComment_nonOwner_throwsAccessDeniedException() {
        // Arrange
        UpdateCommentRequest request = new UpdateCommentRequest("Hacked!");
        MovieComment existing = new MovieComment();
        existing.setId(COMMENT_ID);
        existing.setUserId(USER_ID);
        existing.setStatus(CommentStatus.ACTIVE);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () ->
                commentService.updateComment(COMMENT_ID, OTHER_USER_ID, request)
        );
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_ownerDeletes_setsStatusDeleted() {
        // Arrange
        MovieComment existing = new MovieComment();
        existing.setId(COMMENT_ID);
        existing.setUserId(USER_ID);
        existing.setStatus(CommentStatus.ACTIVE);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(MovieComment.class))).thenReturn(existing);

        // Act
        commentService.deleteComment(COMMENT_ID, USER_ID, false);

        // Assert
        assertEquals(CommentStatus.DELETED, existing.getStatus());
        verify(commentRepository).save(existing);
    }

    @Test
    void deleteComment_adminDeletes_setsStatusDeleted() {
        // Arrange
        MovieComment existing = new MovieComment();
        existing.setId(COMMENT_ID);
        existing.setUserId(USER_ID);
        existing.setStatus(CommentStatus.ACTIVE);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(MovieComment.class))).thenReturn(existing);

        // Act
        commentService.deleteComment(COMMENT_ID, OTHER_USER_ID, true);

        // Assert
        assertEquals(CommentStatus.DELETED, existing.getStatus());
        verify(commentRepository).save(existing);
    }

    @Test
    void deleteComment_nonOwner_throwsAccessDeniedException() {
        // Arrange
        MovieComment existing = new MovieComment();
        existing.setId(COMMENT_ID);
        existing.setUserId(USER_ID);
        existing.setStatus(CommentStatus.ACTIVE);

        when(commentRepository.findById(COMMENT_ID)).thenReturn(Optional.of(existing));

        // Act & Assert
        assertThrows(AccessDeniedException.class, () ->
                commentService.deleteComment(COMMENT_ID, OTHER_USER_ID, false)
        );
        verify(commentRepository, never()).save(any());
    }

    @Test
    void getCommentsByMovie_returnsPaginatedActiveComments() {
        // Arrange
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);

        MovieComment comment1 = new MovieComment();
        comment1.setId(10L);
        comment1.setMovie(movie);
        comment1.setUserId(USER_ID);
        comment1.setContent("First comment");
        comment1.setStatus(CommentStatus.ACTIVE);
        comment1.setCreatedAt(LocalDateTime.now());
        comment1.setUpdatedAt(LocalDateTime.now());

        MovieComment comment2 = new MovieComment();
        comment2.setId(11L);
        comment2.setMovie(movie);
        comment2.setUserId(OTHER_USER_ID);
        comment2.setContent("Second comment");
        comment2.setStatus(CommentStatus.ACTIVE);
        comment2.setCreatedAt(LocalDateTime.now().minusHours(1));
        comment2.setUpdatedAt(LocalDateTime.now().minusHours(1));

        Pageable pageable = PageRequest.of(0, 10);
        Page<MovieComment> page = new PageImpl<>(Arrays.asList(comment1, comment2), pageable, 2);

        when(movieRepository.existsById(MOVIE_ID)).thenReturn(true);
        when(commentRepository.findByMovieIdAndStatusOrderByCreatedAtDesc(MOVIE_ID, CommentStatus.ACTIVE, pageable))
                .thenReturn(page);
        when(reactionRepository.countLikesByCommentId(10L)).thenReturn(5L);
        when(reactionRepository.countDislikesByCommentId(10L)).thenReturn(1L);
        when(reactionRepository.findByCommentIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());
        when(reactionRepository.countLikesByCommentId(11L)).thenReturn(2L);
        when(reactionRepository.countDislikesByCommentId(11L)).thenReturn(0L);
        when(reactionRepository.findByCommentIdAndUserId(11L, USER_ID)).thenReturn(Optional.empty());

        // Act
        Page<MovieCommentDto> result = commentService.getCommentsByMovie(MOVIE_ID, USER_ID, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.getContent().size());
        assertEquals("First comment", result.getContent().get(0).content());
        assertEquals(5L, result.getContent().get(0).likeCount());
        assertEquals("Second comment", result.getContent().get(1).content());
        assertEquals(2L, result.getContent().get(1).likeCount());
    }
}
