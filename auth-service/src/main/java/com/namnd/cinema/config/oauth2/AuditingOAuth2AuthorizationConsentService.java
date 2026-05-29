package com.namnd.cinema.config.oauth2;

import com.namnd.kafka.events.audit.AuditEventPublisher;
import com.namnd.kafka.events.domain.AuditAction;
import com.namnd.kafka.events.domain.AuditEvent;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;

/**
 * Delegating {@link OAuth2AuthorizationConsentService} that emits audit events
 * for every consent persisted or revoked.
 *
 *   save(...)   -> oauth2.consent.granted (AuditAction.CREATE, entityType=OAuth2Consent)
 *   remove(...) -> oauth2.consent.denied  (AuditAction.DELETE, entityType=OAuth2Consent)
 *
 * Wraps the underlying JDBC-backed bean; persistence semantics are untouched.
 * Audit failures are logged but never propagate — consent flow must not fail
 * because of a Kafka outage.
 */
@Slf4j
public class AuditingOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private static final String ENTITY_TYPE_CONSENT = "OAuth2Consent";

    private final OAuth2AuthorizationConsentService delegate;
    private final AuditEventPublisher auditPublisher;
    private final Tracer tracer;
    private final String serviceName;

    public AuditingOAuth2AuthorizationConsentService(OAuth2AuthorizationConsentService delegate,
                                                     AuditEventPublisher auditPublisher,
                                                     Tracer tracer,
                                                     String serviceName) {
        this.delegate = delegate;
        this.auditPublisher = auditPublisher;
        this.tracer = tracer;
        this.serviceName = serviceName;
    }

    @Override
    public void save(OAuth2AuthorizationConsent consent) {
        delegate.save(consent);
        safePublish(AuditAction.CREATE, consent);
    }

    @Override
    public void remove(OAuth2AuthorizationConsent consent) {
        delegate.remove(consent);
        safePublish(AuditAction.DELETE, consent);
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        return delegate.findById(registeredClientId, principalName);
    }

    private void safePublish(AuditAction action, OAuth2AuthorizationConsent consent) {
        try {
            String entityId = consent.getRegisteredClientId() + ":" + consent.getPrincipalName();
            AuditEvent event = new AuditEvent(
                    consent.getPrincipalName(),
                    null,
                    action,
                    ENTITY_TYPE_CONSENT,
                    entityId,
                    null,
                    null,
                    serviceName,
                    extractTraceId(),
                    null
            );
            auditPublisher.publish(event);
        } catch (Exception ex) {
            log.warn("Failed to publish OAuth2 consent audit event action={} clientId={}: {}",
                    action, consent.getRegisteredClientId(), ex.getMessage());
        }
    }

    private String extractTraceId() {
        if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }
}
