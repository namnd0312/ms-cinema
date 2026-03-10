# Phase 4: Testing & Verification

## Context Links
- [Parent Plan](./plan.md)
- [Phase 1](./phase-01-micrometer-actuator-setup.md) | [Phase 2](./phase-02-prometheus-infrastructure.md) | [Phase 3](./phase-03-grafana-dashboards.md)

## Overview
- **Priority:** P2
- **Status:** Pending
- **Description:** Verify entire monitoring stack works end-to-end: services expose metrics → Prometheus scrapes → Grafana displays.

## Implementation Steps

### Step 1: Build all services
```bash
mvn clean package -DskipTests
```

### Step 2: Start the stack
```bash
docker compose up --build -d
```

### Step 3: Verify actuator endpoints

For each service, confirm `/actuator/prometheus` returns metrics:
```bash
# Auth service
curl -s http://localhost:8081/actuator/prometheus | head -20

# API Gateway
curl -s http://localhost:8080/actuator/prometheus | head -20

# Movie service
curl -s http://localhost:8082/actuator/prometheus | head -20

# Booking service
curl -s http://localhost:8083/actuator/prometheus | head -20

# Payment service
curl -s http://localhost:8084/actuator/prometheus | head -20

# Eureka
curl -s http://localhost:8761/actuator/prometheus | head -20

# Config server
curl -s http://localhost:8888/actuator/prometheus | head -20
```

Each should return Prometheus text format with metrics like `jvm_memory_used_bytes`, `http_server_requests_seconds_count`, etc.

### Step 4: Verify Prometheus targets

Open `http://localhost:9090/targets` — all 7 services should show State: UP.

PromQL smoke tests:
```
up                                    # All targets = 1
jvm_memory_used_bytes                 # JVM memory from all services
http_server_requests_seconds_count    # HTTP request counters
hikaricp_connections_active           # DB pool (services with JPA)
```

### Step 5: Verify Grafana dashboards

1. Open `http://localhost:3000` (admin/admin)
2. Navigate to "Microservices" folder
3. Open JVM Micrometer dashboard → select service from `$application` dropdown
4. Open HTTP Overview dashboard → verify panels show data

### Step 6: Generate test traffic
```bash
# Hit auth endpoints to generate metrics
curl -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"test@test.com","password":"test"}'

# Hit movie endpoints
curl http://localhost:8080/api/movies
```

## Todo List
- [ ] Build all services with `mvn clean package`
- [ ] Start Docker Compose stack
- [ ] Verify `/actuator/prometheus` on all 7 services
- [ ] Verify Prometheus targets all UP
- [ ] Verify Grafana dashboards load with data
- [ ] Generate test traffic and confirm metrics update

## Success Criteria
- All 7 services return Prometheus metrics at `/actuator/prometheus`
- Prometheus shows 7/7 targets UP at `:9090/targets`
- Grafana JVM dashboard shows memory/GC/threads per service
- Grafana HTTP dashboard shows request rates after test traffic
- No existing functionality broken (auth, booking, payment flows still work)

## Risk Assessment
- **Services slow to start:** Docker Compose services may take 30-60s to register with Eureka; Prometheus targets show DOWN initially — this is expected, they auto-recover
- **Port conflicts:** 9090 (Prometheus) and 3000 (Grafana) must be free on host
