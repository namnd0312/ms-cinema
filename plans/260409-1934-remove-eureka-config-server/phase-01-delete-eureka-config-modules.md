# Phase 1: Delete eureka-server & config-server Modules

## Context
- [Scout Report](./reports/scout-report.md)
- Branch: k8s

## Overview
- **Priority:** High
- **Status:** pending
- **Description:** Delete the two infrastructure modules and remove from parent pom.xml

## Key Insights
- eureka-server has only 1 Java file (`EurekaServerApplication.java`), 1 yml, 1 Dockerfile
- config-server has 1 Java file, 1 yml, 5 config-repo yml files, 1 Dockerfile
- Config-repo configs already migrated to k8s configmap and service application.yml / application-k8s.yml

## Requirements
- Delete `eureka-server/` directory entirely
- Delete `config-server/` directory entirely
- Remove `<module>eureka-server</module>` and `<module>config-server</module>` from root `pom.xml`

## Related Code Files
| File | Action | Lines |
|------|--------|-------|
| `eureka-server/` | DELETE dir | entire |
| `config-server/` | DELETE dir | entire |
| `pom.xml` (root) | EDIT | L35-36 |

## Implementation Steps
1. Remove `<module>eureka-server</module>` from `/pom.xml` L35
2. Remove `<module>config-server</module>` from `/pom.xml` L36
3. Delete `/eureka-server/` directory
4. Delete `/config-server/` directory
5. Run `mvn validate` to confirm parent pom is valid

## Todo
- [ ] Remove eureka-server module from root pom.xml
- [ ] Remove config-server module from root pom.xml
- [ ] Delete eureka-server/ directory
- [ ] Delete config-server/ directory
- [ ] Verify `mvn validate` passes

## Success Criteria
- `mvn validate` passes without errors
- No eureka-server or config-server directories exist

## Risk Assessment
| Risk | Severity | Mitigation |
|------|----------|------------|
| Config-repo files contain settings not yet migrated | Medium | Verify all config-repo/*.yml settings exist in service application.yml or k8s configmap |

## Next Steps
- Phase 2: Remove eureka/config dependencies from service pom.xml files
