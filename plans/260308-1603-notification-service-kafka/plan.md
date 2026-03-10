---
title: "Notification Service via Kafka"
description: "Centralize all email/notification sending through a new notification-service consuming Kafka events"
status: pending
priority: P2
effort: 6h
branch: master
tags: [feature, backend, kafka, microservice, infra]
created: 2026-03-08
---

# Notification Service via Kafka

## Overview

Create `notification-service` microservice that consumes Kafka notification events and sends emails. Refactor auth-service to publish Kafka events instead of sending emails directly. Follow existing EventEnvelope + domain event patterns from kafka-events module.

## Architecture

```
auth-service ──publish──▶ Kafka (notification-events) ──consume──▶ notification-service ──▶ SMTP
booking-service ─publish─▶ ↑ (future)
payment-service ─publish─▶ ↑ (future)
```

## Phases

| # | Phase | Status | Effort | Link |
|---|-------|--------|--------|------|
| 1 | Kafka Events Module — Add Notification Events | Done | 1h | [phase-01](./phase-01-kafka-events-module-add-notification-events.md) |
| 2 | Notification Service — Consumer & Email Sender | Done | 2h | [phase-02](./phase-02-notification-service-setup-consumer-and-email-sender.md) |
| 3 | Auth-Service — Replace Email with Kafka Producer | Done | 1.5h | [phase-03](./phase-03-auth-service-refactor-replace-email-with-kafka-producer.md) |
| 4 | Docker Compose & Config Server Integration | Done | 1h | [phase-04](./phase-04-docker-compose-and-config-server-integration.md) |
| 5 | End-to-End Testing & Verification | Pending | 0.5h | [phase-05](./phase-05-end-to-end-testing-and-verification.md) |

## Dependencies

- Existing kafka-events module (EventEnvelope, KafkaTopics)
- Existing Kafka broker in docker-compose (Kraft mode, port 9092)
- Gmail SMTP credentials (move from auth-service to notification-service)
- Config server for centralized config
- Eureka for service discovery

## Key Decisions

1. **Single topic** `notification-events` — simple, all notification types routed by eventType
2. **Reuse EventEnvelope** pattern — consistent with payment/booking events
3. **At-least-once delivery** — Kafka consumer + DLT for failed notifications
4. **SMTP stays in notification-service only** — auth-service loses email dependency
