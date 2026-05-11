# Phase 07 — Validate end-to-end trace flow

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-01-spring-boot-otlp-kafka-tracing.md` (Q5 Kafka, Q8 MDC)
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-02-tempo-collector-grafana.md` (section 5)
- Source: docker-compose.yml, k8s/

## Overview

- Date: 2026-05-09
- Priority: P1 (gate to phase 05)
- Status: pending
- Review: not-started
- Description: End-to-end test of new tracing stack. Confirm traces flow from each service through collector to Tempo, are queryable in Grafana, link to Loki logs and Prometheus metrics. Smoke + cross-service flows + Kafka propagation.

## Key Insights

- Spring Kafka 3.x auto-propagates W3C `traceparent` headers — easy to verify via Kafka topic header inspection.
- MDC `traceId` continues to flow into Loki labels post-swap.
- 64-bit trace IDs preserved — audit-service `traceId` column unaffected.
- Validation must work in BOTH compose AND k8s.

## Requirements

**Functional**
- Each of 6 services emits traces to Tempo within 30s of receiving traffic.
- REST chain: client → booking → payment → auth all linked in single trace.
- Kafka chain: booking publishes event → audit consumes → both spans in same trace.
- Click span in Grafana → opens correct Loki query → returns matching logs.
- Service graph (node graph) renders with all 6 services + Kafka edges.

**Non-functional**
- p95 trace ingestion latency ≤ 5s (export → queryable).
- Zero dropped spans under nominal load (10 req/s).
- Memory steady-state: collector < 100Mi, tempo < 400Mi.

## Architecture

```
Test scenarios:
1. Smoke per service: GET /actuator/health → trace appears in Tempo
2. REST chain: POST /booking → trace shows booking + payment + auth spans
3. Kafka chain: booking publishes booking.created → audit-service consumer span linked
4. Trace-to-logs: span click → Loki returns rows
5. Trace-to-metrics: span click → Prometheus latency rate panel
```

## Related Code Files

**Modify**
- none (test/validation phase)

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/reports/phase-07-validation-results.md` (validation results — created during execution)

**Delete**
- none

## Implementation Steps

1. **Pre-flight**: confirm phases 01-04 deployed in target env.
   ```bash
   # Compose
   docker compose ps tempo otel-collector grafana
   # K8s
   kubectl get pod | grep -E 'tempo|otel-collector|grafana'
   ```
   All Ready/Healthy.

2. **Smoke per service** — for each of `auth-service movie-service booking-service payment-service notification-service audit-service`:
   ```bash
   SERVICE=auth-service
   PORT=8081  # adjust per service
   curl -fsS http://localhost:$PORT/actuator/health
   sleep 5
   curl -s "http://localhost:3200/api/search?tags=service.name=$SERVICE&limit=1" | jq .
   ```
   Expect ≥1 trace returned.

3. **REST chain test** — trigger booking flow that fans out:
   ```bash
   TOKEN=$(curl -sX POST http://localhost:8081/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"username":"test","password":"test"}' | jq -r .accessToken)

   curl -sX POST http://localhost:8083/api/v1/bookings \
     -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d '{"movieId":1,"seats":["A1"]}'
   ```
   In Grafana → Explore → Tempo → search by `service.name=booking-service` → open most recent trace → assert spans from `payment-service` and `auth-service` (FeignClient calls) appear under same trace ID.

4. **Kafka chain test** — booking publishes event → audit consumes:
   ```bash
   # Booking already published from step 3 (or trigger again)
   sleep 5
   # Search Tempo by audit-service spans
   curl -s "http://localhost:3200/api/search?tags=service.name=audit-service&limit=5" | jq '.traces[].traceID'
   ```
   Pick a recent trace ID. Open in Grafana. Confirm:
   - `booking-service` produce span present
   - `audit-service` consume span present
   - Both share same `trace_id`

5. **Trace-to-logs test** — in Grafana, open any trace span. Click "Logs for this span" button. Expect Loki Explore opens with query like `{service="booking-service"} |= "<traceId>"`. At least 1 row returned.

6. **Trace-to-metrics test** — same span. Click "Metrics" button. Expect Prometheus query for `http_server_requests_seconds_*` filtered by `service_name`. Panel renders.

7. **Service graph test** — in Tempo Explore, switch to "Service Graph" tab. Confirm nodes present for all 6 services. Edges show inter-service calls (REST + Kafka).

8. **Load smoke** (optional, KISS): `hey -z 60s -c 10 http://localhost:8083/api/v1/movies` then check collector/tempo memory in `docker stats` or `kubectl top pod`. No OOMKills.

9. **Repeat steps 1-7 in k8s** (port-forward as needed):
   ```bash
   kubectl port-forward svc/tempo 3200:3200 &
   kubectl port-forward svc/grafana 3000:3000 &
   ```

10. **Document results** in `plans/260509-2131-otel-grafana-tracing-migration/reports/phase-07-validation-results.md` with screenshots/JSON snippets per test.

## Todo List

- [ ] Pre-flight infra ready in compose
- [ ] Pre-flight infra ready in k8s
- [ ] Smoke trace per service × 6 (compose)
- [ ] REST chain trace verified
- [ ] Kafka chain trace verified
- [ ] Trace-to-logs link works
- [ ] Trace-to-metrics link works
- [ ] Service graph renders 6 services
- [ ] Memory + drop-rate within bounds
- [ ] Repeat in k8s
- [ ] Write validation report

## Success Criteria

- 6/6 services produce queryable traces via Tempo HTTP API.
- 1 multi-service trace contains spans from ≥3 services with shared trace ID.
- 1 trace contains both Kafka producer + consumer spans linked.
- Grafana trace-to-logs returns matching log rows for a sampled span.
- Grafana trace-to-metrics renders without errors.
- Service graph shows all 6 services as nodes.
- Collector mem RSS < 100Mi steady state; tempo < 400Mi.
- Validation report written to `reports/phase-07-validation-results.md`.

## Risk Assessment

- **No traces in Tempo** — most likely Spring config typo or collector→tempo connectivity. Mitigation: tail collector logs `docker compose logs -f otel-collector`; check for `Exporting failed` errors.
- **Kafka spans not linked** — check spring-kafka version ≥ 3.1; check `traceparent` header in Kafka record (`kafka-console-consumer --property print.headers=true`).
- **Trace-to-logs returns empty** — Loki label key mismatch. Mitigation: in Grafana Loki Explore, run `{service="auth-service"}` directly to confirm label exists.
- **Tempo `/api/search` returns 4xx** — Tempo version mismatch with API path. Mitigation: switch to `/api/search/tags` or upgrade pin.
- **Slow trace propagation** to UI — Tempo flush interval. Wait up to 30s.

## Security Considerations

- Validation steps use test credentials only — no production data.
- Port-forward sessions terminated after validation.
- Validation report MUST NOT include real JWT tokens or PII.

## Next Steps

- On success → unlock phase 05 (Zipkin removal).
- On failure → document failure modes, return to phases 01-04 to fix.
- Future: add automated synthetic test in CI to catch tracing regressions.
