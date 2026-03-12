package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CreateRatingRequest;
import com.namnd.movieservice.dto.MovieRatingDto;
import com.namnd.movieservice.dto.MovieRatingSummaryDto;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.MovieRating;
import com.namnd.movieservice.repository.MovieRatingRepository;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.service.MovieRatingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upsert rating logic: creates new or updates existing rating per user+movie.
 */
@Service
@RequiredArgsConstructor
public class MovieRatingServiceImpl implements MovieRatingService {

    private final MovieRatingRepository ratingRepository;
    private final MovieRepository movieRepository;

    @Override
    @Transactional
    public MovieRatingDto createOrUpdateRating(Long movieId, Long userId, CreateRatingRequest request) {
        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + movieId));

        MovieRating rating = ratingRepository.findByMovieIdAndUserId(movieId, userId)
                .orElseGet(() -> {
                    MovieRating r = new MovieRating();
                    r.setMovie(movie);
                    r.setUserId(userId);
                    return r;
                });

        rating.setRating(request.rating());
        MovieRating saved = ratingRepository.save(rating);
        return toDto(saved);
    }

    @Override
    public MovieRatingSummaryDto getRatingSummary(Long movieId, Long userId) {
        if (!movieRepository.existsById(movieId)) {
            throw new EntityNotFoundException("Movie not found: " + movieId);
        }

        Double avg = ratingRepository.findAverageRatingByMovieId(movieId);
        Long total = ratingRepository.countByMovieId(movieId);
        Integer userRating = null;

        if (userId != null) {
            userRating = ratingRepository.findByMovieIdAndUserId(movieId, userId)
                    .map(MovieRating::getRating)
                    .orElse(null);
        }

        return new MovieRatingSummaryDto(
                avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0,
                total,
                userRating
        );
    }

    private static MovieRatingDto toDto(MovieRating r) {
        return new MovieRatingDto(r.getId(), r.getMovie().getId(), r.getUserId(),
                r.getRating(), r.getCreatedAt());
    }
}
