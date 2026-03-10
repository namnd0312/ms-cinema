# Phase 2: Docker Compose + Testing

## Context Links
- [docker-compose.yml](../../docker-compose.yml)
- [Phase 1](./phase-01-redis-dedup-service.md)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Update docker-compose for notification-service Redis connectivity; add unit test for dedup service

## Key Insights
- notification-service in docker-compose currently does NOT depend on `redis-service` and has no `REDIS_HOST` env var
- auth-service and booking-service already depend on `redis-service` — same pattern to follow
- Unit test can use a mocked `StringRedisTemplate` to verify dedup logic without Redis running

## Requirements

### Functional
- notification-service container connects to redis-service
- Unit test covers: new event (proceed), duplicate event (skip), Redis failure (fail-open)

### Non-Functional
- No downtime or breaking changes to other services

## Architecture
No new architecture — just wiring existing redis-service to notification-service container.

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `docker-compose.yml` | Add `redis-service` dependency + `REDIS_HOST` env var to notification-service |

### Files to Create
| File | Purpose |
|------|---------|
| `notification-service/src/test/java/com/namnd/notification/service/NotificationDeduplicationServiceTest.java` | Unit test for dedup logic |

## Implementation Steps

### Step 1: Update docker-compose.yml
In the `notification-service` section:

1. Add `redis-service` to `depends_on`:
```yaml
    depends_on:
      - eureka-server
      - config-server
      - kafka
      - redis-service    # <-- add this
```

2. Add `REDIS_HOST` to `environment`:
```yaml
      REDIS_HOST: redis-service    # <-- add this
```

### Step 2: Create unit test
Path: `notification-service/src/test/java/com/namnd/notification/service/NotificationDeduplicationServiceTest.java`

```java
package com.namnd.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDeduplicationServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private NotificationDeduplicationService service;

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
```

### Step 3: Run tests
```bash
cd notification-service && ../mvnw test -pl . -q
```

## Todo List
- [ ] Add `redis-service` dependency + `REDIS_HOST` env to notification-service in `docker-compose.yml`
- [ ] Create `NotificationDeduplicationServiceTest.java`
- [ ] All tests pass

## Success Criteria
- `docker-compose.yml` notification-service depends on redis-service with REDIS_HOST env var
- 3 unit tests pass: new event, duplicate, Redis failure
- `mvnw test` passes for notification-service module

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Docker compose change breaks startup order | notification-service waits for redis | `depends_on` handles startup ordering |
| Test flakiness | CI failures | Tests use mocks, no real Redis needed |

## Security Considerations
- No credentials added (Redis has no auth in current setup)
- REDIS_HOST uses internal Docker network hostname

## Next Steps
- Manual smoke test: send same Kafka event twice, verify single email + skip log
- Future: add integration test with embedded Redis (Testcontainers) if test suite grows
