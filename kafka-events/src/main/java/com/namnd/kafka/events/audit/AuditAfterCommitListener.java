package com.namnd.kafka.events.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for AuditSpringEvent AFTER DB transaction commits,
 * then delegates to AuditEventPublisher to send Kafka message.
 * Prevents audit publish failures from rolling back business transactions.
 */
public class AuditAfterCommitListener {

    private static final Logger log = LoggerFactory.getLogger(AuditAfterCommitListener.class);

    private final AuditEventPublisher auditEventPublisher;

    public AuditAfterCommitListener(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
    }

    /**
     * Fires after TX commit. fallbackExecution=true ensures it also fires
     * in non-transactional contexts (e.g., login/logout in AuthController).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuditEvent(AuditSpringEvent event) {
        log.debug("Publishing audit event: action={}", event.auditEvent().action());
        auditEventPublisher.publish(event.auditEvent());
    }
}
