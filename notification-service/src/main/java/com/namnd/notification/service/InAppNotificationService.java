package com.namnd.notification.service;

import com.namnd.kafka.events.domain.InAppNotificationEvent;
import com.namnd.notification.dto.BroadcastRequestDto;
import com.namnd.notification.dto.NotificationResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Service interface for in-app notification operations. */
public interface InAppNotificationService {

    NotificationResponseDto saveAndPush(InAppNotificationEvent event);

    Page<NotificationResponseDto> getUserNotifications(Long userId, Pageable pageable);

    void markAsRead(Long notificationId, Long userId);

    void markAllAsRead(Long userId);

    long getUnreadCount(Long userId);

    void broadcast(BroadcastRequestDto request);
}
