# Phase 01: Add Tracing Dependencies

## Context Links
- [Plan overview](plan.md)
- [Parent pom.xml](/pom.xml)
- [Spring Boot 3.4.3 Tracing docs](https://docs.spring.io/spring-boot/reference/actuator/tracing.html)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Add `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-zipkin` to parent pom.xml dependency management, then declare them in each runnable service module.

## Key Insights
- Spring Boot 3.4.3 parent BOM already manages Micrometer Tracing versions -- only need `<dependencyManagement>` for the OTel exporter
- `micrometer-tracing-bridge-otel` bridges Micrometer Observation API to OpenTelemetry SDK
- `opentelemetry-exporter-zipkin` sends spans to Zipkin over HTTP (port 9411)
- Shared libraries (kafka-events, jwt-auth-spring-boot-starter) do NOT need tracing deps -- they inherit context from the host service runtime
- eureka-server and config-server: optional but recommended for completeness

## Requirements
- **Functional:** All 8 runnable services must have tracing dependencies on classpath
- **Non-functional:** No version conflicts; leverage Spring Boot BOM wherever possible

## Architecture
- Parent pom.xml: version management only (no direct dependency declaration)
- Each service pom.xml: declare the 2 tracing deps without version tags
- Spring Boot auto-config detects bridge+exporter on classpath -> auto-creates Tracer, SpanExporter beans

## Related Code Files

### Files to Modify
| File | Change |
|------|--------|
| `pom.xml` (root) | Add `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` to `<dependencyManagement>` |
| `auth-service/pom.xml` | Add 2 tracing deps |
| `movie-service/pom.xml` | Add 2 tracing deps |
| `booking-service/pom.xml` | Add 2 tracing deps |
| `payment-service/pom.xml` | Add 2 tracing deps |
| `notification-service/pom.xml` | Add 2 tracing deps |
| `api-gateway/pom.xml` | Add 2 tracing deps |
| `eureka-server/pom.xml` | Add 2 tracing deps (optional but consistent) |
| `config-server/pom.xml` | Add 2 tracing deps (optional but consistent) |

### Files NOT Modified
- `kafka-events/pom.xml` -- shared library, no runtime
- `jwt-auth-spring-boot-starter/pom.xml` -- shared library, no runtime
- `jwt-auth-spring-boot-autoconfigure/pom.xml` -- shared library, no runtime

## Implementation Steps

### Step 1: Add to root pom.xml `<dependencyManagement>`

Add inside existing `<dependencyManagement><dependencies>` block:

```xml
<!-- Micrometer Tracing bridge to OpenTelemetry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry Zipkin exporter -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

Note: No `<version>` needed -- Spring Boot 3.4.3 parent BOM manages both.

### Step 2: Add to each service pom.xml

Add to `<dependencies>` section of all 8 service modules:

```xml
<!-- Distributed tracing: Micrometer -> OpenTelemetry -> Zipkin -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
```

### Step 3: Verify compilation

```bash
mvn clean compile -pl auth-service,movie-service,booking-service,payment-service,notification-service,api-gateway,eureka-server,config-server
```

## Todo List
- [ ] Add deps to root pom.xml dependencyManagement
- [ ] Add deps to auth-service/pom.xml
- [ ] Add deps to movie-service/pom.xml
- [ ] Add deps to booking-service/pom.xml
- [ ] Add deps to payment-service/pom.xml
- [ ] Add deps to notification-service/pom.xml
- [ ] Add deps to api-gateway/pom.xml
- [ ] Add deps to eureka-server/pom.xml
- [ ] Add deps to config-server/pom.xml
- [ ] Run mvn clean compile to verify no conflicts

## Success Criteria
- All 8 services compile without errors
- `micrometer-tracing-bridge-otel` and `opentelemetry-exporter-zipkin` appear in `mvn dependency:tree` for each service
- No version conflicts in dependency resolution

## Risk Assessment
- **Low:** Spring Boot BOM manages versions; conflicts unlikely
- **Mitigation:** If OTel version conflict, pin version in root pom.xml properties

## Security Considerations
- No security impact -- tracing deps only add observability instrumentation

## Next Steps
- Proceed to [Phase 02](phase-02-configure-tracing-properties.md) for tracing configuration
