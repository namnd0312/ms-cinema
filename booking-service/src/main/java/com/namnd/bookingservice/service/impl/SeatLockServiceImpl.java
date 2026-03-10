package com.namnd.bookingservice.service.impl;

import com.namnd.bookingservice.service.SeatLockService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Redis-backed seat lock implementation.
 * Key pattern: seat:lock:{showtimeId}:{seatId}
 * TTL: 300 seconds (5 minutes)
 */
@Service
public class SeatLockServiceImpl implements SeatLockService {

    private static final String KEY_PREFIX = "seat:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(300);

    private final StringRedisTemplate redisTemplate;

    public SeatLockServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean lockSeats(Long showtimeId, List<Long> seatIds, Long userId) {
        // Sort to prevent potential deadlocks across concurrent requests
        List<Long> sorted = seatIds.stream().sorted(Comparator.naturalOrder()).toList();
        List<Long> locked = new ArrayList<>();

        for (Long seatId : sorted) {
            String key = buildKey(showtimeId, seatId);
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(key, userId.toString(), LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                locked.add(seatId);
            } else {
                // Roll back already-acquired locks
                unlockSeats(showtimeId, locked);
                return false;
            }
        }
        return true;
    }

    @Override
    public void unlockSeats(Long showtimeId, List<Long> seatIds) {
        seatIds.forEach(seatId -> redisTemplate.delete(buildKey(showtimeId, seatId)));
    }

    @Override
    public boolean isLocked(Long showtimeId, Long seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(buildKey(showtimeId, seatId)));
    }

    private String buildKey(Long showtimeId, Long seatId) {
        return KEY_PREFIX + showtimeId + ":" + seatId;
    }
}
