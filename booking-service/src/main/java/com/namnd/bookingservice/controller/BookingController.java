package com.namnd.bookingservice.controller;

import com.namnd.bookingservice.dto.BookingRequestDto;
import com.namnd.bookingservice.dto.BookingResponseDto;
import com.namnd.bookingservice.repository.BookingSeatRepository;
import com.namnd.bookingservice.service.BookingService;
import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing booking endpoints under /api/bookings.
 * All endpoints require a valid JWT (configured in application.yml).
 */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Bookings", description = "Seat booking and reservation management")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;
    private final BookingSeatRepository bookingSeatRepository;

    public BookingController(BookingService bookingService, BookingSeatRepository bookingSeatRepository) {
        this.bookingService = bookingService;
        this.bookingSeatRepository = bookingSeatRepository;
    }

    @Operation(summary = "Get booked/reserved seat IDs for a showtime")
    @GetMapping("/showtimes/{showtimeId}/booked-seats")
    public ResponseEntity<List<Long>> getBookedSeats(@PathVariable Long showtimeId) {
        return ResponseEntity.ok(bookingSeatRepository.findBookedSeatIdsByShowtimeId(showtimeId));
    }

    @Operation(summary = "Reserve seats for a showtime")
    @PostMapping("/reserve")
    public ResponseEntity<BookingResponseDto> reserve(@Valid @RequestBody BookingRequestDto request) {
        Long userId = extractUserId();
        BookingResponseDto response = bookingService.reserve(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get a booking by ID (owner only)")
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponseDto> getBooking(@PathVariable Long id) {
        Long userId = extractUserId();
        return ResponseEntity.ok(bookingService.getBooking(id, userId));
    }

    @Operation(summary = "List all bookings for the authenticated user")
    @GetMapping("/my")
    public ResponseEntity<List<BookingResponseDto>> getMyBookings() {
        Long userId = extractUserId();
        return ResponseEntity.ok(bookingService.getUserBookings(userId));
    }

    @Operation(summary = "Confirm a booking after payment success (internal)")
    @PostMapping("/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id) {
        bookingService.confirmBooking(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cancel a booking (owner only)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        Long userId = extractUserId();
        bookingService.cancelBooking(id, userId);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId() {
        JwtAuthenticatedUser user = (JwtAuthenticatedUser)
                SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.userId();
    }
}
