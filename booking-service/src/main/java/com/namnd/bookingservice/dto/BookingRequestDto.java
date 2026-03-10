package com.namnd.bookingservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for reserving seats in a showtime.
 * Maximum 10 seats per booking.
 */
public record BookingRequestDto(
        @NotNull Long showtimeId,
        @NotEmpty @Size(max = 10) List<Long> seatIds
) {}
