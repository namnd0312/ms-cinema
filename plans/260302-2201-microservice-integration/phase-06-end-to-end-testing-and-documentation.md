# Phase 6: End-to-End Testing and Documentation

## Context Links
- [Plan overview](./plan.md)
- [Phase 2 - new endpoints](./phase-02-new-auth-endpoints-validate-token-and-userinfo.md)
- [Phase 3 - JWT starter](./phase-03-jwt-validation-starter-library.md)
- [Phase 5 - infrastructure](./phase-05-infrastructure-eureka-config-server-gateway.md)
- [README.md](/README.md)
- [docs/](/docs/)

## Overview
- **Priority:** P2
- **Status:** pending
- **Effort:** 1h
- **Depends on:** All previous phases (1-5)
- Validate entire microservice stack works end-to-end. Test starter library with a sample consumer. Update README.md and docs/ with new architecture, endpoints, and setup instructions.

## Key Insights
- Focus on integration testing (services talk to each other), not unit tests of individual classes
- Starter library best tested via a minimal sample service that depends on it
- Docker Compose is the primary integration test harness
- Documentation must reflect new multi-module structure, ports, and startup order

## Requirements

### Functional
- Full auth flow works through gateway (register -> activate -> login -> access protected -> refresh -> logout)
- validate-token endpoint returns correct claims
- userinfo endpoint returns user profile
- Starter library auto-configures JWT security in a sample consumer service
- Eureka shows all services registered

### Non-functional
- All existing unit tests pass (`mvn test`)
- Docker Compose starts cleanly from scratch
- README.md accurately reflects new architecture

## Architecture

### Test Flow Diagram
```
Client -> Gateway(:8080) -> auth-service(:8081)
  1. POST /api/auth/register
  2. GET  /api/auth/activate?token=xxx
  3. POST /api/auth/login          -> get JWT + refresh token
  4. POST /api/auth/validate-token -> valid=true + claims
  5. GET  /api/users/me            -> user profile (authenticated)
  6. POST /api/auth/refresh-token  -> new JWT pair
  7. POST /api/auth/logout         -> token blacklisted
  8. POST /api/auth/validate-token -> valid=false (blacklisted)
```

### Starter Library Test (sample-service)
```
sample-service(:8082)
  - depends on jwt-auth-spring-boot-starter
  - has one endpoint: GET /api/sample/hello (authenticated)
  - jwt.auth.secret from Config Server
  - registers with Eureka
  - Gateway route: /api/sample/** -> sample-service
```

## Related Code Files

### Files to Modify
- `/README.md` -- complete rewrite for microservice architecture
- `/docs/system-architecture.md` -- update with Spring Cloud components
- `/docs/codebase-summary.md` -- update module listing
- `/docs/deployment-guide.md` -- update Docker Compose instructions

### Files to Create (optional, for starter validation)
- `sample-service/pom.xml` -- minimal Spring Boot app with starter dependency
- `sample-service/src/main/java/.../SampleServiceApplication.java`
- `sample-service/src/main/java/.../SampleController.java`
- `sample-service/src/main/resources/application.yml`
- `sample-service/Dockerfile`

Note: sample-service is optional -- for validation only, can be excluded from default Maven build with a profile.

## Implementation Steps

### 1. Run full Maven build
```bash
mvn clean install
```
All modules must build. All existing unit tests must pass.

