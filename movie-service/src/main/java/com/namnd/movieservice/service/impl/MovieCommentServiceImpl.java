package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CreateCommentRequest;
import com.namnd.movieservice.dto.MovieCommentDto;
import com.namnd.movieservice.dto.UpdateCommentRequest;
import com.namnd.movieservice.model.CommentStatus;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.MovieComment;
import com.namnd.movieservice.repository.CommentReactionRepository;
import com.namnd.movieservice.repository.MovieCommentRepository;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.service.MovieCommentService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comment CRUD with ownership enforcement and soft-delete.
 */
@Service
@RequiredArgsConstructor
public class MovieCommentServiceImpl implements MovieCommentService {

    private final MovieCommentRepository commentRepository;
    private final MovieRepository movieRepository;
    private final CommentReactionRepository reactionRepository;

    @Override
    @Transactional
    public MovieCommentDto createComment(Long movieId, Long userId, CreateCommentRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + movieId));

        MovieComment comment = new MovieComment();
        comment.setMovie(movie);
        comment.setUserId(userId);
        comment.setContent(request.content());
        return toDto(commentRepository.save(comment), userId);
    }

    @Override
    @Transactional
    public MovieCommentDto updateComment(Long commentId, Long userId, UpdateCommentRequest request) {
        MovieComment comment = findActiveOrThrow(commentId);
        if (!comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only edit your own comments");
        }
        comment.setContent(request.content());
        return toDto(commentRepository.save(comment), userId);
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId, boolean isAdmin) {
        MovieComment comment = findActiveOrThrow(commentId);
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own comments");
        }
        comment.setStatus(CommentStatus.DELETED);
        commentRepository.save(comment);
    }

    @Override
    public Page<MovieCommentDto> getCommentsByMovie(Long movieId, Long userId, Pageable pageable) {
        if (!movieRepository.existsById(movieId)) {
            throw new EntityNotFoundException("Movie not found: " + movieId);
        }
        return commentRepository
                .findByMovieIdAndStatusOrderByCreatedAtDesc(movieId, CommentStatus.ACTIVE, pageable)
                .map(c -> toDto(c, userId));
    }

    private MovieComment findActiveOrThrow(Long commentId) {
        MovieComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
        if (comment.getStatus() == CommentStatus.DELETED) {
            throw new EntityNotFoundException("Comment not found: " + commentId);
        }
        return comment;
    }

    private MovieCommentDto toDto(MovieComment c, Long userId) {
        Long likes = reactionRepository.countLikesByCommentId(c.getId());
        Long dislikes = reactionRepository.countDislikesByCommentId(c.getId());
        String userReaction = null;
        if (userId != null) {
            userReaction = reactionRepository.findByCommentIdAndUserId(c.getId(), userId)
                    .map(r -> r.getIsLike() ? "LIKE" : "DISLIKE")
                    .orElse(null);
        }
        return new MovieCommentDto(c.getId(), c.getMovie().getId(), c.getUserId(),
                c.getContent(), likes, dislikes, userReaction,
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
