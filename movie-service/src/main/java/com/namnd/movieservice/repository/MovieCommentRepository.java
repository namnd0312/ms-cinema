package com.namnd.movieservice.repository;

import com.namnd.movieservice.model.CommentStatus;
import com.namnd.movieservice.model.MovieComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Comment data access. Paginated finder for active comments by movie.
 */
public interface MovieCommentRepository extends JpaRepository<MovieComment, Long> {

    Page<MovieComment> findByMovieIdAndStatusOrderByCreatedAtDesc(
            Long movieId, CommentStatus status, Pageable pageable);

    Long countByMovieIdAndStatus(Long movieId, CommentStatus status);
}
