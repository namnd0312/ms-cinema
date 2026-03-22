package com.namnd.kafka.events.audit;

import com.namnd.kafka.events.domain.AuditAction;
import com.namnd.kafka.events.domain.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * JPA entity lifecycle listener that publishes audit events on persist/update/remove.
 * Uses AuditBeanProvider to access Spring beans (EntityListeners can't use DI).
 * Only captures afterState for v1 — beforeState deferred to v2 (Envers).
 */
public class AuditEntityListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEntityListener.class);

    @PostPersist
    public void onPostPersist(Object entity) {
        publishEntityAudit(entity, AuditAction.CREATE);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        publishEntityAudit(entity, AuditAction.UPDATE);
    }

    @PostRemove
    public void onPostRemove(Object entity) {
        publishEntityAudit(entity, AuditAction.DELETE);
    }

    private void publishEntityAudit(Object entity, AuditAction action) {
        try {
            ApplicationContext ctx = AuditBeanProvider.getApplicationContext();
            if (ctx == null) return;

            ApplicationEventPublisher publisher = ctx.getBean(ApplicationEventPublisher.class);
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            String serviceName = ctx.getEnvironment().getProperty("spring.application.name", "unknown");

            String entityType = entity.getClass().getSimpleName();
            String entityId = extractEntityId(entity);
            String afterState = action == AuditAction.DELETE ? null : mapper.writeValueAsString(entity);
            String userId = extractUserId();

            AuditEvent auditEvent = new AuditEvent(
                    userId,
                    AuditHttpContext.getClientIp(),
                    action,
                    entityType,
                    entityId,
                    null,
                    afterState,
                    serviceName,
                    null,
                    AuditHttpContext.getRequestPath()
            );

            publisher.publishEvent(new AuditSpringEvent(auditEvent));
        } catch (Exception e) {
            log.warn("Failed to publish entity audit event for {}: {}", entity.getClass().getSimpleName(), e.getMessage());
        }
    }

    private String extractEntityId(Object entity) {
        try {
            var idMethod = entity.getClass().getMethod("getId");
            Object id = idMethod.invoke(entity);
            return id != null ? String.valueOf(id) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        Object principal = auth.getPrincipal();
        try {
            var emailMethod = principal.getClass().getMethod("email");
            Object email = emailMethod.invoke(principal);
            if (email != null) return email.toString();
        } catch (Exception ignored) {}
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails ud) {
            return ud.getUsername();
        }
        return auth.getName();
    }
}
