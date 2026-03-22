package com.namnd.auditservice.dto;

import com.namnd.kafka.events.domain.AuditAction;

import java.time.LocalDateTime;

/**
 * Query parameters for searching audit logs.
 * All fields are optional — null fields are excluded from the query.
 */
public record AuditLogSearchRequest(
        String userId,
        AuditAction action,
        String entityType,
        String entityId,
        LocalDateTime startDate,
        LocalDateTime endDate
) {}
