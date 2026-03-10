# Phase 04: Gateway Aggregation

## Context Links
- [Plan overview](./plan.md)
- [Phase 03](./phase-03-controller-annotations.md)

## Overview
- **Priority:** Medium
- **Status:** Pending
- **Description:** Configure API Gateway to aggregate Swagger docs from all downstream services into a single Swagger UI with a service selector dropdown.

## Key Insights
- API Gateway uses `spring-cloud-starter-gateway-mvc` (servlet-based)
- SpringDoc supports gateway aggregation via `springdoc.swagger-ui.urls` config
- Gateway needs routes to forward `/v3/api-docs` requests to each service
- Users select a service from dropdown to view its docs

## Requirements

### Functional
- Swagger UI at `http://localhost:8080/swagger-ui.html` shows all services
- Dropdown selector to switch between: Auth, Movie, Booking, Payment
- Each service's docs loaded via gateway routes from `/v3/api-docs/{service}`

### Non-functional
- No new Java code needed — YAML config only
- Works with Eureka service discovery (lb:// URIs)

## Architecture

### Request Flow
```
Browser → Gateway:8080/swagger-ui.html → Gateway Swagger UI
  ↓ (user selects service from dropdown)
Gateway:8080/{service}/v3/api-docs → lb://{service}/v3/api-docs
  ↓
Service responds with OpenAPI JSON
```

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `api-gateway/src/main/resources/application.yml` | Add api-docs routes + springdoc aggregation config |

## Implementation Steps

### 1. Add v3/api-docs routes to gateway
Add routes for each service's API docs path. Use path prefix rewrite to strip the service prefix.

```yaml
spring:
  cloud:
    gateway:
      mvc:
        routes:
          # ... existing routes ...

          # Swagger api-docs routes
          - id: auth-service-docs
            uri: lb://auth-service
            predicates:
              - Path=/auth-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: movie-service-docs
            uri: lb://movie-service
            predicates:
              - Path=/movie-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: booking-service-docs
            uri: lb://booking-service
            predicates:
              - Path=/booking-service/v3/api-docs/**
            filters:
              - StripPrefix=1
          - id: payment-service-docs
            uri: lb://payment-service
            predicates:
              - Path=/payment-service/v3/api-docs/**
            filters:
              - StripPrefix=1
```

### 2. Add springdoc aggregation config
```yaml
springdoc:
  swagger-ui:
    urls:
      - name: Auth Service
        url: /auth-service/v3/api-docs
      - name: Movie Service
        url: /movie-service/v3/api-docs
      - name: Booking Service
        url: /booking-service/v3/api-docs
      - name: Payment Service
        url: /payment-service/v3/api-docs
```

## Todo List
- [ ] Add api-docs gateway routes for all 4 services
- [ ] Add springdoc.swagger-ui.urls aggregation config
- [ ] Verify gateway compiles: `cd api-gateway && mvn compile`

## Success Criteria
- `http://localhost:8080/swagger-ui.html` loads with service dropdown
- Selecting each service loads its OpenAPI docs
- All endpoints visible and interactive through gateway

## Risk Assessment
- **StripPrefix filter:** Must strip exactly 1 prefix segment (service name) before forwarding
- **CORS:** May need CORS config if Swagger UI JS makes cross-origin calls — should be fine since all goes through gateway
- **Eureka dependency:** Services must be registered for `lb://` routes to resolve

## Security Considerations
- Gateway Swagger UI is unauthenticated — disable in production via `springdoc.api-docs.enabled=false`

## Next Steps
- Phase 05: Verification
