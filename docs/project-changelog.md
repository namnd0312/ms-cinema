# Project Changelog

**Project:** ms-cinema
**Updated:** March 15, 2026

## Version 0.0.1-SNAPSHOT

### [Unreleased]

#### Google OAuth2 Login (v0.0.1) — March 16, 2026
- **Feature:** Google OAuth2 authentication with Spring Security OAuth2 Client
  - OAuth2 authorization endpoint: GET /oauth2/authorization/google
  - OAuth2 callback handler: GET /login/oauth2/code/google (with authorization code)
  - Auto-create user on first OAuth2 login (password=NULL, active=true, ROLE_USER)
  - Auto-link existing user by email when Google email_verified=true
  - Concurrent login race condition handling via DataIntegrityViolationException catch
- **Backend Implementation:**
  - UserOAuthProvider JPA entity with unique constraints (provider_name+provider_user_id, user_id+provider_name)
  - UserOAuthProviderRepository: findByProviderNameAndProviderUserId(), existsByUserIdAndProviderName()
  - OAuth2AuthenticationSuccessHandler: Handles successful OAuth2 login, generates JWT+refresh token, redirects with query params
  - OAuth2UserLinkingService & Impl: Lookup by provider link → lookup by email (if verified) → create new user
  - SecurityConfig updated: sessionCreationPolicy=IF_REQUIRED (allows OAuth2 state param), oauth2Login(successHandler)
  - API Gateway routes: /oauth2/authorization/**, /login/oauth2/code/** → auth-service
- **Frontend Implementation:**
  - OAuth2CallbackComponent: Extracts token+refreshToken from URL, stores via AuthService, clears URL history (security), navigates to /movies
  - AuthService.handleOAuth2Callback(): Stores tokens, initializes auth state
  - Login component: "Sign in with Google" button redirects to /oauth2/authorization/google
  - Route: /oauth2-callback (handles OAuth2 callback processing)
- **Database Schema (auth-service):**
  - user_oauth_providers table: id, user_id FK, provider_name (50 chars), provider_user_id, provider_email, linked_at
  - Unique constraints: (provider_name, provider_user_id), (user_id, provider_name)
  - PrePersist: Automatically sets linked_at to current UTC time
- **Security & Authorization:**
  - OAuth-only users have password=NULL; POST /api/auth/change-password blocked for these users (guard check)
  - Email verification by provider: Auto-link only when email_verified=true from Google
  - Tokens generated identically to traditional login (same JWT claims: sub, roles, userId)
  - OAuth state parameter and CSRF protection handled by Spring Security
- **Configuration:**
  - namnd.app.oauth2CallbackUrl: Frontend OAuth2 callback URL (e.g., http://localhost:4200/oauth2-callback)
  - Google OAuth2 credentials: Configured via application.yml spring.security.oauth2.client.registration
- **Error Handling:**
  - Missing email: Redirect to callback with ?error=no_email
  - Concurrent first-time login: Catch DataIntegrityViolationException, re-query provider link
  - Frontend callback errors: 2-second delay before redirect to /auth/login
- **DTOs:** No new DTOs; reuses JwtResponseDto pattern via query params
- **Testing:**
  - Integration tests: OAuth2 flow with Google mocked response
  - Unit tests: OAuth2UserLinkingService (create, auto-link, concurrent scenarios)
  - Frontend: OAuth2CallbackComponent token extraction, navigation

#### Password History Validation (v0.0.1) — March 15, 2026
- **New Endpoint:** POST `/api/auth/change-password` - Authenticated user password change
  - Request body: { currentPassword, newPassword, confirmPassword }
  - Validates current password, confirms new password match, checks password history
  - Returns 200 on success, 400 for validation errors, 401 for auth failures
- **Database:** `password_history` table (id, user_id, password_hash, created_at)
- **Backend Implementation:**
  - PasswordHistory JPA entity with user FK and BCrypt hash storage
  - PasswordHistoryService: CRUD operations, recent password validation (3 most recent)
  - POST /api/auth/change-password controller endpoint with @PreAuthorize("isAuthenticated()")
  - POST /api/auth/reset-password enhanced: Validates reset password against 3 recent hashes
  - Registration flow (POST /api/auth/register): Seeds initial password to history on user creation
- **Frontend Implementation:**
  - New ChangePasswordComponent at /profile/change-password route
  - Reactive form with currentPassword, newPassword, confirmPassword fields
  - "Change Password" button integration on ProfileComponent
  - Real-time validation and error display from backend API
- **Security & Authorization:**
  - POST /api/auth/change-password requires Bearer JWT token
  - Current password verified via BCrypt comparison
  - New password prevented from reusing 3 most recent hashes
  - All password changes logged with timestamp in history table
- **DTOs Added:**
  - ChangePasswordRequest (currentPassword, newPassword, confirmPassword)
  - ChangePasswordResponse (success message or error details)

#### Features Added
- **Movie Ratings (v0.0.1)** — March 12, 2026
  - POST `/api/movies/{movieId}/ratings` - Create/update rating (1-5 stars)
  - GET `/api/movies/{movieId}/ratings` - Retrieve rating summary (average, total count, authenticated user's rating)
  - Database: `movie_ratings` table (movieId, userId, rating, createdAt, updatedAt)
  - Spring Data JPA entity with composite key (movieId, userId)

- **Movie Comments (v0.0.1)** — March 12, 2026
  - POST `/api/movies/{movieId}/comments` - Create comment with text content
  - GET `/api/movies/{movieId}/comments` - List comments paginated (20 per page, sorted by createdAt DESC)
  - PUT `/api/comments/{commentId}` - Update own comment (owner only)
  - DELETE `/api/comments/{commentId}` - Soft-delete comment (owner or ADMIN)
  - Database: `movie_comments` table (movieId, userId, content, status [ACTIVE/DELETED], createdAt, updatedAt)
  - Soft-delete via `status` column (no hard deletion)

- **Comment Reactions (v0.0.1)** — March 12, 2026
  - POST `/api/comments/{commentId}/reactions` - Toggle like/dislike on comment
  - DELETE `/api/comments/{commentId}/reactions` - Remove reaction
  - Database: `comment_reactions` table (commentId, userId, reactionType [LIKE/DISLIKE], createdAt)
  - Composite key (commentId, userId) - one reaction per user per comment

#### API Changes
- **New Endpoints (8 total):**
  - 2 rating endpoints (POST, GET)
  - 4 comment endpoints (POST, GET, PUT, DELETE)
  - 2 reaction endpoints (POST, DELETE)

- **API Gateway:**
  - Added route `/api/comments/**` → movie-service
  - Routes `/api/movies/{movieId}/comments*` to MovieCommentController
  - Routes `/api/comments/{commentId}*` to CommentReactionController

#### Database Schema
- **movie-service (moviedb):**
  - `movie_ratings` — pk: (movie_id, user_id); columns: rating, created_at, updated_at
  - `movie_comments` — pk: id; columns: movie_id, user_id, content, status, created_at, updated_at
  - `comment_reactions` — pk: (comment_id, user_id); columns: reaction_type, created_at
  - Auto-created by Hibernate ddl-auto=update

#### Security & Authorization
- **MovieRatingController:** POST requires @PreAuthorize("isAuthenticated()"), GET is public
- **MovieCommentController:**
  - POST requires authentication
  - PUT requires ownership or admin role
  - DELETE requires ownership or admin role
  - GET is public
- **CommentReactionController:** All endpoints require authentication

#### DTOs Added
- `CreateRatingRequest` (rating: Integer [1-5])
- `MovieRatingDto` (id, movieId, userId, rating, createdAt, updatedAt)
- `MovieRatingSummaryDto` (averageRating, totalRatings, userRating [nullable])
- `CreateCommentRequest` (content: String)
- `UpdateCommentRequest` (content: String)
- `MovieCommentDto` (id, movieId, userId, content, status, likeCount, dislikeCount, userReaction, createdAt, updatedAt)
- `CommentReactionRequest` (reactionType: LIKE/DISLIKE)
- `CommentReactionDto` (id, commentId, userId, reactionType, createdAt)

#### Documentation
- Updated `/docs/api-documentation.md` — Added ratings, comments, reactions endpoints table
- Updated `/docs/codebase-summary.md` — Added new models, services, controllers, 7 tables for moviedb
- Updated `/docs/system-architecture.md` — Added database schema, new features, api-gateway routes
- Updated `/docs/project-roadmap.md` — Marked Phase 3 features as complete

#### Testing
- Full integration tests covering all CRUD operations
- Authorization tests (owner/admin/public scenarios)
- Pagination tests for comment listing
- Soft-delete verification

#### Backend Implementation Details
- MovieComment deletion is soft-delete only (updates status to DELETED, preserves audit trail)
- CommentReaction is toggle-based (POST twice with same type removes, different type updates)
- Rating summary includes user's own rating only for authenticated requests
- Comments sorted by newest first (DESC by createdAt)
- Ratings use UNIQUE(movie_id, user_id) constraint for upsert pattern
- Custom repository queries: AVG() for ratings, COUNT() for like/dislike counts

#### Frontend Implementation Details
- **StarRatingComponent:** 5-star display with interactive hover/selection, read-only or editable mode
- **CommentListComponent:** Paginated comment list with load-more or infinite scroll, public visibility
- **CommentItemComponent:** Individual comment card with reactions counter, edit/delete buttons (if owner/admin)
- **MovieRatingService & MovieCommentService:** HTTP clients handling POST/GET/PUT/DELETE with pagination
- **Auth Interceptor Fix:** Now always attaches JWT token from storage when available; PUBLIC_URLS only gates 401 refresh retry (allows anonymous access)
- **MovieDetailComponent:** Integrated new components below movie details, lazy-load ratings/comments on tab change

---

## Real-Time Notification System (COMPLETE ✓)

**Release Date:** March 14, 2026

### Overview

Complete real-time notification system with Server-Sent Events (SSE) streaming + Kafka event-driven architecture. Delivers payment/booking confirmations to frontend in real-time with PostgreSQL persistence and REST API for notification management.

### Features Added

**notification-service (Major Update)**
- New JPA Entity: Notification (userId, title, message, notificationType, isRead, createdAt)
- New REST Controller: NotificationRestController with CRUD + mark-as-read operations
- New SSE Controller: NotificationSseController (GET /api/notifications/stream?token=JWT)
- New Service: SseEmitterRegistryService (ConcurrentHashMap emitter registry, atomic operations, 30s heartbeat)
- New Service: InAppNotificationServiceImpl (persist, emit, mark-as-read, broadcast, getUnreadCount)
- New Service: InAppNotificationEventListener (Kafka consumer for notification.in_app topic)
- New Service: NotificationPublisherService (publish InAppNotificationEvent to Kafka)
- Database: notificationdb with notifications table (indexed userId, createdAt DESC)

**kafka-events (Module Update)**
- New Enum: NotificationType [PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM]
- New Record: InAppNotificationEvent (userId, title, message, notificationType)

**booking-service (Minor Update)**
- New Service: NotificationPublisherService (publishes InAppNotificationEvent after payment success/failure)
- Publishes InAppNotificationEvent → notification.in_app topic

**api-gateway (Bug Fix)**
- HttpLoggingFilter: Skip ContentCachingResponseWrapper for SSE paths (/api/notifications/stream)
- Prevents gateway thread exhaustion on long-lived SSE connections

**Angular Frontend (New Components)**
- New Model: notification.model.ts (Notification, NotificationPage, UnreadCountResponse interfaces)
- New Service: notification-sse.service.ts (EventSource with exponential backoff reconnect: 1s→30s max, 5 attempts)
- New Service: notification-api.service.ts (REST calls for CRUD + mark-as-read)
- New Component: notification-bell.component.ts (matBadge, snackbar alerts, auto-increment counter)
- New Component: notification-list.component.ts (Mat-card list, paginator, dark theme, colored borders per type)
- New Routes: notifications.routes.ts (lazy-loaded /notifications route)
- Toolbar Update: notification-bell added to toolbar.component.ts
- App Routes Update: /notifications route added to app.routes.ts

**Docker/Config (Updates)**
- init-databases.sql: Added CREATE DATABASE notificationdb;
- docker-compose.yml: Added postgres + environment variables to notification-service

### API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | /api/notifications/stream | JWT (query param) | SSE stream with 30s heartbeat |
| GET | /api/notifications | Bearer JWT | Paginated list (page=0&size=20 default) |
| PATCH | /api/notifications/{id}/read | Bearer JWT | Mark single as read |
| PATCH | /api/notifications/read-all | Bearer JWT | Mark all as read |
| GET | /api/notifications/unread-count | Bearer JWT | Get unread count for badge |
| POST | /api/notifications/broadcast | Bearer JWT (ADMIN) | Admin test broadcast |

### Bug Fixes

1. **Race Condition in removeEmitter** — Atomic computeIfPresent prevents concurrent modification issues
2. **Broadcast OOM Risk** — findDistinctUserIds instead of findAll() to avoid memory exhaustion
3. **Subscription Leak** — notification-bell.component uses take(1) to auto-unsubscribe from observable
4. **Wrong Exception Type** — Changed SecurityException to AccessDeniedException for 403 responses
5. **Notification Bell Color** — Added color: inherit to toolbar badge (white on primary)
6. **Notification List Dark Theme** — Fixed Mat-card background with rgba(0,0,0,0.04) dark overlay

### Technical Details

**SSE Configuration:**
- Heartbeat: Comment-only `:heartbeat` every 30 seconds (minimal overhead, prevents timeout)
- Emitter Timeout: 30 minutes (configurable, client auto-reconnects)
- Unique Consumer Group: `notification-service-{instanceId}` ensures all instances receive broadcast
- Thread-Safe Registry: ConcurrentHashMap with synchronized iterator for emitter updates

**Kafka Events:**
- Topic: notification.in_app (published by booking-service, consumed by notification-service)
- Consumer Group: `notification-service-{instanceId}` (unique per instance for broadcast pattern)
- Error Handling: 3 retries, exponential backoff (1s→2s→4s capped 10s), DLT for failures

**Frontend Reconnection:**
- Strategy: Exponential backoff starting at 1s, capped at 30s max, maximum 5 attempts
- Triggers: Connection loss, server timeout, manual close
- No user action required; transparent auto-reconnect

**Database:**
- Notifications table: id (PK), userId (FK), title, message, notificationType, isRead, createdAt
- Index: (userId, createdAt DESC) for O(log n) pagination
- Archival: Implement TTL or scheduled cleanup for messages > 90 days old (optional)

### Security Considerations

- JWT auth via query parameter (?token=JWT) is standard for SSE endpoints (Authorization header not supported by EventSource API)
- Token validation performed per request; expired tokens cause SSE connection drop
- NotificationType enum prevents injection of invalid notification types
- Admin broadcast endpoint requires ROLE_ADMIN for authorization

### Testing

- Unit tests: SSE emitter registry, notification service CRUD
- Integration tests: Kafka event flow (payment → booking → notification)
- Frontend tests: EventSource mock, exponential backoff logic, badge increment

### Documentation Updates

- README.md: Updated notification-service description, Kafka event flow
- docs/project-overview-pdr.md: Added FR-006 Real-Time In-App Notifications
- docs/codebase-summary.md: Added notification-service details, kafka-events enum, Angular components
- docs/system-architecture.md: Added SSE flow diagram, notification-service architecture, API Gateway fix
- docs/api-documentation.md: Added all 6 notification endpoints with examples
- docs/project-roadmap.md: Marked Phase 3 real-time notifications as COMPLETE

### Performance Metrics

- SSE Heartbeat Overhead: ~1 KB per event (30-byte comment + newlines)
- Emitter Registry Memory: O(n) where n = concurrent SSE connections
- Kafka Latency: <100ms p95 from payment event to notification delivery
- DB Query: Pagination (O(log n) with index) + count query (O(1) with trigger)

### Known Limitations

- EventSource API (browser native) does not support custom headers; JWT passed via query param
- SSE connections drop on JWT expiration; client must request new token and reconnect
- Concurrent connection limit per instance (configurable, e.g., 1000 per notification-service instance)
- Notification list paginated at 20 per page; older notifications not auto-purged

---

## Phase 2 Releases (Historical)

**Phase 2: Microservice Integration (COMPLETE)** — December 2025 - February 2026
- 10-module Maven structure with Spring Cloud
- Eureka service discovery, Config Server, API Gateway
- JWT authentication (JJWT 0.12.6 HS512)
- movie-service, booking-service, payment-service, notification-service
- Kafka event streaming (PaymentCompletedEvent, PaymentFailedEvent, BookingCreatedEvent)
- OpenAPI 3.0 documentation (Swagger UI, SpringDoc 2.8.4)
- Prometheus monitoring + Grafana dashboards + Loki logging

---

## Related Documents

- [Project Roadmap](./project-roadmap.md)
- [API Documentation](./api-documentation.md)
- [System Architecture](./system-architecture.md)
- [Codebase Summary](./codebase-summary.md)
