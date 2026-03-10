package com.namnd.paymentservice.dto;

/**
 * Response DTO returned after creating a Stripe PaymentIntent.
 * clientSecret is passed to the frontend for Stripe.js confirmation.
 */
public record PaymentIntentResponse(
        Long paymentId,
        String clientSecret,
        String status
) {}
