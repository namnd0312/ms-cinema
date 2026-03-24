---
title: "FR-3.5: Rate Limiting on Auth Endpoints"
description: "Implement token bucket rate limiting on auth endpoints at API Gateway via custom servlet filter backed by Redis"
status: pending
priority: P2
effort: 2h
branch: master
tags: [security, rate-limiting, api-gateway, redis]
created: 2026-03-23
---

# FR-3.5: Rate Limiting on Sensitive Endpoints

## Context

- [System Architecture](../../docs/system-architecture.md)
- [Code Standards](../../docs/code-standards.md)
- [API Gateway application.yml](../../api-gateway/src/main/resources/application.yml)

## Summary

Add per-IP rate limiting to auth endpoints at the API Gateway level. Gateway uses `spring-cloud-starter-gateway-mvc` (servlet-based), so the reactive `RequestRateLimiter` filter is NOT available. Implementation requires a custom servlet filter + Redis token bucket.

## Architecture Decision

**Gateway MVC (servlet) vs Gateway Reactive:** The project uses `gateway-mvc`. The built-in `RedisRateLimiter` only works with reactive gateway. We implement a custom `OncePerRequestFilter` using `StringRedisTemplate` with Lua script for atomic token bucket operations. This keeps the approach simple, avoids switching to reactive gateway, and reuses the existing Redis instance.

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Gateway Redis Dependency Setup | pending | 30m | [phase-01](./phase-01-gateway-redis-dependency-setup.md) |
| 2 | Rate Limiter Filter & Configuration | pending | 1h30m | [phase-02](./phase-02-rate-limiter-configuration.md) |

## Key Dependencies

- Redis 7 (already running in docker-compose as `redis-service`)
- `spring-boot-starter-data-redis` (new dependency for api-gateway)
- Existing `HttpLoggingFilter` (order -100) runs before rate limiter

## Target Endpoints & Limits

| Endpoint | Limit | replenishRate | burstCapacity |
|----------|-------|---------------|---------------|
| POST /api/auth/login | 5/min | 5 | 5 |
| POST /api/auth/register | 3/min | 3 | 3 |
| POST /api/auth/forgot-password | 3/min | 3 | 3 |
| POST /api/auth/reset-password | 3/min | 3 | 3 |
| POST /api/auth/refresh-token | 10/min | 10 | 10 |
| POST /api/auth/activate | 3/min | 3 | 3 |
| POST /api/auth/** (fallback) | 20/min | 20 | 20 |

## Unresolved Questions

1. Should rate limits be configurable via config-server (application.yml) or hardcoded? Plan assumes YAML-configurable via `@ConfigurationProperties`.
2. Should rate limit responses include `X-RateLimit-Remaining` and `X-RateLimit-Reset` headers for client transparency? Plan includes them.
3. Should there be a global rate limit across all gateway routes (not just auth)? Out of scope for FR-3.5.
