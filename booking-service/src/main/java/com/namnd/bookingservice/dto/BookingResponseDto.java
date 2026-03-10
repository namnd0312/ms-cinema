package com.namnd.bookingservice.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload returned after reserving or fetching a booking.
 * totalAmount is in VND (Long, no decimals).
 */
public record BookingResponseDto(
        Long id,
        Long showtimeId,
        String status,
        Long totalAmount,
        List<BookingSeatDto> seats,
        LocalDateTime reservedAt,
        LocalDateTime expiresAt
) {}
