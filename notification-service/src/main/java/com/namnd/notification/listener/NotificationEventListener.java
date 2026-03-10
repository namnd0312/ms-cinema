package com.namnd.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.namnd.kafka.events.domain.NotificationRequestedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import com.namnd.notification.service.EmailSenderService;
import com.namnd.notification.service.NotificationDeduplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for notification-events topic.
 * Routes events by eventType to the appropriate sender (currently email only).
 * Exceptions propagate to trigger DLT retry via DefaultErrorHandler.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final EmailSenderService emailSenderService;
    private final NotificationDeduplicationService deduplicationService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(EmailSenderService emailSenderService,
                                     NotificationDeduplicationService deduplicationService,
                                     ObjectMapper objectMapper) {
        this.emailSenderService = emailSenderService;
        this.deduplicationService = deduplicationService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_EVENTS, groupId = "notification-service")
    public void handleNotificationEvent(EventEnvelope envelope) {
        String eventType = envelope.eventType();
        log.info("Received notification event: type={}, correlationId={}", eventType, envelope.correlationId());

        if (envelope.eventId() == null) {
            log.warn("Event missing eventId, skipping dedup: correlationId={}", envelope.correlationId());
        } else if (!deduplicationService.tryMarkProcessed(envelope.eventId())) {
            log.info("Duplicate event skipped: eventId={}, correlationId={}",
                     envelope.eventId(), envelope.correlationId());
            return;
        }

        if ("notification.requested".equals(eventType)) {
            NotificationRequestedEvent event = objectMapper.convertValue(
                    envelope.payload(), NotificationRequestedEvent.class);

            if ("EMAIL".equals(event.notificationType())) {
                try {
                    emailSenderService.sendEmail(event);
                } catch (Exception e) {
                    log.error("Failed to send email: {}", e.getMessage(), e);
                    throw e;
                }
            } else {
                log.warn("Unsupported notification type: {}", event.notificationType());
            }
        } else {
            log.warn("Unknown notification event type: {}", eventType);
        }
    }
}
