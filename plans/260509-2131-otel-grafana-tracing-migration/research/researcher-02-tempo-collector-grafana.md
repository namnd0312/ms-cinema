---
title: "Tempo + OTel Collector + Grafana datasource research"
date: 2026-05-09
---

## 1. Tempo Deployment Mode

**Recommendation: Monolithic** (single-binary, all components in one process).

For dev/staging scale with single replica, monolithic is appropriate. Distributed mode (separate querier/distributor/compactor) is overkill. Monolithic ships as single image `grafana/tempo:<version>` (latest stable: v1.2+).

**Key ports:**
- `3200/tcp` — HTTP query API + metrics
- `4317/tcp` — OTLP gRPC receiver
- `4318/tcp` — OTLP HTTP receiver

**Rationale:** Grafana Tempo docs recommend monolithic for single-node deployments; distributed architecture requires shared object storage (S3, GCS). Local filesystem storage is officially supported only for monolithic deployments.

---

## 2. Minimal Tempo Configuration YAML

```yaml
# tempo.yaml - monolithic mode, local filesystem backend
server:
  http_listen_port: 3200
  grpc_listen_port: 3201

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces

retention: 72h
max_trace_limit: 100000
```

**Storage notes:**
- Local filesystem backend stores traces in `/var/tempo/traces`
- Default retention 72h (sufficient for dev/staging audit trails)
- Requires RWX filesystem or PV for K8s
- No S3/object storage configuration needed (YAGNI)

**Source:** Grafana Tempo v1.2 configuration reference. Backend option `local` documented as official but production use requires SSD + shared filesystem for distributed mode.

---

## 3. OTel Collector Image Selection

**Use: `otel/opentelemetry-collector-contrib:latest`**

**Rationale:**
- `contrib` includes Loki exporter (for trace→log export if needed later)
- `contrib` includes spanmetrics processor (for generating metrics from traces)
- `contrib` is actively maintained, stable for production
- Standard `otel/opentelemetry-collector` lacks contrib exporters

**For this scope:** Contrib supports OTLP receiver (gRPC/HTTP) and OTLP exporter → Tempo out-of-box. No additional plugins needed.

---

## 4. OTel Collector Pipeline Configuration

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024
  
  memory_limiter:
    check_interval: 1s
    limit_mib: 128
  
  resource:
    attributes:
      actions:
        - key: deployment.environment
          value: staging
          action: insert

exporters:
  otlp:
    client:
      endpoint: tempo:4317
      tls:
        insecure: true
  
  logging:
    loglevel: debug

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [otlp, logging]
```

**Rationale:**
- Receivers listen on standard OTLP ports (gRPC 4317, HTTP 4318)
- Memory limiter prevents OOM on 128Mi container limit
- Batch processor reduces network calls
- Resource processor adds deployment label for filtering in Grafana
- OTLP exporter ships traces to Tempo on gRPC 4317 (no TLS for internal network)
- Logging exporter optional (debug; remove in production for KISS)

**Source:** OpenTelemetry Collector v0.90+ stable configuration API.

---

## 5. Grafana Tempo Datasource (Provisioning YAML)

```yaml
# datasources.yml - add to existing datasources list
- name: Tempo
  type: tempo
  access: proxy
  isDefault: false
  url: http://tempo:3200
  jsonData:
    tracesToLogsV2:
      datasourceUid: loki-uid
      tags:
        - key: service.name
          value: service
      spanStartTimeAttribute: startTime
      spanEndTimeAttribute: endTime
    tracesToMetrics:
      datasourceUid: prometheus-uid
      queries:
        - name: request_duration
          query: 'rate(http_server_requests_seconds_sum{service_name="$${__span.tags.service.name}"}[5m]) / rate(http_server_requests_seconds_count{service_name="$${__span.tags.service.name}"}[5m])'
    nodeGraph:
      enabled: true
    search:
      enabled: true
```

**Key fields:**
- `tracesToLogsV2` (modern, replaces deprecated `tracesToLogs`) — links traces to Loki logs via `service.name` span tag
- Assumes Loki has `service_name` or `service` label (from loki-logback-appender with MDC)
- `tracesToMetrics` — optional link to Prometheus for request rate/latency correlation
- `nodeGraph` — visualizes service topology from spans

**Loki query assumption:** Services emit logs with `service_name` label (check existing logback config; if using `app` label, adjust key).

**Source:** Grafana 11.x datasource provisioning API.

---

## 6. K8s Manifests

### Tempo StatefulSet + Service

```yaml
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: tempo-traces
  namespace: monitoring
