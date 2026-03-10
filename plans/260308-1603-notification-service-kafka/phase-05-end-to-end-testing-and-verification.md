# Phase 5: End-to-End Testing & Verification

## Context Links
- [Plan Overview](./plan.md)
- All previous phases

## Overview
- **Priority:** Medium
- **Status:** Pending
- **Description:** Verify full flow: auth-service → Kafka → notification-service → email delivered

## Key Insights
- Test both registration (activation email) and forgot-password (reset email) flows
- Verify DLT catches failed messages
- Confirm auth-service no longer has SMTP dependency

## Test Scenarios

### 1. Registration Flow
1. POST `/api/auth/register` with valid user data
2. Verify Kafka `notification-events` topic receives message
3. Verify notification-service consumes and sends activation email
4. Check email inbox for activation link
5. Click activation link → account activated

### 2. Password Reset Flow
1. POST `/api/auth/forgot-password` with existing email
2. Verify Kafka event published
3. Verify notification-service sends reset email
4. Check email inbox for reset link with correct URL (`localhost:4200/auth/reset-password`)

### 3. Kafka Failure Resilience
1. Stop notification-service
2. Trigger registration
3. Start notification-service
4. Verify backlogged message consumed and email sent

### 4. SMTP Failure (DLT)
1. Configure invalid SMTP credentials
2. Trigger notification event
3. Verify 3 retry attempts
4. Verify message lands in `notification-events-dlt` topic

## Implementation Steps

1. Build all modules: `mvn clean package -DskipTests`
2. Start full stack: `docker compose up -d --build`
3. Wait for all services healthy in Eureka dashboard (:8761)
4. Run test scenarios manually or via curl/httpie
5. Check notification-service logs: `docker compose logs -f notification-service`
6. Verify Kafka topics: `docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092`

## Todo List
- [ ] Full stack builds successfully
- [ ] Registration sends email via Kafka flow
- [ ] Password reset sends email via Kafka flow
- [ ] DLT captures failed notifications
- [ ] Prometheus scrapes notification-service
- [ ] Eureka shows notification-service registered
- [ ] Auth-service has no direct SMTP dependency

## Success Criteria
- All email flows work end-to-end through Kafka
- Auth-service pom.xml has no spring-boot-starter-mail
- notification-service visible in Eureka
- notification-service metrics in Prometheus/Grafana

## Risk Assessment
- **Low:** Testing phase, no production impact
- Email delivery depends on valid SMTP credentials

## Next Steps
- Update docs (codebase-summary, system-architecture)
- Consider adding booking/payment notification events in future
