---
title: "Phase 4 — Split Services"
status: pending
priority: P1
effort: 3h
---

# Phase 4 — Split Services

## Context Links
- [Plan overview](plan.md)
- [Phase 3 — Update service deps](phase-03-update-service-dependencies.md)

## Overview

Extract each Java service into its own GitHub repo. Services have no inter-service Maven deps (they communicate via Eureka/HTTP/Kafka at runtime). Split order does not matter — all are independent once pom.xml is standalone.

## Key Insights

- auth-service Dockerfile currently uses `context: .` (monorepo root) — needs new Dockerfile with `context: .` (service root)
- All other services already use `context: ./<service>/` with their own Dockerfile
- Each service needs: standalone pom.xml (from Phase 3), Dockerfile, .gitignore, GitHub Actions workflow
- `mvnw` wrapper should be included in each repo for CI

## Requirements

### Target Repos

| Repo | Source Dir | Port | Internal Deps |
|------|-----------|------|---------------|
| `cinema-auth-service` | `auth-service/` | 8081 | kafka-events:1.0.0 |
| `cinema-api-gateway` | `api-gateway/` | 8080 | none |
| `cinema-movie-service` | `movie-service/` | 8082 | jwt-starter:1.0.0, kafka-events:1.0.0 |
| `cinema-booking-service` | `booking-service/` | 8083 | jwt-starter:1.0.0, kafka-events:1.0.0 |
| `cinema-payment-service` | `payment-service/` | 8084 | jwt-starter:1.0.0, kafka-events:1.0.0 |
| `cinema-notification-service` | `notification-service/` | 8085 | kafka-events:1.0.0 |
| `cinema-eureka-server` | `eureka-server/` | 8761 | none |
| `cinema-config-server` | `config-server/` | 8888 | none |

## Architecture

### Per-Service Repo Structure

```
cinema-{service}/
├── .github/workflows/build.yml     # build + push Docker to GHCR
├── .gitignore
├── Dockerfile                       # multi-stage (build + runtime)
├── mvnw, .mvn/                     # Maven wrapper
├── pom.xml                          # standalone (from Phase 3)
└── src/
    ├── main/java/...
    └── main/resources/...
```

### Dockerfile Template (Multi-Stage)

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY mvnw .mvn/ ./
COPY pom.xml .
RUN ./mvnw dependency:go-offline -B
COPY src src
RUN ./mvnw clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE {PORT}
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Note for CI**: Maven needs `settings.xml` to resolve GitHub Packages deps during Docker build. Use build args or multi-stage with cached deps.

### auth-service Special Case

Current `Dockerfile` is at monorepo root and builds from root context. New Dockerfile must:
- Live inside `cinema-auth-service/`
- Use service-local context
- Include `application.yml` and `roles.sql` from `src/main/resources/`

## Implementation Steps

### Per Service (repeat 8 times)

1. Create GitHub repo: `gh repo create OWNER/cinema-{service} --public`
2. Extract with history:
   ```bash
   cd /path/to/monorepo
   git subtree split --prefix={service} -b split/{service}
   ```
3. Push to new repo:
   ```bash
   cd /tmp && git clone https://github.com/OWNER/cinema-{service}.git
   cd cinema-{service}
   git remote add monorepo /path/to/monorepo
   git fetch monorepo split/{service}
   git merge monorepo/split/{service} --allow-unrelated-histories
   ```
4. Replace `pom.xml` with standalone version (from Phase 3)
5. Add `.gitignore`, `Dockerfile`, `.github/workflows/build.yml`
6. Copy `mvnw`, `.mvn/` from monorepo
7. `mvn clean package -DskipTests` — verify compilation
8. Push, verify GitHub Actions builds successfully

### Split Order (suggested)

1. eureka-server, config-server (no shared lib deps, simplest)
2. api-gateway (no shared lib deps)
3. auth-service (kafka-events only, special Dockerfile)
4. notification-service (kafka-events only)
5. movie-service, booking-service, payment-service (both shared libs)

## Todo List

- [ ] Create + split cinema-eureka-server
- [ ] Create + split cinema-config-server
- [ ] Create + split cinema-api-gateway
- [ ] Create + split cinema-auth-service (fix Dockerfile)
- [ ] Create + split cinema-notification-service
- [ ] Create + split cinema-movie-service
- [ ] Create + split cinema-booking-service
- [ ] Create + split cinema-payment-service
- [ ] Verify `mvn clean package` passes for each
- [ ] Verify GitHub Actions builds pass for each

## Success Criteria

- Each service repo builds independently
- Docker image builds and runs
- Service registers with Eureka when started
- GitHub Actions CI passes

## Risk Assessment

- **Maven settings.xml in Docker**: use build-arg or copy settings.xml during build stage
- **config-server config-repo**: config-server has a `config-repo/` dir for native config — must be included in split
- **Test failures**: some tests may need H2/testcontainers; `DskipTests` for initial split, fix tests later

## Security Considerations

- Do not commit `settings.xml` with PAT
- Dockerfile should not embed secrets; use env vars at runtime
- GHCR images default to private — set visibility to match org policy
