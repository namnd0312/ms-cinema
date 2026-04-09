# Phase 3: Clean application.yml Across All Services

## Context
- [Scout Report](./reports/scout-report.md)

## Overview
- **Priority:** High
- **Status:** pending
- **Description:** Remove eureka config blocks and config-server import lines from all service application.yml files. Clean application-k8s.yml.

## Key Insights
- All 7 services have `eureka:` blocks in application.yml (3-5 lines each)
- All 7 services have `spring.config.import: "optional:configserver:..."` line
- api-gateway has `application-k8s.yml` with `eureka: client: enabled: false` (L101-103) — remove since no eureka dep
- Comment on L1 of application-k8s.yml references eureka/config-server — update

## Requirements
- Remove `eureka:` YAML blocks from all 7 service application.yml
- Remove `spring.config.import: "optional:configserver:..."` lines from all 7 service application.yml
- Clean api-gateway application-k8s.yml (remove eureka block + update comment)

## Related Code Files
| File | Lines | Content to Remove |
|------|-------|-------------------|
| `auth-service/src/main/resources/application.yml` | L7 | config import line |
| `auth-service/src/main/resources/application.yml` | L55-59 | eureka block |
| `api-gateway/src/main/resources/application.yml` | L8 | config import line |
| `api-gateway/src/main/resources/application.yml` | L123-126 | eureka block |
| `api-gateway/src/main/resources/application-k8s.yml` | L1 | comment update |
| `api-gateway/src/main/resources/application-k8s.yml` | L101-103 | eureka block |
| `movie-service/src/main/resources/application.yml` | L8 | config import line |
| `movie-service/src/main/resources/application.yml` | L33-36 | eureka block |
| `booking-service/src/main/resources/application.yml` | L8 | config import line |
| `booking-service/src/main/resources/application.yml` | L50-53 | eureka block |
| `payment-service/src/main/resources/application.yml` | L8 | config import line |
| `payment-service/src/main/resources/application.yml` | L58-61 | eureka block |
| `notification-service/src/main/resources/application.yml` | L8 | config import line |
| `notification-service/src/main/resources/application.yml` | L50-53 | eureka block |
| `audit-service/src/main/resources/application.yml` | L8 | config import line |
| `audit-service/src/main/resources/application.yml` | L35-38 | eureka block |

## Implementation Steps
1. For each of the 7 service application.yml:
   a. Remove `import: "optional:configserver:..."` line under `spring.config`
   b. Remove entire `eureka:` YAML block (typically 3-5 lines)
2. In `api-gateway/src/main/resources/application-k8s.yml`:
   a. Update L1 comment to `# K8s profile: static URI routing`
   b. Remove L101-103 (`eureka: client: enabled: false`)
3. Verify each service starts correctly with `mvn spring-boot:run` (or compile check)

## Todo
- [ ] auth-service application.yml — remove config import + eureka block
- [ ] api-gateway application.yml — remove config import + eureka block
- [ ] api-gateway application-k8s.yml — remove eureka block + update comment
- [ ] movie-service application.yml — remove config import + eureka block
- [ ] booking-service application.yml — remove config import + eureka block
- [ ] payment-service application.yml — remove config import + eureka block
- [ ] notification-service application.yml — remove config import + eureka block
- [ ] audit-service application.yml — remove config import + eureka block
- [ ] Verify `mvn compile` passes

## Success Criteria
- No `eureka:` blocks in any service application.yml
- No `configserver:` import lines in any service application.yml
- No eureka reference in application-k8s.yml
- Services compile and start correctly

## Risk Assessment
| Risk | Severity | Mitigation |
|------|----------|------------|
| Removing config import breaks spring.config resolution | Low | Import is `optional:` prefixed, already disabled via env var |
| YAML indentation issues after removing blocks | Low | Careful editing, verify YAML validity |

## Next Steps
- Phase 4: Clean docker-compose, k8s, prometheus, docs
