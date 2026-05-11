# Phase 07 — Validation Results

**Date:** 2026-05-09
**Migration:** Zipkin → OpenTelemetry + Grafana Tempo
**Validator:** Static + build (runtime smoke deferred to user)

## Static Validation (Performed)

### docker-compose
- `docker compose config --quiet` → OK
- `tempo` + `otel-collector` services present, pinned `grafana/tempo:2.6.0` + `otel/opentelemetry-collector-contrib:0.115.0`
- 6 services have `OTEL_COLLECTOR_HOST=otel-collector` + `DEPLOYMENT_ENV=dev`
- 6 services have `otel-collector` in `depends_on`
- Zipkin block + 6 `ZIPKIN_HOST` env vars removed
- `tempo-data` named volume added
- Grafana `depends_on` lists `tempo` + `otel-collector` (no `zipkin`)

### k8s manifests
- `kubectl --dry-run=client apply -f k8s/infra/tempo/` → 4 resources OK (ConfigMap, PVC, StatefulSet, Service)
- `kubectl --dry-run=client apply -f k8s/infra/otel-collector/` → 3 resources OK (ConfigMap, Deployment, Service)
- `kubectl --dry-run=client apply -f k8s/infra/grafana/` → ConfigMap reconfigured with Loki+Prometheus+Tempo datasources
- Namespace `ms-cinema` consistent across all manifests
- PVC reduced from 10Gi → 5Gi (per validation summary)
- Tempo `block_retention: 24h` (was 72h)
- `k8s/infra/zipkin/` directory removed
- `k8s/deploy-all.sh` updated: zipkin loop entries replaced with `tempo otel-collector`

### Maven build
- All 6 services compile clean (`mvn ... compile -DskipTests`)
- `opentelemetry-exporter-otlp:1.43.0` resolves (from Spring Cloud BOM 2024.0.1)
- 6/6 pom.xml swapped from `opentelemetry-exporter-zipkin` → `opentelemetry-exporter-otlp`
- `micrometer-tracing-bridge-otel` retained (only exporter changed)

### application.yml
- 6/6 services updated: `management.zipkin.tracing.endpoint` → `management.otlp.tracing.endpoint`
- Endpoint format: `http://${OTEL_COLLECTOR_HOST:localhost}:4318/v1/traces`
- Resource attributes added: `service.name=${spring.application.name}`, `deployment.environment=${DEPLOYMENT_ENV:dev}`
- Sampling config (`management.tracing.sampling.probability`) preserved

### Grafana datasource provisioning
- `monitoring/grafana/provisioning/datasources/datasources.yml`:
  - Tempo datasource added (uid: tempo)
  - Prometheus uid set explicitly to `prometheus`
  - `tracesToLogsV2`: maps `service.name` (Tempo) → `service` (Loki) with `filterByTraceID: true`
  - `tracesToMetrics`: maps to `service_name` Prometheus label, includes Request rate + p95 latency queries
  - `serviceMap`, `nodeGraph`, `lokiSearch` enabled
  - Zipkin datasource removed
- `k8s/infra/grafana/deployment.yml` ConfigMap mirrors same Tempo block (consistent UI in both deployments)

### Live-doc cleanup
- `git grep -inI "zipkin" -- ':!plans' README.md docs/` returns only:
  - changelog historical entry (intentional)
  - migration narrative ("migrated from Zipkin", "Zipkin → OTel") (intentional)
- README updated: services table, monitoring stack list, distributed-tracing line
- `docs/system-architecture.md` updated: infra section + monitoring subsection + tech-stack table
- `docs/deployment-guide.md` updated: monitoring list + full distributed-tracing section
- `docs/codebase-summary.md` updated: datasource list + tracing section + dep list
- `docs/project-changelog.md` appended: full migration entry
- `docs/project-overview-pdr.md` + `docs/project-roadmap.md` updated: monitoring stack
- Vietnamese mirrors updated: `docs/vi/system-architecture.md`, `docs/vi/codebase-summary.md`, `docs/vi/deployment-guide.md`, `docs/vi/project-overview-pdr.md`, `docs/vi/project-roadmap.md`

## Runtime Validation (Deferred to User)

The following must be run by the user against a live deployment. Static checks above guarantee the configs are syntactically and structurally valid.

### Compose smoke (per `phase-07-validate-end-to-end-trace-flow.md`)

```bash
docker compose up -d --build
docker compose ps tempo otel-collector grafana   # all healthy

# Tempo readiness
curl -fsS http://localhost:3200/ready                   # → "ready"

# Collector health
curl -fsS http://localhost:13133/                       # → 200

# Per-service smoke (e.g. auth)
curl -fsS http://localhost:8081/actuator/health
sleep 5
curl -s "http://localhost:3200/api/search?tags=service.name=auth-service&limit=1" | jq .
```

### REST chain
- Login → Booking flow → confirm Tempo trace contains spans from `auth-service`, `booking-service`, `payment-service` under same `traceID`.

### Kafka chain
- Trigger event → confirm `booking-service` producer span + `audit-service` consumer span in same trace.

### Grafana correlation
- Open Grafana → Explore → Tempo → search `service.name=auth-service` → click span → "Logs for this span" returns Loki rows with matching traceId.
- Click "Metrics" on a span → Prometheus rate panel renders.
- Switch to "Service Graph" tab → all 6 services + Kafka edges render.

### k8s
- `kubectl apply -f k8s/infra/tempo/ -f k8s/infra/otel-collector/`
- `kubectl get pod` → `tempo-0` and `otel-collector-*` Ready 1/1
- `kubectl get pvc tempo-traces` → Bound (or fall back to `emptyDir` if no default StorageClass)
- Repeat smoke + REST/Kafka chains via port-forward.

## Risks Surfaced (Static Review)

- **Tempo PVC StorageClass:** k8s manifest uses default. If single-node cluster has no default `StorageClass`, PVC will pend. Fall back: edit `k8s/infra/tempo/deployment.yml` to switch the volume from `persistentVolumeClaim` to `emptyDir` (loses traces on pod restart, acceptable for dev).
- **64-bit vs 128-bit trace IDs:** Plan validation summary mentioned upgrading to 128-bit. Current implementation keeps Spring Boot defaults (Micrometer Tracing emits 64-bit IDs to preserve audit-service `trace_id` schema). The OTel Java SDK default `RandomIdGenerator` is 128-bit when used directly, but the Micrometer bridge retains 64-bit by default. This was deferred per Phase 03 risk-assessment (avoids audit-service migration). To upgrade later: add explicit OTel `IdGenerator` config + ALTER `audit_logs.trace_id` to `VARCHAR(32)`.
- **Loki label key:** Phase 04 datasource provisioning assumes Loki label is `service`. Verify against `logback-spring.xml` if trace-to-logs returns empty.
- **Grafana k8s ConfigMap previously had `editable: false`:** New datasources keep that for safety. Removing manually via UI is blocked.

## Unresolved Questions

1. Is there a default `StorageClass` available in target k8s clusters (OrbStack dev cluster mentioned in `deploy-all.sh`)? If not, fall back to `emptyDir`.
2. Should sampling probability remain at 1.0 in dev/staging, or pre-configure 0.1 for staging-like envs? Current default unchanged.
3. Span-metrics processor at the collector (Prometheus exporter) is out of scope — defer until baseline metrics insufficient.
4. Tail-sampling left for future task (needs prod load profile).
