package com.namnd.kafka.events.domain;

/**
 * Enumeration of auditable business actions.
 * Used to categorize audit log entries by operation type.
 */
public enum AuditAction {
    CREATE,
    READ,
    UPDATE,
    DELETE,
    LOGIN,
    LOGOUT,
    CUSTOM
}
