---
title: "Remove API Gateway, Use K8s Ingress"
description: "Delete Spring API Gateway module and replace routing with Kubernetes Ingress resource"
status: pending
priority: P2
effort: 2h
branch: k8s
tags: [refactor, k8s, ingress, api-gateway]
created: 2026-04-09
---

# Remove API Gateway, Use K8s Ingress

## Summary

Replace Spring Cloud Gateway MVC module with a single K8s NGINX Ingress resource. API Gateway features (route aggregation, Swagger UI, HTTP logging) are either unnecessary in K8s or already handled by services individually. This simplifies infrastructure, reduces memory footprint (~512Mi), and aligns with cloud-native patterns.

## Phases

| # | Phase | Status | Effort | Details |
|---|-------|--------|--------|---------|
| 1 | Create K8s Ingress resource | pending | 30m | [phase-01](phase-01-create-k8s-ingress.md) |
| 2 | Update frontend nginx + K8s manifests | pending | 30m | [phase-02](phase-02-update-frontend-nginx-and-k8s.md) |
| 3 | Remove API Gateway module | pending | 30m | [phase-03](phase-03-remove-api-gateway.md) |
| 4 | Update docs | pending | 30m | [phase-04](phase-04-update-docs.md) |

## Key Decisions

- **NGINX Ingress Controller** — standard K8s ingress, OrbStack/Minikube compatible
- **Docker-compose**: nginx.conf routes directly to services (no gateway)
- **K8s**: Ingress handles external routing; frontend nginx only serves static files
- **Swagger**: No aggregation — access each service individually
- **CORS**: Already handled at service level (Spring Security)
- **WebSocket**: nginx-ingress supports WS natively via annotations
- **Frontend Service type**: ClusterIP (Ingress handles external access)

## Dependencies

- NGINX Ingress Controller enabled in cluster (`minikube addons enable ingress` / OrbStack built-in)
- All 6 backend services already have ClusterIP Services in K8s

## Risk

- **WebSocket upgrade**: Needs `nginx.ingress.kubernetes.io/proxy-read-timeout` annotation for long-lived WS connections
- **OAuth2 callback URLs**: Must update OAuth2 provider redirect URIs to match Ingress hostname
- **SSE streaming**: Ingress must not buffer SSE responses (`proxy-buffering: "off"` annotation on notification paths)
