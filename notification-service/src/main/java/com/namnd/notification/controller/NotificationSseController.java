package com.namnd.notification.controller;

import com.namnd.jwt.autoconfigure.JwtTokenValidator;
import com.namnd.notification.service.SseEmitterRegistryService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE streaming endpoint for real-time in-app notifications.
 * JWT passed as query param since EventSource API does not support custom headers.
 */
@RestController
public class NotificationSseController {

    private static final Logger log = LoggerFactory.getLogger(NotificationSseController.class);
    private static final long SSE_TIMEOUT = 300_000L; // 5 minutes

    private final JwtTokenValidator tokenValidator;
    private final SseEmitterRegistryService sseRegistry;

    public NotificationSseController(JwtTokenValidator tokenValidator,
                                     SseEmitterRegistryService sseRegistry) {
        this.tokenValidator = tokenValidator;
        this.sseRegistry = sseRegistry;
    }

    @GetMapping(path = "/api/notifications/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        Claims claims = tokenValidator.parseClaims(token);
        if (claims == null) {
            throw new SecurityException("Invalid or expired JWT token");
        }

        Long userId = tokenValidator.getUserId(claims);
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        sseRegistry.addEmitter(userId, emitter);

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event().name("connected").data("SSE connected for userId=" + userId));
        } catch (Exception e) {
            log.warn("Failed to send initial SSE event to userId={}", userId);
        }

        log.info("SSE connection established for userId={}", userId);
        return emitter;
    }
}
