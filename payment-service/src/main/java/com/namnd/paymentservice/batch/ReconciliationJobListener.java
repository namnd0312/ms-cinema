package com.namnd.paymentservice.batch;

import com.namnd.paymentservice.model.DiscrepancyType;
import com.namnd.paymentservice.model.ReconciliationItem;
import com.namnd.paymentservice.model.ReconciliationRun;
import com.namnd.paymentservice.model.ReconciliationStatus;
import com.namnd.paymentservice.repository.ReconciliationItemRepository;
import com.namnd.paymentservice.repository.ReconciliationRunRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Post-job listener that finds MISSING_LOCAL records via Stripe list API
 * and finalizes aggregated counts on the ReconciliationRun.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationJobListener implements JobExecutionListener {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;

    @Override
    public void afterJob(JobExecution execution) {
        Long runId = execution.getJobParameters().getLong("runId");
        ReconciliationRun run = runRepository.findById(runId).orElseThrow();

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            try {
                findMissingLocalAndFinalize(run, runId);
            } catch (StripeException e) {
                log.error("Failed to list Stripe PaymentIntents for run {}: {}", runId, e.getMessage());
                run.setStatus(ReconciliationStatus.FAILED);
            }
        } else {
            run.setStatus(ReconciliationStatus.FAILED);
        }

        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);
    }

    private void findMissingLocalAndFinalize(ReconciliationRun run, Long runId) throws StripeException {
        PaymentIntentListParams params = PaymentIntentListParams.builder()
                .setCreated(PaymentIntentListParams.Created.builder()
                        .setGte(run.getStartDate().atStartOfDay(ZoneId.of("Asia/Saigon")).toEpochSecond())
                        .setLt(run.getEndDate().plusDays(1).atStartOfDay(ZoneId.of("Asia/Saigon")).toEpochSecond())
                        .build())
                .setLimit(100L)
                .build();

        Set<String> reconciledPiIds = itemRepository.findStripeIdsByRunId(runId);
        int stripeCount = 0;
        List<ReconciliationItem> missingLocal = new ArrayList<>();

        for (PaymentIntent intent : PaymentIntent.list(params).autoPagingIterable()) {
            stripeCount++;
            if (!reconciledPiIds.contains(intent.getId())) {
                missingLocal.add(ReconciliationItem.builder()
                        .run(run)
                        .stripePaymentIntentId(intent.getId())
                        .stripeAmount(intent.getAmount())
                        .stripeStatus(intent.getStatus())
                        .discrepancyType(DiscrepancyType.MISSING_LOCAL)
                        .build());
            }
        }
        itemRepository.saveAll(missingLocal);

        // Finalize counts
        run.setTotalStripeRecords(stripeCount);
        run.setTotalLocalRecords((int) itemRepository.countByRunIdAndDiscrepancyTypeNot(runId, DiscrepancyType.MISSING_LOCAL));
        run.setMatchedCount((int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.MATCHED));
        run.setMismatchedCount(
                (int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.STATUS_MISMATCH)
                + (int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.AMOUNT_MISMATCH));
        run.setMissingLocalCount(missingLocal.size());
        run.setMissingStripeCount((int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.MISSING_STRIPE));
        run.setStatus(ReconciliationStatus.COMPLETED);

        log.info("Reconciliation run {} completed: {} matched, {} mismatched, {} missing local, {} missing stripe",
                runId, run.getMatchedCount(), run.getMismatchedCount(), run.getMissingLocalCount(), run.getMissingStripeCount());
    }
}
