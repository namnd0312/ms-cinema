# Phase 2: Prometheus Infrastructure Setup

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1: Micrometer Setup](./phase-01-micrometer-actuator-setup.md)
- [Prometheus Research](./reports/researcher-01-prometheus-micrometer.md)

## Overview
- **Priority:** P1 (blocking for Phase 3)
- **Status:** Pending
- **Description:** Create Prometheus configuration file with static scrape targets for all 7 services, add Prometheus service to docker-compose.yml.

## Key Insights
- Use static targets — all services have fixed hostnames in Docker Compose
- Scrape interval 15s (global default), metrics_path `/actuator/prometheus`
- Prometheus uses internal Docker service names as hostnames (e.g., `auth-service:8081`)
- Storage via named Docker volume for persistence across restarts

## Requirements
- Prometheus scrapes all 7 services every 15 seconds
- Prometheus UI accessible at `http://localhost:9090`
- Config file at `monitoring/prometheus/prometheus.yml`
- Prometheus joins existing `my-net` Docker network

## Related Code Files

### Files to CREATE
| File | Purpose |
|------|---------|
| `monitoring/prometheus/prometheus.yml` | Scrape configuration |

### Files to MODIFY
| File | Change |
|------|--------|
| `docker-compose.yml` | Add prometheus service + volume |

## Implementation Steps

### Step 1: Create prometheus.yml

Create `monitoring/prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'api-gateway'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['api-gateway:8080']

  - job_name: 'auth-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['auth-service:8081']

  - job_name: 'movie-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['movie-service:8082']

  - job_name: 'booking-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['booking-service:8083']

  - job_name: 'payment-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['payment-service:8084']

  - job_name: 'eureka-server'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['eureka-server:8761']

  - job_name: 'config-server'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['config-server:8888']
```

### Step 2: Add Prometheus to docker-compose.yml

Add to `services:` section:

```yaml
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=7d'
    networks:
      - my-net
    restart: unless-stopped
```

Add to `volumes:` section:
```yaml
  prometheus-data:
```

## Todo List
- [ ] Create `monitoring/prometheus/` directory
- [ ] Create `monitoring/prometheus/prometheus.yml` with all 7 scrape targets
- [ ] Add prometheus service to docker-compose.yml
- [ ] Add prometheus-data volume to docker-compose.yml

## Success Criteria
- `docker compose config` validates without errors
- Prometheus starts and shows all 7 targets as UP at `http://localhost:9090/targets`
- PromQL query `up` returns 1 for all scrape targets

## Risk Assessment
- **Port conflict:** 9090 may conflict with local services — acceptable for dev
- **Service startup order:** Prometheus may start before services are ready — OK, it retries on each scrape interval

## Security Considerations
- Prometheus UI exposed at :9090 on host — acceptable for development
- Config mounted read-only (`:ro`)
- No auth on Prometheus UI — production would need reverse proxy with auth

## Next Steps
- Phase 3: Grafana setup (depends on Prometheus being available)
