package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CreateTheaterRequest;
import com.namnd.movieservice.dto.SeatDto;
import com.namnd.movieservice.dto.TheaterDto;
import com.namnd.movieservice.service.TheaterService;
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
 * REST endpoints for theater management. GET endpoints are public; mutations require ADMIN role.
 * Creating a theater auto-generates its full seat grid.
 */
@RestController
@RequestMapping("/api/theaters")
@RequiredArgsConstructor
@Tag(name = "Theaters", description = "Theater and seat management")
public class TheaterController {

    private final TheaterService theaterService;

    @Operation(summary = "List all theaters")
    @GetMapping
    public ResponseEntity<List<TheaterDto>> listAll() {
        return ResponseEntity.ok(theaterService.findAll());
    }

    @Operation(summary = "Get theater by ID")
    @GetMapping("/{id}")
    public ResponseEntity<TheaterDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(theaterService.findById(id));
    }

    @Operation(summary = "Get seats for a theater")
    @GetMapping("/{id}/seats")
    public ResponseEntity<List<SeatDto>> getSeats(@PathVariable Long id) {
        return ResponseEntity.ok(theaterService.findSeatsById(id));
    }

    @Operation(summary = "Create a new theater (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TheaterDto> create(@Valid @RequestBody CreateTheaterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(theaterService.create(request));
    }

    @Operation(summary = "Update a theater (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TheaterDto> update(@PathVariable Long id,
                                             @Valid @RequestBody CreateTheaterRequest request) {
        return ResponseEntity.ok(theaterService.update(id, request));
    }
}
