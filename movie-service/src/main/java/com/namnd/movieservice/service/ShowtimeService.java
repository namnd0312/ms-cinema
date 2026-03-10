package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateShowtimeRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.ShowtimeDto;

import java.util.List;

/**
 * Business operations for showtime scheduling and seat availability queries.
 */
public interface ShowtimeService {

    List<ShowtimeDto> findAll();

    List<ShowtimeDto> findByMovie(Long movieId);

    ShowtimeDto findById(Long id);

    ShowtimeDto create(CreateShowtimeRequest request);

    ShowtimeDto update(Long id, CreateShowtimeRequest request);

    List<SeatDto> getSeatsForShowtime(Long showtimeId);
}
