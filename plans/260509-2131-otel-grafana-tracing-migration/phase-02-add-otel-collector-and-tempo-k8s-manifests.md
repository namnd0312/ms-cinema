# Phase 02 — Add OTel Collector + Tempo k8s manifests

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-02-tempo-collector-grafana.md` (section 6)
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (section 4)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/zipkin/` (template reference)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/loki/` (template reference)

## Overview

- Date: 2026-05-09
- Priority: P1 (blocking k8s rollout of phase 03)
- Status: pending
- Review: not-started
- Description: Mirror docker-compose stack in k8s. Tempo as StatefulSet with PVC; collector as Deployment. Both in existing app namespace (no new `monitoring` namespace — KISS, matches current zipkin/loki).

## Key Insights

- Existing infra dirs (`k8s/infra/loki/`, `k8s/infra/zipkin/`) deploy in default app namespace via `deploy-all.sh`. Match that pattern.
- Tempo needs RWX or RWO PVC. RWO acceptable since single replica.
- Collector deployment 1 replica, no PVC (stateless).
- Service DNS: `tempo.<ns>.svc.cluster.local` and `otel-collector.<ns>.svc.cluster.local` — short names work in same namespace.

## Requirements

**Functional**
- Apps in same namespace can resolve `otel-collector:4318` and `tempo:3200`.
- Tempo persists traces across pod restarts.
- Collector restarts cleanly without data loss (in-flight spans dropped acceptable).

**Non-functional**
- Tempo: 256Mi mem request, 512Mi limit. CPU 100m/500m.
- Collector: 64Mi/128Mi mem. CPU 50m/200m.
- Configs in ConfigMaps (matches loki/grafana pattern).

## Architecture

```
k8s/infra/
├── tempo/
│   ├── configmap.yml      tempo.yaml
│   ├── pvc.yml            10Gi RWO
│   ├── statefulset.yml    1 replica
│   └── service.yml        ClusterIP, ports 3200/4317/4318
└── otel-collector/
    ├── configmap.yml      collector pipeline
    ├── deployment.yml     1 replica
    └── service.yml        ClusterIP, ports 4317/4318/13133
```

## Related Code Files

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/deploy-all.sh` (add new dirs to apply order)

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/tempo/configmap.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/tempo/pvc.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/tempo/statefulset.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/tempo/service.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/otel-collector/configmap.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/otel-collector/deployment.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/otel-collector/service.yml`

**Delete**
- none (zipkin removed in phase 05)

## Implementation Steps

1. Create `k8s/infra/tempo/configmap.yml`:
   ```yaml
   apiVersion: v1
   kind: ConfigMap
   metadata:
     name: tempo-config
   data:
     tempo.yaml: |
       server:
         http_listen_port: 3200
         grpc_listen_port: 3201
       distributor:
         receivers:
           otlp:
             protocols:
               grpc: { endpoint: 0.0.0.0:4317 }
               http: { endpoint: 0.0.0.0:4318 }
       storage:
         trace:
           backend: local
           local: { path: /var/tempo/traces }
           wal: { path: /var/tempo/wal }
       compactor:
         compaction:
           block_retention: 72h
   ```

2. Create `k8s/infra/tempo/pvc.yml`:
   ```yaml
   apiVersion: v1
   kind: PersistentVolumeClaim
   metadata:
     name: tempo-traces
   spec:
     accessModes: [ReadWriteOnce]
     resources:
       requests:
         storage: 10Gi
   ```

3. Create `k8s/infra/tempo/statefulset.yml`:
   ```yaml
   apiVersion: apps/v1
   kind: StatefulSet
   metadata:
     name: tempo
   spec:
     serviceName: tempo
     replicas: 1
     selector:
       matchLabels: { app: tempo }
     template:
       metadata:
         labels: { app: tempo }
       spec:
         containers:
         - name: tempo
           image: grafana/tempo:latest
           args: ["-config.file=/etc/tempo/tempo.yaml"]
           ports:
           - { name: http, containerPort: 3200 }
           - { name: otlp-grpc, containerPort: 4317 }
           - { name: otlp-http, containerPort: 4318 }
           volumeMounts:
           - { name: config, mountPath: /etc/tempo }
           - { name: traces, mountPath: /var/tempo }
           readinessProbe:
             httpGet: { path: /ready, port: 3200 }
             initialDelaySeconds: 10
           resources:
             requests: { cpu: 100m, memory: 256Mi }
             limits: { cpu: 500m, memory: 512Mi }
         volumes:
         - name: config
           configMap: { name: tempo-config }
         - name: traces
           persistentVolumeClaim: { claimName: tempo-traces }
   ```

4. Create `k8s/infra/tempo/service.yml`:
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: tempo
   spec:
     selector: { app: tempo }
     ports:
     - { name: http, port: 3200, targetPort: 3200 }
     - { name: otlp-grpc, port: 4317, targetPort: 4317 }
     - { name: otlp-http, port: 4318, targetPort: 4318 }
   ```

