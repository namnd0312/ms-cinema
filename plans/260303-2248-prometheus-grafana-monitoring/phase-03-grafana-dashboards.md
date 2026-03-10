# Phase 3: Grafana with Auto-Provisioned Dashboards

## Context Links
- [Parent Plan](./plan.md)
- [Phase 2: Prometheus](./phase-02-prometheus-infrastructure.md)
- [Grafana Research](./reports/researcher-02-grafana-provisioning.md)

## Overview
- **Priority:** P1
- **Status:** Pending
- **Description:** Add Grafana to Docker Compose with auto-provisioned Prometheus datasource and dashboards. Two dashboards: JVM Micrometer (community) + HTTP/Business Overview (custom). Both use `$application` template variable for per-service filtering.

## Key Insights
- Grafana auto-provisioning loads datasources + dashboards from `/etc/grafana/provisioning/` on startup
- Community JVM dashboard ID 4701 covers all JVM metrics (memory, GC, threads, CPU)
- Custom dashboard uses Micrometer's `http.server.requests` metric (Spring Boot 3.x default)
- Template variable `$application` maps to `management.metrics.tags.application` set in Phase 1
- Dashboard edits in UI NOT persisted to JSON files (one-way sync)

## Requirements
- Grafana accessible at `http://localhost:3000` (admin/admin)
- Prometheus datasource auto-configured on startup
- Two dashboards auto-loaded:
  1. JVM Micrometer (community ID 4701) — JVM metrics per service
  2. Spring Boot HTTP Overview (custom) — request rate, error rate, latency, DB pool, per service
- `$application` dropdown filters all panels by service name

## Related Code Files

### Files to CREATE
| File | Purpose |
|------|---------|
| `monitoring/grafana/provisioning/datasources/datasources.yml` | Auto-configure Prometheus datasource |
| `monitoring/grafana/provisioning/dashboards/dashboards.yml` | Dashboard provider config |
| `monitoring/grafana/provisioning/dashboards/json/jvm-micrometer.json` | JVM dashboard (from ID 4701) |
| `monitoring/grafana/provisioning/dashboards/json/spring-boot-http-overview.json` | Custom HTTP/infra dashboard |

### Files to MODIFY
| File | Change |
|------|--------|
| `docker-compose.yml` | Add grafana service + volume |

## Implementation Steps

### Step 1: Create directory structure

```bash
mkdir -p monitoring/grafana/provisioning/datasources
mkdir -p monitoring/grafana/provisioning/dashboards/json
```

### Step 2: Create datasource provisioning

Create `monitoring/grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

### Step 3: Create dashboard provider config

Create `monitoring/grafana/provisioning/dashboards/dashboards.yml`:

```yaml
apiVersion: 1

providers:
  - name: 'Default'
    orgId: 1
    folder: 'Microservices'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    allowUiUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards/json
      foldersFromFilesStructure: false
```

### Step 4: Download JVM Micrometer dashboard

Download community dashboard ID 4701 JSON and save to `monitoring/grafana/provisioning/dashboards/json/jvm-micrometer.json`.

Source: `https://grafana.com/grafana/dashboards/4701-jvm-micrometer/`

Adjust the downloaded JSON:
- Set datasource references to `"Prometheus"` (match provisioned name)
- Verify template variable uses `application` label (matches our `management.metrics.tags.application`)

### Step 5: Create custom HTTP overview dashboard

Create `monitoring/grafana/provisioning/dashboards/json/spring-boot-http-overview.json`.

Dashboard panels (using Micrometer metric names for Spring Boot 3.x):

| Panel | PromQL | Type |
|-------|--------|------|
| Request Rate | `sum(rate(http_server_requests_seconds_count{application="$application"}[5m])) by (uri, method)` | Time series |
| Error Rate (5xx) | `sum(rate(http_server_requests_seconds_count{application="$application",status=~"5.."}[5m]))` | Time series |
| P95 Latency | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application="$application"}[5m])) by (le, uri))` | Time series |
| HikariCP Active Connections | `hikaricp_connections_active{application="$application"}` | Gauge |
| HikariCP Pending | `hikaricp_connections_pending{application="$application"}` | Gauge |
| JVM Memory Used | `jvm_memory_used_bytes{application="$application"}` | Time series |
| Uptime | `process_uptime_seconds{application="$application"}` | Stat |
| Auth Login Rate | `sum(rate(auth_login_success_total{application="auth-service"}[5m]))` | Time series |
| Auth Login Failures | `sum(rate(auth_login_failure_total{application="auth-service"}[5m]))` | Time series |
| Bookings Created | `sum(rate(booking_created_total{application="booking-service"}[5m]))` | Time series |
| Payments Completed | `sum(rate(payment_completed_total{application="payment-service"}[5m]))` | Time series |

Template variables:
- `$application`: `label_values(up{job=~".+"}, application)` — dropdown for service selection

### Step 6: Add Grafana to docker-compose.yml

Add to `services:` section:

```yaml
  grafana:
    image: grafana/grafana-oss:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - ./monitoring/grafana/provisioning:/etc/grafana/provisioning
      - grafana-data:/var/lib/grafana
    networks:
      - my-net
    depends_on:
      - prometheus
    restart: unless-stopped
```

Add to `volumes:` section:
```yaml
  grafana-data:
```

## Todo List
- [ ] Create `monitoring/grafana/provisioning/datasources/datasources.yml`
- [ ] Create `monitoring/grafana/provisioning/dashboards/dashboards.yml`
- [ ] Download + adjust JVM Micrometer dashboard JSON (ID 4701)
- [ ] Create custom HTTP overview dashboard JSON
- [ ] Add grafana service to docker-compose.yml
- [ ] Add grafana-data volume to docker-compose.yml

## Success Criteria
- Grafana starts at :3000 with Prometheus datasource pre-configured
- Both dashboards visible under "Microservices" folder
- `$application` dropdown shows all 7 service names
- JVM dashboard shows memory, GC, threads for selected service
- HTTP dashboard shows request rate, error rate, latency for selected service

## Risk Assessment
- **Dashboard JSON compatibility:** Community dashboard may need datasource UID adjustment — fix by setting `datasource: {"type": "prometheus", "uid": "..."}` or using `"Prometheus"` name
- **Metric name mismatch:** Spring Boot 3.x uses `http.server.requests` (dots in Micrometer) → Prometheus format: `http_server_requests_seconds_*` (underscores). PromQL queries must use underscore format.

## Security Considerations
- Grafana admin password set via env var (default: admin/admin for dev)
- Sign-up disabled (`GF_USERS_ALLOW_SIGN_UP=false`)
- Only accessible on host port 3000 — not routed through API Gateway

## Next Steps
- Phase 4: End-to-end testing and verification
