---
title: "K8s Minikube Full Deployment (No Eureka, No Config-Server, All Infra on K8s)"
description: "Deploy entire ms-cinema stack to K8s on Minikube - infrastructure + services, per-service manifests for CI/CD"
status: completed
priority: P2
effort: 9h
branch: master
tags: [kubernetes, minikube, deployment, devops, cicd]
created: 2026-04-07
updated: 2026-04-08
---

# K8s Minikube Full Deployment Plan

## Changes from Original Plan
1. **Eureka removed** — K8s native service discovery (DNS)
2. **Config-server removed** — K8s ConfigMap/Secret replaces centralized config
3. **All infrastructure on K8s** — PostgreSQL, Kafka, Redis, Zipkin deployed as K8s workloads
4. **Per-service K8s structure** — each service has own directory for independent CI/CD

## Scope
Deploy **everything** to Minikube: infrastructure (PostgreSQL, Kafka, Redis, Zipkin) + 7 services (api-gateway + 6 business) + Angular frontend. No eureka-server, no config-server. Optional monitoring (Prometheus, Grafana, Loki).

## Architecture
- K8s Service DNS for discovery (`http://auth-service:8081`)
- api-gateway: static URI routes (no `lb://`)
- Config-server configs migrated to K8s ConfigMap (Kafka, Zipkin) and Secret (JWT)
- FeignClient (booking→movie): URL via env var
- Per-service K8s dirs for independent CI/CD
- LoadBalancer + `minikube tunnel` for external access
- PersistentVolumeClaim for PostgreSQL data

## File Structure
```
k8s/
├── base/
│   ├── namespace.yml
│   ├── configmap.yml
│   └── secrets.yml
├── infra/
│   ├── postgresql/
│   │   └── deployment.yml      # Deployment + Service + PVC
│   ├── kafka/
│   │   └── deployment.yml      # KRaft mode, single broker
│   ├── redis/
│   │   └── deployment.yml
│   └── zipkin/
│       └── deployment.yml
├── api-gateway/
│   └── deployment.yml
├── auth-service/
│   └── deployment.yml
├── movie-service/
│   └── deployment.yml
├── booking-service/
│   └── deployment.yml
├── payment-service/
│   └── deployment.yml
├── notification-service/
│   └── deployment.yml
├── audit-service/
│   └── deployment.yml
├── cinema-frontend/
│   └── deployment.yml
├── deploy-all.sh
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

## Key Decisions
- Eureka disabled via `EUREKA_CLIENT_ENABLED=false` env var (no pom.xml changes)
- Config-server replaced: `spring.config.import` is `optional:` so services start without it
- Config-server values migrated: JWT→Secret, Kafka/Zipkin config→ConfigMap env vars
- PostgreSQL: single instance, 6 databases created via init script
- Kafka: KRaft mode (no ZooKeeper), single broker
- Redis: no auth (dev mode)
- `SPRING_PROFILES_ACTIVE=k8s` controls all K8s-specific behavior

## Resource Philosophy
**Study/research purpose** — minimize resource usage. No production-grade sizing.
- Spring Boot services: 256Mi limit (no requests specified — let K8s schedule freely)
- Infrastructure: minimal viable (PostgreSQL 256Mi, Kafka 512Mi, Redis 64Mi, Zipkin 128Mi)
- No resource requests — only limits (avoids guaranteed QoS overhead)
- Minikube: 6GB RAM, 2 CPUs sufficient

## Dependencies
- Minikube (6GB RAM, 2 CPUs sufficient for study)
- Maven + Java 21
- No Docker Compose needed — everything on K8s
