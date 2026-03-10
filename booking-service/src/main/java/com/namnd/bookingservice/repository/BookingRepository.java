package com.namnd.bookingservice.repository;

import com.namnd.bookingservice.model.Booking;
import com.namnd.bookingservice.model.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA repository for Booking aggregate.
 * Provides queries for user history and expiry scanning.
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByStatusAndExpiresAtBefore(BookingStatus status, LocalDateTime before);
}
