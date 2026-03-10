# Phase 4: Spring Cloud Integration for Auth-Service

## Context Links
- [Current pom.xml](/pom.xml)
- [Current application.yml](/src/main/resources/application.yml) (moves to auth-service/ after Phase 1)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- **Depends on:** Phase 1
- Add Spring Cloud dependencies to auth-service: Eureka Client (service discovery), Config Client (centralized config), Spring Boot Actuator (health/info for Eureka heartbeat).

## Key Insights
- Spring Cloud BOM `2024.0.1` is compatible with Spring Boot 3.4.x
- No `@EnableDiscoveryClient` needed -- auto-configured when `spring-cloud-starter-netflix-eureka-client` on classpath
- Spring Boot 3.4 uses `spring.config.import` instead of bootstrap.yml for Config Server
- Config Server properties override local application.yml (Config Server has higher priority)
- Actuator `/actuator/health` is required by Eureka for heartbeat checks
- JWT secret (`namnd.app.jwtSecret`) will be moved to Config Server in Phase 5; auth-service keeps local fallback

## Requirements

### Functional
- Auth-service registers with Eureka Server on startup
- Auth-service fetches shared config from Config Server (JWT secret, common properties)
- Actuator health endpoint exposed for Eureka heartbeat
- Application name set to `auth-service` for discovery

### Non-functional
- Graceful fallback when Eureka/Config Server unavailable (use `optional:` prefix)
- No breaking changes to existing auth functionality

## Architecture

### Service Registration Flow
```
auth-service starts
  → fetches config from Config Server (optional, fallback to local)
  → registers with Eureka Server (service name: auth-service)
  → Eureka heartbeat via /actuator/health every 30s
  → other services discover auth-service via Eureka
```

### Dependency Chain
```
spring-cloud-dependencies BOM (2024.0.1)
  ├── spring-cloud-starter-netflix-eureka-client
  ├── spring-cloud-starter-config
  └── (spring-boot-starter-actuator -- separate, not in Cloud BOM)
```

## Related Code Files

### Files to Modify
- `pom.xml` (root parent) -- add Spring Cloud BOM to `<dependencyManagement>`
- `auth-service/pom.xml` -- add Eureka Client, Config Client, Actuator dependencies
- `auth-service/src/main/resources/application.yml` -- add Eureka, Config Server, Actuator settings

### Files to Create
- None (just dependency + config changes)

## Implementation Steps

### 1. Add Spring Cloud BOM to root parent pom.xml

Add inside `<dependencyManagement><dependencies>`:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-dependencies</artifactId>
    <version>2024.0.1</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

Add to `<properties>`:
```xml
<spring-cloud.version>2024.0.1</spring-cloud.version>
```

And reference in BOM:
```xml
<version>${spring-cloud.version}</version>
```

### 2. Add dependencies to auth-service/pom.xml

```xml
<!-- Spring Cloud: Eureka Client -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<!-- Spring Cloud: Config Client -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>

<!-- Actuator for Eureka heartbeat + monitoring -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3. Update auth-service application.yml

Add/modify the following sections:

```yaml
spring:
  application:
    name: auth-service
  config:
    import: "optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888"
  cloud:
    config:
      fail-fast: false   # don't fail if Config Server down
      retry:
        max-attempts: 3

eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
    register-with-eureka: true
    fetch-registry: true
  instance:
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${server.port}

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: when-authorized
```

### 4. Update SecurityConfig -- permit actuator health

Current config permits `/api/auth/**`. Add actuator health:

```java
.requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info").permitAll()
```

### 5. Docker environment variables

Update `docker-compose.yml` ms-authentication-service environment:
```yaml
ms-authentication-service:
  environment:
    - EUREKA_HOST=eureka-server
    - CONFIG_SERVER_HOST=config-server
    - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-service:5432/testdb
    - REDIS_HOST=redis-service
```

### 6. Verify build
```bash
cd auth-service && mvn compile
```

Verify Eureka client auto-configures (check startup logs for Eureka registration attempt -- will fail gracefully if no Eureka Server running yet).

## Todo List
- [ ] Add Spring Cloud BOM to root parent pom.xml dependencyManagement
- [ ] Add `spring-cloud.version` property to root pom.xml
- [ ] Add Eureka Client dependency to auth-service/pom.xml
- [ ] Add Config Client dependency to auth-service/pom.xml
- [ ] Add Actuator dependency to auth-service/pom.xml
- [ ] Add `spring.application.name: auth-service` to application.yml
- [ ] Add `spring.config.import` for Config Server (optional prefix)
- [ ] Add Eureka client config to application.yml
- [ ] Add Actuator management endpoints config
- [ ] Update SecurityConfig to permit `/actuator/health` and `/actuator/info`
- [ ] Update docker-compose.yml with environment variables
- [ ] Compile and verify startup logs

## Success Criteria
- `mvn clean install` succeeds with new Spring Cloud dependencies
- Auth-service starts without Eureka/Config Server (graceful fallback)
- When Eureka Server is running (Phase 5), auth-service registers successfully
- `/actuator/health` returns 200 OK without authentication
- All existing auth endpoints still work

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Spring Cloud BOM version incompatible with Boot 3.4.3 | High | 2024.0.1 officially supports 3.4.x; verify at start |
| Eureka client pulls in unwanted transitive deps | Low | Check dependency tree with `mvn dependency:tree` |
| Config Server import blocks startup when server down | Medium | Use `optional:` prefix in spring.config.import |
| Actuator exposes sensitive endpoints | Medium | Only expose health and info; show-details only when authorized |

## Security Considerations
- Actuator endpoints: only `/health` and `/info` exposed, no sensitive endpoints
- `show-details: when-authorized` prevents health detail leakage to anonymous users
- Config Server connection: use TLS in production, consider Spring Cloud Bus for secret rotation
- Eureka: consider securing Eureka dashboard in production (basic auth or Spring Security)

## Next Steps
- Phase 5: Create Eureka Server, Config Server, and API Gateway
