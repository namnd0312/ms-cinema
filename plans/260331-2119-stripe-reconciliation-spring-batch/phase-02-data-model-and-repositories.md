# Phase 2: Data Model & Repositories

## Context Links
- [plan.md](./plan.md)
- [Payment.java](../../payment-service/src/main/java/com/namnd/paymentservice/model/Payment.java)
- [PaymentRepository.java](../../payment-service/src/main/java/com/namnd/paymentservice/repository/PaymentRepository.java)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Create reconciliation entities, enums, repositories, and DTOs

## Key Insights
- Follow existing patterns: Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`
- DTOs as Java records (consistent with `PaymentHistoryResponse`)
- PaymentRepository needs new query: find payments by date range with non-null stripePaymentIntentId
- Keep files <200 lines each

## Requirements
### Functional
- ReconciliationRun tracks each reconciliation execution
- ReconciliationItem tracks individual comparison results
- DTOs for API responses
- Repository methods for querying by date range, discrepancy type

### Non-functional
- JPA auto-creates tables via `ddl-auto: update`
- Indexed columns for performance

## Architecture
```
ReconciliationRun (1) ---> (*) ReconciliationItem
  - tracks job execution              - tracks each payment comparison
  - aggregated counts                  - links to stripePaymentIntentId / localPaymentId
```

## Related Code Files
### Create
- `model/ReconciliationRun.java` - entity
- `model/ReconciliationItem.java` - entity
- `model/DiscrepancyType.java` - enum
- `model/ReconciliationStatus.java` - enum
- `repository/ReconciliationRunRepository.java`
- `repository/ReconciliationItemRepository.java`
- `dto/ReconciliationRunResponse.java` - record
- `dto/ReconciliationItemResponse.java` - record
- `dto/ReconciliationSummaryResponse.java` - record
- `dto/TriggerReconciliationRequest.java` - record
- `dto/ResolveItemRequest.java` - record

### Modify
- `repository/PaymentRepository.java` - add date-range query

## Implementation Steps

### 1. Enums

**DiscrepancyType.java** (~10 lines):
```java
public enum DiscrepancyType {
    MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE
}
```

**ReconciliationStatus.java** (~10 lines):
```java
public enum ReconciliationStatus {
    RUNNING, COMPLETED, FAILED
}
```

### 2. ReconciliationRun Entity (~60 lines)
```java
@Entity @Table(name = "reconciliation_runs")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate startDate;
    private LocalDate endDate;
    @Enumerated(EnumType.STRING)
    private ReconciliationStatus status;
    private int totalStripeRecords;
    private int totalLocalRecords;
    private int matchedCount;
    private int mismatchedCount;
    private int missingLocalCount;
    private int missingStripeCount;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
```

### 3. ReconciliationItem Entity (~65 lines)
```java
@Entity @Table(name = "reconciliation_items")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    private String stripePaymentIntentId;
    private Long localPaymentId;

    @Enumerated(EnumType.STRING)
    private DiscrepancyType discrepancyType;

    private Long stripeAmount;
    private Long localAmount;
    private String stripeStatus;
    private String localStatus;
    private boolean resolved;
    private String notes;
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() { this.createdAt = LocalDateTime.now(); }
}
```

### 4. Repositories

**ReconciliationRunRepository.java** (~15 lines):
- `findAllByOrderByCreatedAtDesc()` -> `Page<ReconciliationRun>` with Pageable
- `findFirstByOrderByCreatedAtDesc()` -> `Optional<ReconciliationRun>`

**ReconciliationItemRepository.java** (~25 lines):
- `findByRunId(Long runId, Pageable pageable)` -> `Page<ReconciliationItem>`
- `findByRunIdAndDiscrepancyType(Long runId, DiscrepancyType type, Pageable pageable)` -> `Page<ReconciliationItem>`
- `countByRunIdAndDiscrepancyType(Long runId, DiscrepancyType type)` -> long
- `countByRunIdAndDiscrepancyTypeNot(Long runId, DiscrepancyType type)` -> long
- `@Query("SELECT r.stripePaymentIntentId FROM ReconciliationItem r WHERE r.run.id = :runId AND r.stripePaymentIntentId IS NOT NULL") Set<String> findStripeIdsByRunId(@Param("runId") Long runId)`

### 5. Modify PaymentRepository
Add:
```java
@Query("SELECT p FROM Payment p WHERE p.createdAt >= :start AND p.createdAt < :end AND p.stripePaymentIntentId IS NOT NULL")
List<Payment> findByDateRangeWithStripeId(
    @Param("start") LocalDateTime start,
    @Param("end") LocalDateTime end);
```

### 6. DTOs (all as Java records)

**TriggerReconciliationRequest.java**:
```java
public record TriggerReconciliationRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {}
```

**ReconciliationRunResponse.java**:
```java
public record ReconciliationRunResponse(
    Long id, LocalDate startDate, LocalDate endDate, String status,
    int totalStripeRecords, int totalLocalRecords,
    int matchedCount, int mismatchedCount,
    int missingLocalCount, int missingStripeCount,
    LocalDateTime createdAt, LocalDateTime completedAt
) {}
```

**ReconciliationItemResponse.java**:
```java
public record ReconciliationItemResponse(
    Long id, Long runId, String stripePaymentIntentId, Long localPaymentId,
    String discrepancyType, Long stripeAmount, Long localAmount,
    String stripeStatus, String localStatus,
    boolean resolved, String notes, LocalDateTime createdAt
) {}
```

**ReconciliationSummaryResponse.java**:
```java
public record ReconciliationSummaryResponse(
    Long latestRunId, LocalDate startDate, LocalDate endDate, String status,
    int matched, int mismatched, int missingLocal, int missingStripe,
    LocalDateTime completedAt
) {}
```

**ResolveItemRequest.java**:
```java
public record ResolveItemRequest(String notes) {}
```

## Todo List
- [ ] Create DiscrepancyType enum
- [ ] Create ReconciliationStatus enum
- [ ] Create ReconciliationRun entity
- [ ] Create ReconciliationItem entity
- [ ] Create ReconciliationRunRepository
- [ ] Create ReconciliationItemRepository
- [ ] Add date-range query to PaymentRepository
- [ ] Create TriggerReconciliationRequest DTO
- [ ] Create ReconciliationRunResponse DTO
- [ ] Create ReconciliationItemResponse DTO
- [ ] Create ReconciliationSummaryResponse DTO
- [ ] Create ResolveItemRequest DTO
- [ ] Run `mvn clean compile` to verify

## Success Criteria
- All entities compile without errors
- Tables auto-created by Hibernate on startup
- Repository queries work with test data

## Risk Assessment
- **Low:** Hibernate `ddl-auto: update` creates tables automatically
- **Medium:** Large reconciliation runs could have many items - pagination handles this

## Security Considerations
- Entities have no direct API exposure (DTOs used)
- No user-facing data leakage

## Next Steps
- Phase 3: Implement batch job using these entities
