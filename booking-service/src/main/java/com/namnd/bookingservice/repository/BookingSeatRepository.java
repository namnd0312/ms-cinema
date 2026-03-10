package com.namnd.bookingservice.repository;

import com.namnd.bookingservice.model.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * JPA repository for BookingSeat entities.
 */
public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    @Query("SELECT bs.seatId FROM BookingSeat bs WHERE bs.showtimeId = :showtimeId " +
           "AND bs.booking.status IN (com.namnd.bookingservice.model.BookingStatus.PENDING, " +
           "com.namnd.bookingservice.model.BookingStatus.CONFIRMED)")
    List<Long> findBookedSeatIdsByShowtimeId(Long showtimeId);
}
