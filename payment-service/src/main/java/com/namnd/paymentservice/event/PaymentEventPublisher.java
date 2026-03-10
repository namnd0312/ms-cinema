package com.namnd.paymentservice.event;

import com.namnd.kafka.events.domain.PaymentCompletedEvent;
import com.namnd.kafka.events.domain.PaymentFailedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes typed payment domain events wrapped in EventEnvelope to Kafka.
 * Consumed by booking-service to update booking status asynchronously.
 */
@Component
public class PaymentEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a payment.completed event. Message key is bookingId for partition affinity.
     */
    public void publishPaymentCompleted(Long bookingId, String paymentId, Long amount, String currency) {
        PaymentCompletedEvent payload = new PaymentCompletedEvent(bookingId, paymentId, amount, currency);
        EventEnvelope<PaymentCompletedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.completed", bookingId.toString(), payload);
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, bookingId.toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment.completed for bookingId={}: {}", bookingId, ex.getMessage(), ex);
                    } else {
                        log.info("Published payment.completed: bookingId={}, paymentId={}", bookingId, paymentId);
                    }
                });
    }

    /**
     * Publishes a payment.failed event. Message key is bookingId for partition affinity.
     */
    public void publishPaymentFailed(Long bookingId, String reason) {
        PaymentFailedEvent payload = new PaymentFailedEvent(bookingId, reason);
        EventEnvelope<PaymentFailedEvent> envelope = EventEnvelope.of(
                "payment-service", "payment.failed", bookingId.toString(), payload);
        kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, bookingId.toString(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish payment.failed for bookingId={}: {}", bookingId, ex.getMessage(), ex);
                    } else {
                        log.info("Published payment.failed: bookingId={}, reason={}", bookingId, reason);
                    }
                });
    }
}
