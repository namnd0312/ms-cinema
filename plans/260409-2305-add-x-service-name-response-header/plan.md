---
title: "Add X-Service-Name Response Header"
description: "Add custom HTTP header to identify which microservice handled each API request"
status: pending
priority: P2
effort: 1h
branch: k8s
tags: [devtools, observability, shared-library]
created: 2026-04-09
---

# Add X-Service-Name Response Header

## Goal
Add `X-Service-Name` header to ALL HTTP responses so developers can identify the handling service from browser Network tab.

## Approach
Create a `OncePerRequestFilter` in `jwt-auth-autoconfigure` shared library. Inject `spring.application.name` via `@Value`. Register as bean in `JwtAutoConfiguration`. All 6 services auto-inherit.

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Add ServiceNameHeaderFilter to shared library](./phase-01-add-service-name-filter-to-shared-library.md) | pending | 30m |
| 2 | [Verify K8s Ingress and CORS config](./phase-02-verify-k8s-ingress-and-cors-config.md) | pending | 10m |
| 3 | [Compile and test](./phase-03-compile-and-test.md) | pending | 20m |

## Key Decisions
- Filter in shared lib (DRY) -- not per-service
- Use `OncePerRequestFilter` (same pattern as `JwtAuthenticationFilter`)
- No CORS changes needed -- Network tab shows all headers regardless of expose config
- No K8s Ingress changes needed -- NGINX passes upstream headers by default

## Dependencies
- `jwt-auth-autoconfigure` module (already imported by all 6 services)
- `spring.application.name` set in each service's `application.yml` (confirmed)

## Reports
- [Scout Report](./reports/scout-report.md)
