# MS Cinema - Microservices Ticket Booking Platform

Enterprise-grade cinema ticket booking system built on Spring Boot 3.4.3 microservices with Kafka event streaming, JWT authentication, Stripe payments, Redis caching, and comprehensive monitoring.

**Version:** 0.0.1-SNAPSHOT | **Java:** 21 LTS | **Spring Boot:** 3.4.3 | **Spring Cloud:** 2024.0.1

## Architecture Overview

**8 Maven modules:**
- 6 Business Services (auth, movie, booking, payment, notification, audit)
- 2 Shared Libraries (jwt-auth-autoconfigure, kafka-events with @Auditable support)
- 1 Frontend (Angular 18)

**Routing:** K8s NGINX Ingress (path-based) in Kubernetes; frontend nginx.conf in Docker Compose. No dedicated gateway service.

## Quick Start

### Prerequisites
- Java 21 LTS
- Maven 3.8+
- Docker & Docker Compose (recommended)

### Option 1: Docker Compose (Recommended)
```bash
docker-compose up --build
# Starts: PostgreSQL, Kafka, Redis, Prometheus, Grafana, Loki, Tempo, OTel Collector, all 7 app services
```

### Option 2: Local Setup
```bash
# Start infrastructure (PostgreSQL, Kafka, Redis)
docker-compose up postgres kafka redis

# In separate terminals, build & run each service:
mvn -pl auth-service spring-boot:run            # port 8081
mvn -pl movie-service spring-boot:run           # port 8082
mvn -pl booking-service spring-boot:run         # port 8083
mvn -pl payment-service spring-boot:run         # port 8084
mvn -pl notification-service spring-boot:run    # port 8085
mvn -pl audit-service spring-boot:run           # port 8086
```

## Services at a Glance

| Service | Port | Purpose | Key Features |
|---------|------|---------|--------------|
| auth-service | 8081 | Authentication | JWT, email activation, account lockout, OAuth2 Google login, password history |
| movie-service | 8082 | Movies/ratings/comments | Showtimes, auto seat grids, star ratings, comments, reactions |
| booking-service | 8083 | Seat reservation | Redis locking, lifecycle states, WebSocket real-time seat updates |
| payment-service | 8084 | Payment processing | Stripe, webhook verification, payment notifications |
| notification-service | 8085 | Real-time notifications | SSE streaming, Kafka consumer, email (SMTP), PostgreSQL persistence |
| audit-service | 8086 | Audit logging | Kafka consumer, PostgreSQL persistence, admin API with filtering |
| tempo | 3200 | Distributed tracing backend | OTLP ingest, trace storage, Grafana datasource |
| otel-collector | 4317/4318 | OTel Collector | Receives OTLP from apps, exports to Tempo |
| cinema-frontend | 4200→80 | Web UI | Angular 18, Material, Stripe.js, real-time notification bell, seat grid with WebSocket |

## API Documentation

**Swagger UI (per service):**
- Auth: http://localhost:8081/swagger-ui.html
- Movie: http://localhost:8082/swagger-ui.html
- Booking: http://localhost:8083/swagger-ui.html
- Payment: http://localhost:8084/swagger-ui.html
- Notification: http://localhost:8085/swagger-ui.html
- Audit: http://localhost:8086/swagger-ui.html

See [docs/api-documentation.md](./docs/api-documentation.md) for full endpoint reference.

## Key Technologies

**Backend:** Spring Boot 3.4.3, Spring Cloud 2024.0.1, Spring Security, Spring Data JPA

**Data:** PostgreSQL 16 (per-service databases), Redis 7, Kafka 3.7 KRaft

**Authentication:** JJWT 0.12.6 (HS512), JWT refresh rotation, Redis token blacklist

**Payments:** Stripe (idempotency, webhook verification)

**Monitoring:** Prometheus (9090), Grafana (3000), Loki 3.0 (3100), Tempo 2.6 (3200), OTel Collector contrib 0.115 (4317/4318)

**Frontend:** Angular 18, TypeScript 5.5, Material 18, Stripe.js 8.9

