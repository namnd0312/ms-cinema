---
title: "System Design & Sequence Diagrams"
description: "Comprehensive system design docs with Mermaid sequence diagrams for all microservice flows"
status: complete
priority: P2
effort: "3h"
branch: master
tags: [documentation, system-design, mermaid, sequence-diagrams]
created: 2026-03-08
---

# System Design & Sequence Diagrams

## Objective

Create `docs/system-design.md` with Mermaid sequence diagrams covering every user-facing and internal flow across the cinema booking platform. Diagrams must match actual implementation (class names, method names, topics, keys).

## Output

Single file: `docs/system-design.md` — replaces/supplements existing `docs/system-architecture.md` flow sections with proper Mermaid diagrams.

## Phases

| # | Phase | Status | Diagrams |
|---|-------|--------|----------|
| 1 | [System Architecture Overview](phase-01-system-architecture-overview.md) | complete | C4 component, service catalog, infra layout |
| 2 | [Auth Service Flows](phase-02-auth-service-flows.md) | complete | 7 diagrams: register, activate, login, refresh, forgot/reset password, logout, validate-token |
| 3 | [Booking & Payment Flows](phase-03-booking-payment-flows.md) | complete | 5 diagrams: reserve, payment intent, Stripe webhook, Kafka confirm/cancel, expiry scheduler |
| 4 | [Movie Service Flows](phase-04-movie-service-flows.md) | complete | 3 diagrams: movie CRUD, showtime CRUD, Kafka event publishing |
| 5 | [Infrastructure Flows](phase-05-infrastructure-flows.md) | complete | 5 diagrams: gateway routing, Eureka discovery, config server, DLT error handling, JWT starter filter |

## Key Dependencies

- All diagrams reference real class/method names from source code
- Mermaid.js v11 `sequenceDiagram` syntax
- Kafka topics from `KafkaTopics.java`: `payment-events`, `movie-events`
- EventEnvelope record structure for all Kafka messages

## Success Criteria

- [x] Every REST endpoint has a sequence diagram
- [x] Every Kafka producer/consumer flow has a sequence diagram
- [x] Diagram participants match real service/class names
- [x] Renders correctly in GitHub Markdown
