package com.namnd.auditservice.domain;

import com.namnd.kafka.events.domain.AuditAction;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persisted audit log entry. One row per auditable business event.
 * eventId is the idempotency key — Kafka retries won't create duplicates.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity_type", columnList = "entity_type"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String userId;

    private String userIp;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(nullable = false, length = 100)
    private String entityType;

    private String entityId;

    @Column(columnDefinition = "text")
    private String beforeState;

    @Column(columnDefinition = "text")
    private String afterState;

    @Column(nullable = false)
    private String sourceService;

    private String traceId;

    private String requestPath;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
