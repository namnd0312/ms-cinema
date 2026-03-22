package com.namnd.auditservice.mapper;

import com.namnd.auditservice.domain.AuditLog;
import com.namnd.auditservice.dto.AuditLogResponse;
import com.namnd.kafka.events.domain.AuditEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Maps between Kafka events, JPA entities, and API response DTOs.
 */
public final class AuditLogMapper {

    private static final Logger log = LoggerFactory.getLogger(AuditLogMapper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private AuditLogMapper() {}

    public static AuditLog toEntity(EventEnvelope<AuditEvent> envelope) {
        AuditEvent event = envelope.payload();
        return AuditLog.builder()
                .eventId(envelope.eventId())
                .userId(event.userId())
                .userIp(event.userIp())
                .action(event.action())
                .entityType(event.entityType())
                .entityId(event.entityId())
                .beforeState(event.beforeState())
                .afterState(event.afterState())
                .sourceService(event.sourceService())
                .traceId(event.traceId())
                .requestPath(event.requestPath())
                .createdAt(envelope.timestamp() != null ? envelope.timestamp() : LocalDateTime.now())
                .build();
    }

    /** Maps entity to API response, parsing JSON strings to Objects for cleaner output */
    public static AuditLogResponse toResponse(AuditLog entity) {
        return new AuditLogResponse(
                entity.getId(),
                entity.getEventId(),
                entity.getUserId(),
                entity.getUserIp(),
                entity.getAction().name(),
                entity.getEntityType(),
                entity.getEntityId(),
                parseJson(entity.getBeforeState()),
                parseJson(entity.getAfterState()),
                entity.getSourceService(),
                entity.getTraceId(),
                entity.getRequestPath(),
                entity.getCreatedAt()
        );
    }

    /** Parses JSON string to Object (Map/List) for API response, falls back to raw string */
    private static Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            log.debug("Could not parse audit state JSON, returning raw string: {}", e.getMessage());
            return json;
        }
    }
}
