# Spring Cloud Stack Research — Spring Boot 3.4.x / Java 21

**Date:** 2026-03-02
**Scope:** Spring Cloud 2024.0.x BOM, Eureka, Config Server, Gateway, Actuator

---

## 1. Spring Cloud BOM (2024.0.x)

**Compatible train:** Spring Cloud **2024.0.x** (codename Moorgate) → targets Spring Boot 3.4.x / Spring Framework 6.2.x.

Latest patch (as of research date): **2024.0.1** (released 2025-03-19, based on Spring Boot 3.4.3).

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.cloud</groupId>
      <artifactId>spring-cloud-dependencies</artifactId>
      <version>2024.0.1</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

> Do NOT use 2025.0.x — it targets Spring Boot 3.5.x.

---

## 2. Eureka Server + Client

### Dependencies (no version — managed by BOM)

```xml
<!-- Eureka Server module -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-server</artifactId>
</dependency>

<!-- Eureka Client (each microservice) -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<!-- Required for health heartbeat -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### Eureka Server bootstrap

```java
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication { ... }
```

### application.yml — Eureka Server

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

### application.yml — Eureka Client (each service)

```yaml
spring:
  application:
    name: auth-service
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

### Breaking changes vs older versions
- No `@EnableDiscoveryClient` required since Spring Cloud 2020.x — auto-configured via classpath.
- `bootstrap.yml` is disabled by default since Spring Boot 2.4 — use `spring.config.import` instead.

---

## 3. Spring Cloud Config Server

### Dependencies

```xml
<!-- Config Server module -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-config-server</artifactId>
</dependency>

<!-- Config Client (each microservice) -->
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

### Config Server — native filesystem backend

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication { ... }
```

```yaml
# config-server application.yml
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
          search-locations: classpath:/config-repo/
          # or file-based: file:/opt/config-repo/
```

### Storing shared secrets (JWT secret)

Place in a shared file `config-repo/application.yml` (served to ALL clients):

```yaml
# config-repo/application.yml
jwt:
  secret: ${JWT_SECRET}          # inject via env var — never hardcode
  expiration: 86400000
```

Service-specific overrides: `config-repo/auth-service.yml`.

### Client — spring.config.import (modern, Boot 3.4)

```yaml
# auth-service application.yml
spring:
  application:
    name: auth-service
  config:
    import: "optional:configserver:http://localhost:8888"
```

**No bootstrap.yml needed.** `spring-cloud-starter-config` auto-wires the import when on classpath.

`optional:` prefix prevents startup failure if config server is temporarily down.

---

## 4. Spring Cloud Gateway

### Dependency — WebFlux (reactive, recommended)

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-gateway</artifactId>
  <!-- pulls in spring-cloud-starter-gateway-server-webflux -->
</dependency>

<!-- Rate limiting via Redis (Token Bucket) -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

> MVC alternative: `spring-cloud-starter-gateway-server-mvc` — servlet stack with virtual threads (Java 21). Simpler if no reactive code elsewhere.

### Route config — application.yml

```yaml
spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service        # lb = load-balanced via Eureka
          predicates:
            - Path=/api/auth/**
          filters:
            - StripPrefix=1
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 20
                redis-rate-limiter.burstCapacity: 40
                redis-rate-limiter.requestedTokens: 1
                key-resolver: "#{@ipKeyResolver}"
```

### Key resolver bean (rate limit by IP)

```java
@Bean
KeyResolver ipKeyResolver() {
    return exchange -> Mono.just(
        exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
    );
}
```

---

## 5. Spring Boot Actuator — Eureka Heartbeat

Eureka uses the `/health` endpoint for heartbeat. By default it reports UP regardless of actual health.

### Enable real health propagation

```yaml
eureka:
  client:
    healthcheck:
      enabled: true    # propagates Actuator health status to Eureka

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always
```

Without `healthcheck.enabled: true`, Eureka always sees UP even if DB is down.

---

## Summary Table

| Component              | Artifact                                         | Notes                          |
|------------------------|--------------------------------------------------|--------------------------------|
| BOM                    | `spring-cloud-dependencies:2024.0.1`            | Spring Boot 3.4.x compatible   |
| Eureka Server          | `spring-cloud-starter-netflix-eureka-server`    | Port 8761                      |
| Eureka Client          | `spring-cloud-starter-netflix-eureka-client`    | Auto-configured                |
| Config Server          | `spring-cloud-config-server`                    | native profile                 |
| Config Client          | `spring-cloud-starter-config`                   | spring.config.import           |
| Gateway (reactive)     | `spring-cloud-starter-gateway`                  | WebFlux + Netty                |
| Gateway (servlet/VT)   | `spring-cloud-starter-gateway-server-mvc`       | Java 21 virtual threads        |
| Rate Limiting          | `spring-boot-starter-data-redis-reactive`       | Token bucket via Redis         |
| Actuator               | `spring-boot-starter-actuator`                  | Required for Eureka heartbeat  |

---

## Unresolved Questions

1. **Config Server security** — should the `/config` endpoint be secured with Basic Auth or mTLS between services? (env var injection may be sufficient for dev)
2. **Gateway JWT validation** — validate JWT at gateway level (filter) vs pass-through to each service? If at gateway, need shared secret wired from Config Server.
3. **MVC vs WebFlux gateway** — current auth-service is servlet-based (Spring MVC); using WebFlux gateway adds reactive complexity at edge. MVC gateway with virtual threads may be simpler.
4. **Eureka HA** — single instance acceptable for initial setup, but prod needs peer-aware Eureka cluster config.
