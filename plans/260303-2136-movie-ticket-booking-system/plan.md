---
title: "Movie Ticket Booking System"
description: "3 new microservices (movie, booking, payment) integrated into existing Spring Cloud platform"
status: pending
priority: P1
effort: 15h
branch: master
tags: [feature, backend, microservices, stripe, redis]
created: 2026-03-03
---

# Movie Ticket Booking System

## Overview

Add 3 microservices to existing jwt-spring-security platform: movie-service (catalog/theaters/showtimes), booking-service (seat selection/locking), payment-service (Stripe). All integrate via Eureka + OpenFeign + jwt-auth-spring-boot-starter.

## Architecture

```
Client → api-gateway(:8080) → movie-service(:8082)   [moviedb]
                             → booking-service(:8083)  [bookingdb] ← Redis locks
                             → payment-service(:8084)  [paymentdb] ← Stripe
```

Inter-service: OpenFeign + Eureka (sync). Kafka (async: payment→booking events). JWT propagated via RequestInterceptor.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| IDs | Long/BIGSERIAL | Consistent with auth-service |
| Seat locking | Redis SET NX + TTL (5min) | KISS, no extra deps, matches existing Redis pattern |
| Payment | Stripe PaymentIntent | Client-side confirm via Stripe.js |
| Inter-service | OpenFeign + Eureka | Spring Cloud native, service discovery built-in |
| Cross-service refs | IDs only, snapshot data | No cross-DB FKs, denormalize at write time |
| Enum storage | VARCHAR + CHECK | Flexible, no ALTER TYPE needed |
| Payment→Booking notify | Kafka events | Decoupled, no circular Feign deps, reliable delivery |
| Currency | VND | Locale-appropriate, no decimal places |
| Service-to-service auth | Service account JWT | payment-service gets JWT from auth-service for internal calls |
| Public endpoints | GET-only public | Only GET /api/movies/** and GET /api/showtimes/** are public; mutations require JWT |
| DB init | Manual + init script | Manually create DBs on existing volume; init script for fresh envs |

## Phases

| # | Phase | Status | Effort | Link |
|---|-------|--------|--------|------|
| 1 | Project Scaffolding | Pending | 2h | [phase-01](./phase-01-project-scaffolding-maven-docker-gateway.md) |
| 2 | Movie Service | Pending | 4h | [phase-02](./phase-02-movie-service-entities-and-api.md) |
| 3 | Booking Service | Pending | 4h | [phase-03](./phase-03-booking-service-seat-locking-and-reservations.md) |
| 4 | Payment Service | Pending | 3h | [phase-04](./phase-04-payment-service-stripe-integration.md) |
| 5 | Integration & Testing | Pending | 2h | [phase-05](./phase-05-integration-testing-and-docker-verification.md) |

## Dependencies

- Existing: eureka-server, config-server, api-gateway, auth-service, jwt-auth-spring-boot-starter
- New: Stripe Java SDK, Spring Cloud OpenFeign, Spring Kafka, Apache Kafka
- Infrastructure: 3 new PostgreSQL DBs, Redis (shared), Kafka broker (Docker)

## Research Reports

- [Stripe + OpenFeign](./research/researcher-01-stripe-feign-report.md)
- [Seat Locking + DB Schema](./research/researcher-02-redis-distributed-seat-locking-and-postgresql-schema-per-service-design-report.md)

## Validation Summary

**Validated:** 2026-03-03
**Questions asked:** 6

### Confirmed Decisions
- **Auth scope**: Only GET endpoints public for movies/showtimes; POST/PUT/DELETE require JWT
- **Service auth**: Service account JWT for internal calls (payment→booking)
- **DB init**: Manually create DBs on existing volume; keep init script for fresh environments
- **Currency**: VND (Vietnamese Dong, no decimal places)
- **Lock strategy**: Simple Redis SET NX + 5-min TTL (no Redisson)
- **Payment→Booking notify**: Kafka async events (no circular Feign dependency)

### Action Items (Plan Revisions Needed)
- [ ] Phase 1: Add Kafka + Zookeeper to docker-compose.yml
- [ ] Phase 1: Add `spring-kafka` dependency to booking-service and payment-service pom.xml
- [ ] Phase 2: Update SecurityConfig to only allow GET methods as public (not all paths)
- [ ] Phase 3: Replace Feign-based confirm/cancel with Kafka consumer listener
- [ ] Phase 3: Add service account JWT mechanism for any remaining internal Feign calls
- [ ] Phase 4: Replace BookingServiceClient Feign with Kafka producer (publish payment events)
- [ ] Phase 4: Update currency default from USD to VND (amounts in whole numbers, no decimals)
- [ ] All phases: Manually run `CREATE DATABASE moviedb/bookingdb/paymentdb` before first Docker run
