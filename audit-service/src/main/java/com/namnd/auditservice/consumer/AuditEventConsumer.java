package com.namnd.auditservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.namnd.auditservice.mapper.AuditLogMapper;
import com.namnd.auditservice.repository.AuditLogRepository;
import com.namnd.kafka.events.domain.AuditEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for audit-events topic.
 * Uses raw EventEnvelope + ObjectMapper.convertValue for generic payload
 * (same pattern as notification-service).
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditEventConsumer(AuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    @KafkaListener(topics = KafkaTopics.AUDIT_EVENTS, groupId = "audit-service")
    @SuppressWarnings("rawtypes")
    public void consume(EventEnvelope envelope) {
        String eventId = envelope.eventId();
        if (repository.existsByEventId(eventId)) {
            log.debug("Duplicate audit eventId={}, skipping", eventId);
            return;
        }
        try {
            AuditEvent auditEvent = objectMapper.convertValue(envelope.payload(), AuditEvent.class);
            // Reconstruct typed envelope for mapper
            EventEnvelope<AuditEvent> typed = new EventEnvelope<>(
                    envelope.eventId(), envelope.eventType(), envelope.source(),
                    envelope.correlationId(), envelope.timestamp(), auditEvent);

            repository.save(AuditLogMapper.toEntity(typed));
            log.info("Persisted audit log: eventId={}, action={}, entityType={}",
                    eventId, auditEvent.action(), auditEvent.entityType());
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate audit eventId={} caught by constraint, skipping", eventId);
        }
    }
}
