package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CreateMovieRequest;
import com.namnd.movieservice.dto.MovieDto;
import com.namnd.movieservice.event.MovieEventPublisher;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.MovieStatus;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.service.MovieService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD implementation for movie catalog. Soft-delete sets status=INACTIVE.
 */
@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieRepository movieRepository;
    private final MovieEventPublisher movieEventPublisher;

    @Override
    public List<MovieDto> findAll() {
        return movieRepository.findAll().stream()
                .map(MovieServiceImpl::toDto)
                .toList();
    }

    @Override
    public MovieDto findById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Override
    @Transactional
    public MovieDto create(CreateMovieRequest request) {
        Movie movie = new Movie();
        applyRequest(movie, request);
        Movie savedMovie = movieRepository.save(movie);
        movieEventPublisher.publishMovieCreated(savedMovie.getId(), savedMovie.getTitle());
        return toDto(savedMovie);
    }

    @Override
    @Transactional
    public MovieDto update(Long id, CreateMovieRequest request) {
        Movie movie = findOrThrow(id);
        applyRequest(movie, request);
        return toDto(movieRepository.save(movie));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Movie movie = findOrThrow(id);
        movie.setStatus(MovieStatus.INACTIVE);
        movieRepository.save(movie);
    }

    // --- helpers ---

    private Movie findOrThrow(Long id) {
        return movieRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + id));
    }

    private static void applyRequest(Movie movie, CreateMovieRequest req) {
        movie.setTitle(req.title());
        movie.setDescription(req.description());
        movie.setGenre(req.genre());
        movie.setDurationMin(req.durationMin());
        movie.setRating(req.rating());
        movie.setPosterUrl(req.posterUrl());
        movie.setReleaseDate(req.releaseDate());
    }

    public static MovieDto toDto(Movie m) {
        return new MovieDto(
                m.getId(),
                m.getTitle(),
                m.getDescription(),
                m.getGenre(),
                m.getDurationMin(),
                m.getRating(),
                m.getPosterUrl(),
                m.getReleaseDate(),
                m.getStatus() != null ? m.getStatus().name() : null,
                m.getCreatedAt()
        );
    }
}
