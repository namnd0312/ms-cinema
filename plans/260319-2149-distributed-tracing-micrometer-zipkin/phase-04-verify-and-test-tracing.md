# Phase 04: Verify and Test End-to-End Tracing

## Context Links
- [Plan overview](plan.md)
- [Phase 01 - Dependencies](phase-01-add-tracing-dependencies.md)
- [Phase 02 - Config](phase-02-configure-tracing-properties.md)
- [Phase 03 - Infrastructure](phase-03-add-zipkin-infrastructure.md)
- [Codebase summary](/docs/codebase-summary.md)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Verify distributed tracing works end-to-end: HTTP propagation, Kafka propagation, log correlation, and Grafana-Zipkin integration.

## Key Insights
- Spring Boot 3.x auto-instruments: RestTemplate, WebClient, Feign, Spring Kafka, Spring MVC filters
- Feign client (booking -> movie-service) auto-propagates trace headers when tracing bridge on classpath
- Spring Kafka auto-injects/extracts trace context via Kafka headers (no manual config)
- Gateway MVC (servlet-based) auto-propagates via standard servlet filter instrumentation
- traceId in logs enables Grafana Loki -> Zipkin drill-down via "derived fields"

## Requirements
- **Functional:** Single traceId spans across gateway -> service -> Kafka -> consumer
- **Non-functional:** No missing spans, no orphan traces, Kafka events carry traceId

## Architecture

Test flow paths:

```
Path 1 (HTTP only):
  curl POST /api/auth/login -> gateway -> auth-service
  Verify: same traceId in gateway + auth-service logs + Zipkin

Path 2 (HTTP + Feign):
  curl POST /api/bookings/reserve -> gateway -> booking-service -> Feign -> movie-service
  Verify: same traceId across all 3 services

Path 3 (HTTP + Kafka):
  curl POST /api/auth/register -> gateway -> auth-service -> Kafka(notification-events) -> notification-service
  Verify: same traceId in auth-service log + notification-service Kafka consumer log

Path 4 (Full chain):
  Complete booking+payment flow -> PaymentCompletedEvent -> booking-service -> InAppNotificationEvent -> notification-service
  Verify: single trace spans entire flow in Zipkin
```

## Related Code Files

### Files to Inspect (read-only verification)
| File | What to verify |
|------|---------------|
| Service logs (stdout/Loki) | traceId and spanId fields present in JSON |
| Zipkin UI (http://localhost:9411) | Traces show multi-service spans |
| Grafana Zipkin datasource | Connection test succeeds, traces queryable |
| Kafka consumer logs | traceId matches producer traceId |

### Optional Enhancement Files
| File | Change |
|------|--------|
| `monitoring/grafana/provisioning/datasources/datasources.yml` | Add Loki derived field for traceId -> Zipkin link (optional) |

## Implementation Steps

### Step 1: Build and start all services

```bash
mvn clean package -DskipTests
docker-compose up --build -d
```

Wait for all services to register with Eureka (check http://localhost:8761).

### Step 2: Test HTTP trace propagation

```bash
# Login request through gateway
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"password"}'
```

Verify in Zipkin UI (http://localhost:9411):
- Find trace by service name "api-gateway"
- Trace shows 2 spans: api-gateway -> auth-service
- Both spans share same traceId

### Step 3: Test Feign trace propagation

```bash
# Reserve booking (requires auth token) -- triggers Feign call to movie-service
curl -X POST http://localhost:8080/api/bookings/reserve \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{"showtimeId":1,"seatIds":[1,2]}'
```

Verify in Zipkin:
- Trace shows 3 spans: api-gateway -> booking-service -> movie-service (Feign)

### Step 4: Test Kafka trace propagation

```bash
# Register triggers Kafka notification event
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"trace-test@test.com","username":"tracetest","password":"Test1234!","fullName":"Trace Test"}'
```

Verify in Zipkin:
- Trace shows: api-gateway -> auth-service -> [Kafka] -> notification-service
- Kafka span should appear as async child span

### Step 5: Verify log correlation

Query Loki in Grafana for a known traceId:
```
{service="auth-service"} | json | traceId="<traceId-from-zipkin>"
```

Confirm matching log entries appear with the same traceId.

### Step 6 (Optional): Add Loki derived field for Zipkin drill-down

Update Grafana Loki datasource to add derived field:
- Field name: `traceId`
- Regex: `"traceId":"([a-f0-9]+)"`
- Internal link: Zipkin datasource
- URL: `${__value.raw}`

This creates clickable traceId links in Grafana Loki log view that open directly in Zipkin.

## Todo List
- [ ] Build project and start docker-compose
- [ ] Verify Zipkin UI accessible at http://localhost:9411
- [ ] Test HTTP-only trace (login via gateway)
- [ ] Test Feign trace propagation (booking reserve)
- [ ] Test Kafka trace propagation (register -> notification)
- [ ] Verify traceId/spanId in structured JSON logs
- [ ] Query Loki with traceId filter
- [ ] Test Grafana Zipkin datasource connection
- [ ] (Optional) Add Loki derived field for traceId -> Zipkin link

## Success Criteria
- Single traceId visible across all services in a request chain (Zipkin UI)
- Kafka events carry and continue the same trace context
- Structured JSON logs contain traceId and spanId fields
- Grafana Zipkin datasource connection test passes
- No errors in service startup logs related to tracing/exporter

## Risk Assessment
- **Medium:** Kafka trace propagation depends on Spring Kafka version; Spring Boot 3.4.3 bundles Kafka 3.7+ which supports this
- **Mitigation:** If Kafka traces broken, verify `spring.kafka.listener.observation-enabled=true` (may need explicit config)
- **Low:** Zipkin exporter silently drops spans if Zipkin unreachable -- check Zipkin container is healthy first

## Security Considerations
- Test with non-sensitive data (test accounts)
- Zipkin stores trace data in-memory -- no persistence concern for dev
- Ensure Zipkin port (9411) not exposed in production without auth

## Next Steps
- Update project docs (`docs/system-architecture.md`, `docs/codebase-summary.md`) with tracing setup
- Update `docs/development-roadmap.md` with tracing milestone completion
- Consider adding Kafka observation config if auto-propagation doesn't work: `spring.kafka.listener.observation-enabled=true`
