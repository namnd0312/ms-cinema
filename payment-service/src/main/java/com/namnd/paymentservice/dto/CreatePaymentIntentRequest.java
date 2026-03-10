package com.namnd.paymentservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a Stripe PaymentIntent.
 * Amount is in VND (whole numbers, no decimals).
 */
public record CreatePaymentIntentRequest(
        @NotNull Long bookingId,
        @NotNull Long amount
) {}
