---
title: "Remove Eureka & Config Server Code"
description: "Remove all eureka-server and config-server related code, deps, and config from the project"
status: complete
priority: P2
effort: 3h
branch: k8s
tags: [cleanup, eureka, config-server, k8s-migration]
created: 2026-04-09
---

# Remove Eureka & Config Server Code

## Background
The k8s branch disables eureka via `EUREKA_CLIENT_ENABLED: "false"` and `SPRING_CLOUD_CONFIG_ENABLED: "false"` in configmap. Services use static URI routing (application-k8s.yml). Goal: remove all dead code.

## Scope
- 2 modules to delete: `eureka-server/`, `config-server/`
- 7 service pom.xml files: remove eureka-client + config-client deps
- 7 service application.yml: remove eureka blocks + config import lines
- docker-compose.yml: remove eureka/config services + env vars + depends_on
- k8s configmap: remove EUREKA/CONFIG disable flags (no longer needed)
- monitoring: remove prometheus scrape jobs
- docs: update references

## Phases

| # | Phase | Status | File |
|---|-------|--------|------|
| 1 | Delete eureka-server & config-server modules | complete | [phase-01](./phase-01-delete-eureka-config-modules.md) |
| 2 | Remove eureka/config deps from service pom.xml | complete | [phase-02](./phase-02-remove-service-pom-dependencies.md) |
| 3 | Clean application.yml across all services | complete | [phase-03](./phase-03-clean-service-application-yml.md) |
| 4 | Clean docker-compose, k8s, prometheus, docs | complete | [phase-04](./phase-04-clean-infra-and-docs.md) |

## Dependencies
- Phase 1 independent
- Phases 2-3 independent of each other, both after Phase 1
- Phase 4 after Phases 2-3

## Risk
- Low risk: eureka/config already disabled on k8s branch
- Docker-compose still used for local dev — ensure services start without eureka/config
- Verify `spring-cloud-dependencies` BOM still needed (yes — gateway, openfeign, loadbalancer use it)

## Success Criteria
- `mvn compile` succeeds on all modules
- `docker-compose up` starts without eureka/config services
- k8s deploy works unchanged
- No eureka/config references in source code (docs may keep historical mentions)

## Post-Review Issues

### High — api-gateway default profile uses `lb://` without LoadBalancer on classpath
`api-gateway/src/main/resources/application.yml` routes all services via `lb://` (e.g., `lb://auth-service`).
Previously `spring-cloud-starter-netflix-eureka-client` transitively provided `spring-cloud-starter-loadbalancer`. That dep is now removed.
Without `spring-cloud-starter-loadbalancer` explicitly declared in `api-gateway/pom.xml`, `lb://` will throw a runtime error in the **default profile** (local dev / docker-compose).
K8s profile (`application-k8s.yml`) uses static `http://` URIs — unaffected.
**Fix options:**
1. Add `spring-cloud-starter-loadbalancer` to `api-gateway/pom.xml` (keeps `lb://` with in-memory static list or no-op)
2. Replace `lb://` with `http://localhost:<port>` in default profile for local dev

### Low — pom.xml indentation: `<module>api-gateway</module>` (line 35) has no leading spaces
Functional but inconsistent with other module entries. Fix: add 8 spaces indent.
