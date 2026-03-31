# Phase 3: Reconciliation Batch Job (Local-First Approach)

## Context Links
- [plan.md](./plan.md)
- [Phase 2 - Data Model](./phase-02-data-model-and-repositories.md)
- [StripeConfig.java](../../payment-service/src/main/java/com/namnd/paymentservice/config/StripeConfig.java)
- [PaymentServiceImpl.java](../../payment-service/src/main/java/com/namnd/paymentservice/service/impl/PaymentServiceImpl.java)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Local-first reconciliation: Reader queries local payments by createdAt, Processor retrieves Stripe status via PaymentIntent.retrieve(), Writer persists results. Post-job finds orphan Stripe records (MISSING_LOCAL).

## Key Insights
- **Local-first**: Reader queries local Payment records by `createdAt` date range (validated decision)
- Processor calls `PaymentIntent.retrieve(stripePaymentIntentId)` for each local payment
- Stripe amounts in smallest currency unit (same as local `Payment.amount`)
- Stripe PI statuses: succeeded, canceled, requires_payment_method, requires_confirmation, requires_action, processing, requires_capture
- Local statuses: PENDING, COMPLETED, FAILED, REFUNDED
- Status mapping: `succeeded` -> COMPLETED, `canceled` -> FAILED
- Chunk size: 100 (balances memory vs Stripe API calls)
- Two-pass: (1) chunk step processes local records vs Stripe, (2) afterJob finds MISSING_LOCAL via Stripe list API
- Timezone: Asia/Saigon (UTC+7) for date boundaries

## Requirements
### Functional
- Read local payments by createdAt date range
- For each, retrieve Stripe PaymentIntent and compare status/amount
- Classify: MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_STRIPE (local has no Stripe match)
- After main pass, list Stripe PaymentIntents for date range, find those with no local record -> MISSING_LOCAL
- Update ReconciliationRun with aggregated counts
- Handle job failures gracefully (set run status = FAILED)

### Non-functional
- Respect Stripe rate limits (100 req/s — individual retrieve() calls, not bulk)
- Job should complete within minutes for typical daily volume
- Idempotent: re-running same date range creates new run (not update)

## Architecture
```
ReconciliationJobConfig
  ├── Job: reconciliationJob
  │   ├── Step 1: reconcileLocalStep (chunk-oriented)
  │   │   ├── LocalPaymentReader (ItemReader<Payment>) - queries by createdAt
  │   │   ├── ReconciliationProcessor (ItemProcessor<Payment, ReconciliationItem>)
  │   │   │   └── calls PaymentIntent.retrieve() per local payment
  │   │   └── ReconciliationItemWriter (ItemWriter<ReconciliationItem>)
  │   └── Listener: ReconciliationJobListener (afterJob -> MISSING_LOCAL check via Stripe list + finalize counts)
  └── JobParameters: startDate, endDate, runId
```

## Related Code Files
### Create
- `config/ReconciliationJobConfig.java` - Job/Step bean definitions (~80 lines)
- `batch/LocalPaymentReader.java` - ItemReader querying local DB by date range (~60 lines)
- `batch/ReconciliationProcessor.java` - ItemProcessor: retrieve Stripe + compare (~90 lines)
- `batch/ReconciliationItemWriter.java` - ItemWriter persisting items (~40 lines)
- `batch/ReconciliationJobListener.java` - JobExecutionListener for MISSING_LOCAL + finalization (~100 lines)

## Implementation Steps

### 1. LocalPaymentReader (~60 lines)
```java
@StepScope
@Component
public class LocalPaymentReader implements ItemReader<Payment> {
    private Iterator<Payment> iterator;
    private boolean initialized = false;
    private final PaymentRepository paymentRepository;
    private final LocalDate startDate;
    private final LocalDate endDate;

    public LocalPaymentReader(PaymentRepository paymentRepository,
            @Value("#{jobParameters['startDate']}") String startDate,
            @Value("#{jobParameters['endDate']}") String endDate) {
        this.paymentRepository = paymentRepository;
        this.startDate = LocalDate.parse(startDate);
        this.endDate = LocalDate.parse(endDate);
    }

    @Override
    public Payment read() {
        if (!initialized) { initialize(); }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private void initialize() {
        ZoneId zone = ZoneId.of("Asia/Saigon");
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        List<Payment> payments = paymentRepository.findByDateRangeWithStripeId(start, end);
        iterator = payments.iterator();
        initialized = true;
    }
}
```

