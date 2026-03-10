---
title: "Structured JSON Logging - All Services"
description: "Add JSON request/response logging with MDC correlation IDs across all 6 microservices"
status: pending
priority: P2
effort: 3h
branch: master
tags: [logging, observability, json, mdc, spring-boot]
created: 2026-03-09
---

# Structured JSON Logging

## Overview

Add structured JSON logging (Logback + logstash-logback-encoder) with HTTP request/response logging, sensitive-field masking, and MDC-based correlationId to all 6 microservices.

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [JSON Logging Config](./phase-01-json-logging-config.md) | pending | 1h |
| 2 | [HTTP Request/Response Filter](./phase-02-http-logging-filter.md) | pending | 1.5h |
| 3 | [Error Logging & MDC Propagation](./phase-03-error-logging-mdc.md) | pending | 0.5h |

## Key Dependencies

- `logstash-logback-encoder` (net.logstash.logback) — JSON encoder for Logback
- All services use Spring Boot 3.4.3 / Java 21
- api-gateway uses `spring-cloud-starter-gateway-mvc` (servlet, NOT WebFlux)

## Services Affected

| Service | Port | HTTP Filter | Logback Config |
|---------|------|-------------|----------------|
| api-gateway | 8080 | OncePerRequestFilter | logback-spring.xml |
| auth-service | 8081 | OncePerRequestFilter | logback-spring.xml |
| movie-service | 8082 | OncePerRequestFilter | logback-spring.xml |
| booking-service | 8083 | OncePerRequestFilter | logback-spring.xml |
| payment-service | 8084 | OncePerRequestFilter | logback-spring.xml |
| notification-service | 8085 | N/A (Kafka only) | logback-spring.xml |

## File Changes Summary

- `pom.xml` (root) — add logstash-logback-encoder to `<dependencyManagement>`
- `*/pom.xml` (6 services) — add logstash-logback-encoder dependency
- `*/src/main/resources/logback-spring.xml` (6 services) — JSON encoder config
- `*/config/filter/HttpLoggingFilter.java` (5 HTTP services) — request/response log filter
- `*/config/HttpLoggingConfig.java` (5 HTTP services) — register filter bean
