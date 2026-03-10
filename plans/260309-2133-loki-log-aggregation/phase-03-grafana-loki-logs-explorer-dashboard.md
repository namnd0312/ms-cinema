---
title: "Phase 3 - Grafana Loki Logs Explorer Dashboard"
status: pending
priority: P2
effort: 0.5h
---

# Phase 3: Grafana Loki Logs Explorer Dashboard

## Context Links
- Parent plan: [plan.md](./plan.md)
- Phase 2: [phase-02-loki4j-logback-appender-all-six-services.md](./phase-02-loki4j-logback-appender-all-six-services.md)
- Existing dashboards: `monitoring/grafana/provisioning/dashboards/json/`
- Dashboard provider: `monitoring/grafana/provisioning/dashboards/dashboards.yml`

## Overview

Create a Grafana dashboard JSON provisioned at startup for log exploration. Dashboard provides panels for: error/warn log streams per service, correlationId search, and log volume over time.

## Key Insights

- Grafana auto-loads dashboards from `monitoring/grafana/provisioning/dashboards/json/` (configured in `dashboards.yml`)
- Dashboard uses Loki datasource (`datasource: Loki`)
- Key LogQL patterns:
  - All errors: `{service=~".+"} | json | level="ERROR"`
  - By service: `{service="auth-service"} | json`
  - By correlationId: `{service=~".+"} |= "correlationId-value"`
  - Error count over time: `sum by (service) (count_over_time({service=~".+"} | json | level="ERROR" [1m]))`
- Dashboard panels: log volume chart, per-service error stream, full log explorer

## Requirements

- Dashboard auto-provisioned on Grafana startup (no manual import)
- Panels: log volume by service (time series), error log stream (logs panel), service selector variable
- Uses Loki datasource

## Related Code Files

- `monitoring/grafana/provisioning/dashboards/json/loki-microservices-log-explorer-dashboard.json` — new file

## Implementation Steps

### Step 1: Create Grafana dashboard JSON

Create `monitoring/grafana/provisioning/dashboards/json/loki-microservices-log-explorer-dashboard.json`:

```json
{
  "__inputs": [],
  "__requires": [],
  "annotations": { "list": [] },
  "description": "Centralized log explorer for all microservices via Loki",
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 0,
  "id": null,
  "links": [],
  "panels": [
    {
      "datasource": { "type": "loki", "uid": "loki" },
      "fieldConfig": { "defaults": {}, "overrides": [] },
      "gridPos": { "h": 8, "w": 24, "x": 0, "y": 0 },
      "id": 1,
      "options": {
        "dedupStrategy": "none",
        "enableLogDetails": true,
        "prettifyLogMessage": false,
        "showCommonLabels": false,
        "showLabels": true,
        "showTime": true,
        "sortOrder": "Descending",
        "wrapLogMessage": false
      },
      "targets": [
        {
          "datasource": { "type": "loki", "uid": "loki" },
          "expr": "{service=~\"$service\"} | json",
          "legendFormat": "",
          "queryType": "range",
          "refId": "A"
        }
      ],
      "title": "Log Stream",
      "type": "logs"
    },
    {
      "datasource": { "type": "loki", "uid": "loki" },
      "fieldConfig": {
        "defaults": { "color": { "mode": "palette-classic" }, "custom": { "lineWidth": 1 } },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 24, "x": 0, "y": 8 },
      "id": 2,
      "options": { "legend": { "displayMode": "list", "placement": "bottom" }, "tooltip": { "mode": "single" } },
      "targets": [
        {
          "datasource": { "type": "loki", "uid": "loki" },
          "expr": "sum by (service) (count_over_time({service=~\"$service\"} | json | level=\"ERROR\" [1m]))",
          "legendFormat": "{{service}}",
          "queryType": "range",
          "refId": "A"
        }
      ],
      "title": "Error Log Volume by Service (per minute)",
      "type": "timeseries"
    },
    {
      "datasource": { "type": "loki", "uid": "loki" },
      "fieldConfig": { "defaults": {}, "overrides": [] },
      "gridPos": { "h": 8, "w": 24, "x": 0, "y": 16 },
      "id": 3,
      "options": {
        "dedupStrategy": "none",
        "enableLogDetails": true,
        "prettifyLogMessage": true,
        "showLabels": true,
        "showTime": true,
        "sortOrder": "Descending"
      },
      "targets": [
        {
          "datasource": { "type": "loki", "uid": "loki" },
          "expr": "{service=~\".+\"} | json | level=\"ERROR\" or level=\"WARN\"",
          "legendFormat": "",
          "queryType": "range",
          "refId": "A"
        }
      ],
      "title": "Errors & Warnings - All Services",
      "type": "logs"
    }
  ],
  "refresh": "30s",
  "schemaVersion": 38,
  "tags": ["loki", "logs", "microservices"],
  "templating": {
    "list": [
      {
        "current": { "selected": true, "text": "All", "value": "$__all" },
        "datasource": { "type": "loki", "uid": "loki" },
        "definition": "label_values(service)",
        "hide": 0,
        "includeAll": true,
        "label": "Service",
        "multi": false,
        "name": "service",
        "options": [],
        "query": "label_values(service)",
        "refresh": 1,
        "sort": 1,
        "type": "query"
      }
    ]
  },
  "time": { "from": "now-1h", "to": "now" },
  "timepicker": {},
  "timezone": "browser",
  "title": "Microservices Log Explorer",
  "uid": "loki-microservices-logs",
  "version": 1
}
```

**Note on datasource UID**: Grafana assigns UIDs to provisioned datasources. The dashboard references `uid: "loki"` — this must match the UID in `datasources.yml`. Add `uid: loki` to the Loki datasource entry in Phase 1.

## Todo List

- [ ] Create `monitoring/grafana/provisioning/dashboards/json/loki-microservices-log-explorer-dashboard.json`
- [ ] Ensure Loki datasource in `datasources.yml` has `uid: loki` (fix Phase 1 output if needed)
- [ ] Verify dashboard loads in Grafana after `docker-compose up`

## Success Criteria

- "Microservices Log Explorer" dashboard visible in Grafana under "Microservices" folder
- `$service` variable dropdown lists all service names
- Log Stream panel shows live logs
- Error Volume panel shows time-series chart
- Errors & Warnings panel filters correctly

## Risk Assessment

- **Datasource UID mismatch**: If Grafana assigns a different UID to the Loki datasource, panels won't load. Fix: set explicit `uid: loki` in `datasources.yml`
- **No logs yet**: Dashboard shows empty until services start and push logs — expected

## Security Considerations

- Dashboard is read-only for non-admin users (Grafana default)
- No sensitive data exposed in dashboard queries

## Next Steps

- After all 3 phases done: `docker-compose up --build` to validate full stack
- Test: `{service="auth-service"} | json | level="ERROR"` returns error logs in Explore
- Test: search by correlationId using `|= "some-uuid"` in Explore
