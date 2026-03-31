package com.namnd.paymentservice.controller;

import com.namnd.paymentservice.dto.*;
import com.namnd.paymentservice.model.DiscrepancyType;
import com.namnd.paymentservice.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST endpoints for managing payment reconciliation runs.
 * Supports triggering, querying, filtering, and resolving discrepancies.
 */
@RestController
@RequestMapping("/api/payments/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reconciliation", description = "Payment reconciliation management")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/trigger")
    @Operation(summary = "Trigger a reconciliation run for a date range")
    public ResponseEntity<ReconciliationRunResponse> trigger(
            @Valid @RequestBody TriggerReconciliationRequest request) {
        return ResponseEntity.ok(reconciliationService.triggerReconciliation(
                request.startDate(), request.endDate()));
    }

    @GetMapping("/runs")
    @Operation(summary = "List all reconciliation runs (paginated)")
    public ResponseEntity<Page<ReconciliationRunResponse>> getRuns(Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getRuns(pageable));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "Get details of a specific reconciliation run")
    public ResponseEntity<ReconciliationRunResponse> getRunDetails(@PathVariable Long runId) {
        return ResponseEntity.ok(reconciliationService.getRunDetails(runId));
    }

    @GetMapping("/runs/{runId}/items")
    @Operation(summary = "List items of a reconciliation run, optionally filtered by discrepancy type")
    public ResponseEntity<Page<ReconciliationItemResponse>> getRunItems(
            @PathVariable Long runId,
            @RequestParam(required = false) DiscrepancyType discrepancyType,
            Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getRunItems(runId, discrepancyType, pageable));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get summary of the latest reconciliation run")
    public ResponseEntity<ReconciliationSummaryResponse> getSummary() {
        return ResponseEntity.ok(reconciliationService.getSummary());
    }

    @PutMapping("/items/{itemId}/resolve")
    @Operation(summary = "Mark a reconciliation item as resolved with notes")
    public ResponseEntity<ReconciliationItemResponse> resolveItem(
            @PathVariable Long itemId,
            @Valid @RequestBody ResolveItemRequest request) {
        return ResponseEntity.ok(reconciliationService.resolveItem(itemId, request.notes()));
    }
}
