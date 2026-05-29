# API Documentation

**Project:** ms-cinema
**Framework:** SpringDoc OpenAPI 2.8.4
**OpenAPI Version:** 3.0
**Updated:** April 10, 2026

## Swagger UI Access Points

**Development (Direct Service):**
All services export interactive OpenAPI 3.0 documentation via SpringDoc OpenAPI with Swagger UI on individual ports:

| Service | Swagger UI | OpenAPI JSON | Port |
|---------|-----------|---|------|
| auth-service | http://localhost:8081/swagger-ui.html | /v3/api-docs | 8081 |
| movie-service | http://localhost:8082/swagger-ui.html | /v3/api-docs | 8082 |
| booking-service | http://localhost:8083/swagger-ui.html | /v3/api-docs | 8083 |
| payment-service | http://localhost:8084/swagger-ui.html | /v3/api-docs | 8084 |
| notification-service | http://localhost:8085/swagger-ui.html | /v3/api-docs | 8085 |
| audit-service | http://localhost:8086/swagger-ui.html | /v3/api-docs | 8086 |

## Configuration Architecture

### OpenApiConfig Class

Each service contains an `OpenApiConfig` class in the `config` package that:

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

## Per-Service Swagger / OpenAPI Access via K8s Ingress

**Architecture:** No centralized API Gateway. NGINX K8s Ingress routes traffic path-based to individual service instances. Each service publishes its own OpenAPI spec independently.

**Local Development (Docker Compose):**
- Nginx reverse proxy (port 80) routes to services by path
- Access individual Swagger UIs on per-service ports (8081-8086)
- Example: http://localhost:8081/swagger-ui.html (auth-service)

**Kubernetes Deployment:**
- NGINX Ingress Controller (K8s native) routes by path prefix
- Service URLs via K8s DNS (e.g., auth-service:8081)
- Per-service Swagger UI accessible via ingress hostname + path
- Example: https://cinema.example.com/auth/swagger-ui.html (via Ingress rewrite)

**Routing Rules:**
- `/api/auth/**` → auth-service:8081
- `/api/movies/**` → movie-service:8082
- `/api/bookings/**` → booking-service:8083
- `/api/payments/**` → payment-service:8084
- `/api/notifications/**` → notification-service:8085
- `/api/audit/**` → audit-service:8086 (admin-only)
- `/oauth2/**` → auth-service:8081 (OIDC endpoints, rate-limited 10rps)

## Service-Specific Documentation

### auth-service (/api/auth)

| Endpoint | Auth | Description |
|----------|------|-------------|
| POST /api/auth/login | none | Authenticate with email + password |
| POST /api/auth/register | none | Create new user account (no password field, deferred to activation) |
| GET /api/auth/activate | token param | Activate account via email link (legacy, backward compat for OAuth) |
| POST /api/auth/activate-with-password | token | Activate account and set password (user clicks email link, posts password) |
| POST /api/auth/resend-activation | none | Resend activation email |
| POST /api/auth/forgot-password | none | Initiate password reset flow |
| POST /api/auth/reset-password | token | Complete password reset (validates against 3 recent passwords) |
| POST /api/auth/change-password | Bearer JWT | Change password for authenticated user |
| POST /api/auth/refresh-token | refresh token | Obtain new access token |
| POST /api/auth/logout | Bearer JWT | Logout and blacklist token |
| POST /api/auth/validate-token | none | Validate JWT (for downstream services) |
| GET /api/users/me | Bearer JWT | Get current user profile |

#### auth-service — OAuth2 / OIDC Identity Provider (Phase 02-06)

Public, spec-compliant endpoints advertised in `/.well-known/openid-configuration`.

