---
title: "FR-4.1: Audit Logging"
description: "Full audit trail with Kafka event streaming, centralized audit-service, and admin API"
status: completed
priority: P1
effort: 12h
branch: master
tags: [audit, logging, kafka, security, compliance]
created: 2026-03-21
reviewed: 2026-03-21
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
| 1 | [Shared audit event model](phase-01-shared-audit-event-model.md) | 1h | completed |
| 2 | [Audit interceptor library](phase-02-audit-interceptor-library.md) | 3h | completed |
| 3 | [Create audit-service](phase-03-create-audit-service.md) | 3h | completed |
| 4 | [Admin API](phase-04-admin-api.md) | 2h | completed |
| 5 | [Integrate existing services](phase-05-integrate-existing-services.md) | 2h | completed |
| 6 | [Infrastructure](phase-06-infrastructure.md) | 1h | completed |

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

### Action Items — Implementation Deviations (resolved)
- [x] Phase 4: Change API path from `/api/v1/admin/audit-logs` to `/api/audit/logs` — done
- [x] Phase 2: Add HTTP context (IP, request path) capture — done via `AuditHttpContext` (ThreadLocal via `RequestContextHolder`)
- [ ] Phase 2: Move interceptor code to `audit-commons` module — **NOT done**: interceptors placed in `kafka-events/audit/` package instead. Pragmatic choice; acceptable for current scale.
- [ ] Phase 2: Replace JPA EntityListener with Hibernate Envers — **NOT done**: `AuditEntityListener` implemented but never applied (`@EntityListeners` not added to any entity). Deferred to v2.
- [ ] Phase 5: Add Envers `@Audited` annotations — **NOT done** (Envers deferred to v2)
- [ ] Phase 1: Keep AuditEvent DTO in kafka-events, interceptors in audit-commons — **partially done**: AuditEvent is in kafka-events ✓, but interceptors are also in kafka-events ✗

### Code Review Findings (2026-03-21)
See full report: `/plans/reports/code-reviewer-260321-2326-audit-logging-full-trail.md`

**Required fixes:**
- [ ] H1: Add `LOGIN` to `serializeResult` skip list in `AuditAspect` — JWT token leakage risk
- [ ] H2: Fix `@Index.columnList` to use snake_case physical column names in `AuditLog` (userId→user_id, entityType→entity_type, createdAt→created_at)

**Recommended fixes:**
- [ ] M1: Remove unused `AuditEntityListener` (dead code) or document as v2 placeholder
- [ ] M2: Add `@Transactional` + `DataIntegrityViolationException` catch to `AuditEventConsumer.consume()`
- [ ] M4: Null-guard `envelope.timestamp()` in `AuditLogMapper`
