# Phase 7: Deploy Loki Log Aggregation

## Context Links
- [Parent Plan](./plan.md)
- [Phase 2 - Infrastructure Pattern](./phase-02-k8s-infrastructure-postgresql-kafka-redis-zipkin.md)
- [Loki Docs](https://grafana.com/docs/loki/latest/)

## Overview
- **Date:** 2026-04-09
- **Priority:** P3
- **Status:** completed
- **Review:** completed
- **Description:** Deploy Grafana Loki + Promtail to K8s for centralized log aggregation. Loki stores logs, Promtail (DaemonSet) collects container logs and ships to Loki. Optionally deploy Grafana for log visualization.

## Key Insights
- Loki is lightweight, designed for log aggregation without full-text indexing (labels-based)
- Promtail runs as DaemonSet to scrape container logs from each node
- For dev/local: single-instance Loki with filesystem storage (no S3/GCS needed)
- Grafana needed for querying logs via LogQL UI
- Spring Boot services already output structured logs; no app-level changes needed
- Promtail auto-discovers pods via K8s API and adds labels (namespace, pod, container)

## Requirements

### Functional
- Loki deployment accepting log pushes on port 3100
- Promtail DaemonSet scraping all container logs in `ms-cinema` namespace
- Grafana deployment for log visualization on port 3000
- Logs queryable by service name, level, timestamp

### Non-Functional
- Resource-efficient for local dev (Loki ~256-512Mi, Promtail ~128Mi, Grafana ~256Mi)
- Logs persist across pod restarts (emptyDir acceptable for dev)
- Health checks on all components

## Architecture

```
+------------------+     +-------+     +---------+
| Promtail         | --> | Loki  | <-- | Grafana |
| (DaemonSet)      |     | :3100 |     | :3000   |
| scrapes pod logs |     +-------+     +---------+
+------------------+
```

- Promtail reads `/var/log/pods/*` from host, ships to `http://loki:3100/loki/api/v1/push`
- Grafana connects to Loki as datasource at `http://loki:3100`
- All in `ms-cinema` namespace, ClusterIP services

## Related Code Files

### Create
- `k8s/infra/loki/deployment.yml` — Loki Deployment + Service
- `k8s/infra/loki/loki-config.yml` — Loki ConfigMap (minimal config)
- `k8s/infra/promtail/deployment.yml` — Promtail DaemonSet + ConfigMap + RBAC
- `k8s/infra/grafana/deployment.yml` — Grafana Deployment + Service

### Modify
- `k8s/deploy-all.sh` — add loki, promtail, grafana to infra deploy loop
- `k8s/base/configmap.yml` — add `LOKI_HOST: "loki"` (optional, for future app-level log push)

## Implementation Steps

### Step 1: Loki ConfigMap + Deployment
1. Create `k8s/infra/loki/loki-config.yml` — ConfigMap with minimal `loki.yaml`:
   - `auth_enabled: false`
   - `server.http_listen_port: 3100`
   - `ingester` with WAL, lifecycle config
   - `schema_config` with boltdb-shipper + filesystem
   - `storage_config` filesystem at `/loki/chunks`
   - `limits_config` reject old samples
   - `compactor` working dir
2. Create `k8s/infra/loki/deployment.yml`:
   - Deployment: `grafana/loki:2.9.6` (stable), mount config, emptyDir for data
   - Service: ClusterIP port 3100
   - Resources: limits 512Mi memory, 300m CPU
   - Readiness: `GET /ready` port 3100
   - Liveness: `GET /ready` port 3100

### Step 2: Promtail DaemonSet
1. Create `k8s/infra/promtail/deployment.yml`:
   - ServiceAccount + ClusterRole + ClusterRoleBinding for K8s API access
   - ConfigMap with `promtail.yaml`:
     - `server.http_listen_port: 9080`
     - `clients` → `http://loki:3100/loki/api/v1/push`
     - `positions` file at `/run/promtail/positions.yaml`
     - `scrape_configs` with `kubernetes_sd_configs` (role: pod)
     - `relabel_configs` to add namespace, pod, container labels
   - DaemonSet: `grafana/promtail:2.9.6`
     - Mount `/var/log/pods` (hostPath, readOnly)
     - Mount `/var/run/docker.sock` (if needed)
     - Mount config + positions emptyDir
   - Resources: limits 128Mi memory, 100m CPU

### Step 3: Grafana Deployment
1. Create `k8s/infra/grafana/deployment.yml`:
   - Deployment: `grafana/grafana:10.4.0`
   - Env: `GF_AUTH_ANONYMOUS_ENABLED=true`, `GF_AUTH_ANONYMOUS_ORG_ROLE=Admin` (dev only)
   - Service: ClusterIP port 3000 (or LoadBalancer for external access)
   - Resources: limits 256Mi memory, 200m CPU
   - Readiness: `GET /api/health` port 3000
   - Provisioning ConfigMap to auto-add Loki datasource

### Step 4: Update Deploy Scripts
1. `k8s/deploy-all.sh`: add `loki promtail grafana` to infra deploy+wait loops
2. No teardown.sh changes needed (namespace delete cleans all)
3. Optionally add `LOKI_HOST: "loki"` to configmap

### Step 5: Verify
1. `kubectl apply` all loki/promtail/grafana manifests
2. Check pods running: `kubectl get pods -n ms-cinema`
3. Open Grafana, verify Loki datasource connected
4. Query logs: `{namespace="ms-cinema"}` in Grafana Explore

## Todo List
- [x] Create `k8s/infra/loki/loki-config.yml` (ConfigMap)
- [x] Create `k8s/infra/loki/deployment.yml` (Deployment + Service)
- [x] Create `k8s/infra/promtail/deployment.yml` (DaemonSet + RBAC + ConfigMap)
- [x] Create `k8s/infra/grafana/deployment.yml` (Deployment + Service + Datasource provisioning)
- [x] Update `k8s/deploy-all.sh` — add loki, promtail, grafana to infra loop
- [ ] Optionally update `k8s/base/configmap.yml` with LOKI_HOST
- [ ] Test: deploy all, verify logs visible in Grafana

## Success Criteria
- Loki pod running and healthy (`/ready` returns 200)
- Promtail DaemonSet running on all nodes, scraping logs
- Grafana accessible, Loki datasource auto-provisioned
- `{namespace="ms-cinema"}` query returns logs from all services
- No OOMKills or crash loops under normal load

## Risk Assessment
- **Promtail RBAC**: needs ClusterRole to read pod metadata — scoped to minimum permissions
- **Resource pressure**: 3 new pods (~900Mi total) — acceptable for OrbStack dev
- **Log volume**: dev environment is low-traffic, no retention concerns
- **OrbStack log paths**: `/var/log/pods` path may differ — verify on OrbStack node

## Security Considerations
- Grafana anonymous admin is dev-only; production must use auth
- Loki `auth_enabled: false` for dev simplicity
- Promtail has read-only access to host log paths
- No sensitive data exposed via log aggregation (review app log output)

## Next Steps
- After deployment, consider adding dashboards for error rate monitoring
- Production: enable Loki auth, Grafana OIDC, S3 storage backend
- Consider Loki alerting rules for critical errors