| Endpoint | Auth | Description |
|----------|------|-------------|
| GET /.well-known/openid-configuration | public | OIDC discovery document |
| GET /oauth2/authorize | session | Authorization-code + PKCE flow entry point |
| POST /oauth2/token | client_secret_basic | Token issuance (auth-code, refresh) |
| POST /oauth2/revoke | client_secret_basic | Token revocation |
| GET /oauth2/jwks | public | RS256 public keys (Cache-Control max-age=3600) |
| GET /userinfo | Bearer (id_token) | OIDC user-info |
| GET /connect/logout | session | RP-initiated logout |
| GET /oauth/consent | session | Consent screen view-model (Angular) |
| GET /api/oauth/consent | session | Consent view-model JSON for the Angular SPA |

#### auth-service — OAuth2 Admin (Phase 03 + 06)

| Endpoint | Auth | Description |
|----------|------|-------------|
| POST /api/admin/oauth-clients | Bearer JWT, ROLE_ADMIN | Register a partner client (returns plaintext secret ONCE) |
| GET /api/admin/oauth-clients/{clientId} | Bearer JWT, ROLE_ADMIN | View client (no secret) |
| PATCH /api/admin/oauth-clients/{clientId} | Bearer JWT, ROLE_ADMIN | Patch metadata |
| POST /api/admin/oauth-clients/{clientId}/rotate-secret | Bearer JWT, ROLE_ADMIN | Rotate client_secret |
| DELETE /api/admin/oauth-clients/{clientId} | Bearer JWT, ROLE_ADMIN | Disable client |
| POST /api/admin/signing-keys/rotate | Bearer JWT, ROLE_ADMIN | Rotate RSA signing key (ACTIVE → RETIRED, mint new ACTIVE) |
| GET /api/admin/signing-keys | Bearer JWT, ROLE_ADMIN | List ACTIVE + RETIRED keys (metadata only) |
| DELETE /api/admin/signing-keys/{kid} | Bearer JWT, ROLE_ADMIN | Hard-delete a RETIRED key |

**PKCE Authorization Code Flow Example:**
```bash
# 1. Generate PKCE code_challenge (code_challenge_method=S256)
code_verifier=$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')
code_challenge=$(echo -n "$code_verifier" | openssl dgst -sha256 -binary | base64 | tr '+/' '-_' | tr -d '=')

# 2. Redirect user to authorization endpoint
curl -L "https://auth.example.com/oauth2/authorize?client_id=YOUR_CLIENT_ID&redirect_uri=https://yourapp.com/callback&response_type=code&scope=openid%20profile%20email&code_challenge=$code_challenge&code_challenge_method=S256&state=RANDOM_STATE"

# 3. User grants consent, browser redirects to callback with ?code=AUTH_CODE&state=RANDOM_STATE
# 4. Exchange auth code for tokens
curl -X POST https://auth.example.com/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=AUTH_CODE&client_id=YOUR_CLIENT_ID&client_secret=YOUR_CLIENT_SECRET&redirect_uri=https://yourapp.com/callback&code_verifier=$code_verifier"

# Response: { "access_token": "...", "id_token": "...", "refresh_token": "...", "expires_in": 3600 }
```

**OpenID Configuration Endpoint:**
```bash
curl https://auth.example.com/.well-known/openid-configuration | jq
# Returns: issuer, authorization_endpoint, token_endpoint, userinfo_endpoint, jwks_uri, scopes_supported, etc.
```

See [sso-partner-integration-guide.md](./sso-partner-integration-guide.md) for the partner-facing flow walkthrough and [sso-key-rotation-runbook.md](./sso-key-rotation-runbook.md) for the rotation procedure.

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

### booking-service (/ws WebSocket)

**Real-Time Seat Availability Updates (NEW - March 22, 2026):**
| Endpoint | Auth | Protocol | Description |
|----------|------|----------|-------------|
| /ws | JWT | WebSocket STOMP | Subscribe to real-time seat status updates |

