package com.namnd.notification.service;

import com.namnd.notification.dto.NotificationResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages SSE emitter lifecycle: registration, removal, push, heartbeat.
 * Thread-safe via ConcurrentHashMap + CopyOnWriteArrayList.
 */
@Service
public class SseEmitterRegistryService {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistryService.class);

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Register an emitter for a user with automatic cleanup callbacks. */
    public void addEmitter(Long userId, SseEmitter emitter) {
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
        log.debug("SSE emitter added for userId={}", userId);
    }

    /** Remove a specific emitter for a user (atomic to prevent race conditions). */
    public void removeEmitter(Long userId, SseEmitter emitter) {
        emitters.computeIfPresent(userId, (k, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }

    /** Send a notification event to a specific user's emitters. */
    public void sendToUser(Long userId, NotificationResponseDto data) {
        var userEmitters = emitters.get(userId);
        if (userEmitters == null) return;

        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(data));
            } catch (IOException e) {
                log.debug("Failed to send SSE to userId={}, removing emitter", userId);
                removeEmitter(userId, emitter);
            }
        }
    }

    /** Broadcast a notification to all connected users. */
    public void broadcastToAll(NotificationResponseDto data) {
        for (var entry : emitters.entrySet()) {
            sendToUser(entry.getKey(), data);
        }
    }

    /** Heartbeat every 30s to keep connections alive through proxies/gateways. */
    @Scheduled(fixedRate = 30000)
    public void sendHeartbeat() {
        for (var entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(""));
                } catch (IOException e) {
                    removeEmitter(entry.getKey(), emitter);
                }
            }
        }
    }

    /** Check if this instance holds an SSE connection for a user. */
    public boolean hasEmitter(Long userId) {
        var userEmitters = emitters.get(userId);
        return userEmitters != null && !userEmitters.isEmpty();
    }
}
