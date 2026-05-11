# Phase 04 — Update Grafana datasource provisioning for Tempo

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-02-tempo-collector-grafana.md` (section 5)
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (section 5)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/grafana/provisioning/datasources/datasources.yml`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/grafana/`

## Overview

- Date: 2026-05-09
- Priority: P2
- Status: pending
- Review: not-started
- Description: Add Tempo datasource to Grafana provisioning with `tracesToLogsV2` (→ Loki via `service` label) and `tracesToMetrics` (→ Prometheus). Keep Zipkin datasource present until phase 05.

## Key Insights

- Confirmed Loki label is `service` (not `service_name`/`app`) — derived from logback `<label key="service" .../>` per main agent.
- Tempo span attribute is `service.name`. Mapping: span tag `service.name` → Loki label `service`.
- Existing `datasources.yml` already provisions Loki (uid: loki) and Prometheus (uid: prometheus) — reuse those uids.
- Grafana k8s deployment may use a separate provisioning ConfigMap — both must be updated.

## Requirements

**Functional**
- Tempo datasource visible in Grafana UI at `Explore → Tempo`.
- Click trace span → "View logs" links to Loki query filtered by `service` and `traceId`.
- Click trace span → "Metrics" links to Prometheus latency rate.
- Service node graph renders for any selected trace.

**Non-functional**
- Provisioning loads on Grafana startup (no manual UI clicks).
- Works in BOTH docker-compose AND k8s.

## Architecture

```
Grafana
├── Loki    (uid: loki)        ← unchanged
├── Prom    (uid: prometheus)  ← unchanged
├── Zipkin  (uid: zipkin)      ← still present, removed phase 05
└── Tempo   (uid: tempo)       ← NEW
            ├─ tracesToLogsV2  → Loki  (service.name → service)
            └─ tracesToMetrics → Prom  (rate by service_name)
```

## Related Code Files

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/grafana/provisioning/datasources/datasources.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/grafana/configmap.yml` (or wherever provisioning configmap lives — confirm at start)

**Create**
- none

**Delete**
- none

## Implementation Steps

1. Inspect Loki and Prometheus uid values in `monitoring/grafana/provisioning/datasources/datasources.yml`. Note them (e.g. `loki`, `prometheus`).

2. Append Tempo datasource block to `monitoring/grafana/provisioning/datasources/datasources.yml`:
   ```yaml
   - name: Tempo
     type: tempo
     uid: tempo
     access: proxy
     url: http://tempo:3200
     isDefault: false
     editable: true
     jsonData:
       httpMethod: GET
       tracesToLogsV2:
         datasourceUid: loki
         spanStartTimeShift: -1m
         spanEndTimeShift: 1m
         tags:
           - key: service.name
             value: service
         filterByTraceID: true
         customQuery: true
         query: '{service="${__span.tags["service.name"]}"} |= `${__span.traceId}`'
       tracesToMetrics:
         datasourceUid: prometheus
         spanStartTimeShift: -1m
         spanEndTimeShift: 1m
         tags:
           - key: service.name
             value: service_name
         queries:
           - name: 'Request rate'
             query: 'sum(rate(http_server_requests_seconds_count{service_name="$$__tags.service_name"}[5m]))'
           - name: 'p95 latency'
             query: 'histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{service_name="$$__tags.service_name"}[5m])) by (le))'
       serviceMap:
         datasourceUid: prometheus
       nodeGraph:
         enabled: true
       search:
         hide: false
       lokiSearch:
         datasourceUid: loki
   ```

3. Confirm k8s grafana provisioning path. Two cases:
   - If `k8s/infra/grafana/` mounts the same `monitoring/grafana/` files via hostPath/volume → no further change.
   - If it has its own ConfigMap with embedded YAML → mirror the same Tempo block into that ConfigMap.

4. Restart Grafana:
   - Compose: `docker compose restart grafana`
   - K8s: `kubectl rollout restart deploy/grafana`

5. Open Grafana UI → `Connections → Data sources` → confirm Tempo listed and "Save & test" returns success.

6. Generate sample trace (e.g. `curl http://localhost:8080/api/v1/auth/login -d '{...}'`). In Grafana → Explore → Tempo → Search by `service.name=auth-service` → open trace → click "Logs for this span" → confirm Loki query returns the matching log lines.

## Todo List

- [ ] Note current Loki + Prometheus datasource uids
- [ ] Add Tempo block to `monitoring/grafana/provisioning/datasources/datasources.yml`
- [ ] Mirror block into k8s grafana provisioning ConfigMap (if separate)
- [ ] Restart grafana (compose)
- [ ] Restart grafana (k8s)
- [ ] Verify Tempo datasource "Save & test" passes
- [ ] Verify trace → log click navigates to Loki with correct filter
- [ ] Verify node graph renders

## Success Criteria

- Grafana startup logs show: `successfully provisioned datasource Tempo`.
- Tempo "Save & test" → "Data source successfully connected".
- Sample trace search returns ≥1 trace after triggering app traffic.
- Trace span "Logs" button opens Loki with query `{service="auth-service"} |= "<traceId>"` and returns rows.
- "Service Graph" tab renders nodes for active services.

## Risk Assessment

- **Wrong Loki label key** (`app` vs `service`) → broken trace-to-logs. Mitigation: confirmed `service` per main agent; if breaks, grep one logback-spring.xml: `find . -name 'logback-spring.xml' | head -1 | xargs grep label`.
- **`$$` vs `$` interpolation** in Grafana provisioning YAML — Grafana doubles `$` to escape env-var expansion. Variable expressions in `query` strings need correct quoting per Grafana docs.
- **Prometheus metric label key mismatch** (`service` vs `service_name`) — Spring Boot sanitizes attributes (dots → underscores) → metric label is `service_name`. Datasource uses `service_name` for tracesToMetrics correctly above.
- **Datasource uid clash** if `tempo` already used elsewhere — verify before commit.

## Security Considerations

- Tempo URL `http://tempo:3200` — internal only, accessed via Grafana proxy.
- No auth on Tempo — Grafana acts as gatekeeper. Acceptable for current scope.
- No secrets in datasource YAML.

## Next Steps

- Phase 05: remove Zipkin datasource block once parity confirmed.
- Phase 07: validation depends on this datasource being live.
