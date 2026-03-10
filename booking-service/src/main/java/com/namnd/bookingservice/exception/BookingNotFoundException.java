package com.namnd.bookingservice.exception;

/**
 * Thrown when a booking cannot be found by the given ID.
 * Maps to HTTP 404 Not Found.
 */
public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(Long bookingId) {
        super("Booking not found with id: " + bookingId);
    }
}
