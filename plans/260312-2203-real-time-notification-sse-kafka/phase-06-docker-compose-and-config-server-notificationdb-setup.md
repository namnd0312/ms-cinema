# Phase 6: Docker Compose & Config Server — notificationdb Setup

## Context Links
- [docker-compose.yml](../../docker-compose.yml)
- [notification-service application.yml](../../notification-service/src/main/resources/application.yml)
- [config-server notification-service.yml](../../config-server/src/main/resources/config-repo/notification-service.yml)
- [config-server api-gateway.yml](../../config-server/src/main/resources/config-repo/api-gateway.yml)
- [Plan overview](./plan.md)

## Overview
- **Priority:** P1
- **Status:** pending
- **Effort:** 30m

Configure PostgreSQL `notificationdb` database creation, update docker-compose for notification-service database dependency, and update config-server with any environment-specific overrides. Can run in parallel with Phases 2-5.

## Key Insights
- Single PostgreSQL instance (`postgres-service`) hosts all databases: testdb, moviedb, bookingdb, paymentdb
- No init-db script exists — databases created manually or via docker entrypoint
- Other services use env vars: `DB_HOST`, `DB_USERNAME`, `DB_PASSWORD` + datasource URL in application.yml
- notification-service currently has NO postgres dependency in docker-compose
- Need to create `notificationdb` database on PostgreSQL startup
- JPA `ddl-auto: update` handles table creation automatically

## Requirements

### Functional
- `notificationdb` database created on PostgreSQL container startup
- notification-service docker-compose entry updated with DB env vars
- Config-server notification-service.yml updated if needed

### Non-functional
- Database auto-created without manual intervention
- Consistent with existing service patterns

## Architecture

```
postgres-service (PostgreSQL 16)
  ├── testdb       (auth-service)
  ├── moviedb      (movie-service)
  ├── bookingdb    (booking-service)
  ├── paymentdb    (payment-service)
  └── notificationdb  ← NEW (notification-service)
```

## Related Code Files

### Files to Modify
1. `docker-compose.yml` — add postgres dependency + DB env vars for notification-service, add init script
2. `notification-service/src/main/resources/application.yml` — add datasource + JPA config (also in Phase 2)
3. `config-server/src/main/resources/config-repo/notification-service.yml` — add DB overrides if needed

### Files to Create
1. `init-db/init-databases.sql` — SQL script to create all databases on postgres startup

## Implementation Steps

### Step 1: Create Database Init Script

Create `init-db/init-databases.sql`:
```sql
-- Create databases for all services (idempotent)
SELECT 'CREATE DATABASE testdb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'testdb')\gexec
SELECT 'CREATE DATABASE moviedb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'moviedb')\gexec
SELECT 'CREATE DATABASE bookingdb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'bookingdb')\gexec
SELECT 'CREATE DATABASE paymentdb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'paymentdb')\gexec
SELECT 'CREATE DATABASE notificationdb' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notificationdb')\gexec
```

Alternative simpler approach — use multiple `POSTGRES_MULTIPLE_DATABASES` or mount init script:
```sql
CREATE DATABASE notificationdb;
```

Note: Check if other databases are created via `POSTGRES_DB` env var or init scripts. If manually created, just add `notificationdb` to the same process.

### Step 2: Mount Init Script in docker-compose.yml

Update `postgres-service` in `docker-compose.yml`:
```yaml
postgres-service:
  image: 'postgres:16-alpine'
  volumes:
    - ./init-db:/docker-entrypoint-initdb.d
  environment:
    - POSTGRES_USER=postgres
    - POSTGRES_PASSWORD=postgres
```

PostgreSQL auto-runs `.sql` files in `/docker-entrypoint-initdb.d/` on first startup.

### Step 3: Update notification-service in docker-compose.yml

Add postgres dependency and DB environment variables:
```yaml
notification-service:
  depends_on:
    - postgres-service    # ← ADD
    - eureka-server
    - config-server
    - kafka
    - redis-service
  environment:
    <<: *java-tz
    SERVER_PORT: 8085
    EUREKA_HOST: eureka-server
    CONFIG_SERVER_HOST: config-server
    DB_HOST: postgres-service        # ← ADD
    DB_USERNAME: postgres            # ← ADD
    DB_PASSWORD: postgres            # ← ADD
    KAFKA_HOST: kafka
    REDIS_HOST: redis-service
```

### Step 4: Verify notification-service application.yml Has Datasource

Ensure Phase 2 adds this to `notification-service/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/notificationdb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

### Step 5: Update config-server notification-service.yml (optional)

Add DB config override if needed for production profile:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/notificationdb
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
```

### Step 6: Verify Docker Build

```bash
docker-compose build notification-service
docker-compose up postgres-service  # verify notificationdb created
```

## Todo List
- [ ] Create init-db/init-databases.sql (or add notificationdb to existing init process)
- [ ] Mount init-db volume in postgres-service
- [ ] Add postgres dependency + DB env vars to notification-service in docker-compose.yml
- [ ] Add KAFKA_HOST and REDIS_HOST env vars if missing
- [ ] Update config-server notification-service.yml
- [ ] Verify docker-compose build succeeds
- [ ] Verify notificationdb created on postgres startup

## Success Criteria
- `docker-compose up` creates notificationdb automatically
- notification-service connects to notificationdb on startup
- JPA creates `notifications` table via ddl-auto
- No manual database creation required

## Risk Assessment
- **Init script runs only on first startup**: If postgres volume already exists, init script won't run. Must manually create DB or delete volume. Document this in deployment guide.
- **Database name conflict**: `notificationdb` is unique — no conflict risk

## Security Considerations
- Database credentials via environment variables (not hardcoded in production)
- Same postgres user for all databases — acceptable for development; separate users recommended for production

## Next Steps
- All phases complete → integration testing end-to-end
- Update README.md with new notification-service capabilities
- Update docs/system-architecture.md with SSE flow
