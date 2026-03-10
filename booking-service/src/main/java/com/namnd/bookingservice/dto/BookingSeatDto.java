package com.namnd.bookingservice.dto;

/**
 * DTO representing a single seat within a booking response.
 * Price is a VND snapshot at reservation time.
 */
public record BookingSeatDto(
        Long seatId,
        String seatLabel,
        String seatType,
        Long price
) {}
