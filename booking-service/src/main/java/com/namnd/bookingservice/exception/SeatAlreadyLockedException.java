package com.namnd.bookingservice.exception;

/**
 * Thrown when one or more requested seats are already locked by another user.
 * Maps to HTTP 409 Conflict.
 */
public class SeatAlreadyLockedException extends RuntimeException {

    public SeatAlreadyLockedException(Long showtimeId) {
        super("One or more seats are already locked for showtime " + showtimeId
                + ". Please try again later.");
    }
}
