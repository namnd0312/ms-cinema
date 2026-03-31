# Phase 1: Spring Batch Setup

## Context Links
- [plan.md](./plan.md)
- [payment-service pom.xml](../../payment-service/pom.xml)
- [application.yml](../../payment-service/src/main/resources/application.yml)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Add Spring Batch dependency, configure batch infrastructure, enable scheduling

## Key Insights
- Spring Boot 3.4.3 auto-configures Spring Batch 5.x with `@EnableBatchProcessing` NOT needed (Boot 3.x auto-config handles it)
- Batch metadata tables (BATCH_JOB_INSTANCE, etc.) stored in paymentdb
- `spring.batch.jdbc.initialize-schema=always` creates tables on startup
- Must add `@EnableScheduling` for cron-based auto-run

## Requirements
### Functional
- Spring Batch job infrastructure available in payment-service
- Batch metadata persisted to paymentdb
- Scheduling support enabled

### Non-functional
- No impact on existing payment endpoints
- Batch job runs in its own thread pool

## Architecture
- Spring Batch 5.x uses `JobRepository` backed by paymentdb
- Default `TaskExecutor` for job execution (single-threaded sufficient for reconciliation)
- `@EnableScheduling` on main app or dedicated config class

## Related Code Files
### Modify
- `payment-service/pom.xml` - add spring-boot-starter-batch
- `payment-service/src/main/resources/application.yml` - add batch + reconciliation config

### Create
- `com.namnd.paymentservice.config.BatchConfig` - datasource/transaction manager config for batch
- `com.namnd.paymentservice.config.SchedulingConfig` - enable scheduling

## Implementation Steps

1. Add to `payment-service/pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-batch</artifactId>
   </dependency>
   ```

2. Add to `application.yml`:
   ```yaml
   spring:
     batch:
       jdbc:
         initialize-schema: always
       job:
         enabled: false  # prevent auto-run on startup

   reconciliation:
     cron: "0 0 2 * * *"
     auto-run:
       enabled: true
     max-date-range-days: 31
   ```

3. Create `BatchConfig.java` (~30 lines):
   - `@Configuration` class
   - Configure `PlatformTransactionManager` bean if not already present (Spring Boot auto-configures this, likely no-op)
   - Set `spring.batch.job.enabled=false` to prevent jobs from running at startup

4. Create `SchedulingConfig.java` (~15 lines):
   - `@Configuration` + `@EnableScheduling`
   - Simple config class, scheduling logic in Phase 4

## Todo List
- [ ] Add spring-boot-starter-batch to pom.xml
- [ ] Add batch config properties to application.yml
- [ ] Add reconciliation config properties to application.yml
- [ ] Create BatchConfig.java
- [ ] Create SchedulingConfig.java
- [ ] Verify `mvn clean compile` succeeds
- [ ] Verify batch metadata tables created on startup

## Success Criteria
- `mvn clean compile` passes with new dependency
- Application starts without errors
- Spring Batch metadata tables present in paymentdb after first run

## Risk Assessment
- **Low:** Batch metadata tables sharing paymentdb - Spring Batch uses `BATCH_` prefix, no collision
- **Low:** `spring.batch.job.enabled=false` prevents accidental job execution on deploy

## Security Considerations
- No new endpoints in this phase
- Batch jobs run server-side only

## Next Steps
- Phase 2: Create ReconciliationRun and ReconciliationItem entities
