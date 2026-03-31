
# Phase 6: Testing

## Context Links
- [plan.md](./plan.md)
- [Existing test](../../payment-service/src/test/java/com/namnd/paymentservice/event/PaymentEventPublisherTest.java)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Unit and integration tests for reconciliation batch job, service, and controller

## Key Insights
- Project uses Spring Boot Test with JUnit 5
- Stripe API calls need mocking (MockedStatic or Mockito)
- Spring Batch provides `JobLauncherTestUtils` for integration testing
- H2 in-memory DB for integration tests (add test dependency)
- Keep test files <200 lines each

## Requirements
### Functional
- Unit tests for Processor logic (status mapping, discrepancy classification)
- Unit tests for Service layer (validation, DTO mapping)
- Integration test for full batch job execution
- Controller tests for API endpoints

### Non-functional
- Mocked Stripe API (no real API calls in tests)
- Fast execution (<30s total)
- Deterministic (no flaky tests)

## Related Code Files
### Create
- `test/java/.../batch/ReconciliationProcessorTest.java` (~120 lines)
- `test/java/.../service/ReconciliationServiceImplTest.java` (~150 lines)
- `test/java/.../controller/ReconciliationControllerTest.java` (~150 lines)
- `test/java/.../batch/ReconciliationJobIntegrationTest.java` (~150 lines)

### Modify
- `payment-service/pom.xml` - add H2 test dependency if not present

## Implementation Steps

### 1. Add Test Dependencies
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.springframework.batch</groupId>
    <artifactId>spring-batch-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. ReconciliationProcessorTest (~120 lines)
Unit test - no Spring context needed.

Test cases:
- `process_matchingPayment_returnsMatched()` - Stripe PI matches local amount + status
- `process_amountMismatch_returnsAmountMismatch()` - different amounts
- `process_statusMismatch_returnsStatusMismatch()` - Stripe succeeded but local PENDING
- `process_missingLocal_returnsMissingLocal()` - no local payment found
- `process_stripeStatusMapping()` - verify all Stripe statuses map correctly

Setup:
- Mock `PaymentRepository`
- Mock `ReconciliationRunRepository`
- Create `PaymentIntent` mocks with expected fields

### 3. ReconciliationServiceImplTest (~150 lines)
Unit test with mocked dependencies.

Test cases:
- `triggerReconciliation_validRange_createsRunAndLaunchesJob()`
- `triggerReconciliation_invalidRange_throwsException()` (end < start)
- `triggerReconciliation_rangeTooLarge_throwsException()` (> 31 days)
- `getRuns_returnsPaginatedResults()`
- `getRunDetails_existingRun_returnsDto()`
- `getRunDetails_nonExistent_throwsException()`
- `resolveItem_setsResolvedAndNotes()`
- `getSummary_noRuns_handlesGracefully()`

Setup:
- Mock `ReconciliationRunRepository`, `ReconciliationItemRepository`
- Mock `JobLauncher`, `Job`

### 4. ReconciliationControllerTest (~150 lines)
`@WebMvcTest` with mocked service.

Test cases:
- `trigger_asAdmin_returns200()` with valid request body
- `trigger_asUser_returns403()` non-admin role
- `trigger_unauthenticated_returns401()`
- `getRuns_asAdmin_returnsPaginatedRuns()`
- `getRunItems_withFilter_appliesDiscrepancyType()`
- `resolveItem_asAdmin_returns200()`
- `summary_noRuns_returns200WithNull()`

Setup:
- `@MockBean ReconciliationService`
- `@WithMockUser(roles = "ADMIN")` for admin tests
- `@WithMockUser(roles = "USER")` for forbidden tests

### 5. ReconciliationJobIntegrationTest (~150 lines)
`@SpringBatchTest` + `@SpringBootTest` with embedded DB.

Test cases:
- `job_completesSuccessfully_withMatchedRecords()`
- `job_detectsStatusMismatch()`
- `job_detectsMissingStripe()`

Setup:
- Use `application-test.yml` with H2 datasource
- Mock Stripe API calls (`MockedStatic<PaymentIntent>`)
- Pre-populate local Payment records
- Use `JobLauncherTestUtils.launchJob()`
- Verify ReconciliationRun counts and ReconciliationItem discrepancy types

### 6. Test application config
Create `src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    hibernate:
      ddl-auto: create-drop
  batch:
    jdbc:
      initialize-schema: always
    job:
      enabled: false

reconciliation:
  cron: "-"
  auto-run:
    enabled: false
  max-date-range-days: 31

stripe:
  api-key: sk_test_fake
  webhook-secret: whsec_fake
```

## Todo List
- [ ] Add H2 and spring-batch-test dependencies to pom.xml
- [ ] Create application-test.yml
- [ ] Create ReconciliationProcessorTest
- [ ] Create ReconciliationServiceImplTest
- [ ] Create ReconciliationControllerTest
- [ ] Create ReconciliationJobIntegrationTest
- [ ] Run `mvn test` and verify all pass
- [ ] Check code coverage for reconciliation package

## Success Criteria
- All tests pass with `mvn test`
- Processor logic has 100% branch coverage
- Service validation edge cases covered
- Controller auth checks verified
- Integration test proves end-to-end batch flow

## Risk Assessment
- **Medium:** Mocking Stripe SDK objects can be brittle - use wrapper/adapter if needed
- **Low:** H2 dialect differences from PostgreSQL - minimal for these queries
- **Low:** Spring Batch test config conflicts - isolated with `@ActiveProfiles("test")`

## Security Considerations
- Test uses fake Stripe keys (never real)
- No test data leakage
- Auth tests verify role-based access

## Next Steps
- Manual QA with real Stripe test environment
- Consider adding monitoring/alerting for reconciliation failures
