# Project Roadmap

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Updated:** April 2026
**Status:** Active Development

## Vision

MS Cinema is a comprehensive cinema ticket booking platform built on Spring Boot 3.4.3 microservices. The roadmap progresses from single authentication service to a distributed system with event-driven notifications, payment processing, and production-grade observability.

## Roadmap Phases

### Phase 1: Core Authentication (COMPLETE ✓)

**Status:** Complete
**Timeline:** Past
**Focus:** Stateless JWT authentication

**Completed Features:**
- ✓ User login/register with BCrypt encoding
- ✓ JWT token generation (JJWT 0.12.6 HS512)
- ✓ Token validation with JTI uniqueness
- ✓ Role-based access control (@PreAuthorize)
- ✓ Refresh token rotation (7-day TTL)
- ✓ Logout with Redis token blacklist
- ✓ Email activation flow with 24-hour tokens
- ✓ Password reset flow via email
- ✓ Account lockout after 5 failed attempts (15-min auto-unlock)

---

### Phase 2: Microservice Integration (COMPLETE ✓)

**Status:** Complete
**Timeline:** December 2025 - February 2026
**Focus:** Multi-module architecture with service discovery & event streaming

**Completed Features:**
- ✓ 9-module Maven structure (6 business services, 1 infrastructure, 2 shared libs, 1 frontend)
- ✓ Spring Cloud Gateway MVC (:8080, OpenAPI aggregation, static URI routing)
- ✓ JWT tokens embed roles+userId claims for downstream use
- ✓ POST /api/auth/validate-token (microservice JWT validation, no DB hit)
- ✓ GET /api/users/me (authenticated user profile retrieval)
- ✓ jwt-auth-autoconfigure library (plug-in JWT auth for all services)
- ✓ **OpenAPI 3.0 documentation (Swagger UI, SpringDoc 2.8.4)**
- ✓ movie-service (CRUD movies/theaters/showtimes, auto-seat generation)
- ✓ booking-service (Redis locking, lifecycle states, Feign to movie-service)
- ✓ payment-service (Stripe integration, idempotency keys, webhook verification)
- ✓ notification-service (Kafka consumer, SMTP email, Redis dedup)
- ✓ kafka-events shared library (EventEnvelope, domain events)
- ✓ Kafka topics (movie-events, payment-events, notification-events)
- ✓ Prometheus (:9090, 15s scrape) + Grafana (:3000, 2 dashboards) + Loki (:3100)

**Success Metrics:**
- 9 modules successfully deployed via docker-compose
- Cross-service JWT validation < 50ms
- Booking-to-payment event latency < 2s (p95)
- All endpoints documented in OpenAPI (0 manual docs needed)

---

### Phase 3: Features & Enhancements (IN PROGRESS)

**Status:** In Progress
**Timeline:** March - May 2026
**Focus:** Business features and user experience

