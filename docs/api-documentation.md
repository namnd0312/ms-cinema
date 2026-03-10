# API Documentation

**Project:** ms-cinema
**Framework:** SpringDoc OpenAPI 2.8.4
**OpenAPI Version:** 3.0

## Swagger UI Access Points

All services export interactive OpenAPI 3.0 documentation via SpringDoc OpenAPI with Swagger UI:

| Service | Swagger UI | OpenAPI JSON | Port |
|---------|-----------|---|------|
| **api-gateway (aggregated)** | http://localhost:8080/swagger-ui.html | /v3/api-docs | 8080 |
| auth-service | http://localhost:8081/swagger-ui.html | /v3/api-docs | 8081 |
| movie-service | http://localhost:8082/swagger-ui.html | /v3/api-docs | 8082 |
| booking-service | http://localhost:8083/swagger-ui.html | /v3/api-docs | 8083 |
| payment-service | http://localhost:8084/swagger-ui.html | /v3/api-docs | 8084 |

## Configuration Architecture

### OpenApiConfig Class

Each service (auth-service, movie-service, booking-service, payment-service, api-gateway) contains an `OpenApiConfig` class in the `config` package that:

**1. Customizes API Metadata:**
- API title, description, version
- Contact information (support email, url)
- License information

**2. Defines Server URLs:**
```
Development: http://localhost:{port}
Production: https://api.example.com (if configured)
```

**3. Configures Security Scheme:**
- Security scheme name: `bearerAuth`
- Type: HTTP Bearer with JWT tokens
- Bearer format: JWT

**4. Groups Operations by Tag:**
- Organizes endpoints into logical groups (e.g., Authentication, User Profile)
- Each tag includes description and order

### Controller Annotations

All controllers use SpringDoc annotations for comprehensive documentation:

```
@Tag(name="Authentication", description="User authentication endpoints")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  @Operation(
    summary="User login",
    description="Authenticates user with email and password. Returns JWT tokens."
  )
  @ApiResponse(responseCode="200", description="Login successful")
  @ApiResponse(responseCode="401", description="Invalid credentials or inactive account")
  @ApiResponse(responseCode="423", description="Account locked after failed attempts")
  @SecurityRequirement(name="bearerAuth")
  @PostMapping("/login")
  public ResponseEntity<?> authenticateUser(...) { ... }
}
```

**Key Annotations:**
- `@Tag` — Groups related endpoints; appears in Swagger UI sidebar
- `@Operation` — Describes endpoint purpose and behavior
- `@ApiResponse` — Documents each HTTP response code (200, 400, 401, etc.)
- `@SecurityRequirement` — Marks endpoint as requiring authentication
- `@Parameter` — Documents request/query parameters
- `@RequestBody` — Describes request body structure

## OpenAPI JSON Structure

Example OpenAPI 3.0 schema (from `/v3/api-docs`):

```json
{
  "openapi": "3.0.1",
  "info": {
    "title": "MS Cinema API",
    "version": "0.0.1-SNAPSHOT"
  },
  "servers": [
    {
      "url": "http://localhost:8081",
      "description": "Development Server"
    }
  ],
  "paths": {
    "/api/auth/login": {
      "post": {
        "tags": ["Authentication"],
        "operationId": "authenticateUser",
        "requestBody": { ... },
        "responses": { ... },
        "security": [{ "bearerAuth": [] }]
      }
    }
  },
  "components": {
    "securitySchemes": {
      "bearerAuth": {
        "type": "http",
        "scheme": "bearer",
        "bearerFormat": "JWT"
      }
    },
    "schemas": { ... }
  }
}
```

## API Gateway Aggregation

The API Gateway (:8080) aggregates OpenAPI docs from all downstream services:

**How it works:**
1. API Gateway has its own `OpenApiConfig` (port 8080)
2. Clients access http://localhost:8080/swagger-ui.html
3. Gateway's Swagger UI displays routes to each service
4. Each route shows operations from that service's OpenAPI spec

