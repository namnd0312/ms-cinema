package com.namnd.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDeduplicationServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private NotificationDeduplicationService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new NotificationDeduplicationService(redisTemplate);
    }

    @Test
    void tryMarkProcessed_newEvent_returnsTrue() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(true);
        assertThat(service.tryMarkProcessed("event-123")).isTrue();
    }

    @Test
    void tryMarkProcessed_duplicateEvent_returnsFalse() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenReturn(false);
        assertThat(service.tryMarkProcessed("event-123")).isFalse();
    }

    @Test
    void tryMarkProcessed_redisDown_returnsTrue_failOpen() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("Connection refused"));
        assertThat(service.tryMarkProcessed("event-123")).isTrue();
    }
}
