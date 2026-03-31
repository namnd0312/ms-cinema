package com.namnd.paymentservice.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for a single reconciliation comparison item.
 */
public record ReconciliationItemResponse(
        Long id,
        Long runId,
        String stripePaymentIntentId,
        Long localPaymentId,
        String discrepancyType,
        Long stripeAmount,
        Long localAmount,
        String stripeStatus,
        String localStatus,
        boolean resolved,
        String notes,
        LocalDateTime createdAt
) {}
