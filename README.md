# MS Cinema - Microservices Ticket Booking Platform

Enterprise-grade cinema ticket booking system built on Spring Boot 3.4.3 microservices with Kafka event streaming, JWT authentication, Stripe payments, Redis caching, and comprehensive monitoring.

**Version:** 0.0.1-SNAPSHOT | **Java:** 21 LTS | **Spring Boot:** 3.4.3 | **Spring Cloud:** 2024.0.1

## Architecture Overview

**11 Maven modules:**
- 5 Business Services (auth, movie, booking, payment, notification)
- 3 Infrastructure Services (eureka-server, config-server, api-gateway)
- 2 Shared Libraries (jwt-auth starter, kafka-events)
- 1 Frontend (Angular 18)

## Quick Start

### Prerequisites
- Java 21 LTS
- Maven 3.8+
- Docker & Docker Compose (recommended)

### Option 1: Docker Compose (Recommended)
```bash
docker-compose up --build
# Starts: PostgreSQL, Kafka, Redis, Prometheus, Grafana, Loki, all 8 services
```

### Option 2: Local Setup
```bash
# Start infrastructure (PostgreSQL, Kafka, Redis)
docker-compose up postgres kafka redis

# In separate terminals, build & run each service:
mvn -pl eureka-server spring-boot:run           # port 8761
mvn -pl config-server spring-boot:run           # port 8888
mvn -pl api-gateway spring-boot:run             # port 8080
mvn -pl auth-service spring-boot:run            # port 8081
mvn -pl movie-service spring-boot:run           # port 8082
mvn -pl booking-service spring-boot:run         # port 8083
mvn -pl payment-service spring-boot:run         # port 8084
mvn -pl notification-service spring-boot:run    # port 8085
```

## Services at a Glance

| Service | Port | Purpose | Key Features |
|---------|------|---------|--------------|
| eureka-server | 8761 | Service discovery | Dynamic registration |
| config-server | 8888 | Centralized config | Git/classpath profiles |
| api-gateway | 8080 | Request routing | OpenAPI aggregation, logging |
| auth-service | 8081 | Authentication | JWT, email activation, account lockout |
| movie-service | 8082 | Movies/ratings/comments | Showtimes, auto seat grids, star ratings, comments, reactions |
| booking-service | 8083 | Seat reservation | Redis locking, lifecycle states |
| payment-service | 8084 | Payment processing | Stripe, webhook verification |
| notification-service | 8085 | Notifications (email + in-app SSE) | Kafka consumer, SSE emitters, PostgreSQL persistence, JWT auth via query param |
| cinema-frontend | 4200→80 | Web UI | Angular 18, Material, Stripe.js, ratings UI |

## API Documentation

**Swagger UI:** http://localhost:8080/swagger-ui.html (aggregated by gateway)

**Individual service docs:**
- Auth: http://localhost:8081/swagger-ui.html
- Movie: http://localhost:8082/swagger-ui.html
- Booking: http://localhost:8083/swagger-ui.html
- Payment: http://localhost:8084/swagger-ui.html

See [docs/api-documentation.md](./docs/api-documentation.md) for full endpoint reference.

## Key Technologies

**Backend:** Spring Boot 3.4.3, Spring Cloud 2024.0.1, Spring Security, Spring Data JPA

**Data:** PostgreSQL 16 (per-service databases), Redis 7, Kafka 3.7 KRaft

**Authentication:** JJWT 0.12.6 (HS512), JWT refresh rotation, Redis token blacklist

**Payments:** Stripe (idempotency, webhook verification)

**Monitoring:** Prometheus (9090), Grafana (3000), Loki 3.0 (3100)

**Frontend:** Angular 18, TypeScript 5.5, Material 18, Stripe.js 8.9

## Database Schema

Each service has own database:

**auth-service (testdb):** users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens

**movie-service (moviedb):** movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions

**booking-service (bookingdb):** bookings, booking_seats

**payment-service (paymentdb):** payments

**notification-service (notificationdb):** notifications (userId, eventId, notificationType, status, isRead, createdAt)

## Kafka Event Flow

| Topic | Producer | Consumer | Events |
|-------|----------|----------|--------|
| movie-events | movie-service | (future) | MovieCreatedEvent, ShowtimeCreatedEvent |
| payment-events | payment-service | booking-service | PaymentCompletedEvent, PaymentFailedEvent |
| notification-events | auth-service, booking-service | notification-service | NotificationRequestedEvent, InAppNotificationEvent |
| notification.in_app | payment-service | notification-service | InAppNotificationEvent (payment confirm/fail) |

Error handling: 3 retries, exponential backoff, DLT for failures.

## Documentation

- [Project Overview & PDR](./docs/project-overview-pdr.md) — Phases & requirements
- [Codebase Summary](./docs/codebase-summary.md) — Module structure & key classes
- [Code Standards](./docs/code-standards.md) — Spring 3.x patterns, project conventions
- [System Architecture](./docs/system-architecture.md) — Flows, data model, deployment
- [API Documentation](./docs/api-documentation.md) — All endpoints & examples
- [Deployment Guide](./docs/deployment-guide.md) — Docker, AWS, troubleshooting
- [Project Roadmap](./docs/project-roadmap.md) — Phases & progress

## Configuration

**Service Configuration:** `config-server` loads from `classpath:/config-repo/` + Git (if enabled)

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

**Services won't start:** Check `config-server` logs; ensure PostgreSQL/Kafka/Redis are running.

**API returns 401:** JWT expired (use refresh token) or invalid secret; verify Authorization header.

**Stripe webhook fails:** Check webhook secret in config-server; validate signature.

**Booking fails:** Redis lock timeout (increase TTL) or seat already reserved; check booking-service logs.

See [docs/deployment-troubleshooting.md](./docs/deployment-troubleshooting.md) for detailed troubleshooting.

## License

Proprietary
