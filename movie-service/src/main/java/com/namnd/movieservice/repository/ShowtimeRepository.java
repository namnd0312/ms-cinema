package com.namnd.movieservice.repository;

import com.namnd.movieservice.model.Showtime;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ShowtimeRepository extends JpaRepository<Showtime, Long> {

    List<Showtime> findByMovieIdAndStartTimeAfter(Long movieId, LocalDateTime after);

    List<Showtime> findByTheaterIdAndStartTimeBetween(Long theaterId, LocalDateTime start, LocalDateTime end);
}
