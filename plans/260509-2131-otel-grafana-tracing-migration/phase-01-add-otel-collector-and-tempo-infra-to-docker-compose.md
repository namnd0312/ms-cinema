# Phase 01 — Add OTel Collector + Tempo to docker-compose

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Research: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/research/researcher-02-tempo-collector-grafana.md` (sections 2, 4, 7)
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (section 3)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docker-compose.yml`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/`

## Overview

- Date: 2026-05-09
- Priority: P1 (blocking — apps need a target before phase 03)
- Status: pending
- Review: not-started
- Description: Add `tempo` + `otel-collector` services to docker-compose; provide config files at `monitoring/tempo/tempo.yaml` + `monitoring/otel-collector/otel-collector-config.yaml`. Zipkin stays running in parallel (removed in phase 05).

## Key Insights

- Tempo monolithic, local FS, single replica — sufficient for dev/staging.
- Collector contrib image required (Loki/spanmetrics future-proof). 
- Spring Boot will hit collector on port 4318 (HTTP). Collector ships to Tempo on 4317 (gRPC) internally.
- Compose service names auto-resolve via DNS on shared network. Use existing default network (no need for new `observability` network — keep KISS).

## Requirements

**Functional**
- Tempo reachable at `http://tempo:3200` (Grafana) and `tempo:4317` (collector).
- Collector reachable at `otel-collector:4318` (apps).
- Tempo persists traces across restarts via named volume.

**Non-functional**
- Tempo memory ≤ 512Mi; Collector ≤ 128Mi.
- Configs mounted read-only from `monitoring/`.
- Healthchecks: Tempo `/ready`, Collector `:13133/`.

## Architecture

```
docker-compose
├── tempo (grafana/tempo:latest)         3200/4317/4318 → vol tempo-data
├── otel-collector (contrib:latest)      4317/4318 → tempo:4317
└── (existing) zipkin still up           — removed in phase 05
```

## Related Code Files

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docker-compose.yml`

**Create**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/tempo/tempo.yaml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/otel-collector/otel-collector-config.yaml`

**Delete**
- none (zipkin removed in phase 05)

## Implementation Steps

1. Create `monitoring/tempo/tempo.yaml`:
   ```yaml
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
       wal:
         path: /var/tempo/wal
   compactor:
     compaction:
       block_retention: 72h
   ```

2. Create `monitoring/otel-collector/otel-collector-config.yaml`:
   ```yaml
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
       limit_mib: 96
       spike_limit_mib: 32
   exporters:
     otlp:
       endpoint: tempo:4317
       tls:
         insecure: true
   extensions:
     health_check:
       endpoint: 0.0.0.0:13133
   service:
     extensions: [health_check]
     pipelines:
       traces:
         receivers: [otlp]
         processors: [memory_limiter, batch]
         exporters: [otlp]
   ```

3. Edit `docker-compose.yml` — add services (place near `loki`/`grafana` block):
   ```yaml
     tempo:
       image: grafana/tempo:latest
       container_name: tempo
       command: ["-config.file=/etc/tempo/tempo.yaml"]
       volumes:
         - ./monitoring/tempo/tempo.yaml:/etc/tempo/tempo.yaml:ro
         - tempo-data:/var/tempo
       ports:
         - "3200:3200"
       healthcheck:
         test: ["CMD", "wget", "-qO-", "http://localhost:3200/ready"]
         interval: 10s
         timeout: 3s
         retries: 5

     otel-collector:
       image: otel/opentelemetry-collector-contrib:latest
       container_name: otel-collector
       command: ["--config=/etc/otel-collector/config.yaml"]
       volumes:
         - ./monitoring/otel-collector/otel-collector-config.yaml:/etc/otel-collector/config.yaml:ro
       ports:
         - "4317:4317"
         - "4318:4318"
         - "13133:13133"
       depends_on:
         tempo:
           condition: service_healthy
   ```

4. Add named volume to `docker-compose.yml` `volumes:` section:
   ```yaml
     tempo-data:
   ```

5. Add `otel-collector` to grafana `depends_on` (so dashboards load after target up). Tempo too.

6. Run `docker compose config` to validate syntax.

7. Run `docker compose up -d tempo otel-collector` and verify both healthy.

## Todo List

- [ ] Create `monitoring/tempo/tempo.yaml`
- [ ] Create `monitoring/otel-collector/otel-collector-config.yaml`
- [ ] Append `tempo` service to `docker-compose.yml`
- [ ] Append `otel-collector` service to `docker-compose.yml`
- [ ] Add `tempo-data` named volume
- [ ] Update grafana `depends_on` to include `tempo`, `otel-collector`
- [ ] `docker compose config` passes
- [ ] `docker compose up -d tempo otel-collector` both healthy

## Success Criteria

- `curl http://localhost:3200/ready` → `ready`
- `curl http://localhost:13133/` → 200 (collector health)
- `curl -X POST http://localhost:4318/v1/traces -H 'Content-Type: application/json' -d '{"resourceSpans":[]}'` → 200
- `docker compose ps tempo otel-collector` → both `healthy`/`running`

## Risk Assessment

- **Port collision** with existing zipkin (9411 unused by tempo, no conflict). Collector 4317/4318 free. Mitigation: `lsof -i :4318` pre-check.
- **Volume permission errors** on macOS — Tempo runs as UID 10001. Mitigation: use named volume (handled by Docker).
- **Latest tag drift** — pin versions later (`grafana/tempo:2.6.0`, `otel/opentelemetry-collector-contrib:0.115.0`) once stable.

## Security Considerations

- OTLP endpoints `0.0.0.0:4317/4318` — bound to all interfaces inside container; host port mapping exposes to localhost only. Internal-network only in compose.
- Collector has no auth — acceptable for dev. Production hardening deferred.
- Tempo unauthenticated — fronted by Grafana proxy in normal use; direct port `3200` exposed for debugging only.

## Next Steps

- Phase 02: parallel k8s manifests for same components.
- Phase 03 (after 01 verified): point Spring Boot apps at `otel-collector:4318`.
