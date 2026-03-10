# Grafana Auto-Provisioning for Docker Compose: Technical Research

## 1. Docker Compose Service Configuration

**Image & Port**: `grafana/grafana-oss:latest` runs on port 3000.

```yaml
grafana:
  image: grafana/grafana-oss:latest
  ports:
    - "3000:3000"
  environment:
    - GF_SECURITY_ADMIN_PASSWORD=admin
    - GF_INSTALL_PLUGINS=
    - GF_USERS_ALLOW_SIGN_UP=false
    - GF_AUTH_ANONYMOUS_ENABLED=true
  volumes:
    - ./grafana/provisioning:/etc/grafana/provisioning
```

Key env vars: `GF_SECURITY_ADMIN_PASSWORD` (admin creds), `GF_AUTH_ANONYMOUS_ENABLED` (dashboard access).

## 2. Datasource Provisioning (YAML)

**File**: `/grafana/provisioning/datasources/datasources.yml`

```yaml
apiVersion: 1
providers:
  - name: 'Prometheus'
    orgId: 1
    folder: ''
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUIUpdates: true
    options:
      path: /etc/grafana/provisioning/datasources

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true
```

**Critical**: Internal Docker hostname `prometheus:9090` (not localhost). If updateIntervalSeconds ≤10, rely on filesystem watch; >10 forces polling.

## 3. Dashboard Provisioning (Provider Config)

**File**: `/grafana/provisioning/dashboards/dashboards.yml`

```yaml
apiVersion: 1
providers:
  - name: 'Dashboards'
    orgId: 1
    folder: 'Production'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 10
    allowUIUpdates: true
    options:
      path: /etc/grafana/provisioning/dashboards/json
```

**Location**: All dashboard JSON files in `/grafana/provisioning/dashboards/json/`.

## 4. Pre-Built Community Dashboards

| Name | Dashboard ID | Use Case |
|------|--------------|----------|
| JVM Micrometer | 4701 | JVM metrics (CPU, threads, GC, memory) |
| Spring Boot 3 (K8s) | 22108 | Spring Boot on Kubernetes |
| Node Exporter | 1860 | OS-level metrics (optional) |

**Import via provisioning** (not manual import): Download JSON from `grafana.com/grafana/dashboards/{ID}` and place in `/grafana/provisioning/dashboards/json/`.

## 5. Custom Dashboard JSON Structure

**Minimal example** for HTTP metrics:

```json
{
  "annotations": { "list": [] },
  "dashboard": {
    "title": "Spring Boot Metrics",
    "tags": ["spring", "prometheus"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Request Rate",
        "type": "graph",
        "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "rate(http_requests_total{application='$application'}[5m])",
            "legendFormat": "{{instance}}",
            "refId": "A"
          }
        ]
      },
      {
        "id": 2,
        "title": "Error Rate",
        "type": "graph",
        "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "rate(http_requests_failed{application='$application'}[5m])",
            "refId": "B"
          }
        ]
      },
      {
        "id": 3,
        "title": "P95 Latency",
        "type": "graph",
        "gridPos": { "x": 0, "y": 8, "w": 12, "h": 8 },
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{application='$application'}[5m]))",
            "refId": "C"
          }
        ]
      }
    ],
    "templating": {
      "list": [
        {
          "name": "application",
          "type": "query",
          "datasource": "Prometheus",
          "query": "label_values(http_requests_total, application)",
          "refresh": 1,
          "multi": false
        },
        {
          "name": "instance",
          "type": "query",
          "datasource": "Prometheus",
          "query": "label_values(http_requests_total{application='$application'}, instance)",
          "refresh": 1,
          "depends_on": "application"
        }
      ]
    },
    "version": 1
  }
}
```

**gridPos**: width 1-24 (24 columns), height in grid units (each = 30px).

## 6. Template Variables for Per-Service Filtering

**Query variable syntax** (Prometheus):
- `label_values(metric_name, label_name)` — get unique label values
- For chained filtering: `label_values(metric_name{parent_label='$parent_var'}, child_label)`

**Examples**:
```yaml
variables:
  - name: application
    query: label_values(up{job=~".*-service"}, application)
  - name: instance
    query: label_values(up{application="$application"}, instance)
```

In panel queries: `{application='$application', instance=~'$instance'}`

## 7. Volume Mounting Strategy

```yaml
volumes:
  - ./grafana/provisioning:/etc/grafana/provisioning
  - grafana_storage:/var/lib/grafana

volumes:
  grafana_storage:
```

**Directory structure**:
```
grafana/
├── provisioning/
│   ├── datasources/
│   │   └── datasources.yml
│   └── dashboards/
│       ├── dashboards.yml
│       └── json/
│           ├── jvm-micrometer-4701.json
│           └── spring-metrics-custom.json
```

## Key Findings

✓ Auto-provisioning loads on Grafana startup; changes require container restart
✓ Dashboard edits in UI NOT persisted back to JSON (one-way sync)
✓ Prometheus data source accessible via service hostname in compose network
✓ Template variables enable dynamic, per-service dashboard filtering
✓ Micrometer metrics work with labels: `application`, `instance`, `service`

## Implementation Checklist

- [ ] Create `grafana/provisioning/{datasources,dashboards}` directories
- [ ] Write `datasources.yml` with `http://prometheus:9090`
- [ ] Write `dashboards.yml` with path to `/json` folder
- [ ] Download JVM Micrometer dashboard ID 4701 JSON
- [ ] Create custom dashboard JSON with template variables
- [ ] Add volume mounts to docker-compose.yml
- [ ] Verify Prometheus metrics have `application` label (Micrometer requirement)

---

**Sources:**
- [Provision Grafana | Grafana documentation](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Configure Prometheus datasource | Grafana](https://grafana.com/docs/grafana/latest/datasources/prometheus/configure/)
- [Dashboard JSON Model | Grafana docs](https://grafana.com/docs/grafana/latest/dashboards/build-dashboards/view-dashboard-json-model/)
- [JVM Micrometer Dashboard 4701 | Grafana Labs](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/)
- [Prometheus template variables | Grafana docs](https://grafana.com/docs/grafana/latest/datasources/prometheus/template-variables/)
- [Dockerize Grafana Provisioning | Medium](https://medium.com/incluit/dockerize-grafana-self-provisioned-configuration-with-dashboards-9d8caa5ef92e)
