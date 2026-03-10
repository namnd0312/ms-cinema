package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CreateShowtimeRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.ShowtimeDto;
import com.namnd.movieservice.event.MovieEventPublisher;
import com.namnd.movieservice.model.Movie;
import com.namnd.movieservice.model.Showtime;
import com.namnd.movieservice.model.Theater;
import com.namnd.movieservice.repository.MovieRepository;
import com.namnd.movieservice.repository.SeatRepository;
import com.namnd.movieservice.repository.ShowtimeRepository;
import com.namnd.movieservice.repository.TheaterRepository;
import com.namnd.movieservice.service.ShowtimeService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Showtime CRUD + seat availability query delegating to theater's seat list.
 */
@Service
@RequiredArgsConstructor
public class ShowtimeServiceImpl implements ShowtimeService {

    private final ShowtimeRepository showtimeRepository;
    private final MovieRepository movieRepository;
    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;
    private final MovieEventPublisher movieEventPublisher;

    @Override
    public List<ShowtimeDto> findAll() {
        return showtimeRepository.findAll().stream()
                .map(ShowtimeServiceImpl::toDto)
                .toList();
    }

    @Override
    public List<ShowtimeDto> findByMovie(Long movieId) {
        return showtimeRepository
                .findByMovieIdAndStartTimeAfter(movieId, LocalDateTime.now())
                .stream()
                .map(ShowtimeServiceImpl::toDto)
                .toList();
    }

    @Override
    public ShowtimeDto findById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Override
    @Transactional
    public ShowtimeDto create(CreateShowtimeRequest request) {
        Showtime showtime = new Showtime();
        applyRequest(showtime, request);
        Showtime saved = showtimeRepository.save(showtime);
        int availableSeats = saved.getTheater().getTotalRows() * saved.getTheater().getTotalColumns();
        movieEventPublisher.publishShowtimeCreated(
                saved.getId(),
                saved.getMovie().getId(),
                saved.getTheater().getId(),
                saved.getStartTime(),
                availableSeats
        );
        return toDto(saved);
    }

    @Override
    @Transactional
    public ShowtimeDto update(Long id, CreateShowtimeRequest request) {
        Showtime showtime = findOrThrow(id);
        applyRequest(showtime, request);
        return toDto(showtimeRepository.save(showtime));
    }

    @Override
    public List<SeatDto> getSeatsForShowtime(Long showtimeId) {
        Showtime showtime = findOrThrow(showtimeId);
        Long theaterId = showtime.getTheater().getId();
        return seatRepository.findByTheaterId(theaterId).stream()
                .map(TheaterServiceImpl::toSeatDto)
                .toList();
    }

    // --- helpers ---

    private Showtime findOrThrow(Long id) {
        return showtimeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Showtime not found: " + id));
    }

    private void applyRequest(Showtime showtime, CreateShowtimeRequest req) {
        Movie movie = movieRepository.findById(req.movieId())
                .orElseThrow(() -> new EntityNotFoundException("Movie not found: " + req.movieId()));
        Theater theater = theaterRepository.findById(req.theaterId())
                .orElseThrow(() -> new EntityNotFoundException("Theater not found: " + req.theaterId()));
        showtime.setMovie(movie);
        showtime.setTheater(theater);
        showtime.setStartTime(req.startTime());
        showtime.setEndTime(req.endTime());
        showtime.setBasePrice(req.basePrice());
    }

    public static ShowtimeDto toDto(Showtime s) {
        return new ShowtimeDto(
                s.getId(),
                MovieServiceImpl.toDto(s.getMovie()),
                TheaterServiceImpl.toDto(s.getTheater()),
                s.getStartTime(),
                s.getEndTime(),
                s.getBasePrice(),
                s.getStatus() != null ? s.getStatus().name() : null,
                s.getCreatedAt()
        );
    }
}
