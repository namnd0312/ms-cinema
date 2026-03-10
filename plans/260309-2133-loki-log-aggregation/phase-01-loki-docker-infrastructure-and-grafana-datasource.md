---
title: "Phase 1 - Loki Docker Infrastructure & Grafana Datasource"
status: pending
priority: P1
effort: 0.5h
---

# Phase 1: Loki Docker Infrastructure & Grafana Datasource

## Context Links
- Parent plan: [plan.md](./plan.md)
- docker-compose: `docker-compose.yml`
- Grafana datasources: `monitoring/grafana/provisioning/datasources/datasources.yml`

## Overview

Add Loki Docker service to the compose stack and wire it as a Grafana datasource. No Java code changes in this phase.

## Key Insights

- Loki image: `grafana/loki:3.0.0` — stable, lightweight
- Loki default HTTP port: `3100`
- loki4j appender pushes logs via HTTP to `http://loki:3100` (Docker network)
- Grafana needs Loki datasource provisioned so dashboards can query logs
- Use filesystem storage (simplest, no S3 needed for dev)
- Loki config: minimal — just filesystem chunks + index, single-tenant mode

## Requirements

- Loki accessible at `http://loki:3100` within `my-net` Docker network
- Grafana auto-provisions Loki datasource on startup
- Loki data persisted via named Docker volume
- Retention: 7 days (matches Prometheus)

## Architecture

```
[Spring Services] --loki4j HTTP push--> [loki:3100]
                                              |
[Grafana :3000] <--LogQL queries-------------|
```

## Related Code Files

- `docker-compose.yml` — add loki service + volume
- `monitoring/loki/loki-config.yml` — new file
- `monitoring/grafana/provisioning/datasources/datasources.yml` — add Loki entry

## Implementation Steps

### Step 1: Create Loki config file

Create `monitoring/loki/loki-config.yml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

limits_config:
  retention_period: 168h  # 7 days
```

### Step 2: Add Loki service to docker-compose.yml

Add after `grafana` service:
```yaml
  loki:
    image: grafana/loki:3.0.0
    ports:
      - "3100:3100"
    volumes:
      - ./monitoring/loki/loki-config.yml:/etc/loki/loki-config.yml:ro
      - loki-data:/loki
    command: -config.file=/etc/loki/loki-config.yml
    networks:
      - my-net
    restart: unless-stopped
```

Also add `loki-data:` to the `volumes:` section.

Update `grafana` service to depend on loki:
```yaml
    depends_on:
      - prometheus
      - loki
```

### Step 3: Add Loki datasource to Grafana provisioning

Update `monitoring/grafana/provisioning/datasources/datasources.yml`:
```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    access: proxy
    url: http://prometheus:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki:3100
    isDefault: false
    editable: true
    jsonData:
      maxLines: 1000
```

## Todo List

- [ ] Create `monitoring/loki/loki-config.yml`
- [ ] Add `loki` service to `docker-compose.yml`
- [ ] Add `loki-data` volume to `docker-compose.yml`
- [ ] Update grafana `depends_on` to include loki
- [ ] Add Loki datasource to `datasources.yml`

## Success Criteria

- `docker-compose up` starts Loki on port 3100 without errors
- `curl http://localhost:3100/ready` returns `ready`
- Grafana shows Loki datasource in Data Sources UI

## Risk Assessment

- Loki cold start may take ~5s — services should handle connection retries (loki4j batches anyway)
- Volume permissions on macOS: no issue with named volumes

## Security Considerations

- `auth_enabled: false` — acceptable for local/dev; for prod add basic auth
- Loki port 3100 exposed on host for debugging only; services communicate via Docker network

## Next Steps

→ Phase 2: Add loki4j appender to all 6 services
