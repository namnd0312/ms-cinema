---
title: "Zipkin references inventory"
date: 2026-05-09
---

# Zipkin References Inventory

Total: 19 files. Grouped by category.

## 1. Maven dependencies (`pom.xml`)

All 6 services import `io.opentelemetry:opentelemetry-exporter-zipkin`. Pattern repeats with same comment block.

| File | Lines |
|------|-------|
| `auth-service/pom.xml` | 120-128 |
| `booking-service/pom.xml` | 80-87 |
| `movie-service/pom.xml` | 66-73 |
| `payment-service/pom.xml` | 81-88 |
| `notification-service/pom.xml` | 86-93 |
| `audit-service/pom.xml` | ~80 |

Snippet (identical):
```xml
<!-- Distributed tracing: Micrometer -> OpenTelemetry -> Zipkin -->
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
  <groupId>io.opentelemetry</groupId>
  <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

Parent `pom.xml`: check for any version pin (likely Spring Boot BOM-managed, no explicit version).

Shared libs `jwt-auth-autoconfigure/pom.xml` and `kafka-events/pom.xml`: NO zipkin refs (verified by grep — only listed services + parent surfaced).

## 2. Application config (`application.yml`)

All 6 services share identical block:

```yaml
management:
  zipkin:
    tracing:
      endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
```

| File | Lines |
|------|-------|
| `auth-service/src/main/resources/application.yml` | 62-64 |
| `booking-service/src/main/resources/application.yml` | 62-64 |
| `movie-service/src/main/resources/application.yml` | 42-44 |
| `payment-service/src/main/resources/application.yml` | 70-72 |
| `notification-service/src/main/resources/application.yml` | 68-70 |
| `audit-service/src/main/resources/application.yml` | 52-54 |

Sibling block `management.tracing.sampling.probability` stays unchanged (sampling is exporter-agnostic).

## 3. Docker Compose (`docker-compose.yml`)

- Lines 47-52: `zipkin:` service block (image `openzipkin/zipkin:3.4`, port 9411)
- Lines 76, 99, 122, 145, 170, 191: `ZIPKIN_HOST: zipkin` env var injected into 6 services
- Line 233 (approx): `depends_on: [..., zipkin]` for grafana service

## 4. Kubernetes (`k8s/`)

- `k8s/infra/zipkin/deployment.yml` — entire Deployment + Service to delete
- `k8s/base/configmap.yml` line 17: `ZIPKIN_HOST: "zipkin"`
- `k8s/base/configmap.yml` line 26: `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: "http://zipkin:9411/api/v2/spans"`

Service Deployment YAMLs (`k8s/services/<svc>/deployment.yml`): may reference `ZIPKIN_HOST` via `envFrom: configMapRef`. No direct env override expected (configmap-driven).

## 5. Grafana provisioning

`monitoring/grafana/provisioning/datasources/datasources.yml` lines 21-27:
```yaml
- name: Zipkin
  type: zipkin
  uid: zipkin
  access: proxy
  url: http://zipkin:9411
  isDefault: false
  editable: true
```

K8s mirror: `monitoring/grafana/` has its own provisioning configmap if mounted into Grafana k8s pod — confirm during implementation.

## 6. Documentation

- `README.md` — 5+ mentions: port 9411 in services table, "Distributed Tracing: Micrometer Tracing (OpenTelemetry bridge) + Zipkin exporter" line, monitoring stack listing, docker-compose section
- `docs/system-architecture.md` — observability section likely references Zipkin
- `docs/deployment-guide.md` — K8s/docker-compose deployment instructions reference zipkin port + service
- `docs/codebase-summary.md` — tracing stack mention
- `docs/project-changelog.md` — historical Zipkin entries (KEEP — historical)
- `plans/260319-2149-distributed-tracing-micrometer-zipkin/` — archived plan dir (KEEP, historical)

## 7. Logback / logging

All 6 services have `logback-spring.xml` using `loki4j` appender with `<includeMdcProperties>true</includeMdcProperties>`. MDC auto-populates `traceId`/`spanId` via Micrometer Tracing — exporter-agnostic. **No logback changes required.**

Loki labels currently used (assumption pending verify): `app=<service-name>` from logback config. Plan must confirm exact label key for Tempo trace-to-logs derived field.

## 8. Java source code

No direct Zipkin references in Java source. All config flows through Spring Boot properties. Safe for dependency-only swap.

## 9. Environment variables to remove/replace

| Var | Current | Replacement |
|-----|---------|-------------|
| `ZIPKIN_HOST` | `zipkin` | drop, add `OTEL_EXPORTER_OTLP_ENDPOINT` or `MANAGEMENT_OTLP_TRACING_ENDPOINT` |
| `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` | `http://zipkin:9411/api/v2/spans` | replace with `MANAGEMENT_OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces` |

## Removal Checklist

1. Delete `k8s/infra/zipkin/` dir
2. Remove `zipkin` service from `docker-compose.yml`
3. Remove `ZIPKIN_HOST` from 6 services' env in docker-compose.yml
4. Remove zipkin from grafana `depends_on` (docker-compose)
5. Remove zipkin datasource block from `monitoring/grafana/provisioning/datasources/datasources.yml`
6. Remove `ZIPKIN_HOST` + `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` from `k8s/base/configmap.yml`
7. Remove `opentelemetry-exporter-zipkin` from 6 pom.xml files
8. Remove `management.zipkin.tracing.endpoint` block from 6 application.yml
9. Update README.md, docs/system-architecture.md, docs/deployment-guide.md, docs/codebase-summary.md

## Unresolved Questions

- Does parent `pom.xml` pin `opentelemetry-exporter-zipkin` version explicitly? (Likely no — BOM-managed.)
- Exact Loki label key (`app` vs `service_name`) used by loki4j appender — need to read one logback-spring.xml to confirm before writing Tempo derived-field config.
- Whether any Grafana dashboard JSONs reference Zipkin datasource UID and need updating to Tempo UID.
