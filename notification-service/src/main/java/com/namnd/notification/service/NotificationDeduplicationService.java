package com.namnd.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Prevents duplicate notification delivery using Redis SETNX.
 * Key pattern: notification:processed:{eventId} with 24h TTL.
 */
@Service
public class NotificationDeduplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeduplicationService.class);
    private static final String KEY_PREFIX = "notification:processed:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public NotificationDeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Atomically marks eventId as processed.
     * @return true if newly marked (proceed with send), false if already processed (skip)
     */
    public boolean tryMarkProcessed(String eventId) {
        try {
            Boolean result = redisTemplate.opsForValue()
                    .setIfAbsent(KEY_PREFIX + eventId, "1", TTL);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.warn("Redis unavailable for dedup check, proceeding with send: {}", e.getMessage());
            return true; // fail-open
        }
    }
}
