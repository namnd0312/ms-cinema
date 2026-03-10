package com.namnd.movieservice.service.impl;

import com.namnd.movieservice.dto.CreateTheaterRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.TheaterDto;
import com.namnd.movieservice.model.Seat;
import com.namnd.movieservice.model.SeatType;
import com.namnd.movieservice.model.Theater;
import com.namnd.movieservice.repository.SeatRepository;
import com.namnd.movieservice.repository.TheaterRepository;
import com.namnd.movieservice.service.TheaterService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Theater CRUD with automatic seat grid generation on creation.
 * Row labels are A-Z; seat numbers are 1-based column indices.
 */
@Service
@RequiredArgsConstructor
public class TheaterServiceImpl implements TheaterService {

    private final TheaterRepository theaterRepository;
    private final SeatRepository seatRepository;

    @Override
    public List<TheaterDto> findAll() {
        return theaterRepository.findAll().stream()
                .map(TheaterServiceImpl::toDto)
                .toList();
    }

    @Override
    public TheaterDto findById(Long id) {
        return toDto(findOrThrow(id));
    }

    @Override
    public List<SeatDto> findSeatsById(Long id) {
        findOrThrow(id); // validate theater exists
        return seatRepository.findByTheaterId(id).stream()
                .map(TheaterServiceImpl::toSeatDto)
                .toList();
    }

    @Override
    @Transactional
    public TheaterDto create(CreateTheaterRequest request) {
        Theater theater = new Theater();
        applyRequest(theater, request);
        Theater saved = theaterRepository.save(theater);
        generateSeats(saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public TheaterDto update(Long id, CreateTheaterRequest request) {
        Theater theater = findOrThrow(id);
        applyRequest(theater, request);
        return toDto(theaterRepository.save(theater));
    }

    // --- helpers ---

    private Theater findOrThrow(Long id) {
        return theaterRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Theater not found: " + id));
    }

    private static void applyRequest(Theater theater, CreateTheaterRequest req) {
        theater.setName(req.name());
        theater.setLocation(req.location());
        theater.setTotalRows(req.totalRows());
        theater.setTotalColumns(req.totalColumns());
    }

    /**
     * Auto-generate seats using A-Z row labels and 1-based column numbers.
     * All seats default to STANDARD type with price multiplier 1.0.
     */
    private void generateSeats(Theater theater) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 0; row < theater.getTotalRows(); row++) {
            String rowLabel = String.valueOf((char) ('A' + row));
            for (int col = 1; col <= theater.getTotalColumns(); col++) {
                Seat seat = new Seat();
                seat.setTheater(theater);
                seat.setRowLabel(rowLabel);
                seat.setSeatNumber(col);
                seat.setSeatType(SeatType.STANDARD);
                seat.setPriceMultiplier(BigDecimal.ONE);
                seats.add(seat);
            }
        }
        seatRepository.saveAll(seats);
    }

    public static TheaterDto toDto(Theater t) {
        return new TheaterDto(
                t.getId(),
                t.getName(),
                t.getLocation(),
                t.getTotalRows(),
                t.getTotalColumns(),
                t.getCreatedAt()
        );
    }

    public static SeatDto toSeatDto(Seat s) {
        return new SeatDto(
                s.getId(),
                s.getRowLabel(),
                s.getSeatNumber(),
                s.getSeatType() != null ? s.getSeatType().name() : null,
                s.getPriceMultiplier()
        );
    }
}
