# Phase 03: Add Zipkin Infrastructure

## Context Links
- [Plan overview](plan.md)
- [Phase 02 - Config](phase-02-configure-tracing-properties.md)
- [docker-compose.yml](/docker-compose.yml)
- [Grafana datasources](/monitoring/grafana/provisioning/datasources/datasources.yml)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add Zipkin container to docker-compose.yml, wire ZIPKIN_HOST env var to all services, add Zipkin datasource to Grafana provisioning.

## Key Insights
- `openzipkin/zipkin:latest` runs in-memory by default (no external DB needed for dev)
- Port 9411 for Zipkin UI + API
- All services need `ZIPKIN_HOST: zipkin` env var in docker-compose
- Grafana supports Zipkin as a built-in datasource type (no plugin needed)
- Zipkin container has no dependencies -- can start independently

## Requirements
- **Functional:** Zipkin container running, all services export spans to it, Grafana can query Zipkin
- **Non-functional:** Zipkin should start before business services (depends_on not strictly needed since exporters retry)

## Architecture

```
docker-compose.yml:
  zipkin:
    image: openzipkin/zipkin:latest
    ports: 9411:9411
    networks: my-net

  all services:
    environment:
      ZIPKIN_HOST: zipkin

  grafana:
    datasources: + Zipkin (http://zipkin:9411)
```

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `docker-compose.yml` | Add zipkin service, add ZIPKIN_HOST to all 8 services |
| `monitoring/grafana/provisioning/datasources/datasources.yml` | Add Zipkin datasource |

## Implementation Steps

### Step 1: Add Zipkin container to docker-compose.yml

Add after `redis-service` block (infrastructure section):

```yaml
  zipkin:
    image: openzipkin/zipkin:latest
    ports:
      - "9411:9411"
    networks:
      - my-net
    restart: unless-stopped
```

### Step 2: Add ZIPKIN_HOST env var to all service containers

Add `ZIPKIN_HOST: zipkin` to `environment` block of each service:

| Service | Add to environment |
|---------|-------------------|
| `eureka-server` | `ZIPKIN_HOST: zipkin` |
| `config-server` | `ZIPKIN_HOST: zipkin` |
| `api-gateway` | `ZIPKIN_HOST: zipkin` |
| `auth-service` | `ZIPKIN_HOST: zipkin` |
| `movie-service` | `ZIPKIN_HOST: zipkin` |
| `booking-service` | `ZIPKIN_HOST: zipkin` |
| `payment-service` | `ZIPKIN_HOST: zipkin` |
| `notification-service` | `ZIPKIN_HOST: zipkin` |

### Step 3: Add Zipkin datasource to Grafana provisioning

Append to `monitoring/grafana/provisioning/datasources/datasources.yml`:

```yaml
  - name: Zipkin
    type: zipkin
    uid: zipkin
    access: proxy
    url: http://zipkin:9411
    isDefault: false
    editable: true
```

### Step 4: Verify docker-compose validity

```bash
docker-compose config --quiet
```

## Todo List
- [ ] Add zipkin service to docker-compose.yml
- [ ] Add ZIPKIN_HOST env var to eureka-server
- [ ] Add ZIPKIN_HOST env var to config-server
- [ ] Add ZIPKIN_HOST env var to api-gateway
- [ ] Add ZIPKIN_HOST env var to auth-service
- [ ] Add ZIPKIN_HOST env var to movie-service
- [ ] Add ZIPKIN_HOST env var to booking-service
- [ ] Add ZIPKIN_HOST env var to payment-service
- [ ] Add ZIPKIN_HOST env var to notification-service
- [ ] Add Zipkin datasource to Grafana provisioning
- [ ] Run docker-compose config to validate YAML

## Success Criteria
- `docker-compose up zipkin` starts Zipkin, accessible at http://localhost:9411
- All services connect to `http://zipkin:9411/api/v2/spans` in Docker network
- Grafana datasources page shows Zipkin alongside Prometheus and Loki
- Zipkin datasource test connection succeeds in Grafana

## Risk Assessment
- **Low:** Zipkin in-memory storage loses data on restart -- acceptable for dev
- **Mitigation:** For prod, configure Zipkin with Elasticsearch or Cassandra backend (out of scope)
- **Low:** Zipkin unavailable doesn't crash services -- OTel exporter retries silently

## Security Considerations
- Zipkin UI exposed on port 9411 -- dev only, no auth by default
- For prod: restrict Zipkin port to internal network, add reverse proxy with auth
- Trace data contains request paths and service names only (no request/response bodies)

## Next Steps
- Proceed to [Phase 04](phase-04-verify-and-test-tracing.md) for end-to-end verification
