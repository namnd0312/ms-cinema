package com.namnd.kafka.events.audit;

import com.namnd.kafka.events.domain.AuditAction;
import com.namnd.kafka.events.domain.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Tracer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * AOP aspect that intercepts methods annotated with @Auditable.
 * Extracts user context, method args/return value, and publishes
 * AuditSpringEvent for after-commit Kafka delivery.
 */
@Aspect
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final Tracer tracer;

    public AuditAspect(ApplicationEventPublisher eventPublisher,
                       ObjectMapper objectMapper,
                       String serviceName,
                       Tracer tracer) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
        this.tracer = tracer;
    }

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String userId = extractUserId();
            String entityId = extractEntityId(joinPoint.getArgs(), result);
            String entityType = auditable.entityType().isEmpty()
                    ? inferEntityType(joinPoint) : auditable.entityType();
            String afterState = serializeResult(result, auditable.action());
            String traceId = extractTraceId();

            AuditEvent auditEvent = new AuditEvent(
                    userId,
                    AuditHttpContext.getClientIp(),
                    auditable.action(),
                    entityType,
                    entityId,
                    null,
                    afterState,
                    serviceName,
                    traceId,
                    AuditHttpContext.getRequestPath()
            );

            eventPublisher.publishEvent(new AuditSpringEvent(auditEvent));
        } catch (Exception e) {
            // Audit must never break business logic
            log.warn("Failed to create audit event for {}.{}: {}",
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(), e.getMessage());
        }

        return result;
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "anonymous";
    }

    /** Tries to extract entity ID from first arg (Long/String) or return value */
    private String extractEntityId(Object[] args, Object result) {
        // If first arg is a Long or String, treat it as entity ID
        if (args.length > 0) {
            Object first = args[0];
            if (first instanceof Long || first instanceof String) {
                return String.valueOf(first);
            }
        }
        // Try to get ID from return value (convention: DTO with getId() or id())
        if (result != null) {
            try {
                var idMethod = result.getClass().getMethod("getId");
                Object id = idMethod.invoke(result);
                return id != null ? String.valueOf(id) : null;
            } catch (NoSuchMethodException ignored) {
                // Try record-style accessor
                try {
                    var idMethod = result.getClass().getMethod("id");
                    Object id = idMethod.invoke(result);
                    return id != null ? String.valueOf(id) : null;
                } catch (Exception ignored2) {}
            } catch (Exception ignored) {}
        }
        return null;
    }

    /** Infers entity type from the target class name (e.g., MovieServiceImpl -> Movie) */
    private String inferEntityType(ProceedingJoinPoint joinPoint) {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        return className.replace("ServiceImpl", "")
                .replace("Service", "")
                .replace("Controller", "");
    }

    /** Serializes result to JSON for afterState, skip for DELETE/LOGOUT/LOGIN (LOGIN may contain tokens) */
    private String serializeResult(Object result, AuditAction action) {
        if (result == null || action == AuditAction.DELETE
                || action == AuditAction.LOGOUT || action == AuditAction.LOGIN) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.debug("Could not serialize audit afterState: {}", e.getMessage());
            return null;
        }
    }

    private String extractTraceId() {
        if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }
}
