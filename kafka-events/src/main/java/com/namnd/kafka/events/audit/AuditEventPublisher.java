package com.namnd.kafka.events.audit;

import com.namnd.kafka.events.domain.AuditEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Publishes AuditEvent wrapped in EventEnvelope to the audit-events Kafka topic.
 * Message key is entityType:entityId for partition ordering.
 */
public class AuditEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AuditEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String serviceName;

    public AuditEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, String serviceName) {
        this.kafkaTemplate = kafkaTemplate;
        this.serviceName = serviceName;
    }

    public void publish(AuditEvent event) {
        String key = event.entityType() + ":" + (event.entityId() != null ? event.entityId() : "unknown");
        EventEnvelope<AuditEvent> envelope = EventEnvelope.of(
                serviceName, "audit." + event.action().name().toLowerCase(), key, event);

        kafkaTemplate.send(KafkaTopics.AUDIT_EVENTS, key, envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish audit event action={} entity={}: {}",
                                event.action(), event.entityType(), ex.getMessage());
                    } else {
                        log.debug("Published audit event: action={}, entityType={}, entityId={}",
                                event.action(), event.entityType(), event.entityId());
                    }
                });
    }
}
