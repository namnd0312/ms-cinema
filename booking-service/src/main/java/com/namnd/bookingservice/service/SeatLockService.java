package com.namnd.bookingservice.service;

import java.util.List;

/**
 * Service for managing distributed seat locks via Redis.
 * Locks use SET NX EX pattern with 5-minute TTL.
 */
public interface SeatLockService {

    /**
     * Atomically lock all requested seats for a showtime.
     * If any seat is already locked, rolls back previously acquired locks and returns false.
     *
     * @param showtimeId showtime the seats belong to
     * @param seatIds    seats to lock (processed in sorted order to prevent deadlock)
     * @param userId     owner of the lock
     * @return true if all seats were successfully locked
     */
    boolean lockSeats(Long showtimeId, List<Long> seatIds, Long userId);

    /**
     * Release all seat locks for the given showtime and seat list.
     */
    void unlockSeats(Long showtimeId, List<Long> seatIds);

    /**
     * Check whether a specific seat is currently locked.
     */
    boolean isLocked(Long showtimeId, Long seatId);
}
