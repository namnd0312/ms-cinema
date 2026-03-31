---
title: "Stripe Payment Reconciliation with Spring Batch"
description: "Automated daily reconciliation of local payments against Stripe records using Spring Batch"
status: completed
priority: P1
effort: 16h
branch: master
tags: [payment, stripe, spring-batch, reconciliation, admin]
created: 2026-03-31
---

# Stripe Payment Reconciliation - Spring Batch

## Summary
Add automated payment reconciliation to payment-service comparing local Payment records against Stripe PaymentIntents. Uses Spring Batch chunk-oriented processing. Admin dashboard in Angular for triggering, viewing runs, and resolving discrepancies.

## Phases

| # | Phase | Effort | Status |
|---|-------|--------|--------|
| 1 | [Spring Batch Setup](./phase-01-spring-batch-setup.md) | 1h | completed |
| 2 | [Data Model & Repositories](./phase-02-data-model-and-repositories.md) | 2h | completed |
| 3 | [Reconciliation Batch Job](./phase-03-reconciliation-batch-job.md) | 5h | completed |
| 4 | [Admin REST API & Scheduling](./phase-04-admin-rest-api.md) | 3h | completed |
| 5 | [Angular Admin Dashboard](./phase-05-angular-admin-dashboard.md) | 3h | completed |
| 6 | [Testing](./phase-06-testing.md) | 2h | completed |

## Key Dependencies
- Stripe Java SDK 28.2.0 (already in pom.xml)
- Spring Batch 5.x (via spring-boot-starter-batch)
- Existing Payment entity with `stripePaymentIntentId`
- Existing `@PreAuthorize("hasRole('ADMIN')")` pattern
- Angular 18 admin section at `/admin/*`

## Architecture
```
[Scheduler/API Trigger] -> [Spring Batch Job]
  -> Reader: Stripe API auto-pagination (PaymentIntents by date)
  -> Processor: Compare vs local DB, classify discrepancy
  -> Writer: Persist ReconciliationItem + update ReconciliationRun
  -> Post-job: Check local-only payments (MISSING_STRIPE)
```

## Risk Summary
- Stripe API rate limits (100 req/s) - mitigated by batch reading with pagination
- Large date ranges may timeout - add validation (max 31 days)
- Spring Batch metadata tables coexist in paymentdb - low risk with separate prefix

## Validation Summary

**Validated:** 2026-03-31
**Questions asked:** 7

### Confirmed Decisions
- **Date matching**: Query local payments by createdAt first, then fetch corresponding Stripe records (reversed from original plan)
- **Timezone**: Asia/Saigon (UTC+7) for daily scheduler "yesterday" calculation
- **Storage**: Store ALL items including MATCHED for full audit trail
- **Package structure**: New `batch/` sub-package for Spring Batch components
- **Job launch**: Async with polling (POST returns immediately, frontend polls status)
- **Gateway routing**: Existing `/api/payments/**` route covers reconciliation endpoints, no gateway change needed
- **Max date range**: 31 days per reconciliation run

### Action Items
- [x] **Phase 3 revision**: Change reconciliation approach from Stripe-first to local-first. Reader should query local Payment records by createdAt date range, Processor fetches Stripe status for each via `PaymentIntent.retrieve()`, then afterJob finds Stripe records with no local match
- [x] **Phase 4 revision**: Use `ZoneId.of("Asia/Saigon")` in ReconciliationScheduler for yesterday calculation

### Code Review Findings (2026-03-31)
See full report: `plans/reports/code-reviewer-260331-2150-stripe-reconciliation-review.md`

**Must fix:**
- [x] H1: Remove real Stripe key default in `application.yml` — use `${STRIPE_API_KEY:}` (fail fast)
- [x] M4: Fix timezone mismatch in `ReconciliationJobListener.afterJob` — use `Asia/Saigon` ZoneId for Stripe list epoch seconds

**Should fix:**
- [ ] M1: Replace `LocalPaymentReader` in-memory load with `JpaPagingItemReader`
- [ ] M2: Add Stripe SDK retry/backoff config
- [x] M5: Not applicable — exception thrown on failure, stale response never returned
- [x] M6: Add `@Size(max=1000)` validation on `ResolveItemRequest.notes`
- [ ] M7: Replace client-side 10,000-item CSV export with server-side streaming endpoint
