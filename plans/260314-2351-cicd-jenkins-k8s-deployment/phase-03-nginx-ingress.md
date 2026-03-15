# Phase 3: NGINX Ingress

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1: Base Manifests](./phase-01-k8s-base-manifests.md) (dependency)
- [K8s Research](./research/researcher-02-k8s-architecture-decisions.md)

## Overview
- **Date:** 2026-03-14
- **Priority:** P2
- **Status:** pending
- **Effort:** 1h
- **Review status:** not started

Create NGINX Ingress resource to route external traffic. `/api/*` goes to api-gateway service, `/` goes to cinema-frontend. SSE-specific annotations for notification streaming.

## Key Insights
- API Gateway still handles internal routing (custom filters: HttpLoggingFilter, SSE fix, OpenAPI aggregation)
- NGINX Ingress only handles external entry point (TLS, host routing)
- SSE requires `proxy_buffering: off` and increased timeouts
- Minikube has built-in ingress addon (`minikube addons enable ingress`)
- Ingress resource goes in base/ but host values patched per overlay

## Requirements

### Functional
- `http://cinema.local/api/*` routes to api-gateway:8080
- `http://cinema.local/` routes to cinema-frontend:80
- SSE endpoint (`/api/notifications/stream`) works without buffering issues
- Swagger UI accessible at `http://cinema.local/swagger-ui.html`

### Non-Functional
- Connection timeout: 60s for regular, 3600s for SSE
- Proxy buffer size adequate for Spring Boot responses

## Architecture

### Ingress Resource
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: cinema-ingress
  annotations:
    nginx.ingress.kubernetes.io/proxy-body-size: "10m"
    nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
    nginx.ingress.kubernetes.io/proxy-send-timeout: "3600"
spec:
  ingressClassName: nginx
  rules:
  - host: cinema.local
    http:
      paths:
      - path: /api
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
      - path: /swagger-ui.html
        pathType: Exact
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
      - path: /v3/api-docs
        pathType: Prefix
        backend:
          service:
            name: api-gateway
            port:
              number: 8080
      - path: /
        pathType: Prefix
        backend:
          service:
            name: cinema-frontend
            port:
              number: 80
```

### SSE-Specific Annotations
```yaml
nginx.ingress.kubernetes.io/proxy-buffering: "off"
nginx.ingress.kubernetes.io/proxy-cache: "off"
nginx.ingress.kubernetes.io/proxy-read-timeout: "3600"
```

### Per-Overlay Host Patches
- Dev: `cinema-dev.local`
- Prod: `cinema.local` (+ TLS placeholder)

## Related Code Files

### Files to Create
- `k8s/base/ingress.yaml`

### Files to Modify
- `k8s/base/kustomization.yaml` (add ingress.yaml to resources)
- `k8s/overlays/dev/kustomization.yaml` (host patch)
- `k8s/overlays/prod/kustomization.yaml` (host patch + TLS)

## Implementation Steps

1. Create `k8s/base/ingress.yaml` with NGINX Ingress resource
   - SSE annotations (proxy buffering off, high timeout)
   - Routes: `/api` -> api-gateway, `/swagger-ui.html` -> api-gateway, `/v3/api-docs` -> api-gateway, `/` -> cinema-frontend
   - Default host: `cinema.local`
2. Add `ingress.yaml` to `k8s/base/kustomization.yaml` resources
3. Add host patch to dev overlay: `cinema-dev.local`
4. Add host patch to prod overlay: `cinema.local` + TLS Secret reference (placeholder)
5. Add `/etc/hosts` entry instructions to Phase 6 setup docs

## Todo List

- [ ] Create base ingress.yaml with SSE annotations
- [ ] Add to base kustomization.yaml
- [ ] Dev overlay host patch
- [ ] Prod overlay host + TLS patch
- [ ] Validate with `kubectl kustomize`

## Success Criteria
- Ingress renders correctly in both overlays
- SSE annotations present
- Different hosts per environment
- Frontend and API routed correctly

## Risk Assessment
- **Minikube ingress addon not enabled**: Ingress resource created but no controller. Mitigation: Phase 6 setup script enables addon.
- **SSE timeout**: Default NGINX timeouts too low for SSE. Handled via annotations.
- **Path ordering**: NGINX processes longest match first; `/api` before `/` ensures API routes work.

## Security Considerations
- TLS placeholder in prod overlay (real cert via cert-manager or manual Secret)
- No sensitive data in Ingress resource
- Consider rate limiting annotations for prod

## Next Steps
- Phase 6: Setup script enables minikube ingress addon and adds /etc/hosts entry
