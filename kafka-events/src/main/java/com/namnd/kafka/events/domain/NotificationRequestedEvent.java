package com.namnd.kafka.events.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event requesting a notification be sent to a user.
 * Produced by auth-service (and future services) for email/SMS/push delivery.
 *
 * @param notificationType delivery channel: "EMAIL", "SMS", "PUSH"
 * @param channel          subtype: "ACTIVATION", "PASSWORD_RESET", "ACCOUNT_LOCKED"
 * @param recipientEmail   target email address
 * @param subject          notification subject line
 * @param body             pre-rendered notification content
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequestedEvent(
        String notificationType,
        String channel,
        String recipientEmail,
        String subject,
        String body
) {}
