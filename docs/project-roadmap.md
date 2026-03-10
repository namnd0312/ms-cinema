# Project Roadmap

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Updated:** February 2026
**Status:** Active Development

## Vision

MS Cinema is a foundational authentication service providing stateless JWT-based authentication for Spring Boot applications. The roadmap focuses on progressing from a basic implementation to a robust, enterprise-ready authentication platform supporting microservices architectures.

## Roadmap Phases

### Phase 0: Foundation (COMPLETE ✓)

**Status:** Complete
**Timeline:** Past
**Focus:** Core JWT authentication implementation

**Completed Features:**
- ✓ User authentication (login/register)
- ✓ JWT token generation (HS512)
- ✓ Token validation on protected endpoints
- ✓ Role-based access control (@PreAuthorize)
- ✓ BCrypt password encoding
- ✓ PostgreSQL data persistence
- ✓ Docker containerization
- ✓ Basic test coverage

**Artifacts:**
- AuthController with /login, /register endpoints
- JwtService with token generation/validation
- SecurityConfig with filter chain
- User/Role entities with many-to-many relationship
- docker-compose with PostgreSQL + Spring Boot service
- Basic documentation (README, architecture overview)

**Lessons Learned:**
- Stateless JWT design enables horizontal scaling
- HS512 signature validation is fast (no DB calls)
- BCrypt password hashing provides secure storage
- EAGER loading of roles necessary for SecurityContext

---

### Phase 1: Enhancement (IN PROGRESS)

**Status:** In Progress
**Timeline:** February - April 2026
**Focus:** Improve user experience and add foundational features

**Planned Features:**

**FR-1.1: Token Refresh Mechanism**
- Implement refresh token endpoint: POST /api/auth/refresh
- Store refresh tokens in database with expiration
- Return new access token + refresh token on refresh
- Invalidate refresh tokens on logout
- **Priority:** HIGH
- **Effort:** Medium (3-5 days)
- **Acceptance Criteria:**
  - Access token expires after 15 minutes
  - Refresh token valid for 7 days
  - Client can get new access token without re-login
  - Revoked refresh tokens return 401

**FR-1.2: User Profile Endpoints**
- GET /api/users/me - Fetch current user profile
- PUT /api/users/me - Update profile (email, phone, etc)
- GET /api/users/{id} - Get user by ID (admin only)
- DELETE /api/users/{id} - Delete user (admin only)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)
- **Acceptance Criteria:**
  - Protected by @PreAuthorize("isAuthenticated()")
  - /me endpoint requires valid JWT
  - Update validates email format
  - Admin endpoints require ROLE_ADMIN

**FR-1.3: Password Reset Flow**
- POST /api/auth/forgot-password - Request password reset
- POST /api/auth/reset-password - Complete reset with token
- Send reset link via email (integration point)
- Token expires after 1 hour
- **Priority:** MEDIUM
- **Effort:** Medium (4-5 days)
- **Acceptance Criteria:**
  - Reset token is cryptographically random
  - Single-use tokens (consumed on reset)
  - Email with reset link sent to user
  - Expired tokens return 400

**FR-1.4: Input Validation**
- Add @Valid annotations to DTOs
- Implement custom validators (username format, password strength)
- Return detailed validation error messages
- Sanitize inputs to prevent injection
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)
- **Acceptance Criteria:**
  - Password >= 8 chars, 1 uppercase, 1 number
  - Username alphanumeric + underscore only, 3-20 chars
  - Email valid format
  - 400 Bad Request with field-level error details

**FR-1.5: Rate Limiting**
- Implement rate limiter on /api/auth/login (5 attempts per minute)
- Implement rate limiter on /api/auth/register (1 per IP per hour)
- Return 429 Too Many Requests on limit exceeded
- Add X-RateLimit headers to responses
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)
- **Acceptance Criteria:**
  - Blocking after 5 failed logins from same IP
  - Blocking after 1 register from same IP per hour
  - Clear headers showing limit, remaining, reset time
  - Distributed rate limiting via Redis (optional, phase 2)

**Phase 1 Success Metrics:**
- Token refresh success rate: > 99%
- Login endpoint response time: < 200ms (p95)
- 70%+ code coverage (unit tests)
- Zero security vulnerabilities in dependencies
- All endpoints documented in OpenAPI/Swagger

---

### Phase 2: Security Hardening (PLANNED)

