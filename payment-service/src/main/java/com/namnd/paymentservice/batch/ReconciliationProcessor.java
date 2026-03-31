package com.namnd.paymentservice.batch;

import com.namnd.paymentservice.model.DiscrepancyType;
import com.namnd.paymentservice.model.Payment;
import com.namnd.paymentservice.model.ReconciliationItem;
import com.namnd.paymentservice.model.ReconciliationRun;
import com.namnd.paymentservice.repository.ReconciliationRunRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Compares each local Payment against its Stripe PaymentIntent.
 * Retrieves Stripe PI via PaymentIntent.retrieve() and classifies discrepancies.
 */
@Slf4j
@Component
@StepScope
public class ReconciliationProcessor implements ItemProcessor<Payment, ReconciliationItem> {

    private final ReconciliationRunRepository runRepository;
    private final Long runId;
    private ReconciliationRun run;

    public ReconciliationProcessor(ReconciliationRunRepository runRepository,
                                   @Value("#{jobParameters['runId']}") Long runId) {
        this.runRepository = runRepository;
        this.runId = runId;
    }

    @Override
    public ReconciliationItem process(Payment local) {
        if (run == null) {
            run = runRepository.findById(runId).orElseThrow();
        }

        ReconciliationItem item = ReconciliationItem.builder()
                .run(run)
                .localPaymentId(local.getId())
                .localAmount(local.getAmount())
                .localStatus(local.getStatus().name())
                .stripePaymentIntentId(local.getStripePaymentIntentId())
                .build();

        // Local payment without valid Stripe PI ID
        if (local.getStripePaymentIntentId() == null
                || local.getStripePaymentIntentId().startsWith("FAKE-")) {
            item.setDiscrepancyType(DiscrepancyType.MISSING_STRIPE);
            return item;
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(local.getStripePaymentIntentId());
            item.setStripeAmount(intent.getAmount());
            item.setStripeStatus(intent.getStatus());

            if (!intent.getAmount().equals(local.getAmount())) {
                item.setDiscrepancyType(DiscrepancyType.AMOUNT_MISMATCH);
                return item;
            }

            String expectedLocal = mapStripeStatus(intent.getStatus());
            if (!local.getStatus().name().equals(expectedLocal)) {
                item.setDiscrepancyType(DiscrepancyType.STATUS_MISMATCH);
                return item;
            }

            item.setDiscrepancyType(DiscrepancyType.MATCHED);
        } catch (StripeException e) {
            log.warn("Stripe API error for PI {}: {}", local.getStripePaymentIntentId(), e.getMessage());
            item.setDiscrepancyType(DiscrepancyType.MISSING_STRIPE);
            item.setNotes("Stripe API error: " + e.getMessage());
        }
        return item;
    }

    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> "COMPLETED";
            case "canceled" -> "FAILED";
            case "requires_payment_method", "requires_confirmation",
                 "requires_action", "processing", "requires_capture" -> "PENDING";
            default -> "PENDING";
        };
    }
}
