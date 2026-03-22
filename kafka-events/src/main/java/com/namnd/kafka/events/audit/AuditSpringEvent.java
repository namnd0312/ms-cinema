package com.namnd.kafka.events.audit;

import com.namnd.kafka.events.domain.AuditEvent;

/**
 * Spring application event that carries an AuditEvent payload.
 * Published in-transaction, consumed by AuditAfterCommitListener after TX commit.
 */
public record AuditSpringEvent(AuditEvent auditEvent) {}
