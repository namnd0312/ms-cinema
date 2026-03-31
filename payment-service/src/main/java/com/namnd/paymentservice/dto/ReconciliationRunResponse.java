package com.namnd.paymentservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for a reconciliation run with aggregated counts.
 */
public record ReconciliationRunResponse(
        Long id,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        int totalStripeRecords,
        int totalLocalRecords,
        int matchedCount,
        int mismatchedCount,
        int missingLocalCount,
        int missingStripeCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {}
