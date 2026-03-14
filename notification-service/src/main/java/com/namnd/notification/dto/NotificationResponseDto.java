package com.namnd.notification.dto;

import java.time.LocalDateTime;

/** API response DTO for a single notification. */
public record NotificationResponseDto(
        Long id,
        String title,
        String message,
        String notificationType,
        boolean isRead,
        LocalDateTime createdAt
) {}
