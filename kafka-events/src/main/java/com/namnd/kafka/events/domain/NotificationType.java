package com.namnd.kafka.events.domain;

/**
 * Types of in-app notifications delivered via SSE.
 * Used by InAppNotificationEvent to classify notification purpose.
 */
public enum NotificationType {
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    ADMIN_BROADCAST,
    SYSTEM
}
