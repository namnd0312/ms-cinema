package com.namnd.paymentservice.service;

import com.namnd.paymentservice.dto.*;
import com.namnd.paymentservice.model.DiscrepancyType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Contract for reconciliation operations: triggering runs, querying results, resolving items.
 */
public interface ReconciliationService {

    ReconciliationRunResponse triggerReconciliation(LocalDate startDate, LocalDate endDate);

    Page<ReconciliationRunResponse> getRuns(Pageable pageable);

    ReconciliationRunResponse getRunDetails(Long runId);

    Page<ReconciliationItemResponse> getRunItems(Long runId, DiscrepancyType type, Pageable pageable);

    ReconciliationSummaryResponse getSummary();

    ReconciliationItemResponse resolveItem(Long itemId, String notes);
}
