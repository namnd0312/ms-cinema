package com.namnd.notification.service;

import com.namnd.kafka.events.domain.NotificationRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends emails based on NotificationRequestedEvent payload.
 * Gmail SMTP auto-sets From header to the authenticated username.
 * Logs with masked recipient for security.
 */
@Service
public class EmailSenderService {

    private static final Logger log = LoggerFactory.getLogger(EmailSenderService.class);

    private final JavaMailSender mailSender;

    public EmailSenderService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(NotificationRequestedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(event.recipientEmail());
        message.setSubject(event.subject());
        message.setText(event.body());

        mailSender.send(message);
        log.info("Email sent: channel={}, to={}", event.channel(), maskEmail(event.recipientEmail()));
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
