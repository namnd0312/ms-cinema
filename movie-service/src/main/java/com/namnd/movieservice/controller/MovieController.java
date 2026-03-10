package com.namnd.movieservice.controller;

import com.namnd.movieservice.dto.CreateMovieRequest;
import com.namnd.movieservice.dto.MovieDto;
import com.namnd.movieservice.service.MovieService;
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
 * REST endpoints for movie catalog. GET endpoints are public; mutations require ADMIN role.
 */
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
@Tag(name = "Movies", description = "Movie catalog management")
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "List all movies")
    @GetMapping
    public ResponseEntity<List<MovieDto>> listAll() {
        return ResponseEntity.ok(movieService.findAll());
    }

    @Operation(summary = "Get movie by ID")
    @GetMapping("/{id}")
    public ResponseEntity<MovieDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(movieService.findById(id));
    }

    @Operation(summary = "Create a new movie (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovieDto> create(@Valid @RequestBody CreateMovieRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(movieService.create(request));
    }

    @Operation(summary = "Update a movie (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MovieDto> update(@PathVariable Long id,
                                           @Valid @RequestBody CreateMovieRequest request) {
        return ResponseEntity.ok(movieService.update(id, request));
    }

    @Operation(summary = "Delete a movie (ADMIN only)")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        movieService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
