---
title: "Migrate distributed tracing from Zipkin to OpenTelemetry + Grafana Tempo"
description: "Replace Zipkin with OTel Collector + Tempo; preserve trace-to-logs correlation in Grafana"
status: pending
priority: P2
effort: 10h
branch: k8s
tags: [observability, opentelemetry, tracing, grafana, tempo, infrastructure]
created: 2026-05-09
---

## Intent

Replace existing Zipkin tracing pipeline with industry-standard OpenTelemetry stack: Spring Boot apps export OTLP/HTTP → OTel Collector (contrib) → Grafana Tempo. Grafana keeps Loki + Prometheus, gains Tempo datasource with `tracesToLogsV2` correlation via `service.name`. Why: Zipkin is legacy; OTLP unlocks tail-sampling, span metrics, vendor-neutral pipeline, and richer Grafana UX (node graph, trace-to-logs/metrics).

## Architecture

```
                 ┌────────────────┐  OTLP/HTTP 4318
   6 Spring apps ─┤ otel-collector ├──── OTLP/gRPC 4317 ──> Tempo (3200 query)
   (auth, movie,  │  (contrib)     │                          │
    booking, ...) └────────────────┘                          │
        │                                                     │
        │  loki4j (logback)                                    │
        ▼                                                     ▼
       Loki  <─────── Grafana ─── tracesToLogsV2 ───────── Tempo
                       │       (service.name → service)
                       └── Prometheus (tracesToMetrics)
```

## Phases

| ID | Title | Status | Effort | Path |
|----|-------|--------|--------|------|
| 01 | Add OTel Collector + Tempo to docker-compose | pending | 1.5h | phase-01-add-otel-collector-and-tempo-infra-to-docker-compose.md |
| 02 | Add OTel Collector + Tempo k8s manifests | pending | 2h | phase-02-add-otel-collector-and-tempo-k8s-manifests.md |
| 03 | Swap Zipkin exporter for OTLP in 6 services | pending | 2h | phase-03-swap-zipkin-exporter-for-otlp-in-all-six-services.md |
| 04 | Update Grafana datasource provisioning for Tempo | pending | 1h | phase-04-update-grafana-datasource-provisioning-for-tempo.md |
| 05 | Remove Zipkin from docker-compose and k8s | pending | 1h | phase-05-remove-zipkin-from-docker-compose-and-k8s.md |
| 06 | Update README + docs | pending | 1h | phase-06-update-documentation-readme-and-docs.md |
| 07 | Validate end-to-end trace flow | pending | 1.5h | phase-07-validate-end-to-end-trace-flow.md |

## Dependencies

- Phase 01 → blocks 03 (collector must exist before apps target it locally)
- Phase 02 → blocks 03 in k8s path (configmap update lands with app rollout)
- Phase 03 → blocks 04 (must have traces before testing datasource)
- Phase 04 → blocks 07 (validation needs Tempo datasource visible in Grafana)
- Phase 05 → MUST run AFTER 07 confirms traces flow (avoid downtime)
- Phase 06 → can run parallel with 05/07

## Phase Ordering Rationale

Infra-up first (01-02) so traces have somewhere to go BEFORE switching exporters. App swap (03) is reversible config-only change. Grafana datasource (04) makes traces queryable. Validation (07) confirms green path. Only THEN remove Zipkin (05) — defensive ordering avoids losing tracing during rollout. Docs (06) trail behind.

## Out of Scope (Future Tasks)

- Tail-sampling at collector (`tail_sampling` processor) — needs prod load profile
- 128-bit W3C trace IDs — requires audit-service `traceId` column migration to VARCHAR(32)
- S3/object storage backend for Tempo — local FS sufficient for dev/staging
- spanmetrics processor → Prometheus — defer until baseline metrics need supplementing
- mTLS between collector → Tempo — internal network, low risk for now
- Multi-replica Tempo (distributed mode) — single replica adequate

## Unresolved Questions

1. Does k8s cluster auto-provision `standard` StorageClass for Tempo PVC? Fallback to `hostPath` if single-node — confirm during phase 02.
2. Any custom Grafana dashboard JSONs referencing Zipkin datasource UID? Phase 04 must grep `monitoring/grafana/dashboards/`.
3. Parent `pom.xml` zipkin version pin — likely BOM-managed but verify in phase 03.

## Validation Summary

**Validated:** 2026-05-09
**Questions asked:** 4

### Confirmed Decisions
- **Image pinning:** Pin to `grafana/tempo:2.6.0` + `otel/opentelemetry-collector-contrib:0.115.0` (was `:latest`)
- **Trace ID format:** Upgrade to 128-bit W3C trace IDs NOW (was deferred). Requires audit-service schema migration + explicit Spring Boot SDK config
- **Retention:** 24h (was 72h) — aggressive cleanup, smaller PVC sufficient
- **Docs scope:** Update both `docs/` (EN) + `docs/vi/` (Vietnamese mirror)

### Action Items (apply before implementation)

- [ ] **Phase 01 + 02:** replace `image: grafana/tempo:latest` → `grafana/tempo:2.6.0`; `otel/opentelemetry-collector-contrib:latest` → `otel/opentelemetry-collector-contrib:0.115.0` in docker-compose and k8s manifests
- [ ] **Phase 01 + 02:** change `compactor.compaction.block_retention: 72h` → `24h` in `tempo.yaml` (both compose and k8s configmap). Reduce PVC from 10Gi → 5Gi.
- [ ] **Phase 03 → split or extend:** add audit-service DB migration step. Inspect `audit_logs.trace_id` column type — if `VARCHAR(16)` or smaller, ALTER to `VARCHAR(32)`. Use Liquibase/Flyway if present; else native SQL migration.
- [ ] **Phase 03:** add Spring Boot OTel SDK 128-bit ID generator config. Likely via env `OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED=true` + `OTEL_TRACES_RANDOM_ID_GENERATOR=128bit` OR programmatic `IdGenerator.random()` (default in OTel SDK is already 128-bit when using OTLP exporter directly — verify whether Micrometer Tracing bridge inherits this). Research before implementing.
- [ ] **Phase 06:** add explicit todo to update Vietnamese mirror files in `docs/vi/` (system-architecture.md, deployment-guide.md, codebase-summary.md if present)
- [ ] **Out of scope section:** remove "128-bit W3C trace IDs" line (now in scope)
- [ ] **plan.md effort:** bump from 10h → ~12h (add ~1.5h for audit-service migration + 0.5h VI docs)

### Pending Verification (during implementation)
- Whether Micrometer Tracing bridge auto-emits 128-bit IDs when paired with `opentelemetry-exporter-otlp` (default in OTel SDK ≥1.x is 128-bit). May not need explicit config — `RandomIdGenerator` in OTel Java SDK is already 128-bit by default. Confirm via `mvn dependency:tree` + first-trace inspection.
- Audit-service `trace_id` column current type (likely already `VARCHAR(64)` from Spring Boot defaults — may need NO migration; verify with `\d audit_logs` in psql).
