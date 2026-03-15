# Phase 2: Kustomize Overlays

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1: Base Manifests](./phase-01-k8s-base-manifests.md) (dependency)
- [K8s Research](./research/researcher-02-k8s-architecture-decisions.md)

## Overview
- **Date:** 2026-03-14
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- **Review status:** not started

Create Kustomize overlays for dev (cinema-dev namespace) and prod (cinema-prod namespace). Overlays patch replicas, resource limits, log levels, and environment-specific config.

## Key Insights
- Base manifests use sensible defaults (1 replica, 256Mi/512Mi)
- Dev overlay: minimal resources, debug logging, 1 replica
- Prod overlay: higher replicas for stateless services, info logging, stricter resources
- `patchesStrategicMerge` for per-service overrides; `patches` for cross-cutting concerns
- Kustomize `images` transformer handles image tag updates (Jenkins sets tag per build)

## Requirements

### Functional
- `kubectl apply -k k8s/overlays/dev/` deploys to cinema-dev namespace
- `kubectl apply -k k8s/overlays/prod/` deploys to cinema-prod namespace
- Dev: 1 replica per service, debug log level
- Prod: 2+ replicas for api-gateway/auth/movie/booking, info log level

### Non-Functional
- Overlay files minimal (only diffs from base)
- No duplication of base manifests

## Architecture

### Directory Structure
```
k8s/overlays/
  dev/
    kustomization.yaml
    namespace.yaml
    patches/
      replicas-and-resources.yaml
      logging-level.yaml
  prod/
    kustomization.yaml
    namespace.yaml
    patches/
      replicas-and-resources.yaml
      logging-level.yaml
```

### Dev Overlay Config
| Aspect | Value |
|--------|-------|
| Namespace | cinema-dev |
| Replicas | 1 (all services) |
| Memory request/limit | 256Mi / 512Mi |
| CPU request/limit | 200m / 400m |
| Log level | DEBUG |
| Kafka replicas | 1 |
| PostgreSQL storage | 2Gi |

### Prod Overlay Config
| Aspect | Value |
|--------|-------|
| Namespace | cinema-prod |
| Replicas (gateway, auth, movie, booking) | 2 |
| Replicas (payment, notification, frontend) | 1 |
| Memory request/limit | 384Mi / 768Mi |
| CPU request/limit | 250m / 750m |
| Log level | INFO |
| Kafka replicas | 3 |
| PostgreSQL storage | 10Gi |

## Related Code Files

### Files to Create
- `k8s/overlays/dev/kustomization.yaml`
- `k8s/overlays/dev/namespace.yaml`
- `k8s/overlays/dev/patches/replicas-and-resources.yaml`
- `k8s/overlays/dev/patches/logging-level.yaml`
- `k8s/overlays/prod/kustomization.yaml`
- `k8s/overlays/prod/namespace.yaml`
- `k8s/overlays/prod/patches/replicas-and-resources.yaml`
- `k8s/overlays/prod/patches/logging-level.yaml`

### Files to Modify
- None

## Implementation Steps

1. **Dev namespace:**
   a. Create `namespace.yaml` with `cinema-dev`
   b. Create `kustomization.yaml`:
      - `namespace: cinema-dev`
      - `resources: [../../base, namespace.yaml]`
      - `patches:` referencing patch files
      - `images:` section (placeholder, Jenkins fills)
   c. Create `patches/replicas-and-resources.yaml`:
      - Set all Deployments to 1 replica
      - Set memory 256Mi/512Mi, CPU 200m/400m
   d. Create `patches/logging-level.yaml`:
      - Add `LOGGING_LEVEL_ROOT=DEBUG` env var to all services

2. **Prod namespace:**
   a. Create `namespace.yaml` with `cinema-prod`
   b. Create `kustomization.yaml`:
      - `namespace: cinema-prod`
      - `resources: [../../base, namespace.yaml]`
      - `patches:` referencing patch files
      - `images:` section
   c. Create `patches/replicas-and-resources.yaml`:
      - api-gateway, auth-service, movie-service, booking-service: 2 replicas
      - payment-service, notification-service, cinema-frontend: 1 replica
      - Memory 384Mi/768Mi, CPU 250m/750m
   d. Create `patches/logging-level.yaml`:
      - Add `LOGGING_LEVEL_ROOT=INFO` env var

3. **Validate** both overlays: `kubectl kustomize k8s/overlays/dev/` and `kubectl kustomize k8s/overlays/prod/`

## Todo List

- [ ] Dev namespace manifest
- [ ] Dev kustomization.yaml
- [ ] Dev resource/replica patches
- [ ] Dev logging patch
- [ ] Prod namespace manifest
- [ ] Prod kustomization.yaml
- [ ] Prod resource/replica patches
- [ ] Prod logging patch
- [ ] Validate both overlays render correctly

## Success Criteria
- `kubectl kustomize k8s/overlays/dev/` produces valid YAML with cinema-dev namespace
- `kubectl kustomize k8s/overlays/prod/` produces valid YAML with cinema-prod namespace, correct replica counts
- Overlay files only contain diffs (DRY)
- No duplication of base manifests

## Risk Assessment
- **Patch target mismatch**: If Deployment names in patches don't match base, patch silently ignored. Mitigation: validate with `kubectl kustomize`.
- **Namespace collision**: Two envs on same cluster must use different namespaces. Handled by design.

## Security Considerations
- Prod secrets should use different values than dev (different JWT_SECRET, DB passwords)
- Consider `SealedSecrets` or external secret manager for prod (future enhancement)

## Next Steps
- Phase 3: NGINX Ingress (routes to services in correct namespace)
- Phase 5: Jenkins pipeline uses `kustomize edit set image` per overlay
