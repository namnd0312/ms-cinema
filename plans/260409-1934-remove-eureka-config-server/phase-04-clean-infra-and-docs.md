# Phase 4: Clean Docker-Compose, K8s, Prometheus, and Docs

## Context
- [Scout Report](./reports/scout-report.md)

## Overview
- **Priority:** Medium
- **Status:** pending
- **Description:** Remove eureka/config references from docker-compose, k8s configmap, prometheus config, and documentation files.

## Key Insights
- docker-compose.yml has 2 service definitions + env vars/depends_on in all 7 services
- k8s configmap has `EUREKA_CLIENT_ENABLED` and `SPRING_CLOUD_CONFIG_ENABLED` flags — no longer needed
- Prometheus has 2 scrape jobs to remove
- Multiple docs files reference eureka/config — update to reflect k8s-native architecture

## Related Code Files

### docker-compose.yml
| Lines | Content |
|-------|---------|
| L68-79 | `eureka-server:` service block |
| L81-95 | `config-server:` service block |
| L106-107 | api-gateway `depends_on: eureka-server, config-server` |
| L110-111 | api-gateway `EUREKA_HOST`, `CONFIG_SERVER_HOST` env vars |
| L126-128 | auth-service `depends_on: eureka-server, config-server` |
| L132-133 | auth-service `EUREKA_HOST`, `CONFIG_SERVER_HOST` env vars |
| L153-155 | movie-service `depends_on` + env vars |
| L160-161 | movie-service env vars |
| L179-181 | booking-service `depends_on` + env vars |
| L186-187 | booking-service env vars |
| L205-207 | payment-service `depends_on` + env vars |
| L212-213 | payment-service env vars |
| L231-233 | notification-service `depends_on` + env vars |
| L240-241 | notification-service env vars |
| L261-263 | audit-service `depends_on` + env vars |
| L268-269 | audit-service env vars |

### K8s
| File | Lines | Content |
|------|-------|---------|
| `k8s/base/configmap.yml` | L7-11 | Comments + EUREKA/CONFIG disable flags |

### Prometheus
| File | Lines | Content |
|------|-------|---------|
| `monitoring/prometheus/prometheus.yml` | L35-38 | eureka-server scrape job |
| `monitoring/prometheus/prometheus.yml` | L40-43 | config-server scrape job |

### Documentation (update, not delete)
| File | Key Lines |
|------|-----------|
| `README.md` | L11 (infra services list), L34 (run command), L49-50 (table), L130, L164, L168 |
| `docs/system-architecture.md` | L23-25 (diagram), L53-65 (module descriptions) |
| `docs/codebase-summary.md` | L15-16 (module tree), L32-33 (config-repo ref), L60 |
| `docs/project-overview-pdr.md` | L11, L23, L186, L189, L544, L587, L595, L609, L647 |
| `docs/project-roadmap.md` | L41, L151 |
| `docs/project-changelog.md` | L398 (historical — leave as-is) |
| `docs/system-design-mermaid-diagrams-all-services-flows.md` | multiple eureka/config refs in diagrams |

## Implementation Steps

### 1. docker-compose.yml
1. Delete `eureka-server:` service block (L68-79)
2. Delete `config-server:` service block (L81-95)
3. For each of 7 services: remove `eureka-server` and `config-server` from `depends_on`
4. For each of 7 services: remove `EUREKA_HOST` and `CONFIG_SERVER_HOST` env vars

### 2. K8s Configmap
1. Remove L7 comment about Eureka
2. Remove L9-11 (`EUREKA_CLIENT_ENABLED`, `SPRING_CLOUD_CONFIG_ENABLED`, comment)
3. Remove L21 comment about config-server migration (keep the actual config values)

### 3. Prometheus
1. Remove eureka-server scrape job (L35-38)
2. Remove config-server scrape job (L40-43)

### 4. Documentation
1. Update `README.md`: change "3 Infrastructure Services" to "1 Infrastructure Service (api-gateway)", remove eureka/config from table, remove run commands, update troubleshooting
2. Update `docs/system-architecture.md`: remove eureka-server and config-server module descriptions, update diagram
3. Update `docs/codebase-summary.md`: change "11 Maven Modules" to "9 Maven Modules", remove eureka/config entries
4. Update `docs/project-overview-pdr.md`: remove eureka/config references, update module count
5. Update `docs/project-roadmap.md`: mark eureka/config as removed
6. Leave `docs/project-changelog.md` as-is (historical record)
7. Update `docs/system-design-mermaid-diagrams-all-services-flows.md`: remove eureka/config from diagrams

## Todo
- [ ] docker-compose.yml — remove eureka/config service blocks
- [ ] docker-compose.yml — clean depends_on and env vars from all services
- [ ] k8s/base/configmap.yml — remove eureka/config disable flags
- [ ] monitoring/prometheus/prometheus.yml — remove scrape jobs
- [ ] README.md — update infrastructure description
- [ ] docs/system-architecture.md — update
- [ ] docs/codebase-summary.md — update
- [ ] docs/project-overview-pdr.md — update
- [ ] docs/project-roadmap.md — update
- [ ] docs/system-design-mermaid-diagrams-all-services-flows.md — update diagrams
- [ ] Verify `docker-compose config` is valid
- [ ] Verify k8s manifests apply cleanly

## Success Criteria
- `docker-compose config` validates without errors
- No eureka/config references in docker-compose, k8s, or prometheus
- Docs accurately reflect 9-module architecture without eureka/config
- `docker-compose up` starts all services without eureka/config containers

## Risk Assessment
| Risk | Severity | Mitigation |
|------|----------|------------|
| Docker-compose services fail without eureka/config | Low | Already using `optional:` config import prefix |
| Docs update misses references | Low | Use grep to verify after changes |

## Security Considerations
- No security impact — removing unused infrastructure

## Next Steps
- Final verification: `mvn clean compile`, `docker-compose up`, k8s deploy test
