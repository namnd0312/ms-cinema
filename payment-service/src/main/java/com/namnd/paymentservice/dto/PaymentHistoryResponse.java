package com.namnd.paymentservice.dto;

import java.time.LocalDateTime;

/**
 * Response DTO representing a single payment record in user history.
 */
public record PaymentHistoryResponse(
        Long id,
        Long bookingId,
        Long amount,
        String currency,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt
) {}
