# Phase 1: Redis Dedup Service + Listener Integration

## Context Links
- [NotificationEventListener.java](../../notification-service/src/main/java/com/namnd/notification/listener/NotificationEventListener.java)
- [EmailSenderService.java](../../notification-service/src/main/java/com/namnd/notification/service/EmailSenderService.java)
- [EventEnvelope.java](../../kafka-events/src/main/java/com/namnd/kafka/events/envelope/EventEnvelope.java)
- [auth-service RedisConfig](../../auth-service/src/main/java/com/namnd/springjwt/config/RedisConfig.java) — reference pattern

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add Redis dependency, create dedup service, integrate into Kafka listener

## Key Insights
- `EventEnvelope.eventId()` is a UUID string, auto-generated per event — perfect dedup key
- Spring Boot auto-configures `StringRedisTemplate` when `spring-boot-starter-data-redis` is on classpath + `spring.data.redis.host` is set. No custom `RedisConfig` bean needed (KISS — auth-service's custom config is overkill for our string-only use case)
- `StringRedisTemplate.opsForValue().setIfAbsent(key, value, duration)` maps to Redis `SET key value NX EX ttl` — atomic single-command dedup
- Listener currently re-throws exceptions for DLT retry. Dedup check must happen before `sendEmail()` to prevent retried events from re-sending

## Requirements

### Functional
- Skip email send if `eventId` was already processed within 24h
- Log skipped duplicates at INFO level with eventId + correlationId
- Mark eventId as processed only after successful email send (not before)

### Non-Functional
- Fail-open: if Redis is unreachable, proceed with email send + log warning
- Sub-millisecond overhead for the SETNX call
- No new REST endpoints or schema changes

## Architecture

```
NotificationEventListener.handleNotificationEvent(envelope)
  1. Extract eventId from envelope
  2. Call deduplicationService.isDuplicate(eventId)
     -> StringRedisTemplate.opsForValue().setIfAbsent("notification:processed:{eventId}", "1", 24h)
     -> returns Boolean: true = new (key set), false = duplicate (key existed)
     -> on RedisException: log.warn, return false (fail-open = treat as new)
  3. If duplicate -> log.info("Duplicate skipped"), return
  4. Else -> emailSenderService.sendEmail(event)
```

Note: Using SETNX *before* send means if send fails + retries, the key is already set and retry would be skipped. Instead, we use a two-step approach:
- Check with `GET` first. If key exists -> skip.
- After successful send -> `SET` with NX + TTL.

Wait — simpler: just do SETNX before send. If send fails, exception propagates, Kafka retries, but key is set. This is a problem.

**Corrected approach:**
1. `GET` key — if exists, skip (duplicate)
2. Send email
3. `SET` key with TTL (mark processed)

This has a small race window (two consumers process same eventId simultaneously) but notification-service has a single consumer group with one partition consumer, so no real concurrency risk. The `GET-then-SET` pattern is acceptable here.

**Alternative (atomic):** Use SETNX as a lock *before* sending. If send fails, delete the key so retries work. But this adds complexity. KISS: use GET/SET since single consumer.

**Final decision:** Use `setIfAbsent` (SETNX) as the guard. If it returns `true` (key was new), proceed to send. If send throws, let exception propagate — the key stays in Redis, but the DLT/retry handler will see the key and skip. This is actually the safer behavior: if email send fails with an ambiguous error (e.g., SMTP timeout where email might have been sent), we don't want to re-send. Accept the trade-off that a genuinely failed send for a transient error won't be retried via the same eventId. The producer should generate a new eventId for genuine retries.

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `notification-service/pom.xml` | Add `spring-boot-starter-data-redis` dependency |
| `notification-service/src/main/resources/application.yml` | Add `spring.data.redis.host` config |
| `notification-service/src/main/java/com/namnd/notification/listener/NotificationEventListener.java` | Inject dedup service, check before send |

### Files to Create
| File | Purpose |
|------|---------|
| `notification-service/src/main/java/com/namnd/notification/service/NotificationDeduplicationService.java` | Redis-backed dedup with SETNX |

## Implementation Steps

### Step 1: Add Redis dependency to pom.xml
Add after the `spring-boot-starter-mail` dependency block:
```xml
<!-- Redis for event deduplication -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

### Step 2: Add Redis config to application.yml
Under `spring:` section, add:
```yaml
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

### Step 3: Create NotificationDeduplicationService
Path: `notification-service/src/main/java/com/namnd/notification/service/NotificationDeduplicationService.java`

```java
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
```

### Step 4: Integrate into NotificationEventListener
Inject `NotificationDeduplicationService` and add dedup check before `emailSenderService.sendEmail()`:

```java
// Add field + constructor param
private final NotificationDeduplicationService deduplicationService;

// In handleNotificationEvent, before the sendEmail call:
if (!deduplicationService.tryMarkProcessed(envelope.eventId())) {
    log.info("Duplicate event skipped: eventId={}, correlationId={}",
             envelope.eventId(), envelope.correlationId());
    return;
}
```

### Step 5: Compile and verify
```bash
cd notification-service && ../mvnw compile -pl . -q
```

## Todo List
- [ ] Add `spring-boot-starter-data-redis` to `notification-service/pom.xml`
- [ ] Add Redis host/port config to `notification-service/src/main/resources/application.yml`
- [ ] Create `NotificationDeduplicationService.java`
- [ ] Update `NotificationEventListener.java` with dedup check
- [ ] Compile notification-service successfully

## Success Criteria
- `NotificationDeduplicationService` uses `StringRedisTemplate.opsForValue().setIfAbsent()` with 24h TTL
- Listener skips email for duplicate eventIds with INFO log
- Redis failure does not block notification delivery (fail-open)
- Service compiles without errors
- No changes to other microservices

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Redis down | Notifications sent without dedup | Fail-open: log warning, proceed |
| SETNX before send + send failure | Event marked but email not sent; retries skipped | Acceptable: ambiguous failures (SMTP timeout) may have sent; producer generates new eventId for genuine retries |
| TTL too short | Late duplicates slip through | 24h covers Kafka retry + DLT backoff windows |
| Key namespace collision | Wrong dedup | Prefix `notification:processed:` + UUID makes collision impossible |

## Security Considerations
- No sensitive data stored in Redis (only eventId UUIDs + "1" value)
- Redis connection uses internal Docker network (no external exposure)
- No new endpoints exposed

## Next Steps
- Proceed to Phase 2 for docker-compose updates
- Consider adding a Micrometer counter for `notification.dedup.skipped` events (future enhancement, not in scope)
