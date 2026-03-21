---
title: "FR-4.1: Audit Logging"
description: "Full audit trail with Kafka event streaming, centralized audit-service, and admin API"
status: pending
priority: P1
effort: 12h
branch: master
tags: [audit, logging, kafka, security, compliance]
created: 2026-03-21
---

# FR-4.1: Audit Logging — Full Trail

## Overview

Add comprehensive audit logging: AOP-based business event capture, JPA EntityListener for data change tracking (before/after JSON), Kafka `audit-events` topic for async transport, centralized `audit-service` (port 8086) with PostgreSQL persistence, and admin REST API with filtering/pagination.

## Architecture

```
[Business Services] --@Auditable/EntityListener--> [AuditEventPublisher]
    --> Kafka audit-events topic
    --> [audit-service] Kafka consumer --> PostgreSQL auditdb (audit_logs JSONB)
    --> [Admin API] GET /api/v1/admin/audit-logs (JPA Specifications)
```

## Key Decisions (validated 2026-03-21)

- **AOP + @Auditable** for method-level business events
- **Hibernate Envers** for entity before/after data change tracking (full before+after state from v1)
- **New `audit-commons` module** for interceptor code (not in kafka-events — keep it lightweight)
- **Single Kafka topic** `audit-events` partitioned by correlationId
- **AuditEvent record** in kafka-events shared lib (reuse EventEnvelope wrapper)
- **API path: `/api/audit/**`** — matches existing service conventions (no version prefix)
- **HTTP context captured** — IP, request path passed via MDC into audit events
- **No frontend UI** — API only for now, admin dashboard tab deferred
- **JPA Specifications** for dynamic admin query filtering
- **No table partitioning** yet (YAGNI — volume is small, add later if needed)

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Shared audit event model](phase-01-shared-audit-event-model.md) | 1h | pending |
| 2 | [Audit interceptor library](phase-02-audit-interceptor-library.md) | 3h | pending |
| 3 | [Create audit-service](phase-03-create-audit-service.md) | 3h | pending |
| 4 | [Admin API](phase-04-admin-api.md) | 2h | pending |
| 5 | [Integrate existing services](phase-05-integrate-existing-services.md) | 2h | pending |
| 6 | [Infrastructure](phase-06-infrastructure.md) | 1h | pending |

## Dependencies

- kafka-events module (shared lib) — phases 1-2 modify this
- All business services depend on kafka-events — phase 5
- config-server, docker-compose, api-gateway — phase 6
- audit-service new module — phase 3

## Research Reports

- [AOP, Envers, Schema](research/researcher-01-aop-envers-schema.md)
- [Admin API, Retention, Architecture](research/researcher-02-admin-api-retention-architecture.md)

## Validation Summary

**Validated:** 2026-03-21 | **Questions asked:** 6

### Confirmed Decisions
- **Interceptor location:** New `audit-commons` module (not kafka-events)
- **Change tracking:** Full before+after state via Hibernate Envers (not EntityListener)
- **API path:** `/api/audit/**` (match existing convention, no version prefix)
- **HTTP context:** Capture IP + request path via MDC in audit events
- **Before-state approach:** Hibernate Envers (auto revision tables)
- **Frontend:** No UI — API only for now

### Action Items (plan revisions needed before implementation)
- [ ] Phase 2: Replace JPA EntityListener with Hibernate Envers for data change tracking
- [ ] Phase 2: Move interceptor code to new `audit-commons` module instead of kafka-events
- [ ] Phase 4: Change API path from `/api/v1/admin/audit-logs` to `/api/audit/logs`
- [ ] Phase 5: Add Envers `@Audited` annotations to key entities + add Envers deps
- [ ] Phase 2: Add HTTP context (IP, request path) capture via MDC ThreadLocal
- [ ] Phase 1: Keep AuditEvent DTO in kafka-events, but interceptors go to audit-commons
