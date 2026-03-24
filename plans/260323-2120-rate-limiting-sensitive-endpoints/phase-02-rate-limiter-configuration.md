# Phase 2: Rate Limiter Filter & Configuration

## Context Links

- [plan.md](./plan.md)
- [Phase 1](./phase-01-gateway-redis-dependency-setup.md)
- [HttpLoggingFilter](../../api-gateway/src/main/java/com/namnd/apigateway/config/filter/HttpLoggingFilter.java)
- [HttpLoggingConfig](../../api-gateway/src/main/java/com/namnd/apigateway/config/HttpLoggingConfig.java)

## Overview

- **Date:** 2026-03-23
- **Priority:** P2
- **Status:** pending
- **Review:** pending
- **Description:** Implement custom servlet rate limit filter using Redis token bucket algorithm with per-endpoint configurable limits

## Key Insights

- `spring-cloud-starter-gateway-mvc` does NOT support `RequestRateLimiter` GatewayFilter (reactive-only)
- Custom `OncePerRequestFilter` registered as servlet filter is the correct approach
- Token bucket via Redis Lua script ensures atomic increment+check (no race conditions)
- Existing `HttpLoggingFilter` at order -100; rate limiter should run at order -50 (after logging, before gateway routing)
- `resolveClientIp()` logic already exists in HttpLoggingFilter — extract or duplicate for rate limiter
- Redis key pattern: `rate_limit:{endpoint_key}:{client_ip}` with TTL = window size (60s)

## Requirements

### Functional
- Rate limit POST requests to auth endpoints per client IP
- Return HTTP 429 with JSON error body and `Retry-After` header when exceeded
- Per-endpoint configurable limits via application.yml
- General `/api/auth/**` fallback rate for unmatched auth endpoints
- Only rate-limit POST method (GET requests pass through)

### Non-Functional
- Atomic Redis operations (Lua script) — no race conditions under concurrency
- Fail-open: Redis unavailable = allow request (log warning)
- < 1ms overhead per request for Redis check
- Rate limit config reloadable via config-server refresh

## Architecture

```
Request → HttpLoggingFilter(-100) → RateLimitFilter(-50) → Gateway Router → Downstream
                                        |
                                   Redis Lua Script
                                   (INCR + EXPIRE atomic)
                                        |
                                  tokens > 0 → ALLOW
                                  tokens = 0 → 429
```

### Token Bucket Lua Script

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or '0')
if current >= limit then
    return {0, current, tonumber(redis.call('TTL', key))}
end
current = redis.call('INCR', key)
if current == 1 then
    redis.call('EXPIRE', key, window)
end
return {1, current, tonumber(redis.call('TTL', key))}
```

Returns: `{allowed(1/0), current_count, ttl_seconds}`

## Related Code Files

### Files to Create
- `api-gateway/src/main/java/com/namnd/apigateway/config/filter/RateLimitFilter.java` — servlet filter (~120 lines)
- `api-gateway/src/main/java/com/namnd/apigateway/config/RateLimitConfig.java` — filter registration + bean config (~40 lines)
- `api-gateway/src/main/java/com/namnd/apigateway/config/RateLimitProperties.java` — `@ConfigurationProperties` for per-endpoint limits (~60 lines)
- `api-gateway/src/main/resources/scripts/rate-limit.lua` — Redis Lua script (~12 lines)

### Files to Modify
- `api-gateway/src/main/resources/application.yml` — add rate limit properties

## Implementation Steps

### Step 1: Create RateLimitProperties.java

Path: `api-gateway/src/main/java/com/namnd/apigateway/config/RateLimitProperties.java`

```java
package com.namnd.apigateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rate limit configuration per endpoint path.
 * Loaded from application.yml under "rate-limit" prefix.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Whether rate limiting is enabled (fail-safe toggle) */
    private boolean enabled = true;

    /** Default requests per window for unmatched auth endpoints */
    private int defaultLimit = 20;

    /** Window size in seconds */
    private int windowSeconds = 60;

    /**
     * Per-endpoint overrides. Key = path prefix, Value = max requests per window.
     * Matched in order — first match wins. Use LinkedHashMap for insertion order.
     * Example: "/api/auth/login" -> 5
     */
    private Map<String, Integer> endpoints = new LinkedHashMap<>();
}
```

### Step 2: Create rate-limit.lua

Path: `api-gateway/src/main/resources/scripts/rate-limit.lua`

```lua
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])

local current = tonumber(redis.call('GET', key) or '0')
if current >= limit then
    return {0, current, tonumber(redis.call('TTL', key))}
end
current = redis.call('INCR', key)
if current == 1 then
    redis.call('EXPIRE', key, window)
