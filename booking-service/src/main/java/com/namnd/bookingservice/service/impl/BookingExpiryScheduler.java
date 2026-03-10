package com.namnd.bookingservice.service.impl;

import com.namnd.bookingservice.model.Booking;
import com.namnd.bookingservice.model.BookingSeat;
import com.namnd.bookingservice.model.BookingStatus;
import com.namnd.bookingservice.repository.BookingRepository;
import com.namnd.bookingservice.service.SeatLockService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that expires PENDING bookings whose payment window has elapsed.
 * Runs every 60 seconds; marks bookings EXPIRED and releases Redis seat locks.
 */
@Component
public class BookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final SeatLockService seatLockService;

    public BookingExpiryScheduler(BookingRepository bookingRepository,
                                   SeatLockService seatLockService) {
        this.bookingRepository = bookingRepository;
        this.seatLockService = seatLockService;
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void expireStaleBookings() {
        List<Booking> stale = bookingRepository
                .findByStatusAndExpiresAtBefore(BookingStatus.PENDING, LocalDateTime.now());

        for (Booking booking : stale) {
            booking.setStatus(BookingStatus.EXPIRED);
            List<Long> seatIds = booking.getSeats().stream()
                    .map(BookingSeat::getSeatId)
                    .toList();
            seatLockService.unlockSeats(booking.getShowtimeId(), seatIds);
        }
    }
}
