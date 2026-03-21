---
title: "Distributed Tracing with Micrometer + Zipkin"
description: "Add distributed tracing across all microservices using Micrometer Tracing bridge and Zipkin collector"
status: code-reviewed
priority: P2
effort: 3h
branch: master
tags: [observability, tracing, zipkin, micrometer]
created: 2026-03-19
---

# Distributed Tracing with Micrometer + Zipkin

## Overview

Add end-to-end distributed tracing to ms-cinema using Micrometer Tracing (OpenTelemetry bridge) + Zipkin.
Traces propagate across HTTP (Gateway -> services, Feign), Kafka events, and appear in structured JSON logs.

## Key Findings

- API Gateway is **servlet-based** (gateway-mvc), not WebFlux -- no reactor-context complexity
- All 8 services already have `actuator` + `micrometer-registry-prometheus`
- Logging: LogstashEncoder JSON + Loki appender -- traceId/spanId auto-injected via MDC
- Config-server shares Kafka config via `config-repo/application.yml` -- tracing config goes there too
- Spring Boot 3.4.3 auto-configures tracing when bridge + exporter on classpath (zero code)

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Add tracing dependencies | done | 45m | [phase-01](phase-01-add-tracing-dependencies.md) |
| 2 | Configure tracing properties | done | 30m | [phase-02](phase-02-configure-tracing-properties.md) |
| 3 | Add Zipkin infrastructure | done | 30m | [phase-03](phase-03-add-zipkin-infrastructure.md) |
| 4 | Verify and test tracing | pending (manual smoke test required) | 45m | [phase-04](phase-04-verify-and-test-tracing.md) |

## Dependencies

- Micrometer Tracing BOM version managed by Spring Boot 3.4.3 parent
- OpenTelemetry exporter version: use `io.opentelemetry:opentelemetry-exporter-zipkin` (BOM-managed)
- Zipkin Docker image: `openzipkin/zipkin:latest` (port 9411) — **pin to `3.4` tag, see code review H1**

## Architecture

```
Client -> api-gateway -> auth/movie/booking/payment/notification-service
                              |            |
                              v            v
                           Kafka -------> consumer

All services export spans -> Zipkin (:9411)
Grafana links to Zipkin for trace drill-down from logs/metrics
```

## Risk

- Low risk: Spring Boot 3.x auto-config handles most wiring
- Kafka tracing: Spring Kafka 3.x auto-propagates trace context via headers (verify in phase 4)
- Performance: Sampling at 1.0 for dev only; prod should use lower rate

## Code Review Findings (2026-03-19)

See full report: `reports/code-review-260319-distributed-tracing.md`

- **[H1 - High]** `openzipkin/zipkin:latest` unpinned — pin to `openzipkin/zipkin:3.4`
- **[M2 - Medium]** Grafana `depends_on` missing `zipkin` entry in `docker-compose.yml`
- **[M1 - Medium]** Sampling probability `1.0` default — add inline comment warning for prod
- **[L1 - Low]** Add Zipkin healthcheck in `docker-compose.yml`
- **[L3 - Low/Optional]** Add Loki derived field for traceId → Zipkin drill-down link
- All 20 tests pass; build compiles cleanly with new tracing deps
