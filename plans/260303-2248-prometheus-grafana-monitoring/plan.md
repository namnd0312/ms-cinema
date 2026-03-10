---
title: "Prometheus & Grafana Monitoring"
description: "Integrate Prometheus metrics collection and Grafana dashboards into microservice architecture"
status: implementation-complete-pending-runtime-verification
priority: P2
effort: 3.5h
branch: master
tags: [infra, monitoring, observability]
created: 2026-03-03
---

# Prometheus & Grafana Monitoring

## Overview

Add full-stack observability to the movie-ticket-booking microservice platform. Micrometer exports metrics from all 7 services; Prometheus scrapes them; Grafana visualizes via auto-provisioned dashboards with per-service filtering.

## Architecture

```
Services (8081-8084, 8080, 8761, 8888)
    │ /actuator/prometheus
    ▼
Prometheus (:9090) ──scrape 15s──> static targets
    │
    ▼
Grafana (:3000) ──query──> Prometheus datasource
    ├── JVM Dashboard (community ID 4701)
    └── HTTP Overview Dashboard (custom, template vars)
```

## Phases

| # | Phase | Status | Effort | Link |
|---|-------|--------|--------|------|
| 1 | Add Micrometer + configure actuator | Complete | 1h | [phase-01](./phase-01-micrometer-actuator-setup.md) |
| 2 | Setup Prometheus infrastructure | Complete | 30m | [phase-02-prometheus-infrastructure.md](./phase-02-prometheus-infrastructure.md) |
| 3 | Setup Grafana with auto-provisioned dashboards | Complete | 1h | [phase-03-grafana-dashboards.md](./phase-03-grafana-dashboards.md) |
| 4 | Testing & verification | Pending (runtime) | 30m | [phase-04-testing-verification.md](./phase-04-testing-verification.md) |

## Dependencies

- All services must have `spring-boot-starter-actuator` (5/7 already do)
- Docker Compose network `my-net` — Prometheus & Grafana join same network
- JWT starter public-paths must include `/actuator/prometheus` for unauthenticated scraping

## Key Decisions

1. **Single dashboard + template variables** vs separate per-service dashboards → Single dashboard with `$application` variable selector. Same result, zero duplication.
2. **Community JVM dashboard (ID 4701)** for JVM metrics — battle-tested, comprehensive.
3. **Static scrape targets** in prometheus.yml — simple, predictable for Docker Compose.
4. **`monitoring/` directory** at project root for all Prometheus/Grafana config files.

## Validation Summary

**Validated:** 2026-03-03
**Questions asked:** 4
**Code reviewed:** 2026-03-03 — see [review report](../reports/code-reviewer-260303-2307-prometheus-grafana-monitoring.md)

### Confirmed Decisions
- **Custom business metrics:** YES — add Micrometer Counter/Timer beans for auth events (login success/fail, register), booking creation, payment completion (~30min extra, absorbed into Phase 1)
- **auth-service SecurityConfig:** Add `/actuator/**` to `permitAll()` — plan missed that auth-service's SecurityConfig blocks actuator endpoints
- **Docker image versions:** Use `latest` tags — user preference for simplicity
- **Alerting rules:** Dashboards only — no Prometheus alerting rules for now

### Action Items (Plan Revisions Needed)
- [ ] Phase 1: Add step to update `auth-service SecurityConfig.java` — permit `/actuator/**`
- [ ] Phase 1: Add step to create custom Micrometer Counter/Timer beans in auth-service, booking-service, payment-service
- [ ] Phase 3: Add custom business metrics panels to HTTP overview dashboard

## Research Reports

- [Prometheus + Micrometer Research](./reports/researcher-01-prometheus-micrometer.md)
- [Grafana Provisioning Research](./reports/researcher-02-grafana-provisioning.md)
