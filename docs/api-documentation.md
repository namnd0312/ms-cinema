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

#### Password Change Endpoint

**POST /api/auth/change-password** - Authenticated user changes password

Request body:
```json
{
  "currentPassword": "oldPassword123",
  "newPassword": "newPassword456",
  "confirmPassword": "newPassword456"
}
```

Response codes:
- 200 OK: Password changed successfully; new password added to history
- 400 Bad Request: Validation failed (passwords don't match, new password matches recent history, missing fields)
- 401 Unauthorized: Missing or invalid bearer token
- 500 Internal Error: Server exception

Security notes:
- Current password verified via BCrypt comparison
- New password validated against 3 most recent password hashes (prevents reuse)
- Password history persisted to `password_history` table with timestamp
- Initial password seeded to history on user registration

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
| POST /api/auth/reset-password | token | Complete password reset (validates against 3 recent passwords) |
| POST /api/auth/change-password | Bearer JWT | Change password for authenticated user |
| POST /api/auth/refresh-token | refresh token | Obtain new access token |
| POST /api/auth/logout | Bearer JWT | Logout and blacklist token |
| POST /api/auth/validate-token | none | Validate JWT (for downstream services) |
| GET /api/users/me | Bearer JWT | Get current user profile |

### movie-service (/api/movies, /api/comments)

**Movie Management:**
Documented in MovieController with @Tag and @Operation annotations.

**Ratings (1-5 stars):**
| Endpoint | Auth | Description |
|----------|------|-------------|
| POST /api/movies/{movieId}/ratings | USER | Create or update rating |
| GET /api/movies/{movieId}/ratings | public | Get rating summary (avg, count, user's rating) |

**Comments (flat, soft-deleted):**
| Endpoint | Auth | Method | Description |
|----------|------|--------|-------------|
| /api/movies/{movieId}/comments | public | GET | List comments (paginated, page=0&size=20 default) |
| /api/movies/{movieId}/comments | USER | POST | Post comment with content text |
| /api/comments/{commentId} | USER (owner) | PUT | Update own comment content |
| /api/comments/{commentId} | USER (owner/ADMIN) | DELETE | Soft-delete comment (status→DELETED) |

**Comment Reactions (like/dislike):**
| Endpoint | Auth | Method | Description |
|----------|------|--------|-------------|
| /api/comments/{commentId}/reactions | USER | POST | Toggle like/dislike (send: reactionType: LIKE or DISLIKE) |
| /api/comments/{commentId}/reactions | USER | DELETE | Remove user's reaction |

**Response Codes:**
- 200 OK: Successful operation
- 400 Bad Request: Invalid input (rating not 1-5, missing content, invalid page params)
- 401 Unauthorized: Missing/expired token on protected endpoints
- 403 Forbidden: User lacks role (not owner of comment, not admin)
- 404 Not Found: Movie/comment not found
- 500 Internal Error: Server exception

Swagger UI: http://localhost:8082/swagger-ui.html

### booking-service (/api/bookings)

Documented in BookingController with @Tag and @Operation annotations.
Swagger UI: http://localhost:8083/swagger-ui.html

### payment-service (/api/payments)

Documented in PaymentController with @Tag and @Operation annotations.

**Payment Endpoints:**
| Endpoint | Auth | Description |
|----------|------|-------------|
| POST /api/payments/create-intent | USER | Create Stripe PaymentIntent for booking |
| POST /api/payments/{id}/confirm | USER (owner) | Confirm payment status from Stripe |
| GET /api/payments/{id} | USER (owner) | Get payment details by ID |
| GET /api/payments/my | USER | List user's payment history |
| GET /api/payments | ADMIN | List all payments (admin only) |
| POST /api/payments/{id}/refund | ADMIN | Refund a payment (admin only) |
| POST /api/payments/fake-success | none | Fake success for testing (bypass Stripe) |

**Response Codes:**
- 200 OK: Successful operation
- 401 Unauthorized: Missing/expired token
- 403 Forbidden: User lacks permission (ADMIN only endpoint, payment owner only)
- 404 Not Found: Payment not found
- 500 Internal Error: Server exception

Swagger UI: http://localhost:8084/swagger-ui.html

### notification-service (/api/notifications)

**Real-Time SSE Streaming:**
| Endpoint | Auth | Description |
|----------|------|-------------|
| GET /api/notifications/stream | JWT (query param) | Server-Sent Events stream with 30s heartbeat |

**Notification REST API:**
| Endpoint | Auth | Method | Description |
|----------|------|--------|-------------|
| /api/notifications | USER | GET | Paginated list (page=0, size=20, ordered createdAt DESC) |
| /api/notifications/{id}/read | USER (owner) | PATCH | Mark single notification as read |
| /api/notifications/read-all | USER | PATCH | Mark all user's notifications as read |
| /api/notifications/unread-count | USER | GET | Get count of unread notifications for badge |
| /api/notifications/broadcast | ADMIN | POST | Admin-only test broadcast to all users |

**SSE Stream Details:**
- **Auth:** JWT token via query parameter: `?token=<JWT>`
- **Events Received:**
  - `event: InAppNotificationEvent` — payload with userId, title, message, notificationType
  - `:heartbeat` (comment) — 30-second keep-alive, no processing needed
- **Client Behavior:**
  - Use EventSource API (browser native)
  - Auto-reconnect on disconnect with exponential backoff (1s→30s max)
  - Handle heartbeat as no-op (connection keep-alive)
- **Response Codes:**
  - 200 OK: Stream established, receive events
  - 401 Unauthorized: Invalid/expired JWT
  - 429 Too Many Requests: Emitter registry full (too many concurrent connections)

**Notification CRUD Response Codes:**
- 200 OK: Successful operation
- 401 Unauthorized: Missing/expired token
- 403 Forbidden: User not notification owner (for PATCH single)
- 404 Not Found: Notification not found
- 500 Internal Error: Server exception

**Example Requests:**

GET /api/notifications/stream?token=eyJhbGc...
```
Accept: text/event-stream
```

Returns SSE format:
```
event: InAppNotificationEvent
data: {"userId":123,"title":"Payment Received","message":"Your booking payment confirmed","notificationType":"PAYMENT_SUCCESS"}

:heartbeat

event: InAppNotificationEvent
data: {"userId":123,"title":"New Booking","message":"Booking confirmed for March 20","notificationType":"ADMIN_BROADCAST"}

:heartbeat
```

PATCH /api/notifications/read-all
```
Authorization: Bearer <token>
```

GET /api/notifications/unread-count
```
Authorization: Bearer <token>
Response: {"count": 3}
```

Swagger UI: http://localhost:8085/swagger-ui.html

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
