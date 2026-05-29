# Brainstorm — JWT roles bloat & realtime revocation

**Date:** 2026-05-13
**Context:** ms-cinema microservices, Spring Boot, shared `jwt-auth-autoconfigure`, Redis + Kafka already in stack.

---

## 1. Problem statement

Current state:
- `JwtService.generateTokenLogin()` embeds full role-name list into JWT claim `roles` (`auth-service/.../JwtService.java:41-48`).
- Downstream services (booking, movie, payment, notification, audit) consume `jwt-auth-autoconfigure` which does **claims-only** validation — no DB/Redis lookup (`JwtTokenValidator.java`, `JwtAuthenticationFilter.java`).
- Refresh-token flow re-embeds same role list (`JwtService.generateTokenFromEmail(email, userId, roles)`).

Pain points:
1. **Header bloat** — as roles grow (RBAC scales, per-feature roles, multi-cinema scoping), `Authorization` header size explodes. Risk of hitting proxy/server header limits (nginx default 8KB), wasted bandwidth per request.
2. **No realtime revocation** — once issued, roles in JWT remain valid until token expires. Admin demoting a user, banning, or revoking a permission → user keeps elevated access until JWT expiry.
3. Blacklist exists for **whole tokens** (`BlacklistedTokenService`) but is NOT enforced in downstream filter → even token revocation doesn't propagate to other services.

---

## 2. Approaches evaluated

### Approach A — Opaque tokens + introspection (RFC 7662)
JWT replaced by random reference ID. Every downstream request hits `/oauth/introspect` on auth-service to resolve identity + roles.

- **Pros:** True realtime revocation. Tiny header. Standardized (OAuth2).
- **Cons:** Network hop per request → latency + auth-service becomes hot SPOF. Defeats stateless-JWT design. Major rework of `jwt-auth-autoconfigure`.
- **Fit:** ❌ Over-engineered for current scale, high blast radius.

### Approach B — Compact role encoding (bitmap / permission mask)
Keep roles in JWT but encode as bitmap (`long` permission mask) or short codes (`"a,r,w"` instead of `"ROLE_ADMIN,ROLE_REVIEWER,..."`).

- **Pros:** Drastically smaller header (single `long` = 64 perms). Minimal code change. No realtime cost.
- **Cons:** Does NOT solve revocation. Requires permission-code registry kept in sync across services. Bitmap caps at 64 unless using multiple longs/strings.
- **Fit:** ⚠️ Solves only half the problem. Useful as complement, not a full solution.

### Approach C — Slim JWT + roles in Redis with local cache + Kafka invalidation ⭐
JWT carries **only** `sub`, `userId`, `jti`, `exp` (no roles). Downstream services resolve roles from Redis on each request, with in-memory cache (Caffeine, ~30s TTL). Auth-service publishes `RoleChangedEvent` / `UserRevokedEvent` via Kafka → services evict cache.

- **Pros:**
  - Tiny header, scales to unlimited roles.
  - Near-realtime revocation (≤ cache TTL or instant via Kafka).
  - Reuses existing Redis + Kafka infrastructure.
  - Auth flow stateless except for one Redis hit + local cache (~µs hot path).
- **Cons:**
  - Adds Redis dependency to every service (already present in auth, needs adding to others).
  - Slight latency floor (one Redis GET on cache miss).
  - Need to populate Redis on login + keep in sync with role changes.
- **Fit:** ✅ Best fit for current stack.

### Approach D — Token version + Redis tombstone check ⭐ (lighter variant)
Keep roles in JWT (or compact-encoded) but add `pv` (permission version) claim. Auth-service maintains `user:pv:{userId}` in Redis. Downstream filter checks `claim.pv == redis.get(user:pv:userId)`; mismatch → 401 force re-login. Role/permission change → `INCR user:pv:userId`.

- **Pros:**
  - Minimal change to JWT structure.
  - Near-realtime revocation (one Redis GET per request, cacheable locally for 5-10s).
  - Per-user revocation granularity.
- **Cons:**
  - Doesn't solve header bloat unless combined with Approach B (bitmap).
  - User forced to re-login on any role change (worse UX than C, but simpler).
- **Fit:** ✅ Good fit if header bloat is acceptable short-term.

### Approach E — Short-lived JWT + refresh-token rotation
Drop JWT TTL to 60-120s. Roles still in JWT, but revoked role only persists for token lifetime. Force role refresh via refresh-token endpoint.

- **Pros:** Zero new infrastructure. Trivial config change.
- **Cons:** Doesn't fix header bloat. ~60s revocation lag is too long for security-sensitive ops (ban abuse). High refresh-endpoint load.
- **Fit:** ⚠️ Stopgap only.

---

## 3. Recommendation

**Adopt Approach C (Slim JWT + Redis roles + Kafka invalidation)** as the primary solution.

