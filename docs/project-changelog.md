# Project Changelog

**Project:** ms-cinema
**Updated:** May 29, 2026

## Version 0.0.1-SNAPSHOT

### [Unreleased]

#### SSO Identity Provider — Phase 06: Hardening + Partner Onboarding (COMPLETE ✓) — May 29, 2026
- **Feature complete:** Spring Authorization Server 1.3.x embedded in `auth-service` now serves as production OIDC IdP for B2B partners.
- **BREAKING (shared lib):** `jwt-auth-autoconfigure` bumped to v0.1.0 — dual-mode validator (HS512 + RS256) replaces HS512-only path. Resource services must re-build to consume; runtime behavior is backward compatible (HS512 tokens still accepted until rollback flag flipped).
- **New admin endpoints:**
  - `POST /api/admin/signing-keys/rotate` — mint new ACTIVE RSA-2048, retire current
  - `GET /api/admin/signing-keys` — list ACTIVE + RETIRED
  - `DELETE /api/admin/signing-keys/{kid}` — hard-delete RETIRED key
- **Hardening applied:**
  - Refresh-token rotation w/ reuse detection (`reuseRefreshTokens=false`)
  - Per-client-IP NGINX rate limit (10 rps, burst 20) on `/oauth2/(token|authorize|revoke|introspect)`
  - App-layer HTTPS enforcement in `application-prod.yml` (defense-in-depth over ingress TLS)
  - CORS allowlist drawn from registered redirect URI origins (no wildcard)
  - Secure + HttpOnly + SameSite=lax session cookie in prod
  - `forward-headers-strategy=framework` so AS honours `X-Forwarded-Proto`
- **Audit trail wired:** `oauth2.token.issued`, `oauth2.token.revoked`, `oauth2.consent.granted`, `oauth2.consent.denied`, `oauth2.signing_key.rotated`, `oauth2.signing_key.deleted` flow to Kafka topic `audit-events`.
- **New files:**
  - `auth-service/.../controller/oauth2/SigningKeyAdminController.java`
  - `auth-service/.../config/oauth2/OAuth2AuditEventListener.java`
  - `auth-service/.../config/oauth2/AuditingOAuth2AuthorizationConsentService.java`
  - `auth-service/.../config/oauth2/OAuth2CorsConfigurationSource.java`
  - `auth-service/src/main/resources/application-prod.yml`
- **K8s:** `k8s/ingress.yml` — added rate-limit `server-snippet` annotation.
- **Docs added:**
  - `docs/sso-key-rotation-runbook.md` — quarterly rotation + emergency procedure
  - `docs/sso-partner-integration-guide.md` — copy-pasteable partner onboarding
- **Docs updated:**
  - `docs/system-architecture.md` — Identity Provider section + sequence diagram + audit taxonomy
  - `docs/api-documentation.md` — OAuth2 + admin endpoint tables
- **Deferred (operational):** OIDC Conformance Suite run, staging rotation drill, NGINX rate-limit load test, trusted partner pilot — tracked in Phase 06 Todo list.

#### Distributed Tracing Migration: Zipkin → OpenTelemetry + Grafana Tempo (COMPLETE ✓) — May 9, 2026
- **Pipeline Change:** Replaced legacy Zipkin tracing with industry-standard OTel pipeline
  - New flow: Spring Boot apps → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP (4318) → OTel Collector (contrib) → Grafana Tempo (3200)
  - Maven dependency: `opentelemetry-exporter-zipkin` → `opentelemetry-exporter-otlp` (1.43.x via Spring Cloud BOM 2024.0.1)
  - App config: `management.zipkin.tracing.endpoint` → `management.otlp.tracing.endpoint`
  - Env var: `ZIPKIN_HOST` → `OTEL_COLLECTOR_HOST` (+ new `DEPLOYMENT_ENV`)
  - Resource attributes added: `service.name`, `deployment.environment`
