# Phase 4: API Gateway — Add Notification-Service Routes

## Context Links
- [api-gateway application.yml](../../api-gateway/src/main/resources/application.yml)
- [SSE Research — Gateway section](./research/researcher-sse-spring-boot.md)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1 (blocking Phase 5 frontend)
- **Status:** pending
- **Effort:** 15m

Add routes in API Gateway to proxy notification-service REST + SSE endpoints. Gateway is servlet-based (Spring Cloud Gateway MVC), which has known buffering issues with SSE streaming. Mitigated by heartbeat + client-side reconnect.

## Key Insights
- Gateway uses `spring.cloud.gateway.mvc.routes` (servlet, NOT reactive)
- SSE through servlet gateway may buffer first event until second arrives — heartbeat mitigates
- All other services follow pattern: `Path=/api/{service}/**` → `lb://{service-name}`
- notification-service already registers with Eureka → `lb://notification-service` works
- SSE connections are long-lived; gateway default timeout may kill them — no explicit timeout config needed since gateway MVC forwards as standard HTTP

## Requirements

### Functional
- Route `GET /api/notifications/**` → notification-service (REST + SSE endpoints)
- Swagger docs route `GET /notification-service/v3/api-docs/**` → notification-service

### Non-functional
- SSE streaming must work through gateway (accept potential first-event buffering)

## Architecture

```
Frontend → GET /api/notifications/stream?token=JWT → API Gateway (8080)
  → lb://notification-service → notification-service (8085)
  → SseEmitter response streamed back

Frontend → GET /api/notifications?page=0 → API Gateway
  → lb://notification-service → standard REST response
```

## Related Code Files

### Files to Modify
1. `api-gateway/src/main/resources/application.yml` — add notification routes

### Files Unchanged
- All Java files in api-gateway — no code changes needed

## Implementation Steps

### Step 1: Add notification-service route

Add to `spring.cloud.gateway.mvc.routes` in `api-gateway/src/main/resources/application.yml`:

```yaml
- id: notification-service
  uri: lb://notification-service
  predicates:
    - Path=/api/notifications/**
```

Place after the payment-service route, before swagger doc routes.

### Step 2: Add notification-service swagger docs route

Add to the swagger docs section:

```yaml
- id: notification-service-docs
  uri: lb://notification-service
  predicates:
    - Path=/notification-service/v3/api-docs/**
  filters:
    - StripPrefix=1
```

### Step 3: Add notification-service swagger URL

Add to `springdoc.swagger-ui.urls`:

```yaml
- name: Notification Service
  url: /notification-service/v3/api-docs
```

### Step 4: Verify gateway compiles

```bash
mvn clean compile -pl api-gateway
```

## Todo List
- [ ] Add notification-service route to gateway
- [ ] Add notification-service swagger docs route
- [ ] Add notification-service swagger UI URL
- [ ] Verify compilation

## Success Criteria
- `GET http://localhost:8080/api/notifications/stream?token=JWT` proxied to notification-service
- `GET http://localhost:8080/api/notifications` proxied to notification-service
- Swagger UI shows Notification Service docs

## Risk Assessment
- **SSE buffering through servlet gateway**: Known issue; heartbeat every 30s pushes buffered data through. Client reconnects on timeout. Acceptable for current scale.
- **Upgrade path**: If buffering becomes problematic, options: (a) direct SSE to notification-service bypassing gateway, (b) migrate gateway to reactive

## Security Considerations
- SSE endpoint uses JWT in query param — gateway passes through transparently
- REST endpoints use Authorization header — gateway passes through transparently
- No special CORS config needed (frontend already proxied through same origin)

## Next Steps
- Phase 5: Angular frontend connects to SSE via gateway