spec:
  accessModes: [ReadWriteOnce]
  storageClassName: standard
  resources:
    requests:
      storage: 10Gi

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: tempo-config
  namespace: monitoring
data:
  tempo.yaml: |
    server:
      http_listen_port: 3200
      grpc_listen_port: 3201
    distributor:
      receivers:
        otlp:
          protocols:
            grpc:
              endpoint: 0.0.0.0:4317
            http:
              endpoint: 0.0.0.0:4318
    storage:
      trace:
        backend: local
        local:
          path: /var/tempo/traces
    retention: 72h

---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: tempo
  namespace: monitoring
spec:
  serviceName: tempo
  replicas: 1
  selector:
    matchLabels:
      app: tempo
  template:
    metadata:
      labels:
        app: tempo
    spec:
      containers:
      - name: tempo
        image: grafana/tempo:latest
        ports:
        - name: http
          containerPort: 3200
        - name: otlp-grpc
          containerPort: 4317
        - name: otlp-http
          containerPort: 4318
        volumeMounts:
        - name: config
          mountPath: /etc/tempo
        - name: traces
          mountPath: /var/tempo/traces
        resources:
          requests:
            cpu: 100m
            memory: 256Mi
          limits:
            cpu: 500m
            memory: 256Mi
      volumes:
      - name: config
        configMap:
          name: tempo-config
      - name: traces
        persistentVolumeClaim:
          claimName: tempo-traces

---
apiVersion: v1
kind: Service
metadata:
  name: tempo
  namespace: monitoring
spec:
  clusterIP: None
  selector:
    app: tempo
  ports:
  - name: http
    port: 3200
    targetPort: 3200
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
```

### OTel Collector Deployment + Service

```yaml
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: monitoring
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    processors:
      batch:
        timeout: 10s
        send_batch_size: 1024
      memory_limiter:
        check_interval: 1s
        limit_mib: 128
      resource:
        attributes:
          actions:
          - key: deployment.environment
            value: staging
            action: insert
    exporters:
      otlp:
        client:
          endpoint: tempo:4317
          tls:
            insecure: true
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch, resource]
          exporters: [otlp]

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: monitoring
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
      - name: collector
        image: otel/opentelemetry-collector-contrib:latest
        args: ["--config=/etc/otel-collector/config.yaml"]
        ports:
        - name: otlp-grpc
          containerPort: 4317
        - name: otlp-http
          containerPort: 4318
        volumeMounts:
        - name: config
          mountPath: /etc/otel-collector
        resources:
          requests:
            cpu: 50m
            memory: 128Mi
          limits:
            cpu: 200m
            memory: 128Mi
      volumes:
      - name: config
        configMap:
          name: otel-collector-config

---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  namespace: monitoring
spec:
  selector:
    app: otel-collector
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
```

---

## 7. Docker Compose Services

```yaml
services:
  tempo:
    image: grafana/tempo:latest
    ports:
      - "3200:3200"
      - "4317:4317"
      - "4318:4318"
    volumes:
      - ./tempo.yaml:/etc/tempo/tempo.yaml
      - tempo-data:/var/tempo/traces
    command: ["-config.file=/etc/tempo/tempo.yaml"]
    networks:
      - observability

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    ports:
      - "4317:4317"
      - "4318:4318"
    volumes:
      - ./otel-collector-config.yaml:/etc/otel-collector/config.yaml
    command: ["--config=/etc/otel-collector/config.yaml"]
    depends_on:
      - tempo
    networks:
      - observability

volumes:
  tempo-data:

networks:
  observability:
    driver: bridge
```

---

## 8. Networking Summary

| Service | Sender | Receiver | Port | Protocol |
|---------|--------|----------|------|----------|
| App (Spring Boot) | → | OTel Collector | 4318 | HTTP (or 4317 gRPC) |
| OTel Collector | → | Tempo | 4317 | gRPC |
| Grafana | → | Tempo | 3200 | HTTP |

**Hostname resolution:**
- Docker Compose: service name (e.g., `tempo`, `otel-collector`) auto-resolves on `observability` network
- K8s: service name with namespace suffix (e.g., `tempo.monitoring.svc.cluster.local` or just `tempo` same namespace)

---

## Unresolved Questions

1. **Loki label mismatch** — Existing loki-logback-appender: does it push `app` or `service_name` label? (Affects `tracesToLogsV2` configuration.)
2. **PV provisioning** — Does K8s cluster auto-provision `standard` StorageClass? Fallback to `hostPath` if single-node.
3. **TLS for OTLP** — Currently no TLS between Collector→Tempo. Should we add mutual TLS for staging/prod?
4. **Metrics export** — Should Collector also ship spanmetrics to Prometheus, or skip (YAGNI)?
