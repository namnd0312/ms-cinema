package com.namnd.movieservice.repository;

import com.namnd.movieservice.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByTheaterId(Long theaterId);

    List<Seat> findByIdIn(List<Long> ids);
}
