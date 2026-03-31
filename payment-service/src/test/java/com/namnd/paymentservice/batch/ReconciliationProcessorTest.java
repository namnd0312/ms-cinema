package com.namnd.paymentservice.batch;

import com.namnd.paymentservice.model.*;
import com.namnd.paymentservice.repository.ReconciliationRunRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ReconciliationProcessor.
 * Uses MockedStatic to intercept PaymentIntent.retrieve() static calls.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationProcessorTest {

    @Mock
    private ReconciliationRunRepository runRepository;

    private ReconciliationProcessor processor;

    private ReconciliationRun mockRun;

    @BeforeEach
    void setUp() {
        processor = new ReconciliationProcessor(runRepository, 1L);

        mockRun = ReconciliationRun.builder()
                .id(1L)
                .status(ReconciliationStatus.RUNNING)
                .build();

        when(runRepository.findById(1L)).thenReturn(Optional.of(mockRun));
    }

    private Payment buildPayment(String stripeId, Long amount, PaymentStatus status) {
        Payment p = new Payment();
        p.setId(10L);
        p.setBookingId(100L);
        p.setUserId(1L);
        p.setAmount(amount);
        p.setStripePaymentIntentId(stripeId);
        p.setStatus(status);
        return p;
    }

    private PaymentIntent buildStripeIntent(Long amount, String status) {
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getAmount()).thenReturn(amount);
        when(intent.getStatus()).thenReturn(status);
        return intent;
    }

    @Test
    void process_matchingPayment_returnsMatched() throws Exception {
        Payment local = buildPayment("pi_matched", 100_000L, PaymentStatus.COMPLETED);
        PaymentIntent intent = buildStripeIntent(100_000L, "succeeded");

        try (MockedStatic<PaymentIntent> piStatic = Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_matched")).thenReturn(intent);

            ReconciliationItem result = processor.process(local);

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.MATCHED);
            assertThat(result.getStripeAmount()).isEqualTo(100_000L);
            assertThat(result.getStripeStatus()).isEqualTo("succeeded");
        }
    }

    @Test
    void process_amountMismatch_returnsAmountMismatch() throws Exception {
        Payment local = buildPayment("pi_amount", 100_000L, PaymentStatus.COMPLETED);
        PaymentIntent intent = buildStripeIntent(200_000L, "succeeded");

        try (MockedStatic<PaymentIntent> piStatic = Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_amount")).thenReturn(intent);

            ReconciliationItem result = processor.process(local);

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
        }
    }

    @Test
    void process_statusMismatch_returnsStatusMismatch() throws Exception {
        // Stripe says succeeded but local is still PENDING
        Payment local = buildPayment("pi_status", 100_000L, PaymentStatus.PENDING);
        PaymentIntent intent = buildStripeIntent(100_000L, "succeeded");

        try (MockedStatic<PaymentIntent> piStatic = Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_status")).thenReturn(intent);

            ReconciliationItem result = processor.process(local);

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.STATUS_MISMATCH);
        }
    }

    @Test
    void process_missingStripe_whenFakePrefix() throws Exception {
        Payment local = buildPayment("FAKE-pi_fake", 100_000L, PaymentStatus.PENDING);

        ReconciliationItem result = processor.process(local);

        assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.MISSING_STRIPE);
        assertThat(result.getStripePaymentIntentId()).startsWith("FAKE-");
    }

    @Test
    void process_stripeApiError_returnsMissingStripe() throws Exception {
        Payment local = buildPayment("pi_error", 100_000L, PaymentStatus.PENDING);

        try (MockedStatic<PaymentIntent> piStatic = Mockito.mockStatic(PaymentIntent.class)) {
            StripeException stripeEx = mock(StripeException.class);
            when(stripeEx.getMessage()).thenReturn("API connection error");
            piStatic.when(() -> PaymentIntent.retrieve(any(String.class))).thenThrow(stripeEx);

            ReconciliationItem result = processor.process(local);

            assertThat(result.getDiscrepancyType()).isEqualTo(DiscrepancyType.MISSING_STRIPE);
            assertThat(result.getNotes()).contains("Stripe API error");
        }
    }
}
