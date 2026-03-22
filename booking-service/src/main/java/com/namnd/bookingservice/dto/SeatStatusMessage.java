package com.namnd.bookingservice.dto;

import java.util.List;

/**
 * WebSocket message broadcast when seat status changes.
 * Sent to /topic/showtime/{showtimeId}/seats.
 */
public record SeatStatusMessage(
        List<Long> seatIds,
        String status, // LOCKED, UNLOCKED, RESERVED, RELEASED, CONFIRMED
        String timestamp
) {
    public SeatStatusMessage(List<Long> seatIds, String status) {
        this(seatIds, status, java.time.Instant.now().toString());
    }
}
