# Phase 03 — Swap Zipkin exporter for OTLP in all 6 services

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-01-spring-boot-otlp-kafka-tracing.md` (Q1, Q2, Q6, Q7)
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (sections 1, 2, 3, 4, 9)
- Source: 6 `pom.xml`, 6 `application.yml`, `k8s/base/configmap.yml`, `docker-compose.yml`

## Overview

- Date: 2026-05-09
- Priority: P1 (the actual migration step)
- Status: pending
- Review: not-started
- Description: Replace `opentelemetry-exporter-zipkin` Maven dep with `opentelemetry-exporter-otlp` and replace `management.zipkin.tracing.endpoint` config with `management.otlp.tracing.endpoint` across all 6 services. Rename env var `ZIPKIN_HOST` → `OTEL_COLLECTOR_HOST`. Add resource attributes for `service.name` + `deployment.environment`.

## Key Insights

- Identical change × 6 services — high template reuse. Use sed/IDE find-replace.
- Zero Java code touches — pure config.
- Logback already MDC-friendly (`<includeMdcProperties>true</includeMdcProperties>`) — traceId/spanId continue flowing to Loki.
- `micrometer-tracing-bridge-otel` STAYS — only the exporter artifact changes.
- Spring Kafka 3.x auto-instruments W3C `traceparent` headers — no kafka config touch.
- 64-bit trace IDs preserved (default) — avoids audit-service DB schema change.
- Sampling stays `1.0` (env-overridable for prod later).

## Requirements

**Functional**
- Each service emits OTLP/HTTP traces to `otel-collector:4318/v1/traces`.
- Trace ID present in every JSON log line (Loki).
- Cross-service traces (REST + Kafka) propagate parent context.

**Non-functional**
- No app startup regression (>5s).
- Memory delta ≤ +20Mi per pod.
- Backward compat env var support: keep `OTEL_COLLECTOR_HOST` overridable for local dev.

## Architecture

```
Before:  app ── micrometer-tracing-bridge-otel ── opentelemetry-exporter-zipkin ── zipkin:9411
After:   app ── micrometer-tracing-bridge-otel ── opentelemetry-exporter-otlp ──── otel-collector:4318
```

Same Micrometer `Tracer` API. MDC behaviour unchanged. Kafka headers unchanged (W3C `traceparent` was already used internally).

## Related Code Files

**Modify (pom.xml — 6 files)**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/booking-service/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/payment-service/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/notification-service/pom.xml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/audit-service/pom.xml`

**Modify (application.yml — 6 files)**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/auth-service/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/movie-service/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/booking-service/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/payment-service/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/notification-service/src/main/resources/application.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/audit-service/src/main/resources/application.yml`

**Modify (env injection)**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docker-compose.yml` (6 service env blocks)
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/base/configmap.yml` (line 17 + 26)

**Create**
- none

**Delete**
- none in this phase (zipkin infra removed in phase 05)

## Implementation Steps

1. **pom.xml swap (× 6)** — replace block:
   ```xml
   <!-- Before -->
   <dependency>
     <groupId>io.opentelemetry</groupId>
     <artifactId>opentelemetry-exporter-zipkin</artifactId>
   </dependency>
   <!-- After -->
   <dependency>
     <groupId>io.opentelemetry</groupId>
     <artifactId>opentelemetry-exporter-otlp</artifactId>
   </dependency>
   ```
   Update comment `<!-- Distributed tracing: Micrometer -> OpenTelemetry -> Zipkin -->` → `<!-- Distributed tracing: Micrometer -> OpenTelemetry -> OTLP -->`.

2. **application.yml swap (× 6)** — replace:
   ```yaml
   # Before
   management:
     zipkin:
       tracing:
         endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
   # After
   management:
     otlp:
       tracing:
         endpoint: http://${OTEL_COLLECTOR_HOST:localhost}:4318/v1/traces
     opentelemetry:
       resource-attributes:
         service.name: ${spring.application.name}
         deployment.environment: ${DEPLOYMENT_ENV:dev}
   ```
   Keep existing `management.tracing.sampling.probability: 1.0` block unchanged.

3. **docker-compose.yml** — for each of 6 services, replace env var:
   ```yaml
   # Before
   ZIPKIN_HOST: zipkin
   # After
   OTEL_COLLECTOR_HOST: otel-collector
   DEPLOYMENT_ENV: dev
   ```
   Add `depends_on: [otel-collector]` to each service.

4. **k8s/base/configmap.yml** — replace lines 17 + 26:
   ```yaml
   # Before
   ZIPKIN_HOST: "zipkin"
   MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: "http://zipkin:9411/api/v2/spans"
   # After
   OTEL_COLLECTOR_HOST: "otel-collector"
   MANAGEMENT_OTLP_TRACING_ENDPOINT: "http://otel-collector:4318/v1/traces"
   DEPLOYMENT_ENV: "dev"
   ```

5. **Build all services**: `mvn -pl auth-service,movie-service,booking-service,payment-service,notification-service,audit-service -am clean package -DskipTests`. Confirm zero compile errors.

6. **Local verify (compose)**: `docker compose up -d --build auth-service`. Tail logs, look for line `Tracing exporter: OTLP` or first export attempt to `otel-collector:4318`.

7. **k8s verify**: rebuild + push images, `kubectl rollout restart deploy -l tier=app` (or per-service). Ensure pods come up healthy.

## Todo List

- [ ] Swap pom.xml dep in 6 services
- [ ] Update application.yml in 6 services (replace zipkin block + add otel resource attributes)
- [ ] Update docker-compose.yml env vars (6 services + depends_on)
- [ ] Update k8s/base/configmap.yml env vars
- [ ] `mvn clean package -DskipTests` all 6 services
- [ ] `docker compose up -d` and verify each service exports traces
- [ ] k8s rollout and verify

## Success Criteria

- All 6 services compile.
- `docker compose logs auth-service | grep -i otlp` shows successful exports (no 404/connection refused).
- `curl http://localhost:3200/api/search?tags=service.name=auth-service` returns at least 1 trace after a sample request.
- Each pod log line in Loki has `traceId` field populated (verify via Grafana Loki Explorer).
- Cross-service test: trigger booking flow → search Tempo by `service.name=booking-service` → trace shows children spans from `payment-service` and Kafka consume in `notification-service`.

## Risk Assessment

- **`opentelemetry-exporter-otlp` not on classpath** — Spring Cloud BOM 2024.0.1 should provide it. If missing, pin version explicitly. Mitigation: `mvn dependency:tree | grep otlp`.
- **Endpoint typo** (`/v1/traces` missing) — Spring throws on startup. Mitigation: copy exact YAML.
- **Old `ZIPKIN_HOST` lingering in deployment yaml** — env var no longer consumed but harmless until phase 05 cleanup.
- **Audit DB still has 64-bit traceId column** — fine, we keep 64-bit IDs.
- **Loki label key `service` (not `service_name`)** — already confirmed via main agent investigation; phase 04 datasource uses `service` join key.

## Security Considerations

- Endpoint URL hardcoded internal hostname — not user-controlled, no SSRF risk.
- No secrets in new config blocks.
- Resource attribute `deployment.environment` may leak env name to traces — acceptable; visible only to internal Grafana users.

## Next Steps

- Phase 04: provision Tempo datasource so traces become queryable in Grafana UI.
- Phase 05: only after 07 validation, remove zipkin dep + endpoint refs.