end
return {1, current, tonumber(redis.call('TTL', key))}
```

### Step 3: Create RateLimitFilter.java

Path: `api-gateway/src/main/java/com/namnd/apigateway/config/filter/RateLimitFilter.java`

Key logic:
1. Check if request is POST to `/api/auth/**`
2. Resolve client IP (X-Forwarded-For → remoteAddr)
3. Match endpoint against `RateLimitProperties.endpoints` (first match wins)
4. Execute Lua script against Redis with key `rate_limit:{path}:{ip}`
5. If allowed → set `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers, continue chain
6. If denied → return 429 JSON response with `Retry-After` header
7. If Redis error → log warn, allow request (fail-open)

Response body on 429:
```json
{
    "status": 429,
    "error": "Too Many Requests",
    "message": "Rate limit exceeded. Try again later.",
    "retryAfter": 45
}
```

### Step 4: Create RateLimitConfig.java

Path: `api-gateway/src/main/java/com/namnd/apigateway/config/RateLimitConfig.java`

Register filter at order -50, load Lua script as `RedisScript<List>` bean.

```java
@Configuration
public class RateLimitConfig {

    @Bean
    public RedisScript<List> rateLimitScript() {
        return RedisScript.of(new ClassPathResource("scripts/rate-limit.lua"), List.class);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
            StringRedisTemplate redisTemplate,
            RedisScript<List> rateLimitScript,
            RateLimitProperties properties) {
        var filter = new RateLimitFilter(redisTemplate, rateLimitScript, properties);
        var reg = new FilterRegistrationBean<>(filter);
        reg.setOrder(-50);
        reg.addUrlPatterns("/api/auth/*");
        return reg;
    }
}
```

### Step 5: Add rate limit config to application.yml

```yaml
rate-limit:
  enabled: true
  default-limit: 20
  window-seconds: 60
  endpoints:
    /api/auth/login: 5
    /api/auth/register: 3
    /api/auth/forgot-password: 3
    /api/auth/reset-password: 3
    /api/auth/refresh-token: 10
    /api/auth/activate: 3
```

### Step 6: Verify compilation and test

```bash
cd api-gateway && mvn clean compile
```

Manual test with curl:
```bash
# Hit login 6 times rapidly — 6th should return 429
for i in $(seq 1 6); do
  curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"test"}'
done
```

## Todo List

- [ ] Create `RateLimitProperties.java` with `@ConfigurationProperties`
- [ ] Create `rate-limit.lua` Lua script
- [ ] Create `RateLimitFilter.java` extending `OncePerRequestFilter`
- [ ] Create `RateLimitConfig.java` for filter registration and script bean
- [ ] Add rate-limit config block to `application.yml`
- [ ] Run `mvn clean compile` — verify no errors
- [ ] Manual test: hit endpoint N+1 times, verify 429 on excess
- [ ] Verify fail-open: stop Redis, confirm requests pass through
- [ ] Verify `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `Retry-After` headers

## Success Criteria

- POST to rate-limited auth endpoints returns 429 after exceeding configured limit
- Response includes JSON body with status, error, message, retryAfter
- Response includes `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining` headers
- GET requests to auth endpoints are NOT rate-limited
- Non-auth endpoints are NOT affected
- Redis failure = requests pass through with warning log
- Config changeable via application.yml without code changes
- Rate limit resets after window expires (60s default)

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| IP spoofing via X-Forwarded-For | Bypass rate limit | Trust X-Forwarded-For only from known proxies; acceptable for initial impl |
| Shared IP (NAT/VPN) blocks legit users | False positives | 5 req/min for login is generous for single user; monitor and adjust |
| Lua script not loaded | Filter fails silently | Fail-open + startup validation log |
| Redis memory growth | Unbounded keys | TTL on every key (60s window) auto-expires; negligible memory |

## Security Considerations

- Rate limiting is defense-in-depth against brute-force attacks on login
- Complements existing account lockout (5 attempts → 15min lock in auth-service)
- IP-based limiting doesn't protect against distributed attacks but raises the bar significantly
- Redis keys auto-expire — no PII retention concern
- `Retry-After` header helps legitimate clients back off gracefully
- Rate limit bypass via IP spoofing mitigated by reverse proxy (nginx/load balancer) stripping client-set X-Forwarded-For in production

## Next Steps

- After implementation, update `docs/project-roadmap.md` to mark "Rate limiting on login/forgot-password endpoints" as complete
- Update `docs/system-architecture.md` to document rate limiting at gateway level
- Consider future: global rate limiting for all routes, per-user rate limiting (authenticated), distributed rate limiting across gateway instances
