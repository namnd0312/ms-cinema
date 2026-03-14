package com.namnd.notification.controller;

import com.namnd.jwt.autoconfigure.JwtAuthenticatedUser;
import com.namnd.notification.dto.BroadcastRequestDto;
import com.namnd.notification.dto.NotificationResponseDto;
import com.namnd.notification.dto.UnreadCountResponseDto;
import com.namnd.notification.service.InAppNotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for notification history, read status, and admin broadcast.
 * All endpoints require JWT auth via Authorization header (except broadcast = ADMIN only).
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationRestController {

    private final InAppNotificationService notificationService;

    public NotificationRestController(InAppNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponseDto> getNotifications(
            @AuthenticationPrincipal JwtAuthenticatedUser user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationService.getUserNotifications(user.userId(), PageRequest.of(page, size));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        notificationService.markAsRead(id, user.userId());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        notificationService.markAllAsRead(user.userId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponseDto getUnreadCount(
            @AuthenticationPrincipal JwtAuthenticatedUser user) {
        return new UnreadCountResponseDto(notificationService.getUnreadCount(user.userId()));
    }

    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<Void> broadcast(@Valid @RequestBody BroadcastRequestDto request) {
        notificationService.broadcast(request);
        return ResponseEntity.ok().build();
    }
}
