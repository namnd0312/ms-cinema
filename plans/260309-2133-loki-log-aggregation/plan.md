---
title: "Loki Log Aggregation - All Services"
description: "Add Grafana Loki centralized log aggregation with error tracing via correlationId across 6 microservices"
status: pending
priority: P2
effort: 2h
branch: master
tags: [loki, logging, observability, grafana, tracing, microservices]
created: 2026-03-09
---

# Loki Log Aggregation

## Overview

Integrate Grafana Loki into the existing Prometheus+Grafana monitoring stack to centralize logs from all 6 microservices. Logs are already JSON-formatted via `logstash-logback-encoder` with `correlationId` MDC — this plan adds the Loki transport layer.

## Current State

- JSON logging: **DONE** — all 6 services have `logback-spring.xml` with `LogstashEncoder`
- MDC correlationId: **DONE** — `HttpLoggingFilter` sets correlationId per request
- Loki: **NOT YET** — no Docker service, no loki4j appender, no Grafana datasource

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Loki Infrastructure](./phase-01-loki-infrastructure.md) | pending | 0.5h |
| 2 | [loki4j Appender - All Services](./phase-02-loki4j-appender-integration.md) | pending | 1h |
| 3 | [Grafana Loki Dashboard](./phase-03-grafana-loki-dashboard.md) | pending | 0.5h |

## Key Dependencies

- `com.github.loki4j:loki-logback-appender` — native Java push to Loki over HTTP
- Loki `3.x` Docker image — log storage backend
- Grafana already running (`:3000`) — just needs Loki datasource added
- All services already have `logstash-logback-encoder` + `logback-spring.xml`

## Services Affected

| Service | Port | loki4j Appender | logback-spring.xml |
|---------|------|-----------------|-------------------|
| auth-service | 8081 | add | update |
| api-gateway | 8080 | add | update |
| movie-service | 8082 | add | update |
| booking-service | 8083 | add | update |
| payment-service | 8084 | add | update |
| notification-service | 8085 | add | update |

## File Changes Summary

- `docker-compose.yml` — add `loki` service
- `monitoring/loki/loki-config.yml` — new Loki config
- `monitoring/grafana/provisioning/datasources/datasources.yml` — add Loki datasource
- `monitoring/grafana/provisioning/dashboards/json/loki-logs-dashboard.json` — new dashboard
- `pom.xml` (root) — add `loki-logback-appender` to `<dependencyManagement>`
- `*/pom.xml` (6 services) — add `loki-logback-appender` dependency
- `*/src/main/resources/logback-spring.xml` (6 services) — add `LokiAppender`
