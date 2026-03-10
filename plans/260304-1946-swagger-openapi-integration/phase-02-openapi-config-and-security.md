# Phase 02: OpenAPI Config Classes & Security Permits

## Context Links
- [Plan overview](./plan.md)
- [Phase 01](./phase-01-dependencies-and-config.md)

## Overview
- **Priority:** High
- **Status:** Pending
- **Description:** Create OpenAPI configuration class per service defining API metadata + global Bearer JWT security scheme. Update SecurityConfig files and JWT starter publicPaths to permit Swagger UI paths.

## Key Insights
- auth-service and movie-service have their own `SecurityConfig.java` → add requestMatchers directly
- booking-service and payment-service use JWT starter's `publicPaths` config → add Swagger paths to `application.yml`
- api-gateway has no Spring Security → no security changes needed
- Global `@SecurityScheme` annotation on config class applies Bearer auth to entire API

## Requirements

### Functional
- Each service has an `OpenApiConfig.java` class with API title, version, description
- JWT Bearer security scheme defined globally
- Swagger UI paths permitted without authentication

### Non-functional
- Config classes under 40 LOC each (KISS)
- Consistent naming: `OpenApiConfig.java` in each service's `config/` package

## Architecture

### OpenAPI Config Pattern (shared across services)
```java
@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Service Name API",
        version = "1.0",
        description = "Service description"
    )
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
public class OpenApiConfig {}
```

### Swagger Paths to Permit
```
/swagger-ui/**
/v3/api-docs/**
/swagger-ui.html
```

## Related Code Files

### Files to Create
| File | Description |
|------|-------------|
| `auth-service/src/main/java/com/namnd/springjwt/config/OpenApiConfig.java` | Auth API metadata |
| `movie-service/src/main/java/com/namnd/movieservice/config/OpenApiConfig.java` | Movie API metadata |
| `booking-service/src/main/java/com/namnd/bookingservice/config/OpenApiConfig.java` | Booking API metadata |
| `payment-service/src/main/java/com/namnd/paymentservice/config/OpenApiConfig.java` | Payment API metadata |
| `api-gateway/src/main/java/com/namnd/apigateway/config/OpenApiConfig.java` | Gateway API metadata (no JWT scheme) |

### Files to Modify
| File | Change |
|------|--------|
| `auth-service/.../config/security/SecurityConfig.java` | Add Swagger paths to `.requestMatchers().permitAll()` |
| `movie-service/.../config/SecurityConfig.java` | Add Swagger paths to `.requestMatchers().permitAll()` |
| `booking-service/src/main/resources/application.yml` | Add Swagger paths to `jwt.auth.public-paths` |
| `payment-service/src/main/resources/application.yml` | Add Swagger paths to `jwt.auth.public-paths` |

## Implementation Steps

### 1. auth-service OpenApiConfig.java
Package: `com.namnd.springjwt.config`
- Info: title="Auth Service API", version="1.0", description="JWT authentication, registration, password reset, account activation"
- SecurityScheme: bearerAuth (JWT)

### 2. auth-service SecurityConfig.java
Add to existing `requestMatchers().permitAll()`:
```java
.requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info", "/actuator/prometheus",
    "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
```

### 3. movie-service OpenApiConfig.java
Package: `com.namnd.movieservice.config`
- Info: title="Movie Service API", version="1.0", description="Movie catalog, theaters, showtimes"
- SecurityScheme: bearerAuth (JWT)

### 4. movie-service SecurityConfig.java
Add Swagger paths to existing `.permitAll()`:
```java
.requestMatchers("/actuator/health", "/actuator/prometheus",
    "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
```

### 5. booking-service OpenApiConfig.java
Package: `com.namnd.bookingservice.config`
- Info: title="Booking Service API", version="1.0", description="Seat booking and reservation management"
- SecurityScheme: bearerAuth (JWT)

### 6. booking-service application.yml
Add Swagger paths to `jwt.auth.public-paths`:
```yaml
jwt:
  auth:
    public-paths:
      - /actuator/health
      - /actuator/prometheus
      - /swagger-ui/**
      - /v3/api-docs/**
      - /swagger-ui.html
```

### 7. payment-service OpenApiConfig.java
Package: `com.namnd.paymentservice.config`
- Info: title="Payment Service API", version="1.0", description="Stripe payment processing"
- SecurityScheme: bearerAuth (JWT)

### 8. payment-service application.yml
Add Swagger paths to `jwt.auth.public-paths`:
```yaml
jwt:
  auth:
    public-paths:
      - /api/payments/webhook
      - /actuator/health
      - /actuator/prometheus
      - /swagger-ui/**
      - /v3/api-docs/**
      - /swagger-ui.html
```

### 9. api-gateway OpenApiConfig.java
Package: `com.namnd.apigateway.config`
- Info: title="API Gateway", version="1.0", description="Unified API entry point"
- No SecurityScheme (gateway has no auth)

## Todo List
- [ ] Create auth-service OpenApiConfig.java
- [ ] Update auth-service SecurityConfig.java permits
- [ ] Create movie-service OpenApiConfig.java
- [ ] Update movie-service SecurityConfig.java permits
- [ ] Create booking-service OpenApiConfig.java
- [ ] Update booking-service application.yml public-paths
- [ ] Create payment-service OpenApiConfig.java
- [ ] Update payment-service application.yml public-paths
- [ ] Create api-gateway OpenApiConfig.java
- [ ] Run `mvn compile` to verify

## Success Criteria
- All OpenApiConfig.java classes compile
- Swagger UI loads at `/swagger-ui.html` for each service without 401/403
- Bearer JWT "Authorize" button appears in Swagger UI (auth, movie, booking, payment)

## Security Considerations
- Swagger paths are public (no auth required) — acceptable for dev/staging
- For production: disable via `springdoc.api-docs.enabled=false` in prod profile

## Next Steps
- Phase 03: Controller annotations