5. Create `k8s/infra/otel-collector/configmap.yml`:
   ```yaml
   apiVersion: v1
   kind: ConfigMap
   metadata:
     name: otel-collector-config
   data:
     config.yaml: |
       receivers:
         otlp:
           protocols:
             grpc: { endpoint: 0.0.0.0:4317 }
             http: { endpoint: 0.0.0.0:4318 }
       processors:
         batch:
           timeout: 10s
           send_batch_size: 1024
         memory_limiter:
           check_interval: 1s
           limit_mib: 96
           spike_limit_mib: 32
       exporters:
         otlp:
           endpoint: tempo:4317
           tls: { insecure: true }
       extensions:
         health_check: { endpoint: 0.0.0.0:13133 }
       service:
         extensions: [health_check]
         pipelines:
           traces:
             receivers: [otlp]
             processors: [memory_limiter, batch]
             exporters: [otlp]
   ```

6. Create `k8s/infra/otel-collector/deployment.yml`:
   ```yaml
   apiVersion: apps/v1
   kind: Deployment
   metadata:
     name: otel-collector
   spec:
     replicas: 1
     selector:
       matchLabels: { app: otel-collector }
     template:
       metadata:
         labels: { app: otel-collector }
       spec:
         containers:
         - name: collector
           image: otel/opentelemetry-collector-contrib:latest
           args: ["--config=/etc/otel-collector/config.yaml"]
           ports:
           - { name: otlp-grpc, containerPort: 4317 }
           - { name: otlp-http, containerPort: 4318 }
           - { name: health, containerPort: 13133 }
           volumeMounts:
           - { name: config, mountPath: /etc/otel-collector }
           readinessProbe:
             httpGet: { path: /, port: 13133 }
             initialDelaySeconds: 5
           resources:
             requests: { cpu: 50m, memory: 64Mi }
             limits: { cpu: 200m, memory: 128Mi }
         volumes:
         - name: config
           configMap: { name: otel-collector-config }
   ```

7. Create `k8s/infra/otel-collector/service.yml`:
   ```yaml
   apiVersion: v1
   kind: Service
   metadata:
     name: otel-collector
   spec:
     selector: { app: otel-collector }
     ports:
     - { name: otlp-grpc, port: 4317, targetPort: 4317 }
     - { name: otlp-http, port: 4318, targetPort: 4318 }
   ```

8. Edit `k8s/deploy-all.sh` — add `kubectl apply -f k8s/infra/tempo/` and `kubectl apply -f k8s/infra/otel-collector/` after loki, before app services. Order: tempo → collector → apps.

9. Apply: `kubectl apply -f k8s/infra/tempo/ && kubectl apply -f k8s/infra/otel-collector/`. Wait readiness.

## Todo List

- [ ] Create `k8s/infra/tempo/configmap.yml`
- [ ] Create `k8s/infra/tempo/pvc.yml`
- [ ] Create `k8s/infra/tempo/statefulset.yml`
- [ ] Create `k8s/infra/tempo/service.yml`
- [ ] Create `k8s/infra/otel-collector/configmap.yml`
- [ ] Create `k8s/infra/otel-collector/deployment.yml`
- [ ] Create `k8s/infra/otel-collector/service.yml`
- [ ] Update `k8s/deploy-all.sh` (insert tempo + collector before apps)
- [ ] `kubectl apply -f k8s/infra/tempo/` succeeds
- [ ] `kubectl apply -f k8s/infra/otel-collector/` succeeds
- [ ] Pods Ready

## Success Criteria

- `kubectl get pods` shows `tempo-0` and `otel-collector-*` Ready 1/1.
- `kubectl exec deploy/otel-collector -- wget -qO- http://localhost:13133/` → 200.
- From any app pod: `wget -qO- http://tempo:3200/ready` → `ready`.
- `kubectl get pvc tempo-traces` → Bound.

## Risk Assessment

- **PVC stuck Pending** if no default StorageClass. Mitigation: confirm `kubectl get sc` has default; otherwise add `storageClassName: <name>` or fall back to `emptyDir` (loses data on pod restart).
- **Tempo OOM** under load — bump `limits.memory` to 1Gi if needed.
- **Collector restart loop** if config invalid — `kubectl logs otel-collector` reveals YAML parse errors.

## Security Considerations

- No NetworkPolicies created — relies on namespace isolation. Add policies in future hardening.
- Collector + Tempo unauthenticated within cluster — internal-only via ClusterIP.
- ConfigMaps contain no secrets (no API keys).

## Next Steps

- Phase 03: app config swap (depends on this phase being green in k8s).
- Phase 04: Grafana datasource pointing at `tempo:3200`.
