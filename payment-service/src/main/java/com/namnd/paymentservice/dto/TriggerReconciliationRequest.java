package com.namnd.paymentservice.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request body for triggering a reconciliation run with a date range.
 */
public record TriggerReconciliationRequest(
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {}
