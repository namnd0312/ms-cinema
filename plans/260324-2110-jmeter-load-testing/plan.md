---
title: "JMeter Load Testing - 1000+ Concurrent Users"
description: "Stress test all critical ms-cinema API endpoints with JMeter to find breaking points"
status: complete
priority: P1
effort: 6h
branch: master
tags: [load-testing, jmeter, performance, stress-test]
created: 2026-03-24
---

# JMeter Load Testing Plan

## Goal
Stress test ALL critical API endpoints with 1000+ concurrent users to identify breaking points, bottlenecks, and performance limits.

## Architecture Context
- **Entry point**: api-gateway (:8080) routes to all services
- **Auth**: JWT (HS512, 15-min access, 7-day refresh), Redis token blacklist
- **Services**: auth(:8081), movie(:8082), booking(:8083), payment(:8084), notification(:8085), audit(:8086)
- **Infra**: PostgreSQL (6 DBs), Redis, Kafka, Eureka, Config Server

## Load Profile
| Stage | Users | Duration | Purpose |
|-------|-------|----------|---------|
| Ramp-up | 0 -> 1000 | 5 min | Gradual load increase |
| Sustained | 1000 | 10 min | Steady-state performance |
| Spike | 1500 | 2 min | Burst capacity |
| Cool-down | 1500 -> 0 | 3 min | Recovery behavior |

## Phases

| # | Phase | Status | File |
|---|-------|--------|------|
| 1 | [Environment Setup & JMeter Config](./phase-01-environment-setup-jmeter-config.md) | done | Test data, JMeter install, connection config |
| 2 | [Auth Service Load Tests](./phase-02-auth-service-load-tests.md) | done | Login, register, token refresh, logout |
| 3 | [Movie Service Load Tests](./phase-03-movie-service-load-tests.md) | done | Movies, showtimes, ratings, comments |
| 4 | [Booking Service Load Tests](./phase-04-booking-service-load-tests.md) | done | Seat reservation, confirm, cancel |
| 5 | [Payment Service Load Tests](./phase-05-payment-service-load-tests.md) | done | Create intent, confirm, webhook |
| 6 | [Full User Journey E2E](./phase-06-full-user-journey-e2e-load-test.md) | done | Login -> browse -> book -> pay flow |
| 7 | [Execution Scripts & Reporting](./phase-07-execution-scripts-and-html-reporting.md) | done | CLI scripts, HTML reports, dashboards |

## Key Metrics
- Response time: avg, p95, p99
- Throughput: req/sec per endpoint
- Error rate per endpoint
- Breaking point (max concurrent users before >1% error rate)
- Resource utilization: CPU, memory, DB connections, Redis connections

## Dependencies
- Docker Compose running all services + infra
- JMeter 5.6+ installed (CLI mode for execution)
- Test database seeded with movies, theaters, showtimes
- Stripe test mode or fake-success endpoint for payment tests
