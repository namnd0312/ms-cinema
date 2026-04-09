---
title: "K8s Deployment on OrbStack (No Eureka, No Config-Server, All Infra on K8s)"
description: "Deploy entire ms-cinema stack to K8s on OrbStack — infrastructure + services, per-service manifests for CI/CD"
status: completed
priority: P2
effort: 10h
branch: k8s
tags: [kubernetes, orbstack, deployment, devops, cicd]
created: 2026-04-07
updated: 2026-04-09
effort: 12h
---

# K8s OrbStack Deployment Plan

## Changes from Original Minikube Plan
1. **Minikube → OrbStack** — lighter, native macOS K8s with built-in LoadBalancer
2. **No `minikube tunnel`** — OrbStack provides native LoadBalancer IPs + `.k8s.orb.local` DNS
3. **No `eval $(minikube docker-env)`** — use OrbStack Docker context directly
4. **Eureka removed** — K8s native service discovery (DNS)
5. **Config-server removed** — K8s ConfigMap/Secret replaces centralized config
6. **All infrastructure on K8s** — PostgreSQL, Kafka, Redis, Zipkin deployed as K8s workloads
7. **Per-service K8s structure** — each service has own directory for independent CI/CD

## Scope
Deploy **everything** to OrbStack K8s: infrastructure (PostgreSQL, Kafka, Redis, Zipkin, Loki, Promtail, Grafana) + 7 services (api-gateway + 6 business) + Angular frontend. No eureka-server, no config-server.

## Architecture
- K8s Service DNS for discovery (`http://auth-service:8081`)
- api-gateway: static URI routes (no `lb://`)
- Config-server configs migrated to K8s ConfigMap (Kafka, Zipkin) and Secret (JWT)
- FeignClient (booking→movie): URL via env var
- Per-service K8s dirs for independent CI/CD
- OrbStack native LoadBalancer for external access (no tunnel needed)
- OrbStack DNS: `<service>.ms-cinema.svc.cluster.local` or `<service>.k8s.orb.local`
- PersistentVolumeClaim for PostgreSQL data
- `imagePullPolicy: Never` — images built in OrbStack Docker context

## File Structure
```
k8s/
├── base/                        # namespace, configmap, secrets
├── infra/{postgresql,kafka,redis,zipkin}/
├── {api-gateway,auth,movie,booking,payment,notification,audit}-service/
├── cinema-frontend/
├── deploy-all.sh               # OrbStack-aware deploy script
└── teardown.sh
```

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Spring Boot Code Changes](./phase-01-remove-eureka-and-config-server-code-changes.md) | completed | 1.5h |
| 2 | [K8s Infrastructure](./phase-02-k8s-infrastructure-postgresql-kafka-redis-zipkin.md) | completed | 2h |
| 3 | [K8s Base Configs & Service Manifests](./phase-03-k8s-base-configs-and-per-service-manifests.md) | completed | 2.5h |
| 4 | [Frontend & External Access](./phase-04-frontend-and-external-access.md) | completed | 30m |
| 5 | [Deploy Scripts & Testing](./phase-05-deploy-scripts-and-testing.md) | completed | 2.5h |
| 6 | [Migrate to OrbStack](./phase-06-migrate-minikube-to-orbstack.md) | completed | 1h |
| 7 | [Loki Log Aggregation](./phase-07-deploy-loki-log-aggregation.md) | completed | 2h |

## Key Decisions
- OrbStack replaces Minikube — lighter, native macOS LoadBalancer, no tunnel
- `imagePullPolicy: Never` still valid — OrbStack K8s shares Docker daemon
- K8s manifests unchanged — only deploy/teardown scripts updated
- Docker context switched to `orbstack` for builds

## Dependencies
- OrbStack (with Kubernetes enabled)
- Maven + Java 21
- No Docker Compose, no Minikube needed