- **New Infrastructure:**
  - `tempo` service (grafana/tempo:2.6.0) — local FS storage, 24h retention, single replica
  - `otel-collector` service (otel/opentelemetry-collector-contrib:0.115.1) — OTLP receivers (gRPC 4317 + HTTP 4318), batch/memory_limiter processors, OTLP exporter to Tempo
  - K8s: Tempo as StatefulSet with 5Gi PVC; OTel Collector as Deployment
  - Configs at `monitoring/tempo/tempo.yaml`, `monitoring/otel-collector/otel-collector-config.yaml`, `k8s/infra/tempo/`, `k8s/infra/otel-collector/`
- **Grafana Datasource:** Added Tempo datasource with `tracesToLogsV2` (Loki via `service` label) and `tracesToMetrics` (Prometheus latency/rate). Service Graph and node graph enabled.
- **Removed:**
  - Zipkin service from docker-compose
  - `k8s/infra/zipkin/` directory
  - Zipkin Grafana datasource
  - `ZIPKIN_HOST` and `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` env vars
  - `opentelemetry-exporter-zipkin` Maven dependency from 6 services
- **Preserved:** `micrometer-tracing-bridge-otel` (only exporter changed). Logback MDC traceId/spanId continues flowing into Loki. Spring Kafka 3.x W3C `traceparent` header propagation unchanged. 64-bit trace IDs preserved (no audit-service `trace_id` schema change required).
- **Benefits:** Vendor-neutral OTLP pipeline, richer Grafana UX (trace-to-logs/metrics, service graph), unlocks future tail-sampling and span-metrics processing at the collector.

#### Kubernetes Migration: Remove API Gateway & Use K8s Ingress (COMPLETE ✓) — April 9, 2026
- **Architecture Change:** Eliminated Spring Cloud Gateway service; replaced with NGINX K8s Ingress for path-based routing
  - Deleted: spring-cloud-gateway module entirely
  - Replacement: `k8s/ingress.yml` — NGINX Ingress resource for K8s deployment
  - Docker Compose: `cinema-frontend/nginx.conf` routes directly to services (no gateway layer)
  - Frontend Service: Changed to ClusterIP (Ingress handles external access)
  - WebSocket: NGINX Ingress annotations for `/ws/**` WebSocket upgrade support
  - CORS: Handled at service level (Spring Security) — no aggregation at gateway
  - Swagger: Per-service Swagger UI access (no aggregation) — improved modularity
  - Memory Savings: Removed ~512Mi gateway footprint from deployment
- **Benefits:** Simplified deployment topology, reduced memory overhead, native Kubernetes integration
- **Migration Path:** Docker Compose continues to use nginx proxy; K8s uses native Ingress

