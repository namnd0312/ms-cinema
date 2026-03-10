# Phase 03: Controller Annotations

## Context Links
- [Plan overview](./plan.md)
- [Phase 02](./phase-02-openapi-config-and-security.md)

## Overview
- **Priority:** Medium
- **Status:** Pending
- **Description:** Add OpenAPI annotations (`@Tag`, `@Operation`, `@ApiResponse`, `@SecurityRequirement`) to all existing controllers for rich Swagger documentation.

## Key Insights
- SpringDoc auto-detects controllers and generates basic docs without annotations
- Annotations add: operation summaries, response schemas, grouping tags, security requirements
- `@SecurityRequirement(name = "bearerAuth")` marks endpoints requiring JWT
- Public endpoints (login, register) don't need `@SecurityRequirement`

## Requirements

### Functional
- Every controller has `@Tag` for grouping
- Every endpoint has `@Operation` with summary
- Protected endpoints have `@SecurityRequirement(name = "bearerAuth")`
- Key error responses documented with `@ApiResponse`

### Non-functional
- Annotations concise, no verbose descriptions
- Don't add annotations that duplicate what SpringDoc auto-detects (request body, path params)

## Related Code Files

### Controllers to Annotate

| File | Endpoints | Tag |
|------|-----------|-----|
| `auth-service/.../controller/AuthController.java` | login, register, activate, forgot/reset-password, refresh-token, logout | Authentication |
| `auth-service/.../controller/TokenValidationController.java` | validate-token, users/me | Token Validation |
| `auth-service/.../controller/TestController.java` | test endpoints | Test |
| `movie-service/.../controller/MovieController.java` | CRUD movies | Movies |
| `movie-service/.../controller/TheaterController.java` | CRUD theaters | Theaters |
| `movie-service/.../controller/ShowtimeController.java` | CRUD showtimes | Showtimes |
| `booking-service/.../controller/BookingController.java` | create/confirm/cancel bookings | Bookings |
| `payment-service/.../controller/PaymentController.java` | create/query payments | Payments |
| `payment-service/.../controller/StripeWebhookController.java` | webhook receiver | Stripe Webhooks |

## Implementation Steps

### Annotation Pattern

**Class-level:**
```java
@Tag(name = "Authentication", description = "JWT auth endpoints")
@RestController
@RequestMapping("/api/auth")
public class AuthController {
```

**Public endpoint:**
```java
@Operation(summary = "Login with email and password")
@ApiResponse(responseCode = "200", description = "JWT token pair returned")
@ApiResponse(responseCode = "401", description = "Invalid credentials")
@ApiResponse(responseCode = "423", description = "Account locked")
@PostMapping("/login")
public ResponseEntity<?> authenticateUser(@RequestBody LoginRequestDto request) {
```

**Protected endpoint:**
```java
@Operation(summary = "Logout and blacklist current token")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "200", description = "Logged out")
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletRequest request) {
```

### Per-Controller Details

#### AuthController.java (~230 lines)
- `@Tag(name = "Authentication")`
- `/login` — @Operation(summary="Login"), responses: 200, 401, 423
- `/register` — @Operation(summary="Register new user"), responses: 200, 400
- `/activate` — @Operation(summary="Activate account via email token"), responses: 200, 400
- `/resend-activation` — @Operation(summary="Resend activation email"), responses: 200
- `/forgot-password` — @Operation(summary="Request password reset email"), responses: 200
- `/reset-password` — @Operation(summary="Reset password with token"), responses: 200, 400
- `/refresh-token` — @Operation(summary="Refresh access token"), responses: 200, 400
- `/logout` — @Operation + @SecurityRequirement, responses: 200

#### TokenValidationController.java (99 lines)
- `@Tag(name = "Token Validation")`
- `/api/auth/validate-token` — @Operation(summary="Validate JWT token"), responses: 200
- `/api/users/me` — @Operation + @SecurityRequirement, responses: 200, 401

#### MovieController.java
- `@Tag(name = "Movies")`
- GET endpoints: no @SecurityRequirement
- POST/PUT/DELETE: @SecurityRequirement(name = "bearerAuth")

#### TheaterController.java
- `@Tag(name = "Theaters")`
- Same pattern as MovieController

#### ShowtimeController.java
- `@Tag(name = "Showtimes")`
- Same pattern as MovieController

#### BookingController.java
- `@Tag(name = "Bookings")`
- All endpoints: @SecurityRequirement (all require auth)

#### PaymentController.java
- `@Tag(name = "Payments")`
- All endpoints: @SecurityRequirement (all require auth)

#### StripeWebhookController.java
- `@Tag(name = "Stripe Webhooks")`
- Webhook: no @SecurityRequirement (called by Stripe, no JWT)

## Todo List
- [ ] Annotate AuthController.java
- [ ] Annotate TokenValidationController.java
- [ ] Annotate TestController.java
- [ ] Annotate MovieController.java
- [ ] Annotate TheaterController.java
- [ ] Annotate ShowtimeController.java
- [ ] Annotate BookingController.java
- [ ] Annotate PaymentController.java
- [ ] Annotate StripeWebhookController.java
- [ ] Run `mvn compile` to verify

## Success Criteria
- All controllers display properly in Swagger UI with tags, summaries, response codes
- "Authorize" button works to set Bearer token
- Protected vs public endpoints clearly distinguished

## Risk Assessment
- **Large AuthController (230 lines):** Adding annotations may push it over 250 lines — acceptable for annotation-only additions
- **DTO schemas:** SpringDoc auto-generates schemas from DTOs via Jackson — no manual Schema annotations needed

## Next Steps
- Phase 04: Gateway aggregation