**Status:** Planned
**Timeline:** April - June 2026
**Focus:** Enterprise security standards and compliance

**Planned Features:**

**FR-2.1: JWT Advanced Claims**
- Add issuer (iss) claim to all tokens
- Add audience (aud) claim (resource identifier)
- Add JWT ID (jti) claim for token uniqueness
- Validate all claims on token verification
- **Priority:** MEDIUM
- **Effort:** Small (1-2 days)

**FR-2.2: Token Revocation & Logout**
- Implement logout endpoint: POST /api/auth/logout
- Maintain token blacklist (Redis or database)
- Check blacklist on every token validation
- Clean up expired tokens periodically (cron job)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)
- **Acceptance Criteria:**
  - Logout endpoint invalidates refresh token
  - Blacklisted tokens return 401 immediately
  - Expired tokens auto-cleaned after expiration + buffer

**FR-2.3: Two-Factor Authentication (2FA)**
- Support TOTP (Time-based One-Time Password) via authenticator apps
- POST /api/auth/2fa/enable - Enable 2FA
- POST /api/auth/2fa/disable - Disable 2FA
- POST /api/auth/2fa/verify - Submit OTP during login
- **Priority:** MEDIUM
- **Effort:** Large (5-7 days)

**FR-2.4: Audit Logging**
- Log all authentication events (login, register, logout, 2FA)
- Log all access to sensitive endpoints
- Include IP address, user agent, timestamp
- Store audit logs separately with rotation
- Endpoint: GET /api/admin/audit-logs (admin only)
- **Priority:** HIGH
- **Effort:** Medium (3-4 days)

**FR-2.5: OAuth2 Integration**
- Add OAuth2 provider support (Google, GitHub)
- Endpoints: GET /api/auth/oauth2/{provider}/callback
- Link social account to existing user account
- Auto-create user on first OAuth2 login
- **Priority:** MEDIUM
- **Effort:** Large (5-7 days)

**FR-2.6: Dependency Security Scanning**
- Add OWASP Dependency Check to Maven build
- Upgrade JJWT from 0.9.0 to 0.12.x
- Upgrade Spring Boot to latest LTS (if compatible)
- Address CVEs in transitive dependencies
- **Priority:** HIGH
- **Effort:** Medium (2-3 days)

**Phase 2 Success Metrics:**
- OWASP Top 10 compliance verified
- Zero security warnings in dependency scan
- Audit logs retained for 90 days
- 80%+ code coverage (unit + integration tests)
- PII data masked in logs

---

### Phase 3: Operations & Monitoring (PLANNED)

**Status:** Planned
**Timeline:** June - August 2026
**Focus:** Production readiness and observability

**Planned Features:**

**FR-3.1: Health Check Endpoint**
- GET /actuator/health - Overall system health
- GET /actuator/health/db - Database connectivity
- GET /actuator/health/livenessProbe - Kubernetes liveness
- GET /actuator/health/readinessProbe - Kubernetes readiness
- **Priority:** HIGH
- **Effort:** Small (1-2 days)

**FR-3.2: Metrics & Observability**
- Expose Micrometer metrics on /actuator/metrics
- Track authentication success/failure rates
- Track endpoint response times (percentiles)
- Track database connection pool utilization
- Export to Prometheus format
- **Priority:** MEDIUM
- **Effort:** Medium (2-3 days)

**FR-3.3: Distributed Tracing**
- Integrate OpenTelemetry for request tracing
- Add correlation IDs to all requests
- Trace database queries through JPA
- Export traces to Jaeger
- **Priority:** MEDIUM
- **Effort:** Medium (3-4 days)

**FR-3.4: Request Logging Middleware**
- Log all HTTP requests with method, path, status, duration
- Include user ID in structured logs (when authenticated)
- Separate logs: application, security, database
- Implement structured logging (JSON format)
- **Priority:** MEDIUM
- **Effort:** Small (2-3 days)

**FR-3.5: Database Migrations**
- Implement Flyway for schema versioning
- Create migration scripts for all DDL
- Support zero-downtime migrations
- Track schema version in database
- **Priority:** HIGH
- **Effort:** Medium (2-3 days)

**FR-3.6: Configuration Management**
- Move secrets from application.yml to environment variables
- Support environment-specific configs (dev, staging, prod)
- Implement Config Server integration (optional)
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