**WebSocket Connection Details:**
- **Endpoint:** `ws://localhost/ws` (nginx proxy) or `ws://localhost:8083/ws` (direct to booking-service)
- **Protocol:** STOMP v1.2 over WebSocket (SockJS fallback supported)
- **Authentication:** JWT Bearer token required during WebSocket handshake
- **Subscribe to:** `/topic/showtime/{showtimeId}/seats` — Receive seat status updates for specific showtime

**Message Format (SeatStatusMessage):**
```json
{
  "showtimeId": 123,
  "seatId": "A5",
  "status": "LOCKED",
  "userId": 456,
  "action": "LOCK"
}
```

**Action Types:**
- `LOCK` — User reserved seat (temporary, waiting for payment confirmation)
- `RESERVE` — Payment confirmed, seat permanently booked
- `CANCEL` — Booking expired or payment failed, seat released back to available

**Connection Example (JavaScript):**
```javascript
import SockJS from 'sockjs-client';
import Stomp from '@stomp/stompjs';

const socket = new SockJS('http://localhost/ws');
const stompClient = Stomp.over(socket);

stompClient.connect(
  { 'Authorization': `Bearer ${jwtToken}` },
  () => {
    stompClient.subscribe(`/topic/showtime/${showtimeId}/seats`, (message) => {
      const seatUpdate = JSON.parse(message.body);
      console.log(`Seat ${seatUpdate.seatId} status: ${seatUpdate.action}`);
    });
  }
);
```

**Error Handling:**
- 401 Unauthorized: Invalid/expired JWT during handshake
- Connection timeout: 30s inactivity on nginx proxy (keep-alive heartbeat recommended)
- Disconnection: Auto-reconnect with exponential backoff (1s→30s max)

**Performance:**
- Latency: <100ms (direct connection, bypasses HTTP gateway)
- Broadcast: All connected clients receive updates simultaneously
- Scalability: Spring WebSocket in-memory broker for single-instance deployment

