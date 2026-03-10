# Debugger Report: POST /api/payments/{id}/confirm → HTTP 500, status stays PENDING

**Date:** 2026-03-10
**Endpoint:** `POST /api/payments/100/confirm`
**Symptom:** Returns HTTP 500; payment actually succeeds in Stripe but DB status stays `PENDING`.

---

## Executive Summary

The root cause is a **transaction/event-ordering bug** in `confirmPaymentStatus()`.

The method:
1. Updates `payment.status = COMPLETED` and calls `paymentRepository.save()` — correct.
2. Inside the **same `@Transactional` method**, immediately calls `paymentEventPublisher.publishPaymentCompleted()`.
3. `publishPaymentCompleted` calls `kafkaTemplate.send(...)` which returns a `CompletableFuture`. Kafka producer flushing can throw an unchecked exception (e.g., `KafkaException`, network error, serialization error) on the calling thread — **before the `@Transactional` commit**.
4. That unchecked exception propagates up through `confirmPaymentStatus()`, **rolls back the transaction** (Spring's default behavior for unchecked exceptions), and falls into `GlobalExceptionHandler.handleUnexpected()` → HTTP 500.
5. Because the transaction is rolled back, `payment.status` is **never persisted** → stays `PENDING`.

This is a classic **dual-write / transactional outbox anti-pattern** issue: the Kafka publish is coupled inside the DB transaction, and a Kafka failure causes the DB write to roll back.

---

## Detailed Trace

### Call chain

```
POST /api/payments/100/confirm
  → PaymentController.confirmPayment(100)          [line 58]
  → PaymentServiceImpl.confirmPaymentStatus(100, userId)  [@Transactional, line 150]
      1. findById(100)  — OK
      2. userId check   — OK
      3. status == PENDING → proceed
      4. PaymentIntent.retrieve(stripePaymentIntentId)  — OK, returns "succeeded"
      5. payment.setStatus(COMPLETED)
      6. payment.setPaidAt(now)
      7. paymentRepository.save(payment)   ← DB write queued inside TX
      8. paymentCompletedCounter.increment()
      9. paymentEventPublisher.publishPaymentCompleted(...)  ← Kafka send
           └─ kafkaTemplate.send(...).whenComplete(...)
              If Kafka broker is unavailable / serialization fails:
              KafkaException thrown on calling thread
              ↑ bubbles up through confirmPaymentStatus()
              ↑ @Transactional rolls back → DB save never committed
  → GlobalExceptionHandler.handleUnexpected()  → HTTP 500
```

### Why DB status stays PENDING

`@Transactional` on `confirmPaymentStatus` wraps steps 1-9 in one unit of work. Spring rolls back on **any unchecked exception** escaping the method boundary. The `paymentRepository.save()` at step 7 is only flushed/committed when the TX commits — which never happens because the Kafka call at step 9 throws first.

### Why Kafka failing causes an exception here

`kafkaTemplate.send()` is non-blocking (returns `CompletableFuture`). However the Spring Kafka producer can still throw synchronously if:
- The `KafkaProducer` is closed or not yet initialized.
- Serialization of `EventEnvelope<PaymentCompletedEvent>` fails (e.g., missing Jackson config).
- A `ProducerFencedException` or similar is thrown immediately.

The `.whenComplete()` callback only handles async futures — it cannot catch a synchronous exception thrown by `.send()` itself.

### Secondary scenario: Kafka is up, but broker unreachable at confirm time

If Kafka is temporarily unavailable, `kafkaTemplate.send()` may block briefly (producer buffer full) or throw immediately. Same outcome: TX rollback → status stays PENDING → 500.

---

## Evidence from Code

| File | Line | Issue |
|------|------|-------|
| `PaymentServiceImpl.java` | 172 | `publishPaymentCompleted` called inside `@Transactional` boundary |
| `PaymentServiceImpl.java` | 180-183 | `StripeException` is caught, but **KafkaException is not** |
| `PaymentEventPublisher.java` | 34 | `kafkaTemplate.send()` — sync throw not guarded |
| `GlobalExceptionHandler.java` | 39-44 | Catch-all produces HTTP 500 for any uncaught exception |
| `Payment.java` | 37-39 | `status` field default `PENDING`, no optimistic locking — safe to re-save |

---

## Recommended Fix

### Option A — Decouple Kafka from DB transaction (preferred, correct pattern)

Move the Kafka publish **after** the transaction commits using Spring's `@TransactionalEventListener` or by publishing outside the `@Transactional` scope.

**Step 1:** Extract the Kafka publish call out of the `@Transactional` method.

```java
// PaymentServiceImpl.java

@Override
@Transactional
public PaymentHistoryResponse confirmPaymentStatus(Long paymentId, Long userId) {
    // ... existing logic up to paymentRepository.save() ...
    Payment saved = paymentRepository.save(payment);
    paymentCompletedCounter.increment();
    // DO NOT call publishPaymentCompleted here
    return toResponse(saved);
}
```

**Step 2:** Publish the event from the controller AFTER service returns, or use a Spring application event listener with `@TransactionalEventListener(phase = AFTER_COMMIT)`:

```java
// PaymentServiceImpl.java — publish domain event after TX commit
@Override
@Transactional
public PaymentHistoryResponse confirmPaymentStatus(Long paymentId, Long userId) {
    // ... same logic ...
    paymentRepository.save(payment);
    paymentCompletedCounter.increment();
    // Publish Spring ApplicationEvent — Kafka send happens after commit
    applicationEventPublisher.publishEvent(
        new PaymentCompletedApplicationEvent(this,
            payment.getBookingId(), payment.getStripePaymentIntentId(),
            payment.getAmount(), payment.getCurrency()));
    return toResponse(payment);
}

// New listener (separate class):
@Component
class PaymentEventRelay {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedApplicationEvent event) {
        paymentEventPublisher.publishPaymentCompleted(...);
    }
}
```

### Option B — Guard the Kafka call (quick fix, not ideal)

Wrap the Kafka call in a try-catch inside `confirmPaymentStatus` so the DB transaction can still commit even if Kafka fails. Log the error for later retry.

```java
try {
    paymentEventPublisher.publishPaymentCompleted(...);
} catch (Exception kafkaEx) {
    log.error("Kafka publish failed for paymentId={}, will need manual retry: {}", paymentId, kafkaEx.getMessage(), kafkaEx);
    // Do NOT rethrow — allow DB commit to proceed
}
```

**Caveat:** This leaves a gap where the DB is COMPLETED but no Kafka event was sent. Requires a reconciliation/retry mechanism (e.g., outbox pattern). Acceptable as a hotfix.

### Option C — Transactional Outbox Pattern (long-term resilience)

Store the Kafka event payload as a row in an `outbox_events` table within the same DB transaction. A separate scheduler polls and publishes pending outbox rows, then deletes them. Guarantees exactly-once delivery semantics.

---

## Immediate Hotfix Priority

**Apply Option B immediately** to stop the 500s and ensure DB status is persisted correctly. Then **implement Option A** (or Option C) as the proper structural fix.

Also apply the same fix to `handleWebhookEvent()` (lines 137, 142) and `fakePaymentSuccess()` (line 108) which have the identical pattern.

---

## Files to Modify

- `/payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java`
  - `confirmPaymentStatus()` — guard or decouple Kafka publish
  - `handleWebhookEvent()` — same issue at lines 137, 142
  - `fakePaymentSuccess()` — same issue at line 108

---

## Unresolved Questions

1. Is Kafka actually unreachable in the environment where this 500 was observed, or is there a serialization error in `EventEnvelope`? The exact exception class from the logs would confirm which scenario triggers the 500.
2. Are there log entries in `payment-service` logs at the time of the 500 showing `"Unhandled exception:"` from `GlobalExceptionHandler`? That would confirm the catch-all path.
3. Is an outbox pattern acceptable given current infra, or is Option B sufficient as a durable fix?
