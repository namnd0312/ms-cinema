# Phase 02: Update Frontend Nginx & K8s Manifests

## Context Links
- [plan.md](plan.md)
- [Current nginx.conf](../../cinema-frontend/nginx.conf)
- [Frontend K8s deployment](../../k8s/cinema-frontend/deployment.yml)

## Overview
- **Priority:** P1 (blocking — frontend currently routes through api-gateway)
- **Status:** pending
- **Description:** Update nginx.conf for docker-compose (direct service routing) and adjust K8s frontend manifest

## Key Insights
- Current nginx.conf proxies `/api/` and `/oauth2/` to `api-gateway:8080`
- In docker-compose: nginx must route directly to each backend service
- In K8s: Ingress handles all API routing; nginx only serves Angular static files
- Single Dockerfile approach: use docker-compose nginx.conf; K8s Ingress overrides API routing externally

## Requirements

### Functional
- Docker-compose: frontend nginx proxies API calls directly to services
- K8s: frontend nginx serves only static files; Ingress handles `/api/*` routing
- WebSocket proxy to booking-service:8083 preserved in docker-compose nginx.conf
- OAuth2 routes to auth-service:8081 in docker-compose

### Non-Functional
- No downtime; change is config-only
- Backward compatible with existing docker-compose workflow

## Architecture

### Docker-compose flow
```
Browser → cinema-frontend:80 (nginx)
  /           → static files (Angular SPA)
  /api/auth/* → auth-service:8081
  /api/movies/* → movie-service:8082
  /ws/*       → booking-service:8083 (WebSocket)
  /oauth2/*   → auth-service:8081
  etc.
```

### K8s flow
```
Browser → Ingress (nginx-ingress-controller)
  /api/*  → backend services (ClusterIP)
  /ws/*   → booking-service (ClusterIP)
  /       → cinema-frontend (ClusterIP) → nginx → static files only
```

## Related Code Files

### Modify
- `cinema-frontend/nginx.conf` — replace api-gateway proxy with direct service routes (docker-compose)
- `k8s/cinema-frontend/deployment.yml` — change Service type from LoadBalancer to ClusterIP

## Implementation Steps

1. **Update `cinema-frontend/nginx.conf`** for docker-compose direct routing:
   - Replace `proxy_pass http://api-gateway:8080/api/` with individual location blocks:
     - `/api/auth/` → `http://auth-service:8081/api/auth/`
     - `/api/users/` → `http://auth-service:8081/api/users/`
     - `/api/movies/` → `http://movie-service:8082/api/movies/`
     - `/api/showtimes/` → `http://movie-service:8082/api/showtimes/`
     - `/api/theaters/` → `http://movie-service:8082/api/theaters/`
     - `/api/comments/` → `http://movie-service:8082/api/comments/`
     - `/api/bookings/` → `http://booking-service:8083/api/bookings/`
     - `/api/payments/` → `http://payment-service:8084/api/payments/`
     - `/api/notifications/` → `http://notification-service:8085/api/notifications/`
     - `/api/audit/` → `http://audit-service:8086/api/audit/`
   - Update OAuth2 routes to point to auth-service:8081 directly
   - Keep WebSocket `/ws/` pointing to booking-service:8083 (already correct)
   - Remove the catch-all `/api/` block

2. **Update `k8s/cinema-frontend/deployment.yml`**:
   - Change Service `type: LoadBalancer` → `type: ClusterIP`
   - Ingress handles external access; no need for LoadBalancer on frontend

3. **Note on K8s nginx.conf**: In K8s, Ingress routes `/api/*` before it reaches frontend pod. Frontend nginx only handles requests matching `/` (static files). The docker-compose nginx.conf proxy blocks are harmless in K8s (requests never reach them) so a single nginx.conf works for both environments.

## Todo List

- [ ] Update nginx.conf with direct service routes (replace api-gateway references)
- [ ] Keep WebSocket proxy config unchanged (already points to booking-service)
- [ ] Change frontend K8s Service from LoadBalancer to ClusterIP
- [ ] Test docker-compose: `docker compose up --build cinema-frontend` and verify API calls
- [ ] Test K8s: verify Ingress routes to backend and frontend only serves SPA

## Success Criteria
- Docker-compose: `curl http://localhost:4200/api/auth/login` reaches auth-service
- Docker-compose: WebSocket at `ws://localhost:4200/ws/` connects to booking-service
- K8s: frontend Service is ClusterIP
- K8s: Ingress routes `/api/*` to backends, `/` to frontend

## Risk Assessment
- **DNS resolution in docker-compose**: Service names (auth-service, movie-service, etc.) must be resolvable; they are since all services are on same docker network
- **Catch-all `/api/` removal**: Must ensure all API paths have explicit location blocks; missing path = 404 from nginx

## Security Considerations
- No change to auth/security; JWT validation still at service level
- Proxy headers (X-Real-IP, X-Forwarded-For) preserved in all location blocks

## Next Steps
- Phase 03: Delete api-gateway module entirely
