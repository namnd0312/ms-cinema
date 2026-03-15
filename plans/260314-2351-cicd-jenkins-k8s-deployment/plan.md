---
title: "CI/CD Jenkins + Kubernetes Deployment"
description: "Jenkins pipeline and K8s manifests with Kustomize for ms-cinema microservices"
status: pending
priority: P1
effort: 16h
branch: master
tags: [cicd, jenkins, kubernetes, kustomize, devops]
created: 2026-03-14
---

# CI/CD Jenkins + Kubernetes Deployment

## Overview

Migrate ms-cinema (11 Maven modules) from Docker Compose to Kubernetes with Jenkins CI/CD. Keep Docker Compose working via Spring profiles. Use Kustomize base+overlays for dev/prod namespaces.

## Key Architecture Decisions

- **Eureka**: Make optional via `k8s` Spring profile (K8s DNS replaces discovery)
- **Config Server**: Replace with K8s ConfigMaps/Secrets in K8s mode
- **API Gateway**: Keep (has custom filters); NGINX Ingress in front for external routing
- **Kafka**: Strimzi Operator with KRaft (1 broker dev, 3 prod)
- **PostgreSQL**: StatefulSet+PVC for dev; external DB for prod
- **Registry**: Minikube docker-env for dev; Docker Hub for prod
- **Resources**: 256Mi/512Mi per Spring Boot service

## Phases

| # | Phase | Effort | Status | File |
|---|-------|--------|--------|------|
| 1 | K8s Base Manifests | 4h | pending | [phase-01](./phase-01-k8s-base-manifests.md) |
| 2 | Kustomize Overlays | 2h | pending | [phase-02](./phase-02-kustomize-overlays.md) |
| 3 | NGINX Ingress | 1h | pending | [phase-03](./phase-03-nginx-ingress.md) |
| 4 | Spring Boot K8s Adaptation | 3h | pending | [phase-04](./phase-04-spring-boot-k8s-adaptation.md) |
| 5 | Jenkinsfile + Pipeline | 4h | pending | [phase-05](./phase-05-jenkinsfile-pipeline.md) |
| 6 | Local Setup + Testing | 2h | pending | [phase-06](./phase-06-local-setup-testing.md) |

## Dependencies

- Phase 1 + Phase 4 can run in parallel (manifests + code changes)
- Phase 2 depends on Phase 1
- Phase 3 depends on Phase 1
- Phase 5 depends on Phase 1, 2, 4
- Phase 6 depends on all phases

## Research Reports

- [Jenkins Pipeline Patterns](./research/researcher-01-jenkins-pipeline-patterns.md)
- [K8s Architecture Decisions](./research/researcher-02-k8s-architecture-decisions.md)

## Constraints

- Docker Compose must still work (Spring profiles: default vs k8s)
- Eureka/Config Server NOT deleted, just made optional
- All K8s files in `k8s/` directory at project root
- No secrets committed to git (template files only)

## Validation Summary

**Validated:** 2026-03-15
**Questions asked:** 7

### Confirmed Decisions
- **Resources**: 16GB+ machine, 8GB Minikube allocation is fine
- **Eureka/Config Server**: Drop both in K8s mode via Spring profile (confirmed)
- **Jenkins deployment**: Run via Docker Compose (not on host, not in K8s)
- **Kafka**: Strimzi Operator with KRaft (confirmed)
- **Docker Registry**: Docker Hub for prod images (user has account), Minikube registry for dev
- **Config strategy**: Both in-source profiles + ConfigMap overlays (plan's current approach confirmed)
- **Prod environment**: Learning/simulation only — both dev/prod on same Minikube

### Action Items
- [ ] Update Phase 5 (Jenkinsfile): Add Jenkins Docker Compose config (jenkins + docker-in-docker or host socket mount)
- [ ] Update Phase 6: Add Jenkins Docker Compose setup instructions
- [ ] Phase 5: Configure Docker Hub push with user's account credentials
