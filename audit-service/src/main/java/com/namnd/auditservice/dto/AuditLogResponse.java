package com.namnd.auditservice.dto;

import java.time.LocalDateTime;

/**
 * API response DTO for a single audit log entry.
 */
public record AuditLogResponse(
        Long id,
        String eventId,
        String userId,
        String userIp,
        String action,
        String entityType,
        String entityId,
        Object beforeState,
        Object afterState,
        String sourceService,
        String traceId,
        String requestPath,
        LocalDateTime createdAt
) {}
