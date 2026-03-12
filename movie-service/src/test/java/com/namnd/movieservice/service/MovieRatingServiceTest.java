package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateRatingRequest;
import com.namnd.movieservice.dto.MovieRatingDto;
import com.namnd.movieservice.dto.MovieRatingSummaryDto;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.MovieRating;
import com.namnd.movieservice.repository.MovieRatingRepository;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.service.impl.MovieRatingServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MovieRatingServiceImpl.
 * Tests upsert logic, aggregation, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class MovieRatingServiceTest {

    @Mock
    private MovieRatingRepository ratingRepository;

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieRatingServiceImpl ratingService;

    private static final Long MOVIE_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long OTHER_USER_ID = 101L;

    @Test
    void createOrUpdateRating_newRating_createsSuccessfully() {
        // Arrange
        CreateRatingRequest request = new CreateRatingRequest(5);
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);

        MovieRating savedRating = new MovieRating();
        savedRating.setId(1L);
        savedRating.setMovie(movie);
        savedRating.setUserId(USER_ID);
        savedRating.setRating(5);
        savedRating.setCreatedAt(LocalDateTime.now());

        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        when(ratingRepository.findByMovieIdAndUserId(MOVIE_ID, USER_ID)).thenReturn(Optional.empty());
        when(ratingRepository.save(any(MovieRating.class))).thenReturn(savedRating);

        // Act
        MovieRatingDto result = ratingService.createOrUpdateRating(MOVIE_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals(MOVIE_ID, result.movieId());
        assertEquals(USER_ID, result.userId());
        assertEquals(5, result.rating());
        verify(ratingRepository).save(any(MovieRating.class));
    }

    @Test
    void createOrUpdateRating_existingRating_updatesRating() {
        // Arrange
        CreateRatingRequest request = new CreateRatingRequest(4);
        Movie movie = new Movie();
        movie.setId(MOVIE_ID);

        MovieRating existing = new MovieRating();
        existing.setId(1L);
        existing.setMovie(movie);
        existing.setUserId(USER_ID);
        existing.setRating(3);
        existing.setCreatedAt(LocalDateTime.now().minusDays(1));

        MovieRating updated = new MovieRating();
        updated.setId(1L);
        updated.setMovie(movie);
        updated.setUserId(USER_ID);
        updated.setRating(4);
        updated.setCreatedAt(LocalDateTime.now().minusDays(1));

        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.of(movie));
        when(ratingRepository.findByMovieIdAndUserId(MOVIE_ID, USER_ID)).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any(MovieRating.class))).thenReturn(updated);

        // Act
        MovieRatingDto result = ratingService.createOrUpdateRating(MOVIE_ID, USER_ID, request);

        // Assert
        assertNotNull(result);
        assertEquals(4, result.rating());
        verify(ratingRepository).save(existing);
    }

    @Test
    void createOrUpdateRating_movieNotFound_throwsEntityNotFoundException() {
        // Arrange
        CreateRatingRequest request = new CreateRatingRequest(5);
        when(movieRepository.findById(MOVIE_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () ->
                ratingService.createOrUpdateRating(MOVIE_ID, USER_ID, request)
        );
        verify(ratingRepository, never()).save(any());
    }

    @Test
    void getRatingSummary_withRatings_returnsAverageAndCount() {
        // Arrange
        when(movieRepository.existsById(MOVIE_ID)).thenReturn(true);
        when(ratingRepository.findAverageRatingByMovieId(MOVIE_ID)).thenReturn(4.2);
        when(ratingRepository.countByMovieId(MOVIE_ID)).thenReturn(5L);
        when(ratingRepository.findByMovieIdAndUserId(MOVIE_ID, USER_ID)).thenReturn(Optional.empty());

        // Act
        MovieRatingSummaryDto result = ratingService.getRatingSummary(MOVIE_ID, USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals(4.2, result.averageRating());
        assertEquals(5L, result.totalRatings());
        assertNull(result.userRating());
    }

    @Test
    void getRatingSummary_noRatings_returnsZeros() {
        // Arrange
        when(movieRepository.existsById(MOVIE_ID)).thenReturn(true);
        when(ratingRepository.findAverageRatingByMovieId(MOVIE_ID)).thenReturn(null);
        when(ratingRepository.countByMovieId(MOVIE_ID)).thenReturn(0L);

        // Act
        MovieRatingSummaryDto result = ratingService.getRatingSummary(MOVIE_ID, null);

        // Assert
        assertNotNull(result);
        assertEquals(0.0, result.averageRating());
        assertEquals(0L, result.totalRatings());
        assertNull(result.userRating());
    }

    @Test
    void getRatingSummary_withAuthUser_includesUserRating() {
        // Arrange
        when(movieRepository.existsById(MOVIE_ID)).thenReturn(true);
        when(ratingRepository.findAverageRatingByMovieId(MOVIE_ID)).thenReturn(4.5);
        when(ratingRepository.countByMovieId(MOVIE_ID)).thenReturn(10L);

        MovieRating userRating = new MovieRating();
        userRating.setRating(5);
        when(ratingRepository.findByMovieIdAndUserId(MOVIE_ID, USER_ID)).thenReturn(Optional.of(userRating));

        // Act
        MovieRatingSummaryDto result = ratingService.getRatingSummary(MOVIE_ID, USER_ID);

        // Assert
        assertNotNull(result);
        assertEquals(4.5, result.averageRating());
        assertEquals(10L, result.totalRatings());
        assertEquals(5, result.userRating());
    }
}
