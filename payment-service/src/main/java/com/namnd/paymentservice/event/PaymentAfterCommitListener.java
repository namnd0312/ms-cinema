package com.namnd.paymentservice.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for payment Spring events AFTER the DB transaction commits,
 * then delegates to PaymentEventPublisher to send Kafka messages.
 * This prevents Kafka failures from rolling back the DB transaction.
 */
@Component
public class PaymentAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentAfterCommitListener.class);

    private final PaymentEventPublisher paymentEventPublisher;

    public PaymentAfterCommitListener(PaymentEventPublisher paymentEventPublisher) {
        this.paymentEventPublisher = paymentEventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedSpringEvent event) {
        log.info("TX committed — publishing Kafka payment.completed for bookingId={}", event.bookingId());
        paymentEventPublisher.publishPaymentCompleted(
                event.bookingId(), event.paymentId(), event.amount(), event.currency());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedSpringEvent event) {
        log.info("TX committed — publishing Kafka payment.failed for bookingId={}", event.bookingId());
        paymentEventPublisher.publishPaymentFailed(event.bookingId(), event.reason());
    }
}
