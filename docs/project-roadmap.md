# Project Roadmap

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Updated:** March 2026
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
- ✓ 11-module Maven structure (5 business services, 3 infrastructure, 2 shared libs, 1 frontend)
- ✓ Spring Cloud Eureka service discovery (:8761)
- ✓ Spring Cloud Config Server (:8888, classpath:/config-repo/)
- ✓ Spring Cloud Gateway MVC (:8080, OpenAPI aggregation)
- ✓ JWT tokens embed roles+userId claims for downstream use
- ✓ POST /api/auth/validate-token (microservice JWT validation, no DB hit)
- ✓ GET /api/users/me (authenticated user profile retrieval)
- ✓ jwt-auth-spring-boot-starter library (plug-in JWT auth for all services)
- ✓ **OpenAPI 3.0 documentation (Swagger UI, SpringDoc 2.8.4)**
- ✓ movie-service (CRUD movies/theaters/showtimes, auto-seat generation)
- ✓ booking-service (Redis locking, lifecycle states, Feign to movie-service)
- ✓ payment-service (Stripe integration, idempotency keys, webhook verification)
- ✓ notification-service (Kafka consumer, SMTP email, Redis dedup)
- ✓ kafka-events shared library (EventEnvelope, domain events)
- ✓ Kafka topics (movie-events, payment-events, notification-events)
- ✓ Prometheus (:9090, 15s scrape) + Grafana (:3000, 2 dashboards) + Loki (:3100)

**Success Metrics:**
- 11 services successfully deployed via docker-compose
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

**FR-3.1: Seat Grid Display & Booking UI**
- Frontend seat map visualization (theater layout A-Z rows)
- Interactive seat selection with hover/highlight
- Real-time seat availability updates
- Multiple seat selection for group bookings
- **Priority:** HIGH
- **Effort:** Medium (4-5 days)

**FR-3.2: Booking Payment Integration**
- Complete Stripe checkout flow in frontend
- Client secret exchange for payment confirmation
- Error handling for failed payments
- Refund API for admin (ADMIN role only)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)

**FR-3.3: User Booking History**
- GET /api/bookings/user (all user bookings with statuses)
- GET /api/bookings/{bookingId} (booking details + payment status)
- GET /api/payments/user (payment history)
- Cancel booking API (PENDING/CONFIRMED bookings)
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)

**FR-3.4: Admin Dashboard (COMPLETE ✓)**
- ✓ Movie management (CRUD, featured movies)
- ✓ Theater management (capacity, location)
- ✓ Showtime scheduling (CRUD operations)
- ✓ Payment history view (admin-only read)
- Booking analytics (occupancy, cancellation rate) - planned for Phase 4
- **Status:** COMPLETE (March 13, 2026)
- **Implementation:** MatTable lists with MatDialog forms, admin tab navigation

**FR-3.5: Rate Limiting on Sensitive Endpoints**
- /api/auth/login: 5 attempts per IP per minute
- /api/auth/register: 1 per IP per hour
- /api/auth/forgot-password: 3 per IP per hour
- Return 429 Too Many Requests
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)

---

### Phase 4: Security & Operations (PLANNED)

**Status:** Planned
**Timeline:** May - July 2026
**Focus:** Production hardening and compliance

**Planned Features:**

**FR-4.1: Audit Logging**
- Log all authentication events (IP, user agent, timestamp)
- Log sensitive operations (payment confirmation, refund)
- Separate audit log table (immutable, 1-year retention)
- GET /api/admin/audit-logs (admin only, paginated)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)

**FR-4.2: Two-Factor Authentication (2FA)**
- TOTP support via authenticator apps
- POST /api/auth/2fa/enable (generate QR code)
- POST /api/auth/2fa/disable (requires password)
- Backup codes for account recovery
- **Priority:** MEDIUM
- **Effort:** Large (6-8 days)

**FR-4.3: OAuth2 Integration**
- Google OAuth2 login
- GitHub OAuth2 login
- Auto-create user on first OAuth2 login
- Link OAuth2 account to existing user
- **Priority:** LOW
- **Effort:** Large (7-10 days)

**FR-4.4: Distributed Tracing**
- OpenTelemetry integration
- Correlation IDs on all requests (X-Correlation-ID)
- Export traces to Jaeger
- Trace async Kafka messages
- **Priority:** MEDIUM
- **Effort:** Medium (3-4 days)

**FR-4.5: Kubernetes Deployment**
- Helm charts for all services
- Liveness/readiness probes
- Resource limits & requests
- ConfigMaps for environment config
- Secrets for sensitive data
- **Priority:** MEDIUM
- **Effort:** Large (5-7 days)
- Document all configuration parameters
- **Priority:** HIGH
- **Effort:** Small (1-2 days)

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