**Distributed Tracing:** Micrometer Tracing → OpenTelemetry → OTLP/HTTP → OpenTelemetry Collector → Grafana Tempo. Trace-to-logs correlation in Grafana via Loki `service` label and Tempo `service.name` span attribute.

## Database Schema

Each service has own database:

**auth-service (testdb):** users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history

**movie-service (moviedb):** movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions

**booking-service (bookingdb):** bookings, booking_seats

**payment-service (paymentdb):** payments

**notification-service (notificationdb):** notifications (userId, eventId, notificationType, status, isRead, createdAt)

**audit-service (auditdb):** audit_logs (eventId, userId, userIp, action, entityType, entityId, beforeState, afterState, sourceService, traceId, requestPath, createdAt)

## Kafka Event Flow

| Topic | Producer | Consumer | Events |
|-------|----------|----------|--------|
| movie-events | movie-service | (future) | MovieCreatedEvent, ShowtimeCreatedEvent |
| payment-events | payment-service | booking-service | PaymentCompletedEvent, PaymentFailedEvent |
| notification-events | auth-service | notification-service | NotificationRequestedEvent (email) |
| notification.in_app | booking-service, payment-service | notification-service (SSE broadcast) | InAppNotificationEvent (payment/booking events) |
| audit-events | @Auditable-annotated services | audit-service | AuditEvent (userId, action, entityType, entityId, before/afterState) |

Error handling: 3 retries, exponential backoff, DLT for failures. Real-time in-app notifications delivered via SSE with 30s heartbeat.

## Documentation

- [Project Overview & PDR](./docs/project-overview-pdr.md) — Phases & requirements
- [Codebase Summary](./docs/codebase-summary.md) — Module structure & key classes
- [Code Standards](./docs/code-standards.md) — Spring 3.x patterns, project conventions
- [System Architecture](./docs/system-architecture.md) — Flows, data model, deployment
- [API Documentation](./docs/api-documentation.md) — All endpoints & examples
- [Deployment Guide](./docs/deployment-guide.md) — Docker, K8s, troubleshooting
- [Project Roadmap](./docs/project-roadmap.md) — Phases & progress
- **SSO Identity Provider** (Phase 06, COMPLETE May 29, 2026):
  - [SSO Partner Integration Guide](./docs/sso-partner-integration-guide.md) — OIDC partner setup, curl/Node/Java examples
  - [SSO Key Rotation Runbook](./docs/sso-key-rotation-runbook.md) — RS256 signing key lifecycle, rotation procedure
  - [SSO JWT Rollback Runbook](./docs/sso-jwt-rollback-runbook.md) — Emergency HS512 fallback procedures

## Configuration

**Service Configuration:** Services use `k8s` Spring profile with static URIs via K8s DNS (docker-compose) or environment variables.

**Key Environment Variables:**
```bash
JWT_SECRET=<base64-encoded-key>
MAIL_USERNAME=<gmail-account>
MAIL_PASSWORD=<gmail-app-password>
STRIPE_SECRET_KEY=<stripe-secret>
STRIPE_WEBHOOK_SECRET=<stripe-webhook-secret>
KAFKA_BROKERS=localhost:9092
REDIS_HOST=localhost
REDIS_PORT=6379
```

See individual service `application.yml` for service-specific config.

## Testing

```bash
mvn test                    # Unit tests
mvn integration-test        # Integration tests
mvn verify                  # Full verification
```

## Development

Follow [.claude/rules/development-rules.md](./.claude/rules/development-rules.md):
- Kebab-case file naming
- Keep code files <200 lines
- Run `mvn clean compile` before commit
- No secrets in git (use environment variables)

## Troubleshooting

**Services won't start:** Ensure PostgreSQL/Kafka/Redis are running and environment variables are set.

**API returns 401:** JWT expired (use refresh token) or invalid secret; verify Authorization header.

**Stripe webhook fails:** Check STRIPE_WEBHOOK_SECRET env var; validate signature.

**Booking fails:** Redis lock timeout (increase TTL) or seat already reserved; check booking-service logs.

See [docs/deployment-troubleshooting.md](./docs/deployment-troubleshooting.md) for detailed troubleshooting.

## License

Proprietary
