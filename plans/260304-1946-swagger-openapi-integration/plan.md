---
title: "Swagger OpenAPI Integration"
description: "Add SpringDoc OpenAPI (Swagger UI) to all microservices with aggregated docs at API Gateway"
status: completed
priority: P2
effort: 3h
branch: master
tags: [docs, api, backend, infra]
created: 2026-03-04
---

# Swagger OpenAPI Integration

## Overview

Add SpringDoc OpenAPI 2.x (Swagger UI) to all 5 runnable services (auth, movie, booking, payment, api-gateway). Each service exposes its own `/swagger-ui.html`. API Gateway aggregates all service docs into a unified Swagger UI.

## Key Decisions

- **Library:** `springdoc-openapi-starter-webmvc-ui` 2.8.x (all services are servlet-based, including gateway-mvc)
- **Version management:** Single `<springdoc.version>` property in root `pom.xml`
- **Security scheme:** Global Bearer JWT via `@SecurityScheme` annotation on OpenAPI config class
- **Gateway aggregation:** `springdoc.swagger-ui.urls` list pointing to each service's `/v3/api-docs`
- **No Swagger for:** eureka-server, config-server, jwt-auth-starter (infrastructure/library modules)

## Phases

| # | Phase | Status | Effort | Link |
|---|-------|--------|--------|------|
| 1 | Dependencies & Config | Done | 30m | [phase-01](./phase-01-dependencies-and-config.md) |
| 2 | OpenAPI Config Classes & Security Permits | Done | 45m | [phase-02](./phase-02-openapi-config-and-security.md) |
| 3 | Controller Annotations | Done | 60m | [phase-03](./phase-03-controller-annotations.md) |
| 4 | Gateway Aggregation | Done | 30m | [phase-04](./phase-04-gateway-aggregation.md) |
| 5 | Verification | Done | 15m | [phase-05](./phase-05-verification.md) |

## Dependencies

- springdoc-openapi-starter-webmvc-ui 2.8.x
- Spring Boot 3.4.3 (compatible)
- Spring Security 6.x (need to permit Swagger paths)

## Service Ports (for reference)

| Service | Port | Has SecurityConfig |
|---------|------|--------------------|
| auth-service | 8081 | Yes (own) |
| movie-service | 8082 | Yes (overrides starter) |
| booking-service | 8083 | No (JWT starter) |
| payment-service | 8084 | No (JWT starter) |
| api-gateway | 8080 | No (no security) |
