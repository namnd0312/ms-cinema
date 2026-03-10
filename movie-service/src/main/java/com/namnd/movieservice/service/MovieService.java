package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateMovieRequest;
import com.namnd.movieservice.dto.MovieDto;

import java.util.List;

/**
 * Business operations for the movie catalog.
 */
public interface MovieService {

    List<MovieDto> findAll();

    MovieDto findById(Long id);

    MovieDto create(CreateMovieRequest request);

    MovieDto update(Long id, CreateMovieRequest request);

    void delete(Long id);
}
