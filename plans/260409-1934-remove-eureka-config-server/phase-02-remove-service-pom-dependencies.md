# Phase 2: Remove Eureka/Config Dependencies from Service pom.xml

## Context
- [Scout Report](./reports/scout-report.md)

## Overview
- **Priority:** High
- **Status:** pending
- **Description:** Remove `spring-cloud-starter-netflix-eureka-client` and `spring-cloud-starter-config` from all 7 service pom.xml files. Also remove related comments.

## Key Insights
- All 7 services (auth, api-gateway, movie, booking, payment, notification, audit) have both deps
- `spring-cloud-dependencies` BOM must stay — used by gateway, openfeign, loadbalancer
- `spring-boot-starter-actuator` must stay — used for health checks, prometheus metrics
- api-gateway pom.xml L16 description mentions "Eureka discovery" — update it

## Requirements
- Remove `spring-cloud-starter-netflix-eureka-client` dependency block from all 7 pom.xml
- Remove `spring-cloud-starter-config` dependency block from all 7 pom.xml
- Remove related XML comments (e.g. `<!-- Spring Cloud: Eureka + Config -->`)
- Update api-gateway pom.xml description

## Related Code Files
| File | Lines to Remove | Details |
|------|----------------|---------|
| `auth-service/pom.xml` | L110-113, L119 | eureka-client + comment, config client |
| `api-gateway/pom.xml` | L16, L26, L30 | description, eureka-client, config client |
| `movie-service/pom.xml` | L57-60, L64 | eureka-client + comment, config client |
| `booking-service/pom.xml` | L66-69, L73 | eureka-client + comment, config client |
| `payment-service/pom.xml` | L67-70, L74 | eureka-client + comment, config client |
| `notification-service/pom.xml` | L77-80, L84 | eureka-client + comment, config client |
| `audit-service/pom.xml` | L65-68, L72 | eureka-client + comment, config client |

## Implementation Steps
1. For each of the 7 service pom.xml files:
   a. Remove `spring-cloud-starter-netflix-eureka-client` dependency block (4 lines)
   b. Remove `spring-cloud-starter-config` dependency block (4 lines)
   c. Remove associated comment lines (e.g. `<!-- Spring Cloud: Eureka + Config -->`)
2. Update `api-gateway/pom.xml` L16 description — remove "via Eureka discovery"
3. Update `auth-service/pom.xml` comment L122 — remove "for Eureka heartbeat"
4. Run `mvn compile -pl auth-service,api-gateway,movie-service,booking-service,payment-service,notification-service,audit-service`

## Todo
- [ ] auth-service/pom.xml — remove eureka-client + config deps
- [ ] api-gateway/pom.xml — remove eureka-client + config deps + update description
- [ ] movie-service/pom.xml — remove eureka-client + config deps
- [ ] booking-service/pom.xml — remove eureka-client + config deps
- [ ] payment-service/pom.xml — remove eureka-client + config deps
- [ ] notification-service/pom.xml — remove eureka-client + config deps
- [ ] audit-service/pom.xml — remove eureka-client + config deps
- [ ] Verify `mvn compile` passes for all services

## Success Criteria
- No `eureka` or `spring-cloud-starter-config` in any service pom.xml
- `mvn compile` succeeds for all modules

## Risk Assessment
| Risk | Severity | Mitigation |
|------|----------|------------|
| Transitive deps break other features | Low | eureka-client pulls in ribbon/loadbalancer but `spring-cloud-starter-loadbalancer` is likely already explicit dep or pulled by gateway |
| OpenFeign needs service discovery | Low | Feign already uses direct URLs in k8s profile |

## Next Steps
- Phase 3: Clean application.yml files
