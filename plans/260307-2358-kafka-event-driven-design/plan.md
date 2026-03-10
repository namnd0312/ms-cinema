---
title: "Kafka Event-Driven Design"
description: "Apply Apache Kafka EDD across all microservices with shared event library, DLT, and idempotent consumers"
status: complete
priority: P1
effort: "7h"
branch: master
tags: [kafka, event-driven, microservices, spring-kafka]
created: 2026-03-07
reviewed: 2026-03-08
---

# Kafka Event-Driven Design

## Context
- Research: [Spring Kafka + KRaft](./research/researcher-01-spring-kafka-kraft.md) | [EDD Patterns](./research/researcher-02-event-driven-patterns.md)
- Existing: payment-service publishes to `payment-events` topic, booking-service consumes it (manual JSON, no DLT, no idempotency)
- Stack: Spring Boot 3.4.3, Java 21, spring-kafka 3.3.x, Kafka 3.7.0 KRaft

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Shared Kafka Events Module](./phase-01-shared-kafka-events-module.md) | 1.5h | [x] DONE |
| 2 | [Kafka Configuration & Infrastructure](./phase-02-kafka-configuration-infrastructure.md) | 1h | [x] DONE — fix H2 (ExponentialBackOffWithMaxRetries) |
| 3 | [Booking-Payment Saga Events](./phase-03-booking-payment-saga-events.md) | 2h | [x] DONE — fix H1 (JsonDeserializer target type), H3 (@Transactional) |
| 4 | [Movie Service Events](./phase-04-movie-service-events.md) | 1.5h | [x] DONE — fix M4 (remove redundant try-catch in MovieEventPublisher) |
| 5 | [Auth Service Events](./phase-05-auth-service-events.md) (optional) | 1h | [ ] DEFERRED |
| 6 | [Testing & Verification](./phase-06-testing-verification.md) | 1h | [x] DONE — 7 tests (4 integration + 3 unit) |

## Dependencies
- Phase 1 must complete before Phases 3-5
- Phase 2 must complete before Phases 3-5
- Phase 3 is highest priority (fixes existing fragile implementation)
- Phase 5 is optional/low-priority

## Key Decisions
1. **Separate `kafka-events` module** instead of embedding in `jwt-auth-spring-boot-starter` -- keeps auth starter focused on JWT
2. **Choreography saga** (no orchestrator) -- simpler, matches existing payment flow
3. **JsonSerializer** replaces manual ObjectMapper -- Spring Kafka handles ser/deser
4. **Skip Transactional Outbox** for now (YAGNI) -- add if reliability issues arise
5. **Skip @RetryableTopic** -- use `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` (simpler, fewer topics)
6. **Idempotency via booking status check** -- no separate `processed_events` table needed since BookingStatus already guards state transitions

## Topic Registry
| Topic | Producer | Consumer(s) | Key |
|-------|----------|-------------|-----|
| `payment-events` | payment-service | booking-service | bookingId |
| `movie-events` | movie-service | (future) | movieId/showtimeId |

## Validation Summary

**Validated:** 2026-03-07
**Questions asked:** 4

### Confirmed Decisions
- **BookingCreatedEvent**: Skip for now (YAGNI, no consumer)
- **Phase 5 (auth events)**: Skip this round (no downstream consumer)
- **EventEnvelope deserialization**: ObjectMapper.convertValue for payload (simple, no type headers)
- **Idempotency**: BookingStatus check only (no processed_events table)

### Scope Adjustment
- Phase 5 deferred — implement Phases 1-4, 6 only (~7h)
- `booking-events` topic removed from registry (no publisher this round)

## Code Review — 2026-03-08

**Score: 8/10** | Report: `reports/code-reviewer-260308-1415-kafka-event-driven-design.md`

### Must-Fix Before Phase 6

| ID | Severity | File | Issue |
|----|----------|------|-------|
| H1 | HIGH | `KafkaConsumerConfig.java` / booking-service `application.yml` | `JsonDeserializer` target type not configured — listener may receive `LinkedHashMap` instead of `EventEnvelope` at runtime |
| H2 | HIGH | `KafkaConsumerConfig.java` | `setMaxElapsedTime(30000)` allows >3 retries — replace with `ExponentialBackOffWithMaxRetries(3)` |
| H3 | HIGH | `BookingServiceImpl.java` | `confirmBooking` / `cancelBooking` missing `@Transactional` — race condition on concurrent duplicate events |

### Fix Before Merge

| ID | Severity | File | Issue |
|----|----------|------|-------|
| M1 | MEDIUM | `PaymentServiceImpl.java` | `confirmPaymentStatus` publishes event even when webhook already published — guard with `stripeEventId == null` check |
| M4 | MEDIUM | `MovieEventPublisher.java` | Redundant outer `try-catch` around async `kafkaTemplate.send()` — remove, use `.whenComplete` only |

### Next Steps
1. Fix H1 — add `spring.json.value.default.type` to booking-service consumer config
2. Fix H2 — replace `ExponentialBackOff` with `ExponentialBackOffWithMaxRetries(3)`
3. Fix H3 — add `@Transactional` to `confirmBooking` / `cancelBooking`
4. Fix M1 — guard dual-path event publishing in `confirmPaymentStatus`
5. Fix M4 — simplify `MovieEventPublisher` to `.whenComplete` only
6. Proceed to Phase 6: Testing & Verification
