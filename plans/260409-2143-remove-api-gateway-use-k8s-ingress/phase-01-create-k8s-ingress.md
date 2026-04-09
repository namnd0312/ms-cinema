# Phase 01: Create K8s Ingress Resource

## Context Links
- [plan.md](plan.md)
- [K8s base configs](../../k8s/base/)
- [API Gateway routes](../../api-gateway/src/main/resources/application-k8s.yml)

## Overview
- **Priority:** P1 (blocking — must exist before gateway removal)
- **Status:** pending
- **Description:** Create `k8s/ingress.yml` with NGINX Ingress path-based routing for all services

## Key Insights
- Spring Gateway routed 6 backend services + Swagger docs + OAuth2 + WebSocket
- K8s Ingress can handle all HTTP/WS routing natively
- WebSocket needs explicit timeout annotations (default 60s too short)
- SSE (`/api/notifications/stream`) needs proxy-buffering disabled

## Requirements

### Functional
- Route `/api/auth/**`, `/api/users/**` → auth-service:8081
- Route `/oauth2/authorization/**`, `/login/oauth2/code/**` → auth-service:8081
- Route `/api/movies/**`, `/api/showtimes/**`, `/api/theaters/**`, `/api/comments/**` → movie-service:8082
- Route `/ws/**`, `/api/bookings/**` → booking-service:8083
- Route `/api/payments/**` → payment-service:8084
- Route `/api/notifications/**` → notification-service:8085
- Route `/api/audit/**` → audit-service:8086
- Route `/` (default) → cinema-frontend:80

### Non-Functional
- WebSocket connections stay alive (86400s timeout)
- SSE streams not buffered
- Ingress works on OrbStack K8s (ingress-nginx built-in)

## Architecture

```
                    Internet / localhost
                          │
                 ┌────────▼────────┐
                 │  NGINX Ingress  │
                 │  Controller     │
                 └────────┬────────┘
                          │ path-based routing
        ┌─────┬─────┬─────┼─────┬─────┬─────┐
        ▼     ▼     ▼     ▼     ▼     ▼     ▼
      auth  movie booking pay  notif audit frontend
      :8081 :8082 :8083  :8084 :8085 :8086  :80
```

## Related Code Files

### Create
- `k8s/ingress.yml` — Ingress resource with all routing rules

## Implementation Steps

1. Create `k8s/ingress.yml` with:
   - `apiVersion: networking.k8s.io/v1`, `kind: Ingress`
   - `metadata.namespace: ms-cinema`
   - `metadata.annotations`:
     - `nginx.ingress.kubernetes.io/proxy-read-timeout: "86400"` (WebSocket)
     - `nginx.ingress.kubernetes.io/proxy-send-timeout: "86400"`
     - `nginx.ingress.kubernetes.io/proxy-buffering: "off"` (SSE)
     - `nginx.ingress.kubernetes.io/use-regex: "true"`
   - `spec.ingressClassName: nginx`
   - Path rules (Prefix type):
     - `/api/auth` → auth-service:8081
     - `/api/users` → auth-service:8081
     - `/oauth2` → auth-service:8081
     - `/login/oauth2` → auth-service:8081
     - `/api/movies` → movie-service:8082
     - `/api/showtimes` → movie-service:8082
     - `/api/theaters` → movie-service:8082
     - `/api/comments` → movie-service:8082
     - `/ws` → booking-service:8083
     - `/api/bookings` → booking-service:8083
     - `/api/payments` → payment-service:8084
     - `/api/notifications` → notification-service:8085
     - `/api/audit` → audit-service:8086
     - `/` (default backend) → cinema-frontend:80

2. Add WebSocket-specific snippet annotation for `/ws` path if global timeout annotations cause issues with regular HTTP paths (optional optimization)

3. Update `k8s/deploy-all.sh` to apply ingress after services are deployed (add step between 6/7 and 7/7 or after frontend)

## Todo List

- [ ] Create `k8s/ingress.yml` with all path rules
- [ ] Add WebSocket timeout annotations
- [ ] Add SSE proxy-buffering annotation
- [ ] Update `k8s/deploy-all.sh` to apply ingress
- [ ] Test with `kubectl apply -f k8s/ingress.yml`
- [ ] Verify routing via `curl` to each path

## Success Criteria
- `kubectl get ingress -n ms-cinema` shows ingress with all paths
- HTTP requests to `/api/auth/login` reach auth-service
- WebSocket handshake at `/ws/` succeeds to booking-service
- SSE at `/api/notifications/stream` streams without buffering
- Frontend loads at `/`

## Risk Assessment
- **Ingress controller not installed**: OrbStack includes nginx-ingress by default; Minikube needs `minikube addons enable ingress`
- **Path conflicts**: Prefix matching may overlap; order paths from most-specific to least-specific
- **Timeout annotations global**: 86400s timeout applies to all paths; acceptable for microservice backends

## Security Considerations
- No TLS configured (local dev); for prod, add `tls` section with cert-manager
- JWT validation remains at service level (unchanged)
- CORS handled by Spring Security in each service (unchanged)

## Next Steps
- Phase 02: Update frontend nginx.conf for docker-compose (no gateway) and K8s (static-only)
