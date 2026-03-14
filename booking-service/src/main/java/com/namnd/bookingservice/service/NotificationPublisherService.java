package com.namnd.bookingservice.service;

import com.namnd.kafka.events.domain.InAppNotificationEvent;
import com.namnd.kafka.events.domain.NotificationType;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes in-app notification events to Kafka after payment outcomes.
 * Failures are caught and logged — notification must never block booking flow.
 */
@Service
public class NotificationPublisherService {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisherService.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationPublisherService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void notifyPaymentSuccess(Long userId, Long bookingId, String paymentId, Long amount) {
        var event = new InAppNotificationEvent(
                userId,
                "Payment Successful",
                String.format("Your payment of %d for booking #%d has been confirmed.", amount, bookingId),
                NotificationType.PAYMENT_SUCCESS
        );
        publishInAppNotification(event);
    }

    public void notifyPaymentFailed(Long userId, Long bookingId, String reason) {
        var event = new InAppNotificationEvent(
                userId,
                "Payment Failed",
                String.format("Payment for booking #%d failed: %s", bookingId, reason),
                NotificationType.PAYMENT_FAILED
        );
        publishInAppNotification(event);
    }

    private void publishInAppNotification(InAppNotificationEvent event) {
        try {
            var envelope = EventEnvelope.of("booking-service", "notification.in_app", null, event);
            kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS, envelope);
            log.info("Published in-app notification for userId={}", event.userId());
        } catch (Exception e) {
            log.error("Failed to publish in-app notification: {}", e.getMessage(), e);
            // Do NOT rethrow — notification failure must not affect booking flow
        }
    }
}
