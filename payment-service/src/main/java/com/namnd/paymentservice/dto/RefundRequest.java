package com.namnd.paymentservice.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for initiating a refund on an existing payment.
 */
public record RefundRequest(
        @NotNull Long paymentId
) {}
