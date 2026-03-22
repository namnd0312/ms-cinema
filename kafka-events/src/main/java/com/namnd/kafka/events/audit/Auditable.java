package com.namnd.kafka.events.audit;

import com.namnd.kafka.events.domain.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method for audit logging.
 * AuditAspect intercepts calls and publishes AuditEvent to Kafka after TX commit.
 * Only works on public methods called externally (Spring AOP proxy limitation).
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    AuditAction action();
    String entityType() default "";
}
