# Phase 1: Environment Setup & JMeter Configuration

## Context
- Parent plan: [plan.md](./plan.md)
- Docs: [deployment-guide](../../docs/deployment-guide.md), [system-architecture](../../docs/system-architecture.md)

## Overview
- **Priority**: P0 (prerequisite for all other phases)
- **Status**: pending
- **Description**: Install JMeter, prepare test data, configure connection settings, seed databases

## Key Insights
- All traffic goes through api-gateway (:8080) — test against gateway, not individual services
- JWT auth required for most endpoints — need token extraction & reuse in JMeter
- Booking uses Redis locks with 5-min TTL — must account for lock contention
- Payment has `fake-success` endpoint — use this instead of real Stripe for load tests
- Each service has its own PostgreSQL database

## Requirements

### Functional
- JMeter 5.6+ installed and configured for CLI (non-GUI) mode
- Test user accounts pre-created (1000+ users for concurrent login)
- Seed data: movies, theaters (with seats), showtimes in database
- CSV files for parameterized test data (usernames, passwords, movie IDs)

### Non-Functional
- JMeter heap: `-Xms2g -Xmx4g` for 1000+ threads
- Network: Run JMeter on same network as Docker (localhost or Docker host)

## Architecture
```
JMeter (CLI) → api-gateway (:8080) → [auth|movie|booking|payment]-service
                                    → PostgreSQL, Redis, Kafka
```

## Related Code Files
- `docker-compose.yml` — service definitions & ports
- `config-server/src/main/resources/config-repo/` — service configs
- `auth-service/src/main/resources/schema.sql` — user/role tables
- `movie-service/src/main/resources/schema.sql` — movie/theater/showtime tables

## Implementation Steps

1. **Install JMeter**
   ```bash
   brew install jmeter  # macOS
   # Or download from https://jmeter.apache.org/download_jmeter.cgi
   ```

2. **Create JMeter project structure**
   ```
   load-tests/
   ├── jmx/                    # JMeter test plans
   │   ├── auth-load-test.jmx
   │   ├── movie-load-test.jmx
   │   ├── booking-load-test.jmx
   │   ├── payment-load-test.jmx
   │   └── full-journey-e2e-load-test.jmx
   ├── data/                   # CSV test data
   │   ├── users.csv           # email,password
   │   ├── movies.csv          # movieId
   │   └── showtimes.csv       # showtimeId,seatIds
   ├── scripts/
   │   ├── seed-test-data.sql  # SQL to populate test data
   │   ├── run-load-test.sh    # CLI execution script
   │   └── generate-report.sh  # HTML report generator
   └── results/                # Output (gitignored)
       ├── jtl/
       └── reports/
   ```

3. **Create seed SQL script** (`seed-test-data.sql`)
   - Insert 1000 test users in `testdb.users` with BCrypt passwords
   - Insert 10 movies, 5 theaters (with auto-generated seats), 20 showtimes in `moviedb`
   - All users pre-activated (`active=true`) with `ROLE_USER`

4. **Create CSV data files**
   - `users.csv`: 1000 rows of `email,password` matching seeded users
   - `movies.csv`: movie IDs from seed data
   - `showtimes.csv`: showtime IDs with available seat IDs

5. **Configure JMeter properties**
   - `jmeter.properties`: increase thread limits, timeout settings
   - User-defined variables: `BASE_URL=http://localhost:8080`, `CONTENT_TYPE=application/json`

6. **JMeter JWT Token Handling**
   - HTTP Request → POST `/api/auth/login` → JSON Extractor → extract `token` field
   - Store in JMeter variable `${JWT_TOKEN}`
   - Add HTTP Header Manager: `Authorization: Bearer ${JWT_TOKEN}`
   - Use across all subsequent requests in thread group

## Todo
- [ ] Install JMeter 5.6+
- [ ] Create `load-tests/` directory structure
- [ ] Write `seed-test-data.sql` for 1000 users + movies/theaters/showtimes
- [ ] Generate `users.csv` with 1000 test user credentials
- [ ] Configure JMeter heap size for 1500 threads
- [ ] Verify Docker Compose services are running and healthy

## Success Criteria
- JMeter runs in CLI mode without errors
- 1000 test users can login successfully
- Seed data visible in movie-service APIs
- CSV data files correctly parameterize requests

## Risk Assessment
- **Risk**: Docker resource limits may throttle services before JMeter reaches 1000 users
  - **Mitigation**: Increase Docker memory/CPU limits in `docker-compose.yml`
- **Risk**: BCrypt hashing 1000 passwords in seed SQL is slow
  - **Mitigation**: Pre-compute BCrypt hashes, insert directly

## Security Considerations
- Test data uses dummy passwords only
- Never commit real credentials to seed files
- Load test results may contain tokens — gitignore `results/` directory

## Next Steps
- Phase 2: Auth Service Load Tests
