package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateCommentRequest;
import com.namnd.movieservice.dto.MovieCommentDto;
import com.namnd.movieservice.dto.UpdateCommentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Comment business logic: CRUD with ownership checks and soft-delete.
 */
public interface MovieCommentService {
    MovieCommentDto createComment(Long movieId, Long userId, CreateCommentRequest request);
    MovieCommentDto updateComment(Long commentId, Long userId, UpdateCommentRequest request);
    void deleteComment(Long commentId, Long userId, boolean isAdmin);
    Page<MovieCommentDto> getCommentsByMovie(Long movieId, Long userId, Pageable pageable);
}
