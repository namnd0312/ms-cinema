package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event payload for in-app notifications delivered via SSE.
 * Published by booking-service after payment events, consumed by notification-service.
 * Distinct from NotificationRequestedEvent which handles email delivery.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InAppNotificationEvent(
        Long userId,
        String title,
        String message,
        NotificationType notificationType
) {}
