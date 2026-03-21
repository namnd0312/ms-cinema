# Phase 02: Configure Tracing Properties

## Context Links
- [Plan overview](plan.md)
- [Phase 01 - Dependencies](phase-01-add-tracing-dependencies.md)
- [Config-server shared config](/config-server/src/main/resources/config-repo/application.yml)
- [Auth-service application.yml](/auth-service/src/main/resources/application.yml) (reference pattern)
- [Auth-service logback-spring.xml](/auth-service/src/main/resources/logback-spring.xml) (reference pattern)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Configure tracing properties (Zipkin endpoint, sampling rate) centrally in config-server + update logback to include traceId/spanId in structured logs.

## Key Insights
- Config-server `config-repo/application.yml` is shared by ALL services -- tracing config goes here once (DRY)
- Spring Boot 3.x tracing properties: `management.tracing.*` and `management.zipkin.tracing.*`
- LogstashEncoder already includes MDC fields; Micrometer auto-populates `traceId` and `spanId` in MDC
- Loki appender uses `includeMdcProperties=true` -- traceId/spanId automatically appear in Loki logs (zero change)
- eureka-server and config-server don't import from config-server, need local config in their own application.yml

## Requirements
- **Functional:** All services send spans to Zipkin; traceId/spanId appear in log entries
- **Non-functional:** Sampling 1.0 (100%) for dev; configurable via env var for prod

## Architecture

```
config-server/config-repo/application.yml (shared)
  -> management.tracing.sampling.probability=1.0
  -> management.zipkin.tracing.endpoint=http://zipkin:9411/api/v2/spans

eureka-server/application.yml (local, self-configured)
config-server/application.yml (local, self-configured)
  -> same tracing properties with localhost/env-var fallback
```

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `config-server/src/main/resources/config-repo/application.yml` | Add shared tracing config for all downstream services |
| `eureka-server/src/main/resources/application.yml` | Add local tracing config (doesn't use config-server) |
| `config-server/src/main/resources/application.yml` | Add local tracing config (doesn't use config-server) |

### Files NOT Modified
- Individual service `application.yml` files -- they inherit from config-server
- `logback-spring.xml` files -- LogstashEncoder + Loki appender already include MDC (traceId/spanId auto-populated)

## Implementation Steps

### Step 1: Add tracing config to config-server shared application.yml

Append to `config-server/src/main/resources/config-repo/application.yml`:

```yaml
# Distributed tracing: Micrometer -> OpenTelemetry -> Zipkin
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
```

Note: Merge with existing `management:` block if present in per-service configs. Currently no `management:` block exists in shared config (each service defines its own actuator config locally).

**Important:** Since each service already has a `management:` block in its local `application.yml` (for actuator endpoints), Spring will merge the shared config-server properties with local properties. No conflict -- they configure different sub-keys.

### Step 2: Add tracing config to eureka-server application.yml

Append to `eureka-server/src/main/resources/application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
```

### Step 3: Add tracing config to config-server application.yml

Append to `config-server/src/main/resources/application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
```

### Step 4: Add ZIPKIN_HOST env var to docker-compose.yml

For each service in `docker-compose.yml`, add `ZIPKIN_HOST: zipkin` to the `environment` block. This will be done in Phase 03 alongside the Zipkin container setup.

### Step 5: Verify log output includes traceId

After starting a service locally, make an HTTP request and confirm log JSON output contains `traceId` and `spanId` fields. LogstashEncoder includes all MDC keys automatically.

## Todo List
- [ ] Add tracing properties to config-repo/application.yml (shared)
- [ ] Add tracing properties to eureka-server/application.yml (local)
- [ ] Add tracing properties to config-server/application.yml (local)
- [ ] Verify Spring property merge works (shared + local management blocks)
- [ ] Confirm traceId/spanId appear in LogstashEncoder JSON output (no logback changes needed)

## Success Criteria
- All services resolve `management.tracing.sampling.probability` and `management.zipkin.tracing.endpoint`
- Log entries contain `traceId` and `spanId` fields in JSON output
- Same traceId propagates across HTTP calls (gateway -> service, Feign client)
- Sampling probability configurable via `TRACING_SAMPLING_PROBABILITY` env var

## Risk Assessment
- **Low:** Config property merge -- Spring Boot merges YAML from config-server + local seamlessly
- **Low:** LogstashEncoder already includes MDC; Micrometer auto-sets traceId/spanId in MDC
- **Mitigation:** If traceId missing in logs, add explicit `%X{traceId}` to pattern (unlikely needed)

## Security Considerations
- Zipkin endpoint is internal Docker network only; not exposed to public
- Trace data may contain request paths -- no sensitive payload data by default
- Sampling rate of 1.0 acceptable for dev; reduce for prod to limit data volume

## Next Steps
- Proceed to [Phase 03](phase-03-add-zipkin-infrastructure.md) for Zipkin container + Docker env vars
