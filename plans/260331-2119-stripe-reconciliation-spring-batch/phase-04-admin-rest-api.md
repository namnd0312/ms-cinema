# Phase 4: Admin REST API & Scheduling

## Context Links
- [plan.md](./plan.md)
- [Phase 3 - Batch Job](./phase-03-reconciliation-batch-job.md)
- [PaymentController.java](../../payment-service/src/main/java/com/namnd/paymentservice/controller/PaymentController.java)
- [PaymentService.java](../../payment-service/src/main/java/com/namnd/paymentservice/service/PaymentService.java)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Create ReconciliationController, ReconciliationService interface/impl, and scheduled cron job

## Key Insights
- Follow existing pattern: `@PreAuthorize("hasRole('ADMIN')")` on controller
- Service interface + impl pattern (matches PaymentService/PaymentServiceImpl)
- `JobLauncher.run()` to trigger batch job programmatically
- `@Scheduled` with configurable cron expression
- Add `/api/payments/reconciliation/**` to JWT public-paths? No - requires ADMIN auth
- Paginated responses using Spring Data `Page` + `Pageable`

## Requirements
### Functional
- POST trigger starts reconciliation for date range
- GET endpoints return paginated runs/items
- Summary endpoint returns latest run stats
- Resolve endpoint marks items resolved with notes
- Daily cron auto-triggers for previous day

### Non-functional
- Validate date range (max 31 days, endDate >= startDate)
- Prevent concurrent runs for overlapping date ranges
- Async job launch (return immediately, poll for status)

## Architecture
```
ReconciliationController -> ReconciliationService -> ReconciliationServiceImpl
  ├── triggerReconciliation() -> creates ReconciliationRun, launches Job async
  ├── getRuns() -> paginated query
  ├── getRunDetails() -> single run
  ├── getRunItems() -> paginated, filterable
  ├── getSummary() -> latest run stats
  └── resolveItem() -> mark resolved

ReconciliationScheduler
  └── @Scheduled(cron) -> calls ReconciliationService.triggerReconciliation()
```

## Related Code Files
### Create
- `service/ReconciliationService.java` - interface (~25 lines)
- `service/impl/ReconciliationServiceImpl.java` - implementation (~120 lines)
- `controller/ReconciliationController.java` - REST endpoints (~100 lines)
- `config/ReconciliationScheduler.java` - cron scheduler (~30 lines)

## Implementation Steps

### 1. ReconciliationService Interface (~25 lines)
```java
public interface ReconciliationService {
    ReconciliationRunResponse triggerReconciliation(LocalDate startDate, LocalDate endDate);
    Page<ReconciliationRunResponse> getRuns(Pageable pageable);
    ReconciliationRunResponse getRunDetails(Long runId);
    Page<ReconciliationItemResponse> getRunItems(Long runId, DiscrepancyType type, Pageable pageable);
    ReconciliationSummaryResponse getSummary();
    ReconciliationItemResponse resolveItem(Long itemId, String notes);
}
```

### 2. ReconciliationServiceImpl (~120 lines)

**triggerReconciliation():**
1. Validate: endDate >= startDate, range <= 31 days
2. Create `ReconciliationRun` with status RUNNING, save to DB
3. Build `JobParameters` with startDate, endDate, runId, timestamp (for uniqueness)
4. Launch job async via `JobLauncher.run(reconciliationJob, params)`
5. Return run response immediately

**getRuns():**
- `runRepository.findAllByOrderByCreatedAtDesc(pageable)` mapped to DTOs

**getRunDetails():**
- `runRepository.findById(runId)` mapped to DTO, throw 404 if not found

**getRunItems():**
- If `type` is null: `itemRepository.findByRunId(runId, pageable)`
- If `type` provided: `itemRepository.findByRunIdAndDiscrepancyType(runId, type, pageable)`
- Map to DTOs

**getSummary():**
- `runRepository.findFirstByOrderByCreatedAtDesc()` -> map to summary DTO