#### Infrastructure Simplification: Remove Eureka & Config Server (COMPLETE ✓) — April 9, 2026
- **Service Discovery Change:** Removed Spring Cloud Eureka entirely; adopted K8s DNS + static URIs
  - Deleted: eureka-server module
  - Deleted: config-server module
  - Removed: eureka-client dependency from all 6 service pom.xml files
  - Removed: config-client dependency from all 6 service pom.xml files
  - Removed: Eureka configuration blocks from all application.yml files
  - Removed: `spring.config.import` directive from all services
  - New: `application-k8s.yml` profile with static service URIs (http://service-name:port)
- **Service Discovery Mechanism:**
  - K8s: Services discover each other via K8s DNS (e.g., auth-service:8081)
  - Docker Compose: Uses docker-compose service hostnames (already static)
- **Configuration Management:** Services use environment variables for secrets; no centralized Config Server
- **Docker Compose:** Removed eureka-server and config-server services from docker-compose.yml
- **Monitoring:** Removed Prometheus scrape jobs for eureka & config services
- **Benefits:** Reduced operational complexity, simplified K8s deployment, no external service discovery
- **Deployment Impact:** K8s manifests now fully self-contained; no separate infra services needed

#### Kubernetes Minikube Deployment (NEW DEPLOYMENT MODE ✓) — April 7, 2026
- **New Deployment Option:** Full K8s manifests for local Minikube / OrbStack deployment
  - Location: `/k8s` directory with manifests for all 6 services + infrastructure
  - K8s Services: ClusterIP for inter-service communication; external access via NGINX Ingress
  - NGINX Ingress: `k8s/ingress.yml` — path-based routing for `/api/**` and `/ws/**` endpoints
  - ConfigMaps: Per-service k8s environment configuration (KAFKA_BROKERS, REDIS_HOST, JWT_SECRET)
  - Secrets: Stored in K8s Secret objects (STRIPE_SECRET_KEY, MAIL_USERNAME, MAIL_PASSWORD)
  - StatefulSet (optional): For PostgreSQL (if not using external DB)
  - Deployment: All 6 services, Kafka, PostgreSQL, Redis, monitoring stack
- **Networking:** Services use K8s DNS (auth-service, movie-service, etc.) for inter-service calls
- **Persistent Storage:** K8s PVC for PostgreSQL / Kafka (configurable)
- **Usage:** `kubectl apply -f k8s/` to deploy entire stack on Minikube / OrbStack
- **Docker Compose Unchanged:** Existing docker-compose.yml remains the primary local dev mode
- **Benefits:** Native K8s support for production-like testing, scalability, multi-replica deployment


#### Frontend Date/Time Utilities (FR-3.4 COMPLETE ✓) — April 1, 2026
- **Feature:** Timezone-safe date/time formatting utilities for form submissions
  - Utility file: date-format.util.ts (src/app/shared/utils/)
  - Functions: formatDate(date, format?), combineDatetime(dateStr, timeStr), parseTime(timeStr)
  - formatDate: Formats Date to local timezone string (YYYY-MM-DD HH:mm:ss format)
  - combineDatetime: Merges date + time strings into Date object with timezone conversion
  - parseTime: Parses HH:mm string into minutes for time picker logic
  - Integration: Used in showtime-form-dialog and movie-form-dialog
  - Problem Solved: Prevents browser timezone offset issues when submitting datetime values
  - Testing: Manual verification with form submissions across different timezones
- **Benefits:** Consistent datetime handling across frontend forms, eliminates timezone-related data corruption

#### Stripe Reconciliation Admin Dashboard (FR-3.5 COMPLETE ✓) — March 31, 2026
- **Feature:** Admin UI for viewing and managing payment reconciliation results
  - Dashboard component: reconciliation-dashboard.component.ts
    - Summary cards: Total runs, matched/mismatched/missing counts
    - Date range picker: Manual trigger for custom date ranges (max 31 days)
    - Run history table: MatTable with startDate, endDate, status, counts, pagination
  - Detail component: reconciliation-detail.component.ts
    - Items table: stripePaymentIntentId, localPaymentId, discrepancyType, amounts, statuses
    - Filter dropdown: By discrepancyType (MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE)
    - CSV export: Download reconciliation items as CSV file
    - Resolve action: Mark item as resolved with admin notes
  - API service: Wraps backend reconciliation endpoints (trigger, getRuns, getRunDetails, getRunItems, getSummary, resolveItem)
  - Routes: Added /admin/reconciliation, /admin/reconciliation/:runId
  - Navigation: Added "Reconciliation" tab to admin-nav.component.ts
- **Benefits:** Real-time visibility into payment discrepancies, streamlined resolution workflow, audit trail for admin actions

#### Stripe Payment Reconciliation with Spring Batch (FR-3.3 COMPLETE ✓) — March 31, 2026
- **Feature:** Daily automated reconciliation between local payments and Stripe PaymentIntent states
  - Spring Batch job: Runs daily at 2 AM Asia/Saigon (configurable via reconciliation.cron)
  - Batch architecture: LocalPaymentReader → ReconciliationProcessor → ReconciliationItemWriter
  - Reader: Queries local payments by createdAt range from paymentdb
  - Processor: Calls Stripe PaymentIntent.retrieve() per payment, classifies discrepancy type
  - Writer: Persists ReconciliationItem chunks (batch size 100)
  - Listener: afterJob invokes Stripe list API to detect MISSING_LOCAL items, finalizes run counts
  - Backend files (14 new): batch/ (4), config/ (3), model/ (4), repository/ (2), service/ (2), controller/ (1 ReconciliationController with @PreAuthorize("hasRole('ADMIN')"))
  - ReconciliationRun entity: Tracks startDate, endDate, status (RUNNING/COMPLETED/FAILED), counts (matchedCount, mismatchedCount, missingLocalCount, missingStripeCount, totalChecked)
  - ReconciliationItem entity: Stores stripePaymentIntentId, localPaymentId, discrepancyType (MATCHED/STATUS_MISMATCH/AMOUNT_MISMATCH/MISSING_LOCAL/MISSING_STRIPE), amounts, statuses, resolved flag, admin notes
  - Admin API endpoints: POST /trigger (manual run with date range), GET /runs (paginated), GET /runs/{runId}, GET /runs/{runId}/items (filtered by discrepancyType), GET /summary (latest run stats), PUT /items/{itemId}/resolve (admin resolution notes)
  - Validation: Enforces max 31-day date range per run, validates date range before job launch
  - Scheduling: @Scheduled cron via ReconciliationScheduler, @ConditionalOnProperty reconciliation.auto-run=true
  - Configuration: spring.batch.jdbc.initialize-schema=always (auto-create Spring Batch metadata tables), spring.batch.job.enabled=false (no auto-run on startup), reconciliation.* properties (cron, auto-run, max-date-range-days)
  - Environment: STRIPE_API_KEY env var (no hardcoded test key in application.yml)
  - Database: reconciliation_runs, reconciliation_items tables auto-created by Hibernate ddl-auto: update, indexes on (run_id), (discrepancy_type)
  - Tests: 3 test files (15 tests): ReconciliationProcessorTest (5 unit tests with MockedStatic<PaymentIntent>), ReconciliationServiceImplTest (6 unit tests), ReconciliationControllerTest (4 @WebMvcTest tests with role-based auth)
  - Dependencies: Added spring-boot-starter-batch, h2 (test), spring-batch-test (test), spring-security-test (test) to pom.xml
- **Frontend (4 new files - cinema-frontend):**
  - core/models/reconciliation.model.ts: Interfaces for ReconciliationRun, ReconciliationItem, DiscrepancyType, ReconciliationSummary, PageResponse
  - core/services/reconciliation.service.ts: HTTP client for all reconciliation endpoints
  - features/admin/reconciliation/reconciliation-dashboard.component.ts: Summary cards, date range trigger, run history table with pagination
  - features/admin/reconciliation/reconciliation-detail.component.ts: Items table with discrepancy type filter, resolve action, CSV export
  - Modified admin.routes.ts: Added reconciliation + reconciliation/:runId routes
  - Modified admin-nav.component.ts: Added "Reconciliation" tab
- **Benefits:** Early detection of payment discrepancies, immutable audit trail, manual + scheduled processing, admin visibility into payment inconsistencies
- **Security:** @PreAuthorize("hasRole('ADMIN')") on all reconciliation endpoints, no sensitive data in response logs

#### Deferred Password Setup to Activation (FR-3.2 COMPLETE ✓) — March 27, 2026
- **Feature:** Users register without password; password set during email activation
  - Registration flow: Accept username, email, fullName only (NO password field)
  - Email activation: User clicks link to frontend /auth/setup-password?token=uuid
  - New endpoint: POST /api/auth/activate-with-password {token, password, confirmPassword}
  - Sets user.active=true, hashes password, seeds password_history, marks token used
  - Backend: New SetupPasswordDto, ActivationServiceImpl.activateWithPassword() (@Transactional)
  - Frontend: Register form removes password field, new SetupPasswordComponent at /auth/setup-password route
  - Config: activationBaseUrl updated to frontend URL (both application.yml and config-repo/auth-service.yml)
  - Backward compat: GET /api/auth/activate (old endpoint) still works
- **Benefits:** Delayed password setup reduces signup friction, improves security via email verification before password creation
- **Security:** Password always set after email verification; OAuth-only users have password=NULL until setup or password reset

#### Bug Fixes (March 22, 2026)
- **OAuth2 LazyInitializationException Fix:** Force-initialize user.getRoles() within @Transactional context in OAuth2UserLinkingService to prevent lazy loading issues
- **WebSocket nginx Proxy Fix:** Add conditional Connection header (Connection: Upgrade on WebSocket requests, keep-alive otherwise) to support WebSocket upgrade while maintaining regular HTTP requests
- **Seat Data Mapping Fix:** Map API responses correctly (rowLabel/seatNumber API fields → rowNumber/columnNumber/price frontend fields) in seat grid display
- **Global Polyfill Fix:** Added global sockjs-client polyfill to support older browsers and resolve compatibility issues
- **SecurityConfig WebSocket Fix:** Added /ws/** permitAll route in SecurityConfig to ensure WebSocket connections are not intercepted by default authentication filters

#### Seat Grid Display & Booking UI Improvements (FR-3.1 COMPLETE ✓) — March 22, 2026
- **Feature:** Complete theater seat visualization with real-time availability, accessibility, and adjacent seat suggestions
  - Phase 1: Color-coded seats (STANDARD=green, PREMIUM=blue, VIP=amber) with row A-Z labels and type+price legend
  - Phase 2: Curved screen with glow effect, aisle gaps (cols 6, 13), VIP section dividers, responsive grid layout
  - Phase 3: Mobile responsive (36/40/44px seat sizes), horizontal scroll, floating booking summary bar
  - Phase 4: ARIA grid role, arrow key navigation (up/down/left/right), roving tabindex, focus-visible styles
  - Phase 5: Real-time WebSocket STOMP /ws/booking with LOCK/RESERVE/CANCEL events, <100ms latency
  - Phase 6: Client-side O(n*m) adjacent seat suggestion algorithm (proximity + row + type uniformity)
- **Frontend Implementation:**
  - New files: seat-grid-layout.utils.ts, seat-grid-keyboard-navigation.utils.ts, seat-selection-timer.utils.ts
  - New components: seat-suggestion-panel.component.ts
  - New services: seat-websocket.service.ts, seat-suggestion.service.ts
  - Modified: seat-grid.component.ts, seat-selection.component.ts
  - Dependencies: @stomp/stompjs, sockjs-client (added to package.json)
- **Backend Implementation:**
  - New files: WebSocketConfig.java, SeatStatusMessage.java, SeatWebSocketPublisher.java
  - Modified: BookingServiceImpl.java (publishes LOCK/RESERVE/CANCEL via WebSocket)
  - Modified: BookingExpiryScheduler.java (publishes CANCEL on booking expiry)
  - Modified: booking-service/pom.xml (spring-boot-starter-websocket dependency)
  - Modified: booking-service/application.yml (WebSocket routes)
- **Accessibility:** WCAG 2.1 AA compliant (ARIA grid, keyboard nav, color+icons, focus styles, tooltips)
- **Performance:** WebSocket <100ms latency vs. 2-3s polling (100x faster); suggestion O(n*m) acceptable for <1000 seats
- **Security:** WebSocket authenticated via JWT handshake validation
- **Testing:** Integration tests for WebSocket broadcasts, E2E tests for real-time seat updates

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
- Updated `/docs/system-architecture.md` — Added database schema, new features, routing rules
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

**notification-service (Bug Fix)**
- SSE endpoint /api/notifications/stream: ContentCachingResponseWrapper skipped to prevent thread exhaustion on long-lived SSE connections

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
