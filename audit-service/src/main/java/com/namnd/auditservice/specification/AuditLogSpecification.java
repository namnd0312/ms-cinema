package com.namnd.auditservice.specification;

import com.namnd.auditservice.domain.AuditLog;
import com.namnd.auditservice.dto.AuditLogSearchRequest;
import com.namnd.kafka.events.domain.AuditAction;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * JPA Specification builder for dynamic audit log filtering.
 * Chains non-null filters with AND.
 */
public final class AuditLogSpecification {

    private AuditLogSpecification() {}

    public static Specification<AuditLog> byUserId(String userId) {
        return (root, query, cb) -> cb.equal(root.get("userId"), userId);
    }

    public static Specification<AuditLog> byAction(AuditAction action) {
        return (root, query, cb) -> cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> byEntityType(String entityType) {
        return (root, query, cb) -> cb.equal(root.get("entityType"), entityType);
    }

    public static Specification<AuditLog> byEntityId(String entityId) {
        return (root, query, cb) -> cb.equal(root.get("entityId"), entityId);
    }

    public static Specification<AuditLog> byDateRange(LocalDateTime start, LocalDateTime end) {
        return (root, query, cb) -> {
            if (start != null && end != null) {
                return cb.between(root.get("createdAt"), start, end);
            } else if (start != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), start);
            } else {
                return cb.lessThanOrEqualTo(root.get("createdAt"), end);
            }
        };
    }

    /** Builds a composite specification from non-null search fields */
    public static Specification<AuditLog> buildSpecification(AuditLogSearchRequest req) {
        Specification<AuditLog> spec = Specification.where(null);

        if (req.userId() != null && !req.userId().isBlank()) {
            spec = spec.and(byUserId(req.userId()));
        }
        if (req.action() != null) {
            spec = spec.and(byAction(req.action()));
        }
        if (req.entityType() != null && !req.entityType().isBlank()) {
            spec = spec.and(byEntityType(req.entityType()));
        }
        if (req.entityId() != null && !req.entityId().isBlank()) {
            spec = spec.and(byEntityId(req.entityId()));
        }
        if (req.startDate() != null || req.endDate() != null) {
            spec = spec.and(byDateRange(req.startDate(), req.endDate()));
        }

        return spec;
    }
}
