package com.namnd.notification.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.namnd.kafka.events.domain.InAppNotificationEvent;
import com.namnd.kafka.events.domain.NotificationType;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import com.namnd.notification.repository.NotificationRepository;
import com.namnd.notification.service.InAppNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for in-app notification events (eventType "notification.in_app").
 * Uses unique consumer group per instance for broadcast pattern — all instances
 * receive every message, enabling multi-instance scaling with zero code changes.
 */
@Component
public class InAppNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationEventListener.class);

    private final InAppNotificationService inAppNotificationService;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    public InAppNotificationEventListener(InAppNotificationService inAppNotificationService,
                                          NotificationRepository notificationRepository,
                                          ObjectMapper objectMapper) {
        this.inAppNotificationService = inAppNotificationService;
        this.notificationRepository = notificationRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = KafkaTopics.NOTIFICATION_EVENTS,
            groupId = "notification-sse-#{T(java.util.UUID).randomUUID().toString().substring(0,8)}"
    )
    public void handleInAppEvent(EventEnvelope envelope) {
        if (!"notification.in_app".equals(envelope.eventType())) return;

        log.info("Received in-app notification event: eventId={}", envelope.eventId());

        InAppNotificationEvent event = objectMapper.convertValue(
                envelope.payload(), InAppNotificationEvent.class);

        // Admin broadcast: save for all connected users (userId is null)
        if (event.notificationType() == NotificationType.ADMIN_BROADCAST && event.userId() == null) {
            handleBroadcast(event);
            return;
        }

        inAppNotificationService.saveAndPush(event);
    }

    /** Save a broadcast notification for each known user and push via SSE. */
    private void handleBroadcast(InAppNotificationEvent event) {
        var userIds = notificationRepository.findDistinctUserIds();

        for (Long userId : userIds) {
            var userEvent = new InAppNotificationEvent(
                    userId, event.title(), event.message(), event.notificationType());
            inAppNotificationService.saveAndPush(userEvent);
        }

        log.info("Admin broadcast delivered to {} users", userIds.size());
    }
}
