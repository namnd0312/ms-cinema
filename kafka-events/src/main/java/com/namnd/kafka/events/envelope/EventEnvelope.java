package com.namnd.kafka.events.envelope;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic event wrapper that carries metadata alongside any domain payload.
 * Used to maintain consistent envelope structure across all Kafka topics.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        String eventId,        // UUID - uniquely identifies this event instance
        String eventType,      // e.g. "payment.completed" - discriminator for routing
        String source,         // e.g. "payment-service" - originating microservice
        String correlationId,  // propagated across saga steps for distributed tracing
        LocalDateTime timestamp,
        T payload
) {
    /**
     * Factory method that auto-generates eventId and timestamp.
     *
     * @param source        originating service name
     * @param eventType     dot-separated event type descriptor
     * @param correlationId saga/trace correlation id
     * @param payload       domain-specific event payload
     */
    public static <T> EventEnvelope<T> of(String source, String eventType, String correlationId, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                source,
                correlationId,
                LocalDateTime.now(),
                payload
        );
    }
}