**Nginx Routing (NEW March 22, 2026):**
- /ws/* routes directly to booking-service:8083
- WebSocket upgrade headers configured: `Connection: Upgrade`, `Upgrade: websocket`
- Direct nginx proxy to booking-service for low-latency WebSocket connections

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

### payment-service (/api/payments/reconciliation)

**Payment Reconciliation (Admin-Only):**

| Endpoint | Auth | Method | Description |
|----------|------|--------|-------------|
| /api/payments/reconciliation/trigger | ADMIN | POST | Trigger manual reconciliation for date range |
| /api/payments/reconciliation/runs | ADMIN | GET | List all reconciliation runs (paginated) |
| /api/payments/reconciliation/runs/{runId} | ADMIN | GET | Get single reconciliation run details |
| /api/payments/reconciliation/runs/{runId}/items | ADMIN | GET | List items from a run (paginated, filterable) |
| /api/payments/reconciliation/summary | ADMIN | GET | Get latest reconciliation run summary |
| /api/payments/reconciliation/items/{itemId}/resolve | ADMIN | PUT | Mark reconciliation item as resolved |

**POST /api/payments/reconciliation/trigger - Request:**
```json
{
  "startDate": "2026-03-01",
  "endDate": "2026-03-31"
}
```
- Validates date range ≤ 31 days
- Creates ReconciliationRun with status=RUNNING
- Launches Spring Batch job asynchronously
- Returns: run ID, status, estimated completion time

**GET /api/payments/reconciliation/runs - Query Parameters:**
- `page` (default: 0) - Pagination page number
- `size` (default: 20) - Results per page
- Response: ReconciliationRun list with matchedCount, mismatchedCount, missingLocalCount, missingStripeCount, totalChecked

**GET /api/payments/reconciliation/runs/{runId}/items - Query Parameters:**
- `page` (default: 0) - Pagination page number
- `size` (default: 20) - Results per page
- `discrepancyType` (optional) - Filter [MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE]
- Response: ReconciliationItem list with stripePaymentIntentId, localPaymentId, discrepancyType, amounts, statuses, resolved flag

**Response Format (ReconciliationSummary):**
```json
{
  "runId": "550e8400-e29b-41d4-a716-446655440000",
  "startDate": "2026-03-01",
  "endDate": "2026-03-31",
  "status": "COMPLETED",
  "matchedCount": 98,
  "mismatchedCount": 2,
  "missingLocalCount": 0,
  "missingStripeCount": 1,
  "totalChecked": 101,
  "createdAt": "2026-03-31T02:00:00Z",
  "completedAt": "2026-03-31T02:15:32Z"
}
```

**Response Codes:**
- 200 OK: Reconciliation data retrieved or triggered successfully
- 400 Bad Request: Invalid date range (>31 days), invalid date format, or malformed request
- 401 Unauthorized: Missing or invalid Bearer token
- 403 Forbidden: User lacks ADMIN role
- 404 Not Found: Run ID or item ID not found
- 500 Internal Error: Batch job failure or server exception

**Notes:**
- All endpoints require `@PreAuthorize("hasRole('ADMIN')")`
- Scheduled reconciliation runs daily at 2 AM Asia/Saigon (configurable)
- Manual trigger via POST /trigger accepts custom date ranges (max 31 days)
- DiscrepancyType enum: MATCHED (amounts+statuses equal), STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL (in Stripe, not local DB), MISSING_STRIPE (in local DB, not Stripe)
- ReconciliationItem.resolved flag can be set to true via PUT /items/{itemId}/resolve for audit trail

### audit-service (/api/audit)

**Audit Log Management (Admin-Only):**

| Endpoint | Auth | Method | Description |
|----------|------|--------|-------------|
| /api/audit/logs | ADMIN | GET | List audit logs (paginated, filtered) |
| /api/audit/logs/{id} | ADMIN | GET | Retrieve single audit log entry |

**GET /api/audit/logs Query Parameters:**
- `page` (default: 0) - Pagination page number
- `size` (default: 20, max: 100) - Results per page
- `userId` (optional) - Filter by user ID
- `action` (optional) - Filter by audit action [LOGIN, LOGOUT, REGISTER, CHANGE_PASSWORD, CREATE_MOVIE, UPDATE_MOVIE, DELETE_MOVIE, CREATE_SHOWTIME, UPDATE_SHOWTIME, RESERVE_BOOKING, CANCEL_BOOKING, CREATE_PAYMENT]
- `entityType` (optional) - Filter by entity type (e.g., USER, MOVIE, BOOKING)
- `startDate` (optional) - Filter from date (ISO 8601: yyyy-MM-dd)
- `endDate` (optional) - Filter to date (ISO 8601: yyyy-MM-dd)

**Response Format:**
```json
{
  "content": [
    {
      "id": 1,
      "eventId": "550e8400-e29b-41d4-a716-446655440000",
      "userId": "user123",
      "userIp": "192.168.1.1",
      "action": "LOGIN",
      "entityType": "USER",
      "entityId": "user123",
      "beforeState": null,
      "afterState": null,
      "sourceService": "auth-service",
      "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
      "requestPath": "/api/auth/login",
      "createdAt": "2026-03-22T10:30:45Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalElements": 1500,
    "totalPages": 75
  }
}
```

**Response Codes:**
- 200 OK: Audit logs retrieved successfully
- 400 Bad Request: Invalid query parameters or date format
- 401 Unauthorized: Missing or invalid Bearer token
- 403 Forbidden: User lacks ADMIN role
- 500 Internal Error: Server exception

**Notes:**
- All endpoints require `@PreAuthorize("hasRole('ADMIN')")`
- Results sorted by `createdAt DESC` (newest first)
- `beforeState` is NULL in v1 (reserved for Envers integration v2)
- `afterState` contains JSON serialized entity state post-change
- LOGIN action omits `afterState` to prevent JWT token leakage
- `eventId` is globally unique; prevents duplicate audits on Kafka retries

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
