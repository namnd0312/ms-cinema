---
title: "Microservice Integration"
description: "Integrate jwt-spring-security as auth-service with Spring Cloud (Eureka, Config Server, Gateway) + shared JWT starter library"
status: completed
priority: P1
effort: 12h
branch: master
tags: [spring-cloud, microservice, eureka, config-server, gateway, jwt-starter]
created: 2026-03-02
---

# Microservice Integration Plan

## Goal
Transform single-module jwt-spring-security into a multi-module project serving as auth-service in a Spring Cloud ecosystem, with a reusable JWT validation starter for downstream services.

## Architecture Decision
- HS512 symmetric JWT -- all services share secret via Config Server
- JWT validation at per-service level (via starter), gateway only routes
- Roles embedded in JWT claims (requires token generation change)
- Spring Cloud BOM `2024.0.1` for Spring Boot 3.4.x compatibility

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Convert to multi-module Maven project | done | 2h | [phase-01](./phase-01-multi-module-conversion.md) |
| 2 | Add validate-token + userinfo endpoints | done | 1.5h | [phase-02](./phase-02-new-auth-endpoints.md) |
| 3 | Create JWT validation starter library | done | 3h | [phase-03](./phase-03-jwt-starter-library.md) |
| 4 | Spring Cloud integration for auth-service | done | 2h | [phase-04](./phase-04-spring-cloud-auth-service.md) |
| 5 | Infrastructure services (Eureka, Config, Gateway) | done | 2.5h | [phase-05](./phase-05-infrastructure-services.md) |
| 6 | Testing and documentation | done | 1h | [phase-06](./phase-06-testing-documentation.md) |

## Key Dependencies
- Phase 1 must complete first (all others depend on module structure)
- Phase 2 can run parallel with Phase 3
- Phase 4 depends on Phase 1
- Phase 5 depends on Phase 4
- Phase 6 depends on all

## Critical Change: Roles in JWT Claims
Current `JwtService.generateTokenLogin()` does NOT embed roles in JWT. Must add `.claim("roles", rolesList)` so downstream services (via starter) can authorize without DB lookup. This change is in Phase 2.

## Project Structure (Target)
```
jwt-spring-security/            (parent POM, packaging=pom)
  auth-service/                 (existing code, Spring Boot app)
  jwt-auth-spring-boot-autoconfigure/  (auto-config + filter)
  jwt-auth-spring-boot-starter/       (thin wrapper)
  eureka-server/                (discovery)
  config-server/                (centralized config)
  api-gateway/                  (Spring Cloud Gateway MVC)
  samples/sample-service/       (starter demo, Maven profile only)
```

## Validation Summary

**Validated:** 2026-03-02
**Questions asked:** 6

### Confirmed Decisions
- **JWT validation**: Per-service via starter library. Gateway only routes, no JWT logic.
- **Token blacklist**: Starter does NOT check blacklist. 15-min expiry window accepted. Keeps starter lightweight (no Redis).
- **JWT claims migration**: Force re-login approach. Existing tokens validate but roles return empty. No backward-compat code.
- **Secret management**: Plain text in Config Server (dev/staging). Override via env var in prod. Encryption deferred.
- **Port change**: Auth-service moves to 8081. No existing clients affected. Gateway takes 8080.
- **Sample service**: Include in `samples/` dir with Maven profile. Validates starter works end-to-end.

### Action Items
- [ ] Phase 6: Add `sample-service` to `samples/` directory (Maven profile: `-Psamples`)
- [ ] Phase 6: Add gateway route for `/api/sample/**` -> sample-service in test compose
