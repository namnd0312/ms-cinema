package com.namnd.movieservice.repository;

import com.namnd.movieservice.model.Theater;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TheaterRepository extends JpaRepository<Theater, Long> {
}
