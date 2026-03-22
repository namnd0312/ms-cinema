package com.namnd.kafka.events.topic;

/**
 * Central registry of Kafka topic name constants.
 * All producers and consumers must reference these constants — never use raw strings.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /** Topic for all payment lifecycle events (completed, failed, refunded). */
    public static final String PAYMENT_EVENTS = "payment-events";

    /** Topic for movie catalog events (created, updated, deleted). */
    public static final String MOVIE_EVENTS = "movie-events";

    /** Topic for notification events (email, SMS, push — routed by notificationType). */
    public static final String NOTIFICATION_EVENTS = "notification-events";

    /** Topic for audit trail events (business actions + data changes). */
    public static final String AUDIT_EVENTS = "audit-events";
}
