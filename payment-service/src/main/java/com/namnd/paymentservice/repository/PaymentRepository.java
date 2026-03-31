package com.namnd.paymentservice.repository;

import com.namnd.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA repository for Payment entity with lookup methods
 * by Stripe PaymentIntent ID, booking ID, and user ID.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<Payment> findByBookingId(Long bookingId);

    List<Payment> findByUserId(Long userId);

    @Query("SELECT p FROM Payment p WHERE p.createdAt >= :start AND p.createdAt < :end AND p.stripePaymentIntentId IS NOT NULL")
    List<Payment> findByDateRangeWithStripeId(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
