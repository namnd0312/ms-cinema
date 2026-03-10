# Phase 01: Maven pom.xml Renames

## Context Links
- [plan.md](plan.md)
- [Root pom.xml](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/pom.xml)

## Overview
- **Priority:** High (must go first; all child modules reference parent artifactId)
- **Status:** pending
- **Description:** Rename root Maven artifactId/name from `spring-jwt` to `ms-cinema`, update all child module parent references.

## Key Insights
- Root pom.xml declares `<artifactId>spring-jwt</artifactId>` and `<name>spring-jwt</name>`
- All 10 child modules reference `<artifactId>spring-jwt</artifactId>` in their `<parent>` block
- groupId `com.namnd` stays unchanged

## Requirements
- Root artifactId: `spring-jwt` -> `ms-cinema`
- Root name: `spring-jwt` -> `ms-cinema`
- All child `<parent><artifactId>` refs: `spring-jwt` -> `ms-cinema`

## Related Code Files

**Files to modify:**

| File | Change |
|------|--------|
| `pom.xml` (root) | `<artifactId>spring-jwt</artifactId>` -> `ms-cinema`, `<name>spring-jwt</name>` -> `ms-cinema` |
| `auth-service/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `eureka-server/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `config-server/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `api-gateway/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `movie-service/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `booking-service/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `payment-service/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `notification-service/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `jwt-auth-spring-boot-starter/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `jwt-auth-spring-boot-autoconfigure/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |
| `kafka-events/pom.xml` | `<parent><artifactId>spring-jwt</artifactId>` -> `ms-cinema` |

## Implementation Steps

1. Open root `pom.xml`, change line 16 `<artifactId>spring-jwt</artifactId>` to `<artifactId>ms-cinema</artifactId>`
2. Change line 19 `<name>spring-jwt</name>` to `<name>ms-cinema</name>`
3. For each of the 10 child module `pom.xml` files, change `<parent>` block `<artifactId>spring-jwt</artifactId>` to `<artifactId>ms-cinema</artifactId>` (all on line ~10)
4. Run `mvn clean compile` from root to verify reactor resolves all modules

## Todo List

- [ ] Update root `pom.xml` artifactId and name
- [ ] Update `auth-service/pom.xml` parent artifactId
- [ ] Update `eureka-server/pom.xml` parent artifactId
- [ ] Update `config-server/pom.xml` parent artifactId
- [ ] Update `api-gateway/pom.xml` parent artifactId
- [ ] Update `movie-service/pom.xml` parent artifactId
- [ ] Update `booking-service/pom.xml` parent artifactId
- [ ] Update `payment-service/pom.xml` parent artifactId
- [ ] Update `notification-service/pom.xml` parent artifactId
- [ ] Update `jwt-auth-spring-boot-starter/pom.xml` parent artifactId
- [ ] Update `jwt-auth-spring-boot-autoconfigure/pom.xml` parent artifactId
- [ ] Update `kafka-events/pom.xml` parent artifactId
- [ ] Verify `mvn clean compile` passes

## Success Criteria
- `mvn clean compile` passes from root
- No module fails with "Could not find artifact com.namnd:spring-jwt"
- `mvn help:effective-pom` shows `ms-cinema` as parent for all modules

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Missed child module | Medium | Grep confirmed exactly 10 child pom.xml files with `spring-jwt` parent ref |
| Local Maven cache stale | Low | `mvn clean` clears target dirs; `-U` flag forces snapshot update if needed |
