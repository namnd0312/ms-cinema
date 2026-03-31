package com.namnd.paymentservice.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Summary DTO for the latest reconciliation run stats.
 */
public record ReconciliationSummaryResponse(
        Long latestRunId,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        int matched,
        int mismatched,
        int missingLocal,
        int missingStripe,
        LocalDateTime completedAt
) {}
