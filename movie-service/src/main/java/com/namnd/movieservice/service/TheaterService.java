package com.namnd.movieservice.service;

import com.namnd.movieservice.dto.CreateTheaterRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.TheaterDto;

import java.util.List;

/**
 * Business operations for theater management including seat auto-generation.
 */
public interface TheaterService {

    List<TheaterDto> findAll();

    TheaterDto findById(Long id);

    List<SeatDto> findSeatsById(Long id);

    TheaterDto create(CreateTheaterRequest request);

    TheaterDto update(Long id, CreateTheaterRequest request);
}
