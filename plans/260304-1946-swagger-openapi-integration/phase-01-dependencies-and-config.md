# Phase 01: Dependencies & Config

## Context Links
- [Plan overview](./plan.md)
- [Root pom.xml](/pom.xml)

## Overview
- **Priority:** High (blocks all other phases)
- **Status:** Pending
- **Description:** Add springdoc-openapi dependency version to parent POM and add the dependency to each service module POM. Add springdoc YAML config per service.

## Key Insights
- All services are servlet-based (including api-gateway which uses `gateway-mvc`, NOT reactive WebFlux)
- Use `springdoc-openapi-starter-webmvc-ui` for ALL services (single artifact)
- Version managed centrally in root pom.xml `<properties>` + `<dependencyManagement>`

## Requirements

### Functional
- springdoc version managed in root pom.xml
- Dependency added to: auth-service, movie-service, booking-service, payment-service, api-gateway

### Non-functional
- No version duplication across module POMs

## Related Code Files

### Files to Modify
| File | Action | Change |
|------|--------|--------|
| `pom.xml` (root) | Modify | Add `<springdoc.version>` property + dependencyManagement entry |
| `auth-service/pom.xml` | Modify | Add springdoc dependency (no version) |
| `movie-service/pom.xml` | Modify | Add springdoc dependency (no version) |
| `booking-service/pom.xml` | Modify | Add springdoc dependency (no version) |
| `payment-service/pom.xml` | Modify | Add springdoc dependency (no version) |
| `api-gateway/pom.xml` | Modify | Add springdoc dependency (no version) |
| `auth-service/src/main/resources/application.yml` | Modify | Add springdoc config |
| `movie-service/src/main/resources/application.yml` | Modify | Add springdoc config |
| `booking-service/src/main/resources/application.yml` | Modify | Add springdoc config |
| `payment-service/src/main/resources/application.yml` | Modify | Add springdoc config |
| `api-gateway/src/main/resources/application.yml` | Modify | Add springdoc config (aggregated) |

## Implementation Steps

### 1. Root pom.xml — version property
```xml
<properties>
    ...
    <springdoc.version>2.8.4</springdoc.version>
</properties>
```

### 2. Root pom.xml — dependencyManagement
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

### 3. Each service pom.xml — add dependency (no version)
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

### 4. application.yml — add per service
```yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

## Todo List
- [ ] Add `springdoc.version` to root pom.xml `<properties>`
- [ ] Add springdoc to root `<dependencyManagement>`
- [ ] Add dependency to auth-service pom.xml
- [ ] Add dependency to movie-service pom.xml
- [ ] Add dependency to booking-service pom.xml
- [ ] Add dependency to payment-service pom.xml
- [ ] Add dependency to api-gateway pom.xml
- [ ] Add springdoc YAML config to each service's application.yml
- [ ] Run `mvn compile` to verify no errors

## Success Criteria
- `mvn compile` passes for all modules
- No version duplication in child POMs

## Risk Assessment
- **springdoc version compatibility:** 2.8.x is confirmed compatible with Spring Boot 3.4.x
- **Gateway MVC support:** springdoc-webmvc-ui works with gateway-mvc (servlet-based)

## Next Steps
- Phase 02: OpenAPI config classes + security permits
