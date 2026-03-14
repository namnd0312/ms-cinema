package com.namnd.notification.service.impl;

import com.namnd.kafka.events.domain.InAppNotificationEvent;
import com.namnd.kafka.events.domain.NotificationType;
import com.namnd.kafka.events.envelope.EventEnvelope;
import com.namnd.kafka.events.topic.KafkaTopics;
import com.namnd.notification.dto.BroadcastRequestDto;
import com.namnd.notification.dto.NotificationResponseDto;
import com.namnd.notification.model.Notification;
import com.namnd.notification.repository.NotificationRepository;
import com.namnd.notification.service.InAppNotificationService;
import com.namnd.notification.service.SseEmitterRegistryService;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles in-app notification persistence, SSE push, and admin broadcast.
 * Saves notifications to PostgreSQL and pushes to connected SSE clients.
 */
@Service
public class InAppNotificationServiceImpl implements InAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationServiceImpl.class);

    private final NotificationRepository notificationRepository;
    private final SseEmitterRegistryService sseRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InAppNotificationServiceImpl(NotificationRepository notificationRepository,
                                        SseEmitterRegistryService sseRegistry,
                                        KafkaTemplate<String, Object> kafkaTemplate) {
        this.notificationRepository = notificationRepository;
        this.sseRegistry = sseRegistry;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    @Transactional
    public NotificationResponseDto saveAndPush(InAppNotificationEvent event) {
        Notification notification = new Notification();
        notification.setUserId(event.userId());
        notification.setTitle(event.title());
        notification.setMessage(event.message());
        notification.setNotificationType(event.notificationType());

        Notification saved = notificationRepository.save(notification);
        NotificationResponseDto dto = toDto(saved);

        // Push via SSE (sendToUser is a no-op if no emitter registered)
        sseRegistry.sendToUser(event.userId(), dto);

        log.info("In-app notification saved: id={}, userId={}, type={}",
                saved.getId(), event.userId(), event.notificationType());
        return dto;
    }

    @Override
    public Page<NotificationResponseDto> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new AccessDeniedException("Not authorized to modify this notification");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }

    @Override
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    public void broadcast(BroadcastRequestDto request) {
        var event = new InAppNotificationEvent(
                null, request.title(), request.message(), NotificationType.ADMIN_BROADCAST);
        var envelope = EventEnvelope.of("notification-service", "notification.in_app", null, event);
        kafkaTemplate.send(KafkaTopics.NOTIFICATION_EVENTS, envelope);
        log.info("Admin broadcast published: title={}", request.title());
    }

    private NotificationResponseDto toDto(Notification n) {
        return new NotificationResponseDto(
                n.getId(), n.getTitle(), n.getMessage(),
                n.getNotificationType().name(), n.isRead(), n.getCreatedAt());
    }
}
