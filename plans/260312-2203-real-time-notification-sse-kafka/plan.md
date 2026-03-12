---
title: "Real-Time Notification System (SSE + Kafka)"
description: "Add SSE real-time notifications for payment events and admin broadcasts"
status: pending
priority: P2
effort: "6h"
branch: master
tags: [notification, sse, kafka, real-time]
created: 2026-03-12
---

# Real-Time Notification System (SSE + Kafka)

## Summary

Extend notification-service (port 8085) with SSE push, PostgreSQL persistence, and REST API. Booking-service publishes `InAppNotificationEvent` after payment confirm/fail. Admin can broadcast to all connected clients via Kafka.

## Architecture Flow

```
Payment-service → payment-events → booking-service (confirms booking)
  → publishes InAppNotificationEvent to notification-events
  → notification-service consumes → saves to notificationdb → pushes via SSE

Admin → POST /api/notifications/broadcast → Kafka notification-events
  → notification-service consumes → saves + pushes to all SSE clients

Frontend → EventSource(/api/notifications/stream?token=JWT)
  → API Gateway routes to notification-service
```

## Key Design Decisions

1. **Booking-service publishes InAppNotificationEvent** (has userId + bookingId context)
2. **JWT via query param** for SSE auth (EventSource lacks header support)
3. **In-memory ConcurrentHashMap** for userId→SseEmitter registry (single-instance sufficient)
4. **Reuse notification-events topic** with new eventType `notification.in_app`
5. **Servlet-based SSE** via SseEmitter (matches existing Spring MVC stack)
6. **Unique consumer group per instance** `notification-sse-{instanceId}` — broadcast pattern so all instances receive every message, enabling multi-instance scaling with zero code changes

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [kafka-events module](./phase-01-kafka-events-module-add-inapp-notification-event.md) | pending | 30m |
| 2 | [notification-service backend](./phase-02-notification-service-backend-sse-persistence-rest-api.md) | pending | 2h |
| 3 | [payment/booking integration](./phase-03-booking-service-publish-inapp-notification-after-payment.md) | pending | 45m |
| 4 | [API Gateway routing](./phase-04-api-gateway-add-notification-service-routes.md) | pending | 15m |
| 5 | [Angular frontend](./phase-05-angular-frontend-sse-client-notification-bell-and-list.md) | pending | 1.5h |
| 6 | [Configuration & Docker](./phase-06-docker-compose-and-config-server-notificationdb-setup.md) | pending | 30m |

## Dependencies

- Phase 2 depends on Phase 1 (new event type)
- Phase 3 depends on Phase 1 (new event type)
- Phase 5 depends on Phase 4 (gateway route)
- Phase 6 can run in parallel with Phase 2-5

## Risk Summary

- **SSE through servlet gateway**: May have buffering issues; mitigated by heartbeat + response flushing
- **No multi-instance SSE**: Acceptable for current scale; document upgrade path to Redis pub/sub

## Validation Summary

**Validated:** 2026-03-12
**Questions asked:** 4

### Confirmed Decisions
- **SSE Auth**: JWT via query param accepted. Mitigate by not logging query params.
- **Admin Broadcast**: Via Kafka (consistent pattern, future multi-instance ready).
- **SSE Timeout**: 5 min + auto-reconnect. Industry standard, prevents resource leaks.
- **DB Schema**: `ddl-auto: update` — consistent with other services in project.

### No Action Items
All plan decisions confirmed as-is. No phase file changes needed.
