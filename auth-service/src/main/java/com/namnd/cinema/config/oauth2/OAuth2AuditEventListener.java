package com.namnd.cinema.config.oauth2;

import com.namnd.kafka.events.audit.AuditEventPublisher;
import com.namnd.kafka.events.domain.AuditAction;
import com.namnd.kafka.events.domain.AuditEvent;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenRevocationAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Bridges Spring Authorization Server authentication events to the audit-events Kafka stream.
 *
 * Spring AS does not expose typed events for token issuance; instead it fires the standard
 * Spring Security {@link AuthenticationSuccessEvent} with the OAuth2-specific authentication
 * token as the source. We inspect the authentication class to classify the event:
 *
 *   - {@link OAuth2AccessTokenAuthenticationToken}      -> oauth2.token.issued
 *   - {@link OAuth2TokenRevocationAuthenticationToken}  -> oauth2.token.revoked
 *
 * Any other {@link Authentication} subtype is ignored so this listener does not duplicate
 * audit entries already emitted by service-level @Auditable annotations (e.g. login).
 *
 * Audit failures are swallowed — token issuance must never fail because of a Kafka outage.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2AuditEventListener {

    private static final String ENTITY_TYPE_TOKEN = "OAuth2Token";

    private final AuditEventPublisher auditPublisher;
    private final Tracer tracer;

    @Value("${spring.application.name:auth-service}")
    private String serviceName;

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        Authentication authentication = event.getAuthentication();
        try {
            if (authentication instanceof OAuth2AccessTokenAuthenticationToken issued) {
                String clientId = issued.getRegisteredClient() != null
                        ? issued.getRegisteredClient().getClientId() : authentication.getName();
                publishTokenEvent(AuditAction.CREATE, clientId, authentication);
            } else if (authentication instanceof OAuth2TokenRevocationAuthenticationToken) {
                // Revocation token does not expose RegisteredClient directly; the authenticated
                // client principal name == client_id under client_secret_basic / client_secret_post.
                publishTokenEvent(AuditAction.DELETE, authentication.getName(), authentication);
            }
        } catch (Exception ex) {
            // Audit must never break the OAuth2 flow.
            log.warn("Failed to publish OAuth2 audit event for {}: {}",
                    authentication.getClass().getSimpleName(), ex.getMessage());
        }
    }

    private void publishTokenEvent(AuditAction action, String clientId, Authentication authentication) {
        String principalName = authentication.getName(); // resolved end-user OR client_id for client_credentials
        AuditEvent auditEvent = new AuditEvent(
                principalName,
                null,                       // client IP not available off-thread; populated by AuditAspect path only
                action,
                ENTITY_TYPE_TOKEN,
                clientId,
                null,
                null,
                serviceName,
                extractTraceId(),
                null
        );
        auditPublisher.publish(auditEvent);
    }

    private String extractTraceId() {
        if (tracer != null && tracer.currentSpan() != null && tracer.currentSpan().context() != null) {
            return tracer.currentSpan().context().traceId();
        }
        return null;
    }
}
