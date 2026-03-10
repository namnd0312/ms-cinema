package com.namnd.paymentservice.event;

import com.namnd.kafka.events.domain.PaymentCompletedEvent;
import com.namnd.kafka.events.domain.PaymentFailedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for PaymentEventPublisher.
 * Verifies EventEnvelope structure and correct topic/key for both event types.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<Object> envelopeCaptor;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentEventPublisher(kafkaTemplate);
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @Test
    void shouldPublishPaymentCompletedWithCorrectEnvelope() {
        publisher.publishPaymentCompleted(42L, "pi_abc", 150_000L, "VND");

        verify(kafkaTemplate).send(
                eq(KafkaTopics.PAYMENT_EVENTS),
                eq("42"),
                envelopeCaptor.capture()
        );

        @SuppressWarnings("unchecked")
        EventEnvelope<PaymentCompletedEvent> envelope =
                (EventEnvelope<PaymentCompletedEvent>) envelopeCaptor.getValue();

        assertThat(envelope.eventType()).isEqualTo("payment.completed");
        assertThat(envelope.source()).isEqualTo("payment-service");
        assertThat(envelope.correlationId()).isEqualTo("42");
        assertThat(envelope.eventId()).isNotBlank();
        assertThat(envelope.timestamp()).isNotNull();

        // Payload is the concrete event type (not yet serialized)
        PaymentCompletedEvent payload = envelope.payload();
        assertThat(payload.bookingId()).isEqualTo(42L);
        assertThat(payload.paymentId()).isEqualTo("pi_abc");
        assertThat(payload.amount()).isEqualTo(150_000L);
        assertThat(payload.currency()).isEqualTo("VND");
    }

    @Test
    void shouldPublishPaymentFailedWithCorrectEnvelope() {
        publisher.publishPaymentFailed(99L, "Insufficient funds");

        verify(kafkaTemplate).send(
                eq(KafkaTopics.PAYMENT_EVENTS),
                eq("99"),
                envelopeCaptor.capture()
        );

        @SuppressWarnings("unchecked")
        EventEnvelope<PaymentFailedEvent> envelope =
                (EventEnvelope<PaymentFailedEvent>) envelopeCaptor.getValue();

        assertThat(envelope.eventType()).isEqualTo("payment.failed");
        assertThat(envelope.source()).isEqualTo("payment-service");
        assertThat(envelope.correlationId()).isEqualTo("99");

        PaymentFailedEvent payload = envelope.payload();
        assertThat(payload.bookingId()).isEqualTo(99L);
        assertThat(payload.reason()).isEqualTo("Insufficient funds");
    }

    @Test
    void shouldUseBookingIdAsMessageKey() {
        publisher.publishPaymentCompleted(7L, "pi_xyz", 50_000L, "VND");

        verify(kafkaTemplate).send(
                eq(KafkaTopics.PAYMENT_EVENTS),
                eq("7"),
                any()
        );
    }
}
