package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CreateShowtimeRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.ShowtimeDto;
import com.namnd.movieservice.service.ShowtimeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for showtime scheduling. GET endpoints are public; mutations require ADMIN role.
 */
@RestController
@RequestMapping("/api/showtimes")
@RequiredArgsConstructor
@Tag(name = "Showtimes", description = "Showtime scheduling and seat availability")
public class ShowtimeController {

    private final ShowtimeService showtimeService;

    @Operation(summary = "List all showtimes, optionally filtered by movieId")
    @GetMapping
    public ResponseEntity<List<ShowtimeDto>> listAll(
            @RequestParam(required = false) Long movieId) {
        if (movieId != null) {
            return ResponseEntity.ok(showtimeService.findByMovie(movieId));
        }
        return ResponseEntity.ok(showtimeService.findAll());
    }

    @Operation(summary = "Get showtime by ID")
    @GetMapping("/{id}")
    public ResponseEntity<ShowtimeDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(showtimeService.findById(id));
    }

    @Operation(summary = "Get seat availability for a showtime")
    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatDto>> getSeats(@PathVariable Long id) {
        return ResponseEntity.ok(showtimeService.getSeatsForShowtime(id));
    }

    @Operation(summary = "Create a new showtime (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShowtimeDto> create(@Valid @RequestBody CreateShowtimeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(showtimeService.create(request));
    }

    @Operation(summary = "Update a showtime (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ShowtimeDto> update(@PathVariable Long id,
                                              @Valid @RequestBody CreateShowtimeRequest request) {
        return ResponseEntity.ok(showtimeService.update(id, request));
    }
}
