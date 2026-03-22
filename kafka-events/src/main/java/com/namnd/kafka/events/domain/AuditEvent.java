package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Audit event payload carried inside EventEnvelope.
 * Captures who did what, to which entity, from where.
 * beforeState/afterState are JSON strings for schema flexibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEvent(
        String userId,
        String userIp,
        AuditAction action,
        String entityType,
        String entityId,
        String beforeState,
        String afterState,
        String sourceService,
        String traceId,
        String requestPath
) {}