### 2. Docker Compose smoke test
```bash
docker-compose up --build
```
Wait for all services healthy. Verify in Eureka dashboard (http://localhost:8761):
- auth-service registered
- config-server registered
- api-gateway registered

### 3. Test auth flow through gateway

```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com","password":"pass123","fullName":"Test User","roles":[{"name":"ROLE_USER"}]}'

# Login (after activation)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@test.com","password":"pass123"}'
# Save the JWT token from response

# Validate token
curl -X POST http://localhost:8080/api/auth/validate-token \
  -H "Content-Type: application/json" \
  -d '{"token":"<jwt-from-login>"}'
# Expect: {"valid":true,"userId":1,"email":"test@test.com","roles":["ROLE_USER"]}

# User info
curl -X GET http://localhost:8080/api/users/me \
  -H "Authorization: Bearer <jwt-from-login>"
# Expect: {"id":1,"email":"test@test.com","username":"test","fullName":"Test User","roles":["ROLE_USER"]}

# Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <jwt-from-login>"

# Validate blacklisted token
curl -X POST http://localhost:8080/api/auth/validate-token \
  -H "Content-Type: application/json" \
  -d '{"token":"<jwt-from-login>"}'
# Expect: {"valid":false}
```

### 4. Test Config Server serves config

```bash
curl http://localhost:8888/auth-service/default
# Should return JSON with JWT secret and auth-service properties
```

### 5. (Optional) Create sample-service to test starter

Minimal Spring Boot app:

**SampleController.java:**
```java
@RestController
@RequestMapping("/api/sample")
public class SampleController {
    @GetMapping("/hello")
    public ResponseEntity<String> hello(@AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok("Hello from sample-service! User: " + principal);
    }
}
```

**application.yml:**
```yaml
server:
  port: 8082
spring:
  application:
    name: sample-service
  config:
    import: "optional:configserver:http://localhost:8888"
jwt:
  auth:
    secret: ${namnd.app.jwtSecret}
    public-paths:
      - /actuator/health
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

Test: login via auth-service, use JWT to call sample-service's `/api/sample/hello` -> should return 200 with principal info.

### 6. Update README.md

Key sections to update:
- **Quick Start**: multi-module build instructions, Docker Compose with all services
- **Architecture**: Spring Cloud diagram (Eureka, Config Server, Gateway, auth-service)
- **Ports**: 8761 (Eureka), 8888 (Config Server), 8080 (Gateway), 8081 (auth-service)
- **API Reference**: add validate-token and userinfo endpoints
- **Project Structure**: multi-module layout
- **Starter Library Usage**: how to add JWT starter to a new service
- **Configuration**: centralized config via Config Server

### 7. Update docs/

- `docs/system-architecture.md` -- add Spring Cloud components, service topology diagram
- `docs/codebase-summary.md` -- list all 6 modules with descriptions
- `docs/deployment-guide.md` -- updated Docker Compose instructions, service startup order

## Todo List
- [ ] Run `mvn clean install` -- all modules build, all tests pass
- [ ] Run `docker-compose up --build` -- all services start
- [ ] Verify Eureka dashboard shows all services registered
- [ ] Verify Config Server returns auth-service config
- [ ] Test full auth flow through gateway (register, login, validate-token, userinfo, logout)
- [ ] Test validate-token returns valid=false for blacklisted token
- [ ] (Optional) Create sample-service and test starter auto-configuration
- [ ] Update README.md with new architecture and setup instructions
- [ ] Update docs/system-architecture.md
- [ ] Update docs/codebase-summary.md
- [ ] Update docs/deployment-guide.md

## Success Criteria
- `mvn clean install` passes with 0 failures
- Docker Compose starts all 6 containers (postgres, redis, eureka, config, gateway, auth-service)
- Eureka dashboard shows 3 registered services (auth-service, config-server, api-gateway)
- Complete auth flow works end-to-end via gateway
- validate-token and userinfo endpoints return expected responses
- README.md accurately describes the microservice setup
- Docs updated to reflect multi-module architecture

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Services timeout waiting for dependencies on first startup | Medium | Use depends_on in compose + optional config import + retry |
| Email activation requires real SMTP in test | Low | Use database query to get activation token for testing |
| Sample service conflicts in Maven build | Low | Use Maven profile to exclude from default build |
| Docker build cache invalidation rebuilds everything | Low | Order Dockerfile layers (deps first, code last) |

## Security Considerations
- Test that unauthenticated requests to protected endpoints return 401 through gateway
- Test that validate-token does not leak sensitive data beyond what's in the JWT
- Verify Config Server does not expose JWT secret without proper network isolation
- Ensure docker-compose only exposes gateway port (8080) externally in production config

## Unresolved Questions
- Should sample-service be a permanent module or just a test artifact?
  - Recommendation: keep it in a `samples/` directory with its own README, excluded from default build via Maven profile
- Should gateway have rate limiting in initial release?
  - Recommendation: no, add in future iteration (YAGNI)
- Health check strategy for docker-compose (healthcheck vs depends_on)?
  - Recommendation: use depends_on for now, add healthchecks in production compose file
