---
title: "Idempotent Notification Delivery via Redis Deduplication"
description: "Prevent duplicate email notifications by tracking processed eventIds in Redis with SETNX + TTL"
status: complete
priority: P2
effort: 2h
branch: master
tags: [notification-service, redis, idempotency, kafka]
created: 2026-03-08
---

# Idempotent Notification Delivery via Redis Deduplication

## Problem
Kafka at-least-once delivery + DLT retries can cause duplicate email sends. No dedup guard exists in `NotificationEventListener`.

## Solution
Use Redis `SETNX` (atomic check-and-set) on `notification:processed:{eventId}` keys with 24h TTL. Check before sending email; skip if key exists.

## Scope
- 4 files modified, 1 file created
- No schema changes, no new APIs, no breaking changes

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Redis dependency, dedup service, listener integration, config](./phase-01-redis-dedup-service.md) | complete | 1.5h |
| 2 | [Docker Compose + integration test](./phase-02-docker-and-testing.md) | complete | 0.5h |

## Key Dependencies
- `redis-service:6379` already running in docker-compose
- `EventEnvelope.eventId()` — UUID string, unique per event instance
- auth-service pattern: `spring-boot-starter-data-redis` + `StringRedisTemplate` (we use simpler approach — no custom RedisConfig needed)

## Architecture
```
Kafka --> NotificationEventListener
              |
              v
         DeduplicationService.tryMarkProcessed(eventId)
              |
        +-----------+
        | Redis     |
        | SETNX key |
        | TTL 24h   |
        +-----------+
              |
         true (new) --> EmailSenderService.sendEmail()
         false (dup) --> log + skip
```

## Risk
- Redis down: fail-open (send email, log warning) vs fail-closed. Recommend fail-open to avoid blocking notifications.
- TTL too short: duplicates possible after expiry. 24h covers all realistic retry windows.