**resolveItem():**
- Find item by ID, set `resolved=true`, set `notes`, save, return DTO

### 3. ReconciliationController (~100 lines)
```java
@RestController
@RequestMapping("/api/payments/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Reconciliation", description = "Payment reconciliation management")
@SecurityRequirement(name = "bearerAuth")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    @PostMapping("/trigger")
    public ResponseEntity<ReconciliationRunResponse> trigger(
            @Valid @RequestBody TriggerReconciliationRequest request) {
        return ResponseEntity.ok(reconciliationService.triggerReconciliation(
            request.startDate(), request.endDate()));
    }

    @GetMapping("/runs")
    public ResponseEntity<Page<ReconciliationRunResponse>> getRuns(Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getRuns(pageable));
    }

    @GetMapping("/runs/{runId}")
    public ResponseEntity<ReconciliationRunResponse> getRunDetails(@PathVariable Long runId) {
        return ResponseEntity.ok(reconciliationService.getRunDetails(runId));
    }

    @GetMapping("/runs/{runId}/items")
    public ResponseEntity<Page<ReconciliationItemResponse>> getRunItems(
            @PathVariable Long runId,
            @RequestParam(required = false) DiscrepancyType discrepancyType,
            Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getRunItems(runId, discrepancyType, pageable));
    }

    @GetMapping("/summary")
    public ResponseEntity<ReconciliationSummaryResponse> getSummary() {
        return ResponseEntity.ok(reconciliationService.getSummary());
    }

    @PutMapping("/items/{itemId}/resolve")
    public ResponseEntity<ReconciliationItemResponse> resolveItem(
            @PathVariable Long itemId,
            @Valid @RequestBody ResolveItemRequest request) {
        return ResponseEntity.ok(reconciliationService.resolveItem(itemId, request.notes()));
    }
}
```

### 4. ReconciliationScheduler (~30 lines)
```java
@Configuration
@ConditionalOnProperty(name = "reconciliation.auto-run.enabled", havingValue = "true")
public class ReconciliationScheduler {
    private final ReconciliationService reconciliationService;

    @Scheduled(cron = "${reconciliation.cron}")
    public void runDailyReconciliation() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Saigon")).minusDays(1);
        log.info("Starting scheduled reconciliation for {}", yesterday);
        reconciliationService.triggerReconciliation(yesterday, yesterday);
    }
}
```

### 5. Exception Handling
- Add to existing `GlobalExceptionHandler`:
  - Handle `JobExecutionAlreadyRunningException` -> 409 Conflict
  - Handle validation errors (bad date range) -> 400 Bad Request

## Todo List
- [ ] Create ReconciliationService interface
- [ ] Create ReconciliationServiceImpl with all methods
- [ ] Create ReconciliationController with all endpoints
- [ ] Create ReconciliationScheduler with @Scheduled cron
- [ ] Add date range validation (max 31 days)
- [ ] Add exception handling for job conflicts
- [ ] Update GlobalExceptionHandler if needed
- [ ] Add OpenAPI annotations on controller
- [ ] Run `mvn clean compile` to verify

## Success Criteria
- POST /trigger creates run and launches job
- GET /runs returns paginated list
- GET /runs/{id}/items supports filtering by discrepancy type
- PUT /items/{id}/resolve marks item resolved
- Scheduler triggers daily at configured cron time
- Date range validation rejects invalid ranges

## Risk Assessment
- **Medium:** Concurrent job launches - validate no RUNNING job exists for overlapping dates before launch
- **Low:** Scheduler runs when service is down - next day catches up
- **Low:** Large page requests - default page size (20) is reasonable

## Security Considerations
- All endpoints require ADMIN role via `@PreAuthorize`
- No sensitive data in responses (amounts, statuses only)
- `@Auditable` on trigger endpoint for audit trail

## Next Steps
- Phase 5: Angular admin dashboard
