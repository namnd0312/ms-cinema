package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateRatingRequest;
import com.namnd.movieservice.dto.MovieRatingDto;
import com.namnd.movieservice.dto.MovieRatingSummaryDto;

/**
 * Rating business logic: upsert and aggregation queries.
 */
public interface MovieRatingService {
    MovieRatingDto createOrUpdateRating(Long movieId, Long userId, CreateRatingRequest request);
    MovieRatingSummaryDto getRatingSummary(Long movieId, Long userId);
}
