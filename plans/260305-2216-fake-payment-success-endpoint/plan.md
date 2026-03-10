---
title: "Fake Payment Success Endpoint"
description: "Add test endpoint to simulate payment success without Stripe"
status: pending
priority: P2
effort: 30m
branch: master
tags: [payment, testing, fake-endpoint]
created: 2026-03-05
---

# Fake Payment Success Endpoint

## Goal
Add `POST /api/payments/fake-success` endpoint to payment-service that bypasses Stripe, creates a COMPLETED payment record, and publishes Kafka event — enabling end-to-end booking flow testing without Stripe credentials.

## Current Flow
```
Client → POST /create-intent → Stripe PaymentIntent → Stripe Webhook → handleWebhookEvent → Kafka → booking-service confirms
```

## Fake Flow
```
Client → POST /fake-success → DB save (COMPLETED) → Kafka event → booking-service confirms
```

## Phases

| # | Phase | Status | File |
|---|-------|--------|------|
| 1 | Add fake payment method & endpoint | Complete | [phase-01](./phase-01-add-fake-payment-endpoint.md) |

## Files to Modify
- `payment-service/.../service/PaymentService.java` — add interface method
- `payment-service/.../service/impl/PaymentServiceImpl.java` — add implementation
- `payment-service/.../controller/PaymentController.java` — add endpoint

## Success Criteria
- [ ] `POST /api/payments/fake-success` with `{bookingId, amount}` + JWT returns COMPLETED payment
- [ ] Payment record saved in DB with status=COMPLETED
- [ ] Kafka event published to `payment-events` topic
- [ ] booking-service receives event and confirms booking
- [ ] No Stripe dependency in fake flow