### Why
1. **Solves both problems** — header stays constant size regardless of role count; revocation propagates within Kafka latency (sub-second) or worst-case local cache TTL.
2. **Leverages existing infra** — Redis + Kafka already in stack. No new dependencies.
3. **Stateless-friendly** — downstream services stay horizontally scalable; Redis hit is cheap and cacheable.
4. **Future-proof** — supports RBAC scaling, per-resource permissions, multi-tenant role scoping later.

### Architecture sketch

```
LOGIN
  auth-service
    ├── issues slim JWT { sub, userId, jti, exp }   (~200 bytes header)
    └── writes Redis: user:roles:{userId} = [roles...] TTL = jwtExp + buffer

REQUEST to booking-service
  jwt-auth-autoconfigure filter
    ├── verify JWT signature + exp (existing)
    ├── Caffeine cache GET user:roles:{userId}
    │     └── miss → Redis GET → populate cache (TTL 30s)
    └── build Authentication with roles → SecurityContext

ROLE CHANGE (admin demotes user, ban, etc.)
  auth-service
    ├── update DB
    ├── update Redis: user:roles:{userId}
    └── publish Kafka: user-role-changed { userId }

  every service consumer
    └── evict Caffeine cache entry → next request re-reads Redis

REVOKE / LOGOUT
  auth-service
    ├── DELETE user:roles:{userId}    (forces 401 on next request anywhere)
    └── publish Kafka: user-revoked { userId }
```

### Optional combo: pair with Approach B
Once C is in place, you can additionally compress role list (Approach B). But likely unnecessary — once roles are out of the JWT, header bloat is gone regardless.

---

## 4. Implementation considerations

### Migration path (low-risk)
1. **Phase 1** — Slim JWT: stop embedding `roles` claim; only `userId/sub/jti/exp`. Auth-service writes roles to Redis at login. `jwt-auth-autoconfigure` reads roles from Redis (add Spring Data Redis dep to shared module). Backwards-compat: if `roles` claim present, prefer it (rolling deploy).
2. **Phase 2** — Add Caffeine local cache layer in filter to absorb Redis load.
3. **Phase 3** — Add Kafka topic `user-role-changed` / `user-revoked`. Auth-service publishes; downstream services subscribe and invalidate local cache.
4. **Phase 4** — Remove legacy `roles` claim fallback after all clients re-issue tokens.

### Redis schema
```
user:roles:{userId}        → SET of role names         (TTL = jwt-exp + 5min)
user:revoked:{userId}      → "1"                        (TTL = jwt-exp; tombstone for ban)
session:{jti}              → existing blacklist        (unchanged)
```

### Failure modes
- **Redis down** → filter must fail-closed (401) or fail-open with cached values only. Recommend: serve from local cache if present, else 503 with retry-after. Document the dependency.
- **Kafka lag** → cache TTL acts as upper bound on staleness (set conservatively, e.g., 30s).
- **Cache stampede** → Caffeine + `refreshAfterWrite` handles single-flight.

### Performance
- Redis GET: ~0.5ms LAN
- Caffeine hit: ~µs
- At 99% cache hit ratio with 30s TTL, ~1 Redis call / 30s / user / service = negligible load.

---

## 5. Success metrics

- `Authorization` header avg size < 500 bytes (down from current ~N×40 bytes/role).
- Role revocation propagation P99 < 2 seconds (Kafka + cache TTL bound).
- Auth filter P99 latency unchanged (< 5ms).
- Zero auth-service hot-pathing per request (no introspection added).

---

## 6. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Redis becomes new SPOF for auth | Add Redis HA (sentinel/cluster) + local cache fallback window |
| Stale roles in cache during incident | Tunable TTL + Kafka push invalidation |
| Increased coupling: services need Redis | Acceptable — Redis already used for caching elsewhere; alternative is worse (introspection) |
| Migration breaks running tokens | Dual-mode filter during phase 1 (read from claim OR Redis) |

---

## 7. Next steps

1. Confirm direction with user (Approach C vs. lighter Approach D).
2. Spawn `/plan` to break down phased implementation (auto-config changes, kafka event schema, migration cutover).
3. Define Kafka event contracts in `kafka-events` shared module.
4. Decide Redis cache TTL policy (recommend 30s) and fail-closed vs fail-open behavior.

---

## 8. Unresolved questions

- Should slim JWT also drop `userId` claim (resolve from `sub`)? Recommend keep `userId` for cheap downstream FK use.
- Multi-tenant / per-cinema role scoping planned? If yes, Redis key shape may need `user:roles:{userId}:{tenantId}`.
- SLA target for revocation propagation? Sub-second (Kafka) vs. acceptable 30s (TTL only) drives complexity.
- Are there clients holding long-lived JWTs (mobile)? They'd benefit most from this change.
