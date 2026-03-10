# Phase 5: Infrastructure Services (Eureka Server, Config Server, API Gateway)

## Context Links
- [Current docker-compose.yml](/docker-compose.yml)
- [Current Dockerfile](/Dockerfile)
- [Plan overview](./plan.md)
- [Phase 4 - auth-service cloud config](./phase-04-spring-cloud-integration-for-auth-service.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 2.5h
- **Depends on:** Phase 1 (module structure), Phase 4 (auth-service has Eureka/Config Client)
- Create three new Spring Boot modules: Eureka Server (discovery), Config Server (centralized config with native/filesystem profile), API Gateway (Spring Cloud Gateway MVC, servlet-based). Update docker-compose.yml to orchestrate all services.

## Key Insights
- Each infrastructure service is a minimal Spring Boot app (single class + config)
- Config Server uses `native` profile with filesystem configs (no Git repo needed for dev)
- Gateway uses `spring-cloud-starter-gateway-server-mvc` (servlet-based) -- avoids mixing WebFlux with servlet stack
- Gateway only routes requests; JWT validation happens per-service via starter library
- Eureka Server is self-contained: `@EnableEurekaServer` + `register-with-eureka: false`
- All three services are lightweight (< 5 files each)

## Requirements

### Functional
- Eureka Server runs on port 8761, dashboard accessible
- Config Server runs on port 8888, serves configs from `config-repo/` directory
- API Gateway runs on port 8080 (takes over from auth-service), routes:
  - `/api/auth/**` -> `auth-service` (via Eureka discovery)
  - `/api/users/**` -> `auth-service`
- Auth-service port changes to 8081 (gateway takes 8080)
- Centralized config: JWT secret + common properties served by Config Server

### Non-functional
- Infrastructure services start before auth-service (docker-compose depends_on)
- All services register with Eureka
- Gateway uses service discovery for routing (no hardcoded URLs)

## Architecture

### Service Topology
```
                    ┌─────────────┐
                    │ Config      │ :8888
                    │ Server      │
                    └──────┬──────┘
                           │ config
    ┌──────────────────────┼──────────────────────┐
    │                      │                      │
┌───▼───┐          ┌───────▼──────┐        ┌──────▼──────┐
│Eureka │ :8761    │ API Gateway  │ :8080  │auth-service │ :8081
│Server │◄─────────┤ (routes)     │────────►│ (JWT auth)  │
└───────┘ register └──────────────┘ route  └─────────────┘
```

### Config Repo Structure (filesystem)
```
config-repo/
├── application.yml           (shared by ALL services)
├── auth-service.yml          (auth-service specific overrides)
└── api-gateway.yml           (gateway specific overrides)
```

### Module Structure
```
jwt-spring-security/
├── eureka-server/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/namnd/eurekaserver/EurekaServerApplication.java
│   └── src/main/resources/application.yml
├── config-server/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── config-repo/          (native config files)
│   │   ├── application.yml
│   │   ├── auth-service.yml
│   │   └── api-gateway.yml
│   └── src/main/java/com/namnd/configserver/ConfigServerApplication.java
│   └── src/main/resources/application.yml
├── api-gateway/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/namnd/apigateway/ApiGatewayApplication.java
│   └── src/main/resources/application.yml
```

## Related Code Files

### Files to Modify
- `/pom.xml` (root) -- add eureka-server, config-server, api-gateway modules
- `/docker-compose.yml` -- add all infrastructure services
- `auth-service/src/main/resources/application.yml` -- change port to 8081

### Files to Create (per module, listed below)

## Implementation Steps

### 1. Add modules to root pom.xml

```xml
<modules>
    <module>auth-service</module>
    <module>jwt-auth-spring-boot-autoconfigure</module>
    <module>jwt-auth-spring-boot-starter</module>
    <module>eureka-server</module>
    <module>config-server</module>
    <module>api-gateway</module>
</modules>
```

### 2. Create Eureka Server

**eureka-server/pom.xml:**
```xml
<project>
    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>eureka-server</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
        <finalName>eureka-server</finalName>
    </build>
</project>
```

**EurekaServerApplication.java:**
```java
package com.namnd.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
```

**eureka-server application.yml:**
```yaml
server:
  port: 8761

spring:
  application:
    name: eureka-server

eureka:
  client:
    register-with-eureka: false
    fetch-registry: false
  server:
    wait-time-in-ms-when-sync-empty: 0
```

**eureka-server/Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/eureka-server.jar eureka-server.jar
ENTRYPOINT ["java", "-jar", "eureka-server.jar"]
```

### 3. Create Config Server

**config-server/pom.xml:**
```xml
<project>
    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>config-server</artifactId>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
        <finalName>config-server</finalName>
    </build>
</project>
```

**ConfigServerApplication.java:**
```java
package com.namnd.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

**config-server application.yml:**
```yaml
server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config-repo

eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
```

**config-repo/application.yml (shared config for all services):**
```yaml
# Shared config served to ALL services
namnd:
  app:
    jwtSecret: ${JWT_SECRET:kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==}

# Also exposed as jwt.auth.secret for starter library consumers
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}
```

**config-repo/auth-service.yml:**
```yaml
# Auth-service specific config (overrides shared)
namnd:
  app:
    jwtExpiration: 900000
    jwtRefreshExpiration: 604800000
    maxFailedAttempts: ${MAX_FAILED_ATTEMPTS:5}
    lockDurationMs: ${LOCK_DURATION_MS:900000}
    passwordResetBaseUrl: ${PASSWORD_RESET_BASE_URL:http://localhost:3000/reset-password}
    activationBaseUrl: ${ACTIVATION_BASE_URL:http://localhost:8080/api/auth/activate}
```

**config-repo/api-gateway.yml:**
```yaml
# Gateway specific config (placeholder for future)
```

Note: Place config-repo inside config-server's `src/main/resources/` so it ships inside the JAR for dev. In production, mount external volume.

**config-server/Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/config-server.jar config-server.jar
ENTRYPOINT ["java", "-jar", "config-server.jar"]
```

### 4. Create API Gateway

**api-gateway/pom.xml:**
```xml
<project>
    <parent>
        <groupId>com.namnd</groupId>
        <artifactId>spring-jwt</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </parent>
    <artifactId>api-gateway</artifactId>

    <dependencies>
        <!-- Servlet-based gateway (NOT WebFlux) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway-server-mvc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
        <finalName>api-gateway</finalName>
    </build>
</project>
```

**ApiGatewayApplication.java:**
```java
package com.namnd.apigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

**api-gateway application.yml:**
```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  config:
    import: "optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888"
  cloud:
    gateway:
      mvc:
        routes:
          - id: auth-service
            uri: lb://auth-service
            predicates:
              - Path=/api/auth/**
          - id: auth-service-users
            uri: lb://auth-service
            predicates:
              - Path=/api/users/**

eureka:
  client:
    service-url:
      defaultZone: http://${EUREKA_HOST:localhost}:8761/eureka/
  instance:
    prefer-ip-address: true

management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
```

**api-gateway/Dockerfile:**
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/api-gateway.jar api-gateway.jar
ENTRYPOINT ["java", "-jar", "api-gateway.jar"]
```

### 5. Change auth-service port to 8081

In `auth-service/src/main/resources/application.yml`:
```yaml
server:
  port: ${SERVER_PORT:8081}
```

### 6. Update docker-compose.yml

```yaml
services:
  postgres-service:
    image: 'postgres:16-alpine'
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
    networks:
      - my-net
    restart: unless-stopped
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres

  redis-service:
    image: 'redis:7-alpine'
    ports:
      - "6379:6379"
    networks:
      - my-net
    restart: unless-stopped

  eureka-server:
    build:
      context: ./eureka-server
      dockerfile: Dockerfile
    ports:
      - "8761:8761"
    networks:
      - my-net
    restart: unless-stopped

  config-server:
    build:
      context: ./config-server
      dockerfile: Dockerfile
    ports:
      - "8888:8888"
    networks:
      - my-net
    depends_on:
      - eureka-server
    environment:
      - EUREKA_HOST=eureka-server
    restart: unless-stopped

  api-gateway:
    build:
      context: ./api-gateway
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    networks:
      - my-net
    depends_on:
      - eureka-server
      - config-server
    environment:
      - EUREKA_HOST=eureka-server
      - CONFIG_SERVER_HOST=config-server
    restart: unless-stopped

  auth-service:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8081:8081"
    networks:
      - my-net
    depends_on:
      - postgres-service
      - redis-service
      - eureka-server
      - config-server
    environment:
      - SERVER_PORT=8081
      - EUREKA_HOST=eureka-server
      - CONFIG_SERVER_HOST=config-server
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres-service:5432/testdb
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - REDIS_HOST=redis-service
    restart: unless-stopped

networks:
  my-net:
    driver: bridge

volumes:
  postgres-data:
```

### 7. Update root Dockerfile for auth-service

Since auth-service now lives in a subdirectory:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY auth-service/target/auth-service.jar auth-service.jar
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
```

### 8. Build and verify
```bash
mvn clean install
docker-compose up --build
```

Check:
- Eureka dashboard at http://localhost:8761
- Config Server at http://localhost:8888/auth-service/default
- Gateway routes at http://localhost:8080/api/auth/login

## Todo List
- [ ] Add eureka-server, config-server, api-gateway to root pom.xml modules
- [ ] Create eureka-server module (pom.xml, Application.java, application.yml, Dockerfile)
- [ ] Create config-server module (pom.xml, Application.java, application.yml, Dockerfile)
- [ ] Create config-repo/ with shared application.yml and auth-service.yml
- [ ] Create api-gateway module (pom.xml, Application.java, application.yml, Dockerfile)
- [ ] Configure gateway routes: /api/auth/**, /api/users/**
- [ ] Change auth-service port from 8080 to 8081
- [ ] Update docker-compose.yml with all services and correct startup order
- [ ] Update root Dockerfile for auth-service JAR path
- [ ] Build all modules: `mvn clean install`
- [ ] Docker compose up and verify Eureka dashboard
- [ ] Verify gateway routes to auth-service

## Success Criteria
- All 6 modules build: `mvn clean install`
- `docker-compose up --build` starts all services without errors
- Eureka dashboard (http://localhost:8761) shows: auth-service, config-server, api-gateway registered
- Config Server (http://localhost:8888/auth-service/default) returns auth-service config including JWT secret
- Gateway (http://localhost:8080/api/auth/login) routes to auth-service and returns valid response
- Auth-service directly accessible at http://localhost:8081/api/auth/login

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Service startup order race condition | High | Use docker-compose depends_on + optional config import + Eureka retry |
| Gateway MVC starter conflicts with WebFlux | High | Use `spring-cloud-starter-gateway-server-mvc` (servlet), NOT `spring-cloud-starter-gateway` (WebFlux) |
| Config Server native search-locations path | Medium | Use classpath:/config-repo for dev; volume mount for prod |
| Port conflicts (8080 taken by gateway) | Low | Auth-service moves to 8081; document the change |
| Eureka server crashes, services can't discover | Medium | Services cache registry locally; add health checks in compose |

## Security Considerations
- Config Server exposes JWT secret via REST -- secure with basic auth or network isolation in production
- Eureka dashboard -- add Spring Security to eureka-server in production
- Gateway does NOT validate JWT (by design) -- just forwards requests. Per-service validation via starter.
- Docker network `my-net` isolates services; only gateway port (8080) exposed externally in production
- Consider TLS between services in production

## Next Steps
- Phase 6: End-to-end testing and documentation
- Future: Add rate limiting to gateway (RequestRateLimiter + Redis)
- Future: Add circuit breaker (Resilience4j) to gateway
