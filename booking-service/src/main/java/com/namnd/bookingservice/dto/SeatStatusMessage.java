package com.namnd.bookingservice.dto;

import java.time.Instant;
import java.util.List;

/**
 * WebSocket message broadcast when seat status changes.
 * Sent to /topic/showtime/{showtimeId}/seats.
 */
public record SeatStatusMessage(
        List<Long> seatIds,
        String status, // LOCKED, UNLOCKED, RESERVED, RELEASED, CONFIRMED
        Instant timestamp
) {
    public SeatStatusMessage(List<Long> seatIds, String status) {
        this(seatIds, status, Instant.now());
    }
}
