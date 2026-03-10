package com.namnd.cinema.service.impl;

import com.namnd.kafka.events.domain.NotificationRequestedEvent;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import com.namnd.cinema.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Publishes notification events to Kafka instead of sending emails directly.
 * notification-service consumes these events and handles actual SMTP delivery.
 */
@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${namnd.app.activationBaseUrl}")
    private String activationBaseUrl;

    @Value("${namnd.app.passwordResetBaseUrl}")
    private String passwordResetBaseUrl;

    public EmailServiceImpl(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void sendActivationEmail(String to, String token) {
        String activationLink = activationBaseUrl + "?token=" + token;
        var event = new NotificationRequestedEvent(
                "EMAIL", "ACTIVATION", to,
                "Activate Your Account",
                "Welcome! Please activate your account.\n\n"
                        + "Click the link below to activate:\n"
                        + activationLink + "\n\n"
                        + "This link will expire in 24 hours.\n"
                        + "If you did not register, please ignore this email."
        );
        publishNotification(event);
    }

    @Override
    public void sendPasswordResetEmail(String to, String token) {
        String resetLink = passwordResetBaseUrl + "?token=" + token;
        var event = new NotificationRequestedEvent(
                "EMAIL", "PASSWORD_RESET", to,
                "Password Reset Request",
                "You have requested to reset your password.\n\n"
                        + "Click the link below to reset your password:\n"
                        + resetLink + "\n\n"
                        + "This link will expire in 30 minutes.\n"
                        + "If you did not request this, please ignore this email."
        );
        publishNotification(event);
    }

    private void publishNotification(NotificationRequestedEvent event) {
        EventEnvelope<NotificationRequestedEvent> envelope = EventEnvelope.of(
                "auth-service", "notification.requested",
                UUID.randomUUID().toString(), event);

        kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS, event.recipientEmail(), envelope)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        logger.error("Failed to publish notification event for {}: {}",
                                maskEmail(event.recipientEmail()), ex.getMessage());
                    } else {
                        logger.info("Published notification event: channel={}, to={}",
                                event.channel(), maskEmail(event.recipientEmail()));
                    }
                });
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