**Server URLs in Gateway Swagger:**
- auth-service routes: /api/auth/**, /api/users/**
- movie-service routes: /api/movies/**
- booking-service routes: /api/bookings/**
- payment-service routes: /api/payments/**

## Service-Specific Documentation

### auth-service (/api/auth)

| Endpoint | Auth | Description |
|----------|------|-------------|
| POST /api/auth/login | none | Authenticate with email + password |
| POST /api/auth/register | none | Create new user account |
| GET /api/auth/activate | token param | Activate account via email link |
| POST /api/auth/resend-activation | none | Resend activation email |
| POST /api/auth/forgot-password | none | Initiate password reset flow |
| POST /api/auth/reset-password | token | Complete password reset |
| POST /api/auth/refresh-token | refresh token | Obtain new access token |
| POST /api/auth/logout | Bearer JWT | Logout and blacklist token |
| POST /api/auth/validate-token | none | Validate JWT (for downstream services) |
| GET /api/users/me | Bearer JWT | Get current user profile |

### movie-service (/api/movies)

Documented in MovieController with @Tag and @Operation annotations.
Swagger UI: http://localhost:8082/swagger-ui.html

### booking-service (/api/bookings)

Documented in BookingController with @Tag and @Operation annotations.
Swagger UI: http://localhost:8083/swagger-ui.html

### payment-service (/api/payments)

Documented in PaymentController with @Tag and @Operation annotations.
Swagger UI: http://localhost:8084/swagger-ui.html

## Security in OpenAPI

### Bearer Token Authentication

All protected endpoints require `Authorization: Bearer <token>` header:

```bash
curl -H "Authorization: Bearer eyJhbGc..." http://localhost:8081/api/users/me
```

**In Swagger UI:**
1. Click "Authorize" button (top-right)
2. Paste JWT token (without "Bearer " prefix)
3. Swagger UI includes token in all subsequent requests

### Endpoint Security Declaration

Protected endpoints show `🔒 Authorize` badge in Swagger UI:

```
@SecurityRequirement(name="bearerAuth")
@GetMapping("/users/me")
public ResponseEntity<?> getUserProfile(...) { ... }
```

Public endpoints (login, register) do NOT include `@SecurityRequirement`.

## Integration with Code Generation Tools

### OpenAPI Codegen

Generate client libraries from any service's `/v3/api-docs`:

```bash
# Generate TypeScript client from auth-service
openapi-generator-cli generate \
  -i http://localhost:8081/v3/api-docs \
  -g typescript-axios \
  -o ./generated-client

# Generate Java client from API Gateway
openapi-generator-cli generate \
  -i http://localhost:8080/v3/api-docs \
  -g java \
  -o ./java-client
```

### Swagger Editor

Import OpenAPI JSON directly into Swagger Editor:
1. Visit https://editor.swagger.io/
2. File → Import URL → paste service's `/v3/api-docs` URL
3. Explore API documentation and test endpoints

## Validation & Best Practices

### Controller Annotation Checklist

Before committing controller code, ensure:
- ✓ `@Tag(name="...", description="...")` on class
- ✓ `@Operation(summary="...", description="...")` on each endpoint
- ✓ `@ApiResponse(responseCode="...", description="...")` for all response codes (200, 400, 401, 403, 404, 500)
- ✓ `@SecurityRequirement(name="bearerAuth")` on protected endpoints
- ✓ `@RequestBody` documents request body fields
- ✓ `@Parameter` documents query/path parameters

### Common Response Codes

| Code | Meaning | Use Case |
|------|---------|----------|
| 200 | OK | Successful request |
| 400 | Bad Request | Invalid input validation failure |
| 401 | Unauthorized | Missing or invalid auth token |
| 403 | Forbidden | User lacks required role |
| 404 | Not Found | Resource doesn't exist |
| 423 | Locked | Account locked after failed attempts |
| 500 | Internal Error | Server-side exception |

## Troubleshooting

**Swagger UI shows empty spec**
- Check if service is running on expected port
- Verify `OpenApiConfig` bean is initialized
- Check logs for SpringDoc initialization errors

**Missing endpoints in Swagger UI**
- Ensure controller has `@RestController` annotation
- Verify controller is in Spring component scan path
- Check `@Tag` and `@Operation` annotations are present

**Bearer token not working in Swagger UI**
- Click "Authorize" button, not individual endpoint "Execute"
- Paste token without "Bearer " prefix
- Token must not be expired (default: 15 min)

**CORS errors when accessing /v3/api-docs**
- Verify CORS is enabled in SecurityConfig
- Check if client origin is in allowed origins list