### 2. ReconciliationProcessor (~90 lines)
```java
@Component
@StepScope
public class ReconciliationProcessor implements ItemProcessor<Payment, ReconciliationItem> {
    private final ReconciliationRunRepository runRepository;
    private ReconciliationRun run;
    private final Long runId;

    public ReconciliationProcessor(ReconciliationRunRepository runRepository,
            @Value("#{jobParameters['runId']}") Long runId) {
        this.runRepository = runRepository;
        this.runId = runId;
    }

    @Override
    public ReconciliationItem process(Payment local) {
        if (run == null) { run = runRepository.findById(runId).orElseThrow(); }

        ReconciliationItem item = ReconciliationItem.builder()
            .run(run)
            .localPaymentId(local.getId())
            .localAmount(local.getAmount())
            .localStatus(local.getStatus().name())
            .stripePaymentIntentId(local.getStripePaymentIntentId())
            .build();

        // If no Stripe PI ID stored, mark as MISSING_STRIPE
        if (local.getStripePaymentIntentId() == null
                || local.getStripePaymentIntentId().startsWith("FAKE-")) {
            item.setDiscrepancyType(DiscrepancyType.MISSING_STRIPE);
            return item;
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(local.getStripePaymentIntentId());
            item.setStripeAmount(intent.getAmount());
            item.setStripeStatus(intent.getStatus());

            // Compare amounts
            if (!intent.getAmount().equals(local.getAmount())) {
                item.setDiscrepancyType(DiscrepancyType.AMOUNT_MISMATCH);
                return item;
            }

            // Compare statuses
            String expectedLocal = mapStripeStatus(intent.getStatus());
            if (!local.getStatus().name().equals(expectedLocal)) {
                item.setDiscrepancyType(DiscrepancyType.STATUS_MISMATCH);
                return item;
            }

            item.setDiscrepancyType(DiscrepancyType.MATCHED);
        } catch (StripeException e) {
            item.setDiscrepancyType(DiscrepancyType.MISSING_STRIPE);
            item.setNotes("Stripe API error: " + e.getMessage());
        }
        return item;
    }

    private String mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "succeeded" -> "COMPLETED";
            case "canceled" -> "FAILED";
            case "requires_payment_method", "requires_confirmation",
                 "requires_action", "processing" -> "PENDING";
            default -> "PENDING";
        };
    }
}
```

### 3. ReconciliationItemWriter (~40 lines)
```java
@Component
@StepScope
public class ReconciliationItemWriter implements ItemWriter<ReconciliationItem> {
    private final ReconciliationItemRepository repository;

    @Override
    public void write(Chunk<? extends ReconciliationItem> chunk) {
        repository.saveAll(chunk.getItems());
    }
}
```

