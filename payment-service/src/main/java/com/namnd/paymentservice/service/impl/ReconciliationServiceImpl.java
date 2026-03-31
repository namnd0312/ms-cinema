package com.namnd.paymentservice.service.impl;

import com.namnd.paymentservice.dto.*;
import com.namnd.paymentservice.exception.PaymentException;
import com.namnd.paymentservice.model.DiscrepancyType;
import com.namnd.paymentservice.model.ReconciliationItem;
import com.namnd.paymentservice.model.ReconciliationRun;
import com.namnd.paymentservice.model.ReconciliationStatus;
import com.namnd.paymentservice.repository.ReconciliationItemRepository;
import com.namnd.paymentservice.repository.ReconciliationRunRepository;
import com.namnd.paymentservice.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Manages reconciliation lifecycle: validates date ranges, launches batch jobs,
 * queries run/item results, and resolves discrepancy items.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationServiceImpl implements ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;
    private final JobLauncher jobLauncher;
    private final Job reconciliationJob;

    @Value("${reconciliation.max-date-range-days:31}")
    private int maxDateRangeDays;

    @Override
    public ReconciliationRunResponse triggerReconciliation(LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);

        // Save run in its own transaction (Spring Data auto-commits)
        ReconciliationRun run = ReconciliationRun.builder()
                .startDate(startDate)
                .endDate(endDate)
                .status(ReconciliationStatus.RUNNING)
                .build();
        run = runRepository.save(run);

        JobParameters params = new JobParametersBuilder()
                .addString("startDate", startDate.toString())
                .addString("endDate", endDate.toString())
                .addLong("runId", run.getId())
                .addLong("timestamp", System.currentTimeMillis())
                .toJobParameters();

        // Launch outside any transaction — JobRepository manages its own
        try {
            jobLauncher.run(reconciliationJob, params);
        } catch (Exception e) {
            log.error("Failed to launch reconciliation job for run {}: {}", run.getId(), e.getMessage());
            run.setStatus(ReconciliationStatus.FAILED);
            runRepository.save(run);
            throw new PaymentException("Failed to launch reconciliation job: " + e.getMessage());
        }

        return toRunResponse(run);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReconciliationRunResponse> getRuns(Pageable pageable) {
        return runRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toRunResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationRunResponse getRunDetails(Long runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new PaymentException("Reconciliation run not found: " + runId));
        return toRunResponse(run);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReconciliationItemResponse> getRunItems(Long runId, DiscrepancyType type, Pageable pageable) {
        Page<ReconciliationItem> items = (type == null)
                ? itemRepository.findByRunId(runId, pageable)
                : itemRepository.findByRunIdAndDiscrepancyType(runId, type, pageable);
        return items.map(this::toItemResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ReconciliationSummaryResponse getSummary() {
        return runRepository.findFirstByOrderByCreatedAtDesc()
                .map(run -> new ReconciliationSummaryResponse(
                        run.getId(), run.getStartDate(), run.getEndDate(),
                        run.getStatus().name(),
                        run.getMatchedCount(), run.getMismatchedCount(),
                        run.getMissingLocalCount(), run.getMissingStripeCount(),
                        run.getCompletedAt()))
                .orElse(null);
    }

    @Override
    @Transactional
    public ReconciliationItemResponse resolveItem(Long itemId, String notes) {
        ReconciliationItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new PaymentException("Reconciliation item not found: " + itemId));
        item.setResolved(true);
        item.setNotes(notes);
        itemRepository.save(item);
        return toItemResponse(item);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new PaymentException("endDate must be >= startDate");
        }
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > maxDateRangeDays) {
            throw new PaymentException("Date range exceeds maximum of " + maxDateRangeDays + " days");
        }
    }

    private ReconciliationRunResponse toRunResponse(ReconciliationRun run) {
        return new ReconciliationRunResponse(
                run.getId(), run.getStartDate(), run.getEndDate(),
                run.getStatus().name(),
                run.getTotalStripeRecords(), run.getTotalLocalRecords(),
                run.getMatchedCount(), run.getMismatchedCount(),
                run.getMissingLocalCount(), run.getMissingStripeCount(),
                run.getCreatedAt(), run.getCompletedAt());
    }

    private ReconciliationItemResponse toItemResponse(ReconciliationItem item) {
        return new ReconciliationItemResponse(
                item.getId(), item.getRun().getId(),
                item.getStripePaymentIntentId(), item.getLocalPaymentId(),
                item.getDiscrepancyType().name(),
                item.getStripeAmount(), item.getLocalAmount(),
                item.getStripeStatus(), item.getLocalStatus(),
                item.isResolved(), item.getNotes(), item.getCreatedAt());
    }
}
