# Phase 2: Auth Service Load Tests

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: [Phase 1](./phase-01-environment-setup-jmeter-config.md)
- API docs: [api-documentation](../../docs/api-documentation.md)

## Overview
- **Priority**: P1
- **Status**: pending
- **Description**: Load test auth endpoints — login, register, token refresh, logout. Auth is the gateway to all other tests since JWT is required.

## Key Insights
- Login uses BCrypt verification — CPU-intensive, likely first bottleneck
- Account lockout after 5 failed attempts — must use correct credentials
- Token blacklist uses Redis — Redis performance critical for logout
- Refresh token rotation — each refresh invalidates old token
- 15-min access token TTL, 7-day refresh token

## Requirements

### Functional
- Test login with 1000 concurrent users
- Test registration with unique emails (avoid duplicate errors)
- Test token refresh under load
- Test logout (Redis blacklist write throughput)

### Non-Functional
- Login p95 < 2s under 1000 users
- Error rate < 1% under sustained load
- No account lockouts from test traffic

## Architecture
```
JMeter Thread Group (1000 threads)
├── Login Request → POST /api/auth/login
│   └── Extract JWT token + refresh token
├── Get Profile → GET /api/users/me (with JWT)
├── Refresh Token → POST /api/auth/refresh-token
│   └── Extract new JWT token
└── Logout → POST /api/auth/logout
```

## Related Code Files
- `auth-service/src/main/java/com/namnd/cinema/controller/AuthController.java`
- `auth-service/src/main/java/com/namnd/cinema/service/JwtService.java`
- `auth-service/src/main/java/com/namnd/cinema/service/impl/BlacklistedTokenServiceImpl.java`
- `auth-service/src/main/java/com/namnd/cinema/service/impl/AccountLockServiceImpl.java`

## Implementation Steps

### Test Plan: `auth-load-test.jmx`

1. **Thread Group Configuration**
   - Threads: 1000
   - Ramp-up: 300s (5 min)
   - Loop count: 3 (each user does 3 full cycles)
   - Duration: 900s (15 min total)

2. **CSV Data Set Config**
   - File: `data/users.csv`
   - Variables: `email,password`
   - Sharing mode: All threads

3. **HTTP Requests**

   **a) Login (POST /api/auth/login)**
   ```json
   {
     "email": "${email}",
     "password": "${password}"
   }
   ```
   - JSON Extractor: `$.token` → `JWT_TOKEN`
   - JSON Extractor: `$.refreshToken` → `REFRESH_TOKEN`
   - Response Assertion: HTTP 200

   **b) Get Profile (GET /api/users/me)**
   - Header: `Authorization: Bearer ${JWT_TOKEN}`
   - Response Assertion: HTTP 200, body contains `email`

   **c) Refresh Token (POST /api/auth/refresh-token)**
   ```json
   {
     "refreshToken": "${REFRESH_TOKEN}"
   }
   ```
   - JSON Extractor: `$.token` → `JWT_TOKEN` (updated)
   - Response Assertion: HTTP 200

   **d) Logout (POST /api/auth/logout)**
   - Header: `Authorization: Bearer ${JWT_TOKEN}`
   - Response Assertion: HTTP 200

4. **Listeners**
   - Summary Report
   - Aggregate Report
   - Response Times Over Time
   - Active Threads Over Time

5. **Separate Registration Test**
   - Thread Group: 200 threads, ramp-up 60s
   - Unique email per thread: `loadtest_${__threadNum}_${__time(,)}@test.com`
   - POST /api/auth/register with unique email
   - Verify 200/201 response

## Todo
- [ ] Create `auth-load-test.jmx` test plan
- [ ] Configure CSV Data Set for user credentials
- [ ] Add JSON Extractors for token extraction
- [ ] Add HTTP Header Manager with Bearer token
- [ ] Create separate registration stress test
- [ ] Add assertions and listeners
- [ ] Test with 10 users first, then scale to 1000

## Success Criteria
- 1000 concurrent logins complete with <1% error rate
- Login p95 response time < 2 seconds
- Token refresh works correctly under load (no stale token errors)
- Logout properly blacklists tokens in Redis
- No account lockouts during test execution

## Risk Assessment
- **Risk**: BCrypt CPU bottleneck on login — auth-service may max CPU at ~500 users
  - **Mitigation**: Monitor CPU, consider scaling auth-service replicas
- **Risk**: Redis connection pool exhaustion on mass logout
  - **Mitigation**: Check Redis max connections config, monitor pool usage
- **Risk**: Refresh token rotation race conditions under load
  - **Mitigation**: Add think time between login and refresh (Gaussian Timer 1-3s)

## Security Considerations
- Test user passwords are weak (intentional for load testing)
- Token values logged in JMeter results — keep results local

## Next Steps
- Phase 3: Movie Service Load Tests