### 4. ReconciliationJobListener (~100 lines)
```java
@Component
public class ReconciliationJobListener implements JobExecutionListener {
    private final PaymentRepository paymentRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;

    @Override
    public void afterJob(JobExecution execution) {
        Long runId = execution.getJobParameters().getLong("runId");
        ReconciliationRun run = runRepository.findById(runId).orElseThrow();

        if (execution.getStatus() == BatchStatus.COMPLETED) {
            LocalDate start = run.getStartDate();
            LocalDate end = run.getEndDate();

            // Find MISSING_LOCAL: Stripe records with no local match
            // List Stripe PaymentIntents for the date range
            try {
                PaymentIntentListParams params = PaymentIntentListParams.builder()
                    .setCreated(PaymentIntentListParams.Created.builder()
                        .setGte(start.atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                        .setLt(end.plusDays(1).atStartOfDay().toEpochSecond(ZoneOffset.UTC))
                        .build())
                    .setLimit(100L)
                    .build();

                Set<String> reconciledPiIds = itemRepository.findStripeIdsByRunId(runId);
                int stripeCount = 0;
                List<ReconciliationItem> missingLocal = new ArrayList<>();

                for (PaymentIntent intent : PaymentIntent.list(params).autoPagingIterable()) {
                    stripeCount++;
                    if (!reconciledPiIds.contains(intent.getId())) {
                        missingLocal.add(ReconciliationItem.builder()
                            .run(run)
                            .stripePaymentIntentId(intent.getId())
                            .stripeAmount(intent.getAmount())
                            .stripeStatus(intent.getStatus())
                            .discrepancyType(DiscrepancyType.MISSING_LOCAL)
                            .build());
                    }
                }
                itemRepository.saveAll(missingLocal);

                // Finalize counts
                run.setTotalStripeRecords(stripeCount);
                run.setTotalLocalRecords((int) itemRepository.countByRunIdAndDiscrepancyTypeNot(runId, DiscrepancyType.MISSING_LOCAL));
                run.setMatchedCount((int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.MATCHED));
                run.setMismatchedCount(
                    (int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.STATUS_MISMATCH)
                    + (int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.AMOUNT_MISMATCH));
                run.setMissingLocalCount(missingLocal.size());
                run.setMissingStripeCount((int) itemRepository.countByRunIdAndDiscrepancyType(runId, DiscrepancyType.MISSING_STRIPE));
                run.setStatus(ReconciliationStatus.COMPLETED);
            } catch (StripeException e) {
                run.setStatus(ReconciliationStatus.FAILED);
            }
            run.setCompletedAt(LocalDateTime.now());
        } else {
            run.setStatus(ReconciliationStatus.FAILED);
            run.setCompletedAt(LocalDateTime.now());
        }
        runRepository.save(run);
    }
}
```

### 5. ReconciliationJobConfig (~80 lines)
```java
@Configuration
public class ReconciliationJobConfig {
    @Bean
    public Job reconciliationJob(JobRepository jobRepository,
                                  Step reconcileLocalStep,
                                  ReconciliationJobListener listener) {
        return new JobBuilder("reconciliationJob", jobRepository)
            .listener(listener)
            .start(reconcileLocalStep)
            .build();
    }

    @Bean
    public Step reconcileLocalStep(JobRepository jobRepository,
                                    PlatformTransactionManager txManager,
                                    LocalPaymentReader reader,
                                    ReconciliationProcessor processor,
                                    ReconciliationItemWriter writer) {
        return new StepBuilder("reconcileLocalStep", jobRepository)
            .<Payment, ReconciliationItem>chunk(100, txManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build();
    }
}
```

## Todo List
- [ ] Create LocalPaymentReader (reads local Payment by createdAt range)
- [ ] Create ReconciliationProcessor (retrieve Stripe PI + compare)
- [ ] Create ReconciliationItemWriter
- [ ] Create ReconciliationJobListener (MISSING_LOCAL via Stripe list + count finalization)
- [ ] Create ReconciliationJobConfig (Job + Step beans)
- [ ] Add `findStripeIdsByRunId` query to ReconciliationItemRepository
- [ ] Add `countByRunIdAndDiscrepancyTypeNot` query to ReconciliationItemRepository
- [ ] Run `mvn clean compile` to verify
- [ ] Manual test with small date range

## Success Criteria
- Job completes for a date range and produces ReconciliationRun with accurate counts
- All 5 discrepancy types correctly identified
- MISSING_LOCAL records detected in afterJob via Stripe list API
- MISSING_STRIPE detected for local payments without valid Stripe PI ID
- Job failures set run status to FAILED

## Risk Assessment
- **Medium:** Stripe API rate limits - individual retrieve() calls per local payment; for high volume, consider batch of 100/s limit
- **Low:** Memory usage for large date ranges - chunk processing limits in-memory items
- **Medium:** Race condition if webhook arrives during reconciliation - acceptable, next run corrects
- **Low:** FAKE- prefixed PI IDs correctly classified as MISSING_STRIPE

## Security Considerations
- Stripe API key already configured in StripeConfig
- No user-facing data in batch processing
- Job parameters (dates) validated before launch

## Next Steps
- Phase 4: REST API to trigger/query jobs + scheduling
