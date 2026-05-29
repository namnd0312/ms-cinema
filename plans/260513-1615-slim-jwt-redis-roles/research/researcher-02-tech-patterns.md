# Research Report: Spring Boot Autoconfigure + Caffeine + Redis + Kafka Patterns

**Date:** 2026-05-13 | **Sources:** 5 WebSearch queries, Spring Boot docs, GitHub repos, industry blogs

---

## 1. Spring Boot Autoconfigure Module: Caffeine + Redis (Conditional Beans)

### Best Practice: `@ConditionalOnClass` + `@ConditionalOnProperty`

Spring Boot auto-detects cache providers in precedence order. To add dual-layer cache to shared autoconfigure without forcing dependencies:

```java
@Configuration
@ConditionalOnClass({Caffeine.class, RedisTemplate.class})
@ConditionalOnProperty(name = "app.cache.roles.enabled", havingValue = "true")
public class UserRolesCacheAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public UserRolesCache userRolesCache(
            CaffeineCacheManager l1,
            RedisTemplate<String, Set<String>> l2) {
        return new TwoLevelUserRolesCache(l1, l2);
    }
}
```

**Why:** Consumers opt-in via properties. Beans only instantiate if both libs present + property enabled.

### L1 Cache Config: `refreshAfterWrite` vs `expireAfterWrite`

For "30s stale tolerance with refresh on access":
- **`refreshAfterWrite(30, SECONDS)`**: Stale reads allowed; triggers async reload after 30s inactivity
- **`expireAfterWrite(30, SECONDS)`**: Hard eviction; blocks until refresh (slower, fresher)

**Recommendation:** Use `refreshAfterWrite` + background refresh loader. Caffeine spec:
```properties
spring.cache.caffeine.spec=maximumSize=1000,refreshAfterWrite=30s,recordStats
```

### Cache Stampede Protection: Single-Flight Loading

Implement via `LoadingCache` with striped locking:
```java
LoadingCache<String, Set<String>> cache = Caffeine.newBuilder()
    .refreshAfterWrite(30, SECONDS)
    .build(userId -> loadRolesFromRedis(userId));  // Only 1 thread loads per key
```

Spring Data Caffeine auto-wires `CaffeineCacheManager`; no stampede risk if using standard `@Cacheable`.

---

## 2. Kafka Consumer in Shared Autoconfigure

### Auto-picked Consumer Pattern

Add `@KafkaListener` in autoconfigure config class—Spring Boot auto-detects if `spring-kafka` on classpath:

```java
@Configuration
@ConditionalOnClass(KafkaTemplate.class)
public class RoleCacheInvalidationAutoConfiguration {
    
    @KafkaListener(
        topics = "user-role-changed",
        groupId = "${spring.application.name}", // Each service gets unique group
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRoleChanged(UserRoleEvent event) {
        // evict cache for event.getUserId()
    }
}
```

**Topic Naming:** Kafka docs recommend `kebab-case` (e.g., `user-role-changed`), though dots work fine. Use kebab for CLI readability.

**Consumer Group Strategy:** Each service instance uses `${spring.application.name}` group ID—broadcasts same message to all instances. Avoids partition-only semantics.

**Idempotency:** Cache eviction is naturally idempotent (DEL twice = same result). No deduplication needed vs. stateful operations.

---

## 3. Redis Schema: Roles Storage

### Data Structure Recommendation: SET

```redis
SET user:roles:123 '["ADMIN","USER"]' EX 3600
```

**Why SET over Hash/JSON:**
- **SET (string):** Simple, TTL applies to whole key, fast serialization
- **HASH:** Better for objects with multiple fields (role + expiry per-field), but overkill for array
- **JSON:** Requires RedisJSON module; unnecessary complexity

**TTL Strategy:** Set to JWT expiry (1h typical) + 10m buffer. Refresh on each validated JWT decode.

```java
redisTemplate.opsForValue().set(
    "user:roles:" + userId,
    roles,
    tokenExpiry.plusMinutes(10),
    TimeUnit.MILLISECONDS
);
```

### Revocation Pattern: Delete + Tombstone

```redis
DEL user:roles:123          // Immediate eviction
SET user:revoked:123 "1" EX 3600  // Tombstone (optional logging)
```

On cache miss: check tombstone before loading from DB. Prevents reload during grace period.

---

## 4. Backwards-Compatible JWT Migration

### Dual-Mode Auth Filter Pattern

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    protected void doFilterInternal(HttpServletRequest req, ...) {
        String token = extractToken(req);
        
        // Try slim JWT (new)
        if (isSlimJwt(token)) {
            validateAndEnrichWithCachedRoles(token);
        }
        // Fallback to fat JWT (old, with roles claim)
        else if (isFatJwt(token)) {
            validateTraditional(token);  
        }
    }
}
```

**Detection:** Check `kid` header claim or token structure. Slim tokens have `jti` only; fat tokens have `roles`.

**Feature Flag:**
```properties
auth.jwt.migration.enabled=true  # Enable dual-mode

@ConditionalOnProperty(name = "auth.jwt.migration.enabled")
public JwtAuthenticationFilter dualModeFilter() { ... }
```

---

## 5. Fail-Closed vs Fail-Open: Redis Outage

### Industry Standard: Fail-Closed

**Auth0/Okta/Keycloak Pattern:** When auth dependency unreachable → return **503 Service Unavailable**, not 401.
- **503:** Temporary infrastructure problem (client retries safe)
- **401:** Auth failed (client shouldn't retry, treats as invalid cred)

**Implementation:**
```java
catch (RedisConnectionFailureException e) {
    logger.error("Redis unavailable, denying request", e);
    throw new ServiceUnavailableException("Auth cache unavailable");
    // → 503 in HTTP response
}
```

**Logging:** Use rate-limited alerts (not per-request) to avoid spam during outage:
```java
rateLimitedLogger.warn("Redis down for {} seconds", outageDuration);
```

**Fallback Option** (optional, risky): Cache roles in-memory with longer TTL during Redis outage. Only if you accept stale roles for degraded UX.

---

## Key Sources

- [Spring Boot Caching Docs](https://docs.spring.io/spring-boot/reference/io/caching.html)
- [GitHub: Multi-Layer Cache](https://github.com/GaetanoPiazzolla/spring-boot-multi-layer-cache)
- [Baeldung: Two-Level Cache](https://www.baeldung.com/spring-two-level-cache)
- [Redis Token Storage](https://redis.io/tutorials/authentication-token-storage-with-redis/)
- [Kafka Idempotent Consumer Patterns](https://www.conduktor.io/blog/building-idempotent-consumers)

---

## Unresolved Questions

1. **Caffeine stats integration:** How to export `recordStats()` metrics to Prometheus in shared autoconfigure?
2. **Lua script atomic updates:** When role changes while JWT valid—use Redis EVAL (Lua) for atomic compare-and-swap on revoked set?
3. **Consumer group naming collision:** If two services both named "auth-service"—will they collide on same consumer group? (Likely yes, mitigation: add instance-id to group name)
4. **Slow Redis recovery:** On Redis reconnect after long outage, cold cache warms gradually. Risk of auth storms hitting DB?
