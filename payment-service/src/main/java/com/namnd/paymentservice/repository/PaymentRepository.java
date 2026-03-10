package com.namnd.paymentservice.repository;

import com.namnd.paymentservice.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
