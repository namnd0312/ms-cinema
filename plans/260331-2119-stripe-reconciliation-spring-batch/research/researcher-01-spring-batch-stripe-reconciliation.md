# Spring Batch 5.x & Stripe Java SDK Integration Research
**Date:** 2026-03-31 | **Focus:** Payment reconciliation batch processing

## 1. Spring Batch 5.x Fundamentals (Spring Boot 3.4 Compatible)

### Chunk-Oriented Processing
- **Core Flow:** ItemReader → ItemProcessor → ItemWriter (transactional boundary)
- **Configuration:**
  ```java
  @Bean
  public Step reconciliationStep(JobRepository jobRepo,
      PlatformTransactionManager txManager) {
    return new StepBuilder("reconcile", jobRepo)
      .chunk(250, txManager)  // 250-item chunks (recommended: 100-500)
      .reader(stripeChargeReader())
      .processor(reconciliationProcessor())
      .writer(reconciliationWriter())
      .faultTolerant()
      .retry(Exception.class).maxRetries(3)
      .build();
  }
  ```
- **Chunk Size Trade-off:** Smaller (100-250) = frequent commits, less rollback loss; Larger (500+) = efficiency, higher rollback risk
- **ItemReader:** Stateful, reads one item at a time; must support restart via ExecutionContext
- **ItemProcessor:** Optional; transforms item or filters (return null to skip)
- **ItemWriter:** Receives List<Item>; writes in batch within transaction

### Job Configuration (Spring Boot 3.4)
```java
@Bean
public Job reconciliationJob(JobRepository jobRepo, Step reconcileStep) {
  return new JobBuilder("stripeReconciliation", jobRepo)
    .start(reconcileStep)
    .build();
}
```

---

## 2. Stripe Java SDK for Reconciliation

### BalanceTransaction API (Recommended over Charge API)
- **Why BalanceTransaction:** Provides actual impact on account balance; includes fees, refunds, disputes in single source
- **Charge API Limitation:** Doesn't reflect net balance impact; requires separate fee/refund queries
- **2025 Enhancement:** `balance_type` field added (payments, issuing, refund_and_dispute_prefunding) for faster categorization
- **Auto-Pagination Implementation:**
  ```java
  BalanceTransactionCollection page = BalanceTransaction.list(params);
  // Auto-pagination: SDK internally handles cursor/offset pagination
  for (BalanceTransaction txn : page.autoPagingIterable()) {
    // Process each transaction
  }
  ```
- **Date Range Filter:**
  ```java
  params.put("created[gte]", startEpoch);  // Unix timestamp
  params.put("created[lte]", endEpoch);
  params.put("limit", 100);  // Per-page size
  ```

### Pagination Strategy
- Auto-pagination enabled by default in stripe-java; handles offset transparently
- Recommended: Use `autoPagingIterable()` instead of manual pagination to avoid data loss
- Default limit: 10; set `limit: 100` for efficiency (max 100)

---

## 3. Financial Reconciliation Best Practices

### Idempotency (Critical for Restarts)
- **Principle:** Job produces identical results whether run once or restarted
- **Implementation:**
  ```java
  // Track processed transaction IDs in DB
  public boolean isProcessed(String stripeTransactionId) {
    return reconciliationRepository.existsByStripeId(stripeTransactionId);
  }
  ```
- **External Call Idempotency:** Add idempotency keys to Stripe API calls; prevents duplicate operations if network fails mid-request

### Restart Resilience
- **ExecutionContext Save:** Reader saves pagination state (last processed ID/date)
  ```java
  @Bean
  public ItemReader<BalanceTransaction> stripeReader() {
    return new ItemReader<BalanceTransaction>() {
      private Iterator<BalanceTransaction> iterator;
      private long lastProcessedTimestamp;

      @Override
      public BalanceTransaction read() throws Exception {
        if (iterator == null) {
          // Restore from context if restarting
          lastProcessedTimestamp =
            executionContext.getLong("lastTimestamp", startDate);
          iterator = fetchTransactions(lastProcessedTimestamp);
        }
        BalanceTransaction txn = iterator.next();
        if (txn != null) {
          executionContext.putLong("lastTimestamp", txn.getCreated());
        }
        return txn;
      }
    };
  }
  ```
- **Transactional Safety:** Spring Batch commits chunk only on successful write; failure rolls back entire chunk
- **Error Handling:** Configure `.faultTolerant().retry()` for transient network errors; `.skip()` for known invalid records

### Data Integrity Checks
- Reconciliation job must be re-entrant: same input data produces same output
- Store checksum/hash of processed batch for audit trail
- Log all discrepancies; flag for manual review rather than auto-correcting

---

## 4. Job Scheduling

### @Scheduled (Simple, Single-Instance)
```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily 2 AM
public void scheduleReconciliation() throws Exception {
  jobLauncher.run(reconciliationJob, new JobParameters());
}
```
- **Limitation:** Runs on all instances in cluster; no distributed coordination

### Quartz (Enterprise, Distributed)
```java
// Dependency: spring-boot-starter-quartz
@Bean
public JobDetail reconciliationJobDetail() {
  return JobBuilder.newJob(StripeReconciliationQuartzJob.class)
    .withIdentity("stripeReconciliation")
    .build();
}

@Bean
public Trigger reconciliationTrigger(JobDetail detail) {
  return TriggerBuilder.newTrigger()
    .forJob(detail)
    .withIdentity("stripeReconciliationTrigger")
    .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
    .build();
}

// application.yml
spring:
  quartz:
    job-store-type: jdbc  # Persist in DB
    jdbc:
      initialize-schema: always
```
- **Advantage:** Single-instance coordination via DB JobStore; persists across restarts; mis-fire handling
- **Recommended for:** Multi-instance deployments, strict exactly-once guarantees

---

## 5. Key Configuration Parameters

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| Chunk Size | 250 | Balance between transaction overhead & rollback loss |
| Stripe Limit | 100 | Max allowed by API; reduces HTTP calls |
| Retry Count | 3 | Transient network failures (1s exponential backoff) |
| Date Range | Daily | Aligns with Stripe payout cycles |
| Scheduler | Quartz (prod), @Scheduled (dev) | Distributed coordination requirement |

---

## Unresolved Questions

1. Should we store `balance_type` field separately or just use it for filtering during reconciliation?
2. How to handle timezone conversion for Stripe Unix timestamps vs application time?
3. Batch size optimization—should we test with 500+ chunk size for this dataset?
4. Error notification strategy (email/Slack) for failed reconciliation jobs?

---

## Sources
- [Spring Batch Chunk-oriented Processing](https://docs.spring.io/spring-batch/reference/step/chunk-oriented-processing.html)
- [Stripe Reporting & Reconciliation](https://docs.stripe.com/plan-integration/get-started/reporting-reconciliation)
- [Stripe BalanceTransaction API](https://docs.stripe.com/api/balance_transactions)
- [Spring Batch Restart & Retry](https://docs.spring.io/spring-batch/reference/5.1-SNAPSHOT/transaction-appendix.html)
- [Spring Boot Quartz Integration](https://docs.spring.io/spring-boot/reference/io/quartz.html)
- [Spring Batch Best Practices - Error Handling](https://www.javacodegeeks.com/2025/02/robust-error-handling-in-spring-batch.html)
