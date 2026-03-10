package com.namnd.bookingservice.service;

import com.namnd.bookingservice.dto.BookingRequestDto;
import com.namnd.bookingservice.dto.BookingResponseDto;

import java.util.List;

/**
 * Core booking operations: reserve, query, confirm, and cancel.
 */
public interface BookingService {

    /**
     * Reserve seats for a showtime. Locks seats in Redis, calculates total, persists booking.
     */
    BookingResponseDto reserve(Long userId, BookingRequestDto request);

    /**
     * Fetch a booking by ID, enforcing owner-only access.
     */
    BookingResponseDto getBooking(Long bookingId, Long userId);

    /**
     * Return all bookings belonging to the given user.
     */
    List<BookingResponseDto> getUserBookings(Long userId);

    /**
     * Confirm a PENDING booking (called after successful payment).
     */
    void confirmBooking(Long bookingId);

    /**
     * Cancel a booking. Pass null userId for system-initiated cancellations
     * (skips ownership check).
     */
    void cancelBooking(Long bookingId, Long userId);
}