**Completed Features:**
- ✓ Movie Ratings (1-5 stars) - POST/GET with summary stats (COMPLETE: March 12, 2026)
- ✓ Movie Comments (flat, paginated, soft-delete) (COMPLETE: March 12, 2026)
- ✓ Comment Reactions (like/dislike toggle) (COMPLETE: March 12, 2026)
- ✓ API Gateway /api/comments/** route (COMPLETE: March 12, 2026)

**Completed Features (Continued):**
- ✓ Admin CRUD Dashboard Frontend (4 management pages: Movies, Theaters, Showtimes, Payments) (COMPLETE: March 13, 2026)
- ✓ MatTable-based list views with edit/delete actions (COMPLETE: March 13, 2026)
- ✓ MatDialog forms for create/edit operations (COMPLETE: March 13, 2026)
- ✓ Admin tab-based navigation (COMPLETE: March 13, 2026)
- ✓ PaymentManagementComponent with admin-only GET /api/payments (COMPLETE: March 13, 2026)

**Completed Features (Real-Time Notifications - COMPLETE ✓ March 14, 2026):**
- ✓ Real-time notifications (SSE + Kafka) - Server-Sent Events with 30s heartbeat for instant delivery
- ✓ NotificationSseService with exponential backoff reconnect (1s→30s max, 5 attempts)
- ✓ NotificationBellComponent (toolbar badge with matBadge, snackbar alerts, increment on new)
- ✓ NotificationListComponent (paginated Mat-card list, dark theme, colored borders per type)
- ✓ PostgreSQL persistence (notificationdb: notifications table with userId, title, message, notificationType, isRead, createdAt)
- ✓ InAppNotificationEvent and NotificationType enum (PAYMENT_SUCCESS, PAYMENT_FAILED, ADMIN_BROADCAST, SYSTEM)
- ✓ SseEmitterRegistryService (ConcurrentHashMap-based registry, atomic operations, 30s heartbeat)
- ✓ NotificationRestController (GET list, PATCH mark-read, PATCH mark-all, GET unread-count, POST broadcast)
- ✓ JWT authentication via query parameter (?token=JWT) for SSE endpoint
- ✓ Payment event notifications (success/failure broadcast to user via SSE)
- ✓ NotificationPublisherService in booking-service (publishes to notification.in_app topic)
- ✓ Bug fixes: race condition, broadcast OOM, subscription leak, toolbar badge color, dark theme
- ✓ API Gateway SSE support (skips ContentCachingResponseWrapper to prevent thread exhaustion)
- ✓ Docker config updates (init-databases.sql, docker-compose notification-service env vars)

**Completed Features (Password History Validation - COMPLETE ✓ March 15, 2026):**
- ✓ POST /api/auth/change-password endpoint (Bearer JWT required, validates current & new passwords)
- ✓ password_history table (id, user_id, password_hash, created_at) for tracking recent passwords
- ✓ PasswordHistory JPA entity and PasswordHistoryRepository with findTop3ByUserIdOrderByCreatedAtDesc()
- ✓ PasswordHistoryService managing CRUD and 3-password reuse prevention (isPasswordReused, savePasswordToHistory)
- ✓ Enhanced password reset validation against 3 most recent hashes via PasswordHistoryService.isPasswordReused()
- ✓ Registration flow seeding initial password to history via PasswordHistoryService.savePasswordToHistory()
- ✓ Frontend ChangePasswordComponent with reactive form at /profile/change-password
- ✓ Integration: "Change Password" button on ProfileComponent
- ✓ DTOs: ChangePasswordRequest, ChangePasswordResponse
- ✓ SecurityConfig updated to require authentication for POST /api/auth/change-password

**Completed Features (Seat Grid Display & Booking UI - COMPLETE ✓ March 22, 2026):**
- ✓ seat-grid-layout.utils.ts: Layout calculations (screen curves, aisle gaps, responsive sizing)
- ✓ seat-grid-keyboard-navigation.utils.ts: Arrow key nav, roving tabindex, focus management
- ✓ seat-selection-timer.utils.ts: Booking timer with countdown
- ✓ seat-suggestion-panel.component.ts: UI for adjacent seat recommendations
- ✓ seat-websocket.service.ts: STOMP/SockJS WebSocket client connection
- ✓ seat-suggestion.service.ts: Algorithm for finding best adjacent groups
- ✓ Modified seat-grid.component.ts: Color-coded seats (STANDARD/PREMIUM/VIP), row labels, legend
- ✓ Modified seat-selection.component.ts: Integration with WebSocket and suggestion services
- ✓ WebSocketConfig.java: Spring WebSocket + STOMP endpoint /ws/booking
- ✓ SeatStatusMessage.java: DTO for seat status events (action: LOCK/RESERVE/CANCEL)
- ✓ SeatWebSocketPublisher.java: Service to broadcast seat events to connected clients
- ✓ Modified BookingServiceImpl.java: Calls SeatWebSocketPublisher on lock/reserve/cancel
- ✓ Modified BookingExpiryScheduler.java: Publishes CANCEL event on booking expiry
- ✓ Modified booking-service/pom.xml: Added spring-boot-starter-websocket
- ✓ Configured nginx/K8s Ingress: WebSocket route /ws → booking-service:8083
- ✓ Frontend dependencies: @stomp/stompjs, sockjs-client
- ✓ WCAG 2.1 AA accessibility (ARIA grid, keyboard nav, color + icons)
- ✓ Real-time availability updates (<100ms latency vs. 2-3s polling)

**Completed Features (Centralized Audit Logging - COMPLETE ✓ March 21, 2026):**
- ✓ audit-service (port 8086) - Kafka consumer for audit-events topic
- ✓ AuditEvent record in kafka-events library with AuditAction enum
- ✓ @Auditable annotation on auth-service (login, register, logout, change-password)
- ✓ @Auditable annotation on movie-service (create/update/delete movie, create/update showtime)
- ✓ @Auditable annotation on booking-service (reserve, cancelBooking)
- ✓ @Auditable annotation on payment-service (createPaymentIntent)
- ✓ AuditAspect (AOP) for method interception and event capture
- ✓ AuditEntityListener (JPA lifecycle hooks) for entity-level audit
- ✓ AuditEventPublisher with @TransactionalEventListener(AFTER_COMMIT, fallbackExecution=true)
- ✓ AuditAutoConfiguration (@ConditionalOnClass) for optional audit beans
- ✓ AdminAuditLogController (GET /api/audit/logs with filtering, GET /api/audit/logs/{id})
- ✓ Audit API: filter by userId, action, entityType, dateRange (paginated, 20/page max 100)
- ✓ PostgreSQL auditdb with audit_logs table (eventId UNIQUE, userId, action ENUM, entityType, afterState JSON, 90-day retention)
- ✓ Kafka topic: audit-events (3 partitions, 90-day retention, DLT)
- ✓ Idempotent consumer: eventId UNIQUE constraint + DataIntegrityViolationException catch
- ✓ @JsonIgnore on User.password for API security
- ✓ Ingress/nginx route /api/audit/** → audit-service
- ✓ Requires ADMIN role (@PreAuthorize("hasRole('ADMIN')"))
- ✓ CONFIG: audit-service application.yml (kafka, retention settings)
- ✓ DOCKER: audit-service Dockerfile, docker-compose.yml service definition, init-databases.sql auditdb creation

**Planned Features:**

**FR-3.0: Real-Time Notifications (COMPLETE ✓ - March 14, 2026)**
- ✓ Server-Sent Events (SSE) infrastructure for real-time notifications
- ✓ PostgreSQL persistence (notificationdb)
- ✓ InAppNotificationEvent Kafka topic (notification.in_app)
- ✓ Frontend SSE client with exponential backoff reconnect
- ✓ Notification bell component with unread badge
- ✓ Notification list page (paginated, mark-as-read)
- ✓ Payment events broadcast (confirmation/failure to user)
- ✓ JWT auth via query parameter for SSE endpoint
- **Status:** COMPLETE (March 14, 2026)
- **Implementation:** SSE emitter registry per user, 30s heartbeat, unique Kafka consumer group per instance

**FR-3.1: Seat Grid Display & Booking UI (COMPLETE ✓ March 22, 2026)**
- ✓ 6-phase implementation: color-coded seats, theater realism, responsive design, accessibility, WebSocket, suggestions
- ✓ Frontend seat map (A-Z rows, STANDARD=green, PREMIUM=blue, VIP=amber colors)
- ✓ Theater layout realism (curved screen with glow, aisle gaps at cols 6/13, VIP section dividers)
- ✓ Responsive mobile (36/40/44px sizing, horizontal scroll, floating summary bar)
- ✓ WCAG 2.1 AA accessibility (ARIA grid role, arrow key nav, roving tabindex, focus-visible, tooltips)
- ✓ Real-time WebSocket STOMP /ws/booking (SeatStatusMessage with LOCK/RESERVE/CANCEL actions, <100ms latency)
- ✓ Adjacent seat suggestions (O(n*m) client-side algorithm: proximity + row + type uniformity)
- ✓ Backend WebSocket support (WebSocketConfig, SeatStatusMessage, SeatWebSocketPublisher)
- ✓ Modified BookingServiceImpl & BookingExpiryScheduler (WebSocket event publishing)
- **Frontend New Files:** seat-grid-layout.utils.ts, seat-grid-keyboard-navigation.utils.ts, seat-selection-timer.utils.ts, seat-suggestion-panel.component.ts, seat-websocket.service.ts, seat-suggestion.service.ts
- **Backend New Files:** WebSocketConfig.java, SeatStatusMessage.java, SeatWebSocketPublisher.java
- **Dependencies:** @stomp/stompjs, sockjs-client (npm install)
- **Effort Actual:** 6 phases, ~8-10 days
- **Status:** COMPLETE (March 22, 2026)

**FR-3.2: Deferred Password Setup to Activation (COMPLETE ✓ March 27, 2026)**
- ✓ Registration without password (username, email, fullName only)
- ✓ Email activation token links to frontend /auth/setup-password?token=uuid
- ✓ New endpoint: POST /api/auth/activate-with-password {token, password, confirmPassword}
- ✓ SetupPasswordDto with validation (token, password, confirmPassword)
- ✓ ActivationServiceImpl.activateWithPassword() with @Transactional
- ✓ Password hashing, password_history seeding, user.active=true, token marked used
- ✓ Frontend Register form: removed password field (username, email, fullName only)
- ✓ New SetupPasswordComponent at /auth/setup-password route
- ✓ Config: activationBaseUrl updated (auth-service application.yml)
- ✓ Backward compatibility: GET /api/auth/activate still works
- **Status:** COMPLETE (March 27, 2026)
- **Benefits:** Reduced signup friction, improved security (email verified before password), consistent with OAuth flow

**FR-3.3: Stripe Payment Reconciliation (COMPLETE ✓ March 31, 2026) — NEW**
- ✓ Spring Batch reconciliation job comparing local vs Stripe PaymentIntent states
- ✓ Daily schedule: 2 AM Asia/Saigon timezone (configurable via reconciliation.cron)
- ✓ Batch architecture: LocalPaymentReader → ReconciliationProcessor (PaymentIntent.retrieve) → ReconciliationItemWriter
- ✓ afterJob listener: Calls Stripe list API for MISSING_LOCAL, finalizes run counts
- ✓ ReconciliationRun entity: startDate, endDate, status (RUNNING/COMPLETED/FAILED), counts
- ✓ ReconciliationItem entity: stripePaymentIntentId, localPaymentId, discrepancyType (MATCHED/STATUS_MISMATCH/AMOUNT_MISMATCH/MISSING_LOCAL/MISSING_STRIPE)
- ✓ Admin API: POST /trigger (manual run), GET /runs (paginated), GET /runs/{runId}, GET /runs/{runId}/items (filtered by discrepancyType), GET /summary, PUT /items/{itemId}/resolve
- ✓ Validation: Max 31-day date range per run (@PreAuthorize("hasRole('ADMIN')"))
- ✓ Database: reconciliation_runs, reconciliation_items tables with indexes (run_id, discrepancy_type)
- ✓ Configuration: spring.batch.jdbc.initialize-schema=always, spring.batch.job.enabled=false, reconciliation.* properties
- ✓ Stripe config: STRIPE_API_KEY env var (no hardcoded test key)
- **Status:** COMPLETE (March 31, 2026)
- **Effort Actual:** ~6 days backend + 4 files new, 1 modified pom.xml
- **Benefits:** Detects payment discrepancies early, audit trail for reconciliation items, manual + scheduled processing

**FR-3.4: Frontend Date/Time Utilities (COMPLETE ✓ April 1, 2026)**
- ✓ date-format.util.ts: Timezone-safe formatDate, combineDatetime, parseTime
- ✓ Integration: showtime-form-dialog, movie-form-dialog use utilities for datetime handling
- ✓ Solves: Timezone offset issues when submitting datetime forms
- **Status:** COMPLETE (April 1, 2026)
- **Impact:** Prevents incorrect datetime values in form submissions

**FR-3.5: Stripe Reconciliation Dashboard (COMPLETE ✓ March 31, 2026)**
- ✓ reconciliation-dashboard.component.ts: Main component for reconciliation tab
- ✓ reconciliation-detail.component.ts: Detail view for single run with filtering
- ✓ Displays: Reconciliation run history, item details, CSV export
- ✓ Actions: Manual trigger button (date range picker), resolve items with notes
- ✓ Filters: By discrepancyType (MATCHED, STATUS_MISMATCH, AMOUNT_MISMATCH, MISSING_LOCAL, MISSING_STRIPE)
- ✓ Admin API service: triggerReconciliation, getRuns, getRunDetails, getRunItems, getSummary, resolveItem
- **Status:** COMPLETE (March 31, 2026)
- **Benefits:** Real-time visibility into payment discrepancies, audit trail for resolution

**FR-3.6: Booking Payment Integration** (was FR-3.3)
- Complete Stripe checkout flow in frontend
- Client secret exchange for payment confirmation
- Error handling for failed payments
- Refund API for admin (ADMIN role only)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)

**FR-3.7: User Booking History** (was FR-3.4)
- GET /api/bookings/user (all user bookings with statuses)
- GET /api/bookings/{bookingId} (booking details + payment status)
- GET /api/payments/user (payment history)
- Cancel booking API (PENDING/CONFIRMED bookings)
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)

**FR-3.8: Admin Dashboard (COMPLETE ✓)** (was FR-3.5)
- ✓ Movie management (CRUD, featured movies)
- ✓ Theater management (capacity, location)
- ✓ Showtime scheduling (CRUD operations)
- ✓ Payment history view (admin-only read)
- Booking analytics (occupancy, cancellation rate) - planned for Phase 4
- **Status:** COMPLETE (March 13, 2026)
- **Implementation:** MatTable lists with MatDialog forms, admin tab navigation

**FR-3.9: Rate Limiting on Sensitive Endpoints** (was FR-3.6)
- /api/auth/login: 5 attempts per IP per minute
- /api/auth/register: 1 per IP per hour
- /api/auth/forgot-password: 3 per IP per hour
- Return 429 Too Many Requests
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)

---

### Phase 4: Infrastructure Simplification & Security (PARTIALLY COMPLETE)

**Status:** Partially Complete
**Timeline:** April 2026 - Ongoing
**Focus:** Simplified architecture, production hardening, and compliance

**Completed Features:**

**FR-4.0: Infrastructure Simplification (COMPLETE ✓ April 9, 2026)**
- ✓ **Removed Eureka Service Discovery:** Deleted eureka-server module, removed eureka-client from all 6 services
- ✓ **Removed Config Server:** Deleted config-server module, removed config-client and spring.config.import from all services
- ✓ **Adopted Static Service URIs:** Services configured via application-k8s.yml with K8s DNS (e.g., auth-service:8081)
- ✓ **Simplified Configuration:** Environment variables for secrets, no centralized config repository
- ✓ **Removed API Gateway:** Deleted Spring Cloud Gateway service, replaced with NGINX K8s Ingress
- ✓ **Updated docker-compose:** Removed eureka-server and config-server services
- ✓ **Updated Prometheus:** Removed scrape jobs for eureka and config services
- ✓ **Updated pom.xml:** All 6 services cleaned of spring-cloud-eureka and config-client dependencies
- **Status:** COMPLETE (April 9, 2026)
- **Benefits:** Reduced operational complexity, faster startup, smaller memory footprint, K8s-native approach

**FR-4.1: Audit Logging (COMPLETE ✓ March 21, 2026)**
- ✓ Centralized audit-service (:8086) — Kafka event consumer
- ✓ AuditLog entity (eventId UNIQUE, userId, userIp, action, entityType, entityId, beforeState, afterState, sourceService, traceId, requestPath, createdAt)
- ✓ @Auditable AOP annotation for auto-capture on business service methods
- ✓ Admin API: GET /api/audit/logs (paginated, filtered by userId/action/entityType/dateRange) + GET /api/audit/logs/{id}
- ✓ Kafka topic: audit-events with consumer dedup (eventId UNIQUE)
- ✓ PostgreSQL persistence (auditdb) with indexes for query performance
- ✓ admin-only access via @PreAuthorize("hasRole('ADMIN')")
- ✓ Error handling: 3 retries, exponential backoff (1s→2s→4s capped 10s), DLT for failures
- **Status:** COMPLETE (March 21, 2026)
- **Implementation:** Auto-configuration via kafka-events shared library

**Planned Features:**

**FR-4.2: Two-Factor Authentication (2FA)**
- TOTP support via authenticator apps
- POST /api/auth/2fa/enable (generate QR code)
- POST /api/auth/2fa/disable (requires password)
- Backup codes for account recovery
- **Priority:** MEDIUM
- **Effort:** Large (6-8 days)

**FR-4.3: OAuth2 Integration (COMPLETE ✓ March 16, 2026)**
- ✓ Google OAuth2 login via Spring Security OAuth2 Client
- ✓ Auto-create user on first OAuth2 login (password=NULL, ROLE_USER, active=true)
- ✓ Auto-link existing user by email when email_verified=true (verification by Google)
- ✓ UserOAuthProvider table for provider linkage tracking
- ✓ OAuth2AuthenticationSuccessHandler: JWT + refresh token generation, redirect to frontend
- ✓ Custom OAuth2UserLinkingService: Find/create user, handle concurrent logins
- ✓ API Gateway routes: /oauth2/authorization/**, /login/oauth2/code/**
- ✓ Frontend: OAuth2CallbackComponent, "Sign in with Google" button
- ✓ Guards: Password change blocked for OAuth-only users (password=NULL)
- **Status:** COMPLETE (March 16, 2026)
- **Priority:** Was LOW, now COMPLETE
- **Implementation Time:** ~7 days

**FR-4.4: Distributed Tracing**
- OpenTelemetry integration
- Correlation IDs on all requests (X-Correlation-ID)
- Export traces to Jaeger
- Trace async Kafka messages
- **Priority:** MEDIUM
- **Effort:** Medium (3-4 days)

**FR-4.5: Kubernetes Deployment (COMPLETE ✓ April 7, 2026)**
- ✓ Full K8s manifests under `/k8s` directory for Minikube / OrbStack deployment
- ✓ Services use ClusterIP for inter-service communication
- ✓ NGINX Ingress resource for path-based routing (no API Gateway)
- ✓ ConfigMaps per service for environment configuration
- ✓ Kubernetes Secrets for sensitive data (JWT_SECRET, STRIPE_SECRET_KEY, mail credentials)
- ✓ StatefulSet or Deployment for PostgreSQL, Kafka, Redis (configurable)
- ✓ Deployment manifest for all 6 business services
- ✓ Service Discovery via K8s DNS (e.g., auth-service:8081, movie-service:8082)
- ✓ Updated docker-compose to use static service URIs (already compatible)
- ✓ All monitoring stack (Prometheus, Grafana, Loki, Zipkin) K8s-ready
- **Status:** COMPLETE (April 7, 2026)
- **Benefits:** Native Kubernetes support, production-ready deployments, no external service discovery needed

**Phase 3 Success Metrics:**
- 99.95% availability in production
- p95 response time: < 300ms
- Logs queryable and searchable in ELK stack
- All deployments tracked in metrics
- Alert rules configured for critical issues

---

### Phase 4: Scaling & Architecture (PLANNED)

**Status:** Planned
**Timeline:** August - October 2026
**Focus:** Enterprise scale and microservices integration

**Planned Features:**

**FR-4.1: Caching Layer**
- Implement Redis for session/token caching
- Cache user roles to reduce DB queries
- Cache validation results (token signatures)
- TTL-based cache invalidation
- **Priority:** MEDIUM
- **Effort:** Medium (3-4 days)

**FR-4.2: API Gateway Integration**
- Document integration with API Gateway (Kong, AWS API Gateway)
- Provide JWT validation middleware
- Support API Gateway auth delegation
- Rate limiting at gateway level
- **Priority:** MEDIUM
- **Effort:** Medium (2-3 days)

**FR-4.3: Multi-Tenancy (Optional)**
- Support multiple organizations/tenants
- Data isolation at database/application level
- Tenant ID in JWT claims
- Separate audit logs per tenant
- **Priority:** LOW
- **Effort:** Large (8-10 days)

**FR-4.4: Admin Dashboard (Companion Service)**
- Create separate admin UI (React/Angular)
- User management (CRUD)
- Role management
- View audit logs and metrics
- **Priority:** LOW
- **Effort:** Large (10-15 days, not in this service)

**FR-4.5: Kubernetes Deployment**
- Create Helm chart for Kubernetes deployment
- ConfigMap for non-secret config
- Secret for credentials (jwtSecret, dbPassword)
- Liveness/readiness probes
- Horizontal Pod Autoscaler configuration
- **Priority:** MEDIUM
- **Effort:** Medium (2-3 days)

**FR-4.6: CI/CD Pipeline**
- GitHub Actions for automated testing on PR
- Build Docker image on merge to main
- Push to container registry (Docker Hub, ECR)
- Run security scans (Trivy, Snyk)
- Automated deployment to staging
- **Priority:** HIGH
- **Effort:** Medium (2-3 days)

**Phase 4 Success Metrics:**
- Supports 10K concurrent users per instance
- Sub-100ms p95 latency with caching
- Automated deployments 10+ times per day
- Zero manual deployment steps
- Infrastructure as Code for all deployments

---

## Dependency Chain

```
Phase 1 ──┬─→ Phase 2 ──┬─→ Phase 3 ──┬─→ Phase 4
          │            │             │
          │ (Blocking:  │ (Blocking:  │ (Blocking:
          │ Input      │ Audit      │ Caching,
          │ Validation)│ Logging,   │ K8s,
          │            │ Rate Limit)│ CI/CD)
          │            │            │
          └────────────┴────────────┴──→ Production Ready
```

**Critical Path:**
- Phase 1: Token Refresh + Input Validation (foundation)
- Phase 2: Token Revocation + Security Hardening (enterprise)
- Phase 3: Health Checks + Migrations (operations)
- Phase 4: Kubernetes + CI/CD (scale)

---

## Timeline & Milestones

| Phase | Start | End | Effort | Status |
|-------|-------|-----|--------|--------|
| Phase 0 | Past | Past | ~20d | COMPLETE ✓ |
| Phase 1.1 (Refresh) | Feb 10 | Feb 20 | 3-5d | TODO |
| Phase 1.2 (Profile) | Feb 20 | Mar 5 | 3-4d | TODO |
| Phase 1.3 (Password Reset) | Mar 5 | Mar 15 | 4-5d | TODO |
| Phase 1.4 (Validation) | Mar 15 | Mar 22 | 2-3d | TODO |
| Phase 1.5 (Rate Limiting) | Mar 22 | Mar 30 | 2-3d | TODO |
| Phase 1 Completion | Mar 30 | | | ETA |
| Phase 2.1 (JWT Claims) | Apr 1 | Apr 5 | 1-2d | TODO |
| Phase 2.2 (Revocation) | Apr 5 | Apr 15 | 3-4d | TODO |
| Phase 2.3 (2FA) | Apr 15 | May 5 | 5-7d | TODO |
| Phase 2.4 (Audit Logs) | May 5 | May 15 | 3-4d | TODO |
| Phase 2.5 (OAuth2) | May 15 | Jun 1 | 5-7d | TODO |
| Phase 2.6 (Security Scan) | Jun 1 | Jun 5 | 2-3d | TODO |
| Phase 2 Completion | Jun 5 | | | ETA |

---

## Resource Allocation

**Team Composition:**
- 1 Backend Engineer (primary implementation)
- 1 QA/Test Engineer (testing & quality)
- 1 DevOps/Infrastructure (Phase 3+, Docker/K8s)
- 1 Security Review (Phase 2, OAuth2/2FA/Audit)

**Estimated Total Effort:**
- Phase 1: ~18-20 days
- Phase 2: ~20-25 days
- Phase 3: ~12-15 days
- Phase 4: ~10-15 days
- **Total: 60-75 person-days**

---

## Success Criteria by Phase

### Phase 1: Enhancement
- [ ] All new features have unit tests (> 70% coverage)
- [ ] All endpoints documented in OpenAPI/Swagger
- [ ] No security vulnerabilities in new code
- [ ] Password reset works end-to-end
- [ ] Rate limiting prevents brute force attempts
- [ ] User can refresh token without re-login

### Phase 2: Security Hardening
- [ ] 2FA working with authenticator apps (Google Authenticator, Authy)
- [ ] Token revocation verified (blacklist working)
- [ ] Audit logs stored and queryable
- [ ] OAuth2 login working with at least 2 providers
- [ ] All OWASP Top 10 mitigations in place
- [ ] Zero high/critical CVEs in dependencies

### Phase 3: Operations
- [ ] Health checks returning correct status
- [ ] Metrics exported to Prometheus format
- [ ] Distributed traces visible in Jaeger UI
- [ ] Database schema versioned with Flyway
- [ ] Deployment automated via CI/CD
- [ ] 99.5%+ uptime in staging environment

### Phase 4: Scaling
- [ ] Load tests show 10K+ concurrent user support
- [ ] Cache hit ratio > 80% for hot data
- [ ] Horizontal scaling tested (2+ replicas)
- [ ] Kubernetes deployment manifests reviewed
- [ ] Helm chart installable without manual steps
- [ ] Production deployment checklist documented

---

## Known Issues & Debt

**Current Limitations:**
1. **No token refresh:** Users must re-login after 24 hours
2. **No logout:** Tokens can't be invalidated before expiration
3. **Limited validation:** No password strength checks
4. **JJWT version:** 0.9.0 is stable but older (0.12.x available)
5. **No audit trail:** Login attempts not logged
6. **Hardcoded secret:** JWT secret in application.yml (should be env var)
7. **Limited test coverage:** Only 1 smoke test

**Technical Debt:**
- Add integration tests for auth flow
- Implement request/response logging
- Extract magic numbers to constants
- Add error handling middleware (global exception handler)
- Improve error messages (currently generic)

---

## Risk Assessment

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|-----------|
| JJWT security vulnerability | HIGH | LOW | Phase 2: Upgrade to 0.12.x |
| Spring Security breaking change | HIGH | LOW | Monitor release notes, test upgrades in CI |
| Database scalability limit | MEDIUM | MEDIUM | Phase 4: Add caching, connection pooling tuning |
| Token theft (no logout) | HIGH | MEDIUM | Phase 2: Implement token blacklist |
| Brute force attacks | MEDIUM | HIGH | Phase 1: Add rate limiting |
| Performance degradation at scale | MEDIUM | MEDIUM | Phase 3: Add metrics, Phase 4: caching |

---

## Open Questions

1. **Multi-tenancy:** Is this a requirement? (Currently not planned)
2. **API Gateway:** Will this integrate with Kong, AWS API Gateway, or custom?
3. **Email Service:** Which provider for password reset emails? (SendGrid, AWS SES)
4. **Audit Retention:** How long to keep audit logs? (Proposed: 90 days)
5. **OAuth2 Providers:** Which providers to prioritize? (Suggested: Google, GitHub)
6. **Deployment Target:** Kubernetes only, or also Docker Swarm/traditional servers?
7. **Admin Dashboard:** Build in this repo or separate frontend service?
8. **Backward Compatibility:** Must maintain HS512 or can switch to RS256?

---

## Related Documents

- [Project Overview & PDR](./project-overview-pdr.md)
- [System Architecture](./system-architecture.md)
- [Deployment Guide](./deployment-guide.md)
- [Code Standards](./code-standards.md)
- [Development Rules](./../.claude/rules/development-rules.md)
