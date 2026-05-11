# Deployment Guide

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Updated:** April 10, 2026

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Docker Compose Deployment](#docker-compose-deployment)
4. [Kubernetes Minikube Deployment](#kubernetes-minikube-deployment)
5. [Production Deployment](#production-deployment)
6. [Configuration Management](#configuration-management)
7. [Database Setup](#database-setup)
8. [Monitoring & Logging](#monitoring--logging)
9. [Troubleshooting](./deployment-troubleshooting.md#troubleshooting)
10. [Rollback Procedures](./deployment-troubleshooting.md#rollback-procedures)

---

## Prerequisites

### System Requirements

**Development Machine:**
- OS: Linux, macOS, or Windows (with WSL2)
- Disk Space: 5GB minimum
- RAM: 8GB minimum
- Network: Internet access for Maven dependencies

**For Docker:**
- Docker 20.10+
- Docker Compose 1.29+
- Docker daemon running

**For Kubernetes (Phase 4):**
- kubectl 1.22+
- Helm 3.8+
- Kubernetes 1.22+ cluster access

### Software Dependencies

```bash
# Java 21 LTS (local development)
java -version
# Output: openjdk version "21" or higher

# Maven (local builds)
mvn -version
# Output: Apache Maven 3.8.0 or higher

# PostgreSQL Client (optional, for direct DB access)
psql --version
# Output: psql (PostgreSQL) 13.1 or higher

# Docker (containerized deployment)
docker --version
# Output: Docker version 20.10.0 or higher

docker-compose --version
# Output: Docker Compose version 1.29.0 or higher
```

### Network & Firewall

**Local Development:**
- Port 8080 available for Spring Boot app
- Port 5432 available for PostgreSQL
- No firewall blocking localhost

**Production:**
- Port 8080 (HTTP) exposed via load balancer (HTTPS/443 recommended)
- Port 5432 (PostgreSQL) NOT exposed to internet, only to app
- Inbound traffic to 8080 allowed from load balancer
- Outbound traffic for external services (email, OAuth2 providers)

**AWS Example:**
```
Internet → Load Balancer (443) → Security Group (8080)
           └─ auth-service
              └─ Security Group (5432) ← PostgreSQL RDS
```

---

## Local Development Setup

### 1. Clone Repository

```bash
git clone https://github.com/your-org/ms-cinema.git
cd ms-cinema
```

### 2. Configure Local Database

**Option A: Docker PostgreSQL (Recommended)**
```bash
docker run -d \
  --name jwt-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  postgres:13.1-alpine
```

**Option B: Local PostgreSQL Installation**
```bash
# macOS with Homebrew
brew install postgresql@13
brew services start postgresql@13

# Linux (Ubuntu)
sudo apt-get install postgresql-13
sudo service postgresql start

# Create database
createdb -U postgres testdb
```

### 3. Initialize Database Schema

```bash
# Method 1: Using Docker Compose (easiest)
docker-compose up postgres-service

# Wait for postgres to start (30 seconds)
# Then in another terminal:

docker exec jwt-postgres psql -U postgres -d testdb \
  -f /docker-entrypoint-initdb.d/roles.sql

# Method 2: Manual psql
psql -U postgres -d testdb -c "
INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_PM');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');
"
```

**Verify Schema:**
```bash
psql -U postgres -d testdb -c "\dt"
# Output: users, roles, user_roles tables

psql -U postgres -d testdb -c "SELECT * FROM roles;"
# Output: 3 rows (ROLE_USER, ROLE_PM, ROLE_ADMIN)
```

### 4. Build Application

```bash
# Clean build
mvn clean install

# Expected output:
# BUILD SUCCESS
# Target: target/auth-service.jar

# Verify compilation
mvn compile
# No errors should occur
```

### 5. Run Locally

**Option A: Maven Spring Boot Plugin**
```bash
mvn spring-boot:run
```

**Option B: Direct JAR Execution**
```bash
java -jar target/auth-service.jar
```

**Option C: IDE Execution**
- IntelliJ IDEA: Right-click CinemaAuthApplication.java → Run
- Eclipse: Run as → Spring Boot App

**Verify Running:**
```bash
curl http://localhost:8080/api/auth/register
# Expected: 400 Bad Request (no body provided)
# This means app is running and endpoint is accessible
```

### 6. Test Authentication Flow

1. Register user: POST /api/auth/register (no password field)
2. Activate with password: POST /api/auth/activate-with-password (token + password from email)
3. Login: POST /api/auth/login (returns JWT token)
4. Access protected endpoints: Include Authorization: Bearer {token} header

---

## Docker Compose Deployment

### 1. Build Docker Images

```bash
# Build all services and frontend
docker-compose build

# Or build specific service
docker-compose build auth-service
```

### 2. Start All Services

```bash
# Start in background
docker-compose up -d

# Verify services running
docker-compose ps

# View logs for a specific service
docker-compose logs -f auth-service

# View all logs
docker-compose logs -f
```

**Services Started:**
- PostgreSQL (6 databases: testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb)
- Kafka KRaft (single broker, no Zookeeper)
- Redis
- All 6 business services (auth, movie, booking, payment, notification, audit)
- Cinema-frontend (Angular 18 via Nginx on port 80)
- Monitoring: Prometheus, Grafana, Loki, Tempo, OTel Collector

### 3. Environment Variables for Docker

Create `.env` file in project root:
```env
# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=secure_password_here
POSTGRES_DB=testdb

# JWT Configuration
JWT_SECRET=your-secret-key-here
JWT_EXPIRATION=86400000

# Server
SERVER_PORT=8080

# Logging
LOG_LEVEL=DEBUG
```

**Update docker-compose.yml** to use env file:
```yaml
services:
  auth-service:
    build: ./
    ports:
      - "8080:8080"
    environment:
      - JWT_SECRET=${JWT_SECRET}
      - JWT_EXPIRATION=${JWT_EXPIRATION}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
```

### 4. Stop & Clean Up

```bash
docker-compose down     # Stop services
docker-compose down -v  # Stop + remove volumes (WARNING: deletes data)
```

---

## Kubernetes Minikube Deployment

### 1. Prerequisites

```bash
# Install kubectl
kubectl version --client

# Install Minikube (macOS)
brew install minikube

# Or OrbStack (alternative)
# https://orbstack.dev/

# Start Minikube cluster
minikube start --memory 4096 --cpus 2

# Verify cluster
kubectl cluster-info
kubectl get nodes
```

### 2. Build and Push Images to Minikube

```bash
# Option A: Use Minikube Docker (no registry needed for local testing)
eval $(minikube docker-env)
docker-compose build

# Option B: Build and push to registry (for production-like setup)
docker-compose build
docker tag auth-service:latest myregistry.azurecr.io/auth-service:latest
docker push myregistry.azurecr.io/auth-service:latest
# Update k8s/deployments/auth-service.yaml image reference
```

### 3. Deploy to Kubernetes

```bash
# Create namespaces (optional)
kubectl create namespace ms-cinema

# Apply all K8s manifests
kubectl apply -f k8s/

# Verify deployments
kubectl get deployments
kubectl get services
kubectl get pods

# Check logs
kubectl logs -f deployment/auth-service
```

### 4. Access Services

```bash
# Get Minikube IP
MINIKUBE_IP=$(minikube ip)
echo $MINIKUBE_IP

# Access Ingress endpoints via Minikube IP
# Frontend: http://$MINIKUBE_IP/
# Auth Swagger: http://$MINIKUBE_IP/api/auth/swagger-ui.html
# Movie Swagger: http://$MINIKUBE_IP/api/movies/swagger-ui.html

# Port forward for Kafka/Redis (optional)
kubectl port-forward svc/kafka 9092:9092
kubectl port-forward svc/redis 6379:6379

# Port forward for monitoring
kubectl port-forward svc/prometheus 9090:9090
kubectl port-forward svc/grafana 3000:3000
```

### 5. K8s Manifest Structure

```
k8s/
├── configmap-kafka.yml         # Kafka broker configuration
├── configmap-auth-service.yml  # Auth-service config
├── configmap-*.yml             # Per-service configs
├── secret-app.yml              # Stripe key, JWT secret, mail credentials (create manually)
├── deployment-*.yml            # Service deployments (6 services)
├── service-*.yml               # K8s ClusterIP services
├── statefulset-postgres.yml    # PostgreSQL StatefulSet (optional, use managed DB in production)
├── statefulset-kafka.yml       # Kafka StatefulSet
├── deployment-redis.yml        # Redis deployment
├── ingress.yml                 # NGINX Ingress with path-based routing
└── monitoring/                 # Prometheus, Grafana, Loki manifests (optional)
```

### 6. Create Required Secrets

```bash
# Create Secret for sensitive data
kubectl create secret generic app-secrets \
  --from-literal=jwt-secret="your-jwt-secret-key" \
  --from-literal=stripe-secret-key="sk_test_..." \
  --from-literal=stripe-webhook-secret="whsec_..." \
  --from-literal=mail-username="your-gmail@gmail.com" \
  --from-literal=mail-password="your-app-password"

# Verify secret created
kubectl get secrets
kubectl describe secret app-secrets
```

### 7. Monitor Deployment

```bash
# Watch rollout status
kubectl rollout status deployment/auth-service

# Check service endpoints
kubectl get svc

# Describe ingress
kubectl describe ingress ms-cinema-ingress

# Get logs from pod
kubectl logs <pod-name>

# SSH into pod for debugging
kubectl exec -it <pod-name> -- /bin/sh
```

### 8. Stop and Clean Up

```bash
# Delete all resources
kubectl delete -f k8s/

# Or delete specific namespace
kubectl delete namespace ms-cinema

# Stop Minikube
minikube stop

# Delete Minikube cluster (WARNING: deletes all data)
minikube delete
```

---

## Production Deployment

### 1. Pre-Deployment Checklist

- [ ] All tests passing (`mvn test`)
- [ ] Code review completed
- [ ] Security scan completed (OWASP, CVE check)
- [ ] Secrets stored in vault, not in code
- [ ] Database backups configured
- [ ] Monitoring/alerting configured
- [ ] Load balancer/reverse proxy configured
- [ ] HTTPS certificates valid
- [ ] Rollback plan tested

### 2. Secrets Management

- **Never commit secrets to git** — use environment variables: `jwtSecret: ${JWT_SECRET}`
- **Set at runtime:** `export JWT_SECRET="..."; java -jar app.jar`
- **Docker Secrets:** `docker secret create jwt_secret -` + `secrets:` section in compose
- **Kubernetes Secrets:** `kubectl create secret generic jwt-auth-secret --from-literal=jwt-secret="..."`

### 3. Deploying to VM/Server

```bash
# 1. Copy JAR to server
scp target/auth-service.jar user@prod-server:/opt/app/

# 2. Create systemd service (for auto-start)
# /etc/systemd/system/jwt-auth.service
[Unit]
Description=JWT Authentication Service
After=network.target

[Service]
User=appuser
WorkingDirectory=/opt/app
ExecStart=/usr/bin/java -jar auth-service.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target

# 3. Start service
sudo systemctl start jwt-auth
sudo systemctl enable jwt-auth  # Auto-start on reboot

# 4. Check status
sudo systemctl status jwt-auth
```

### 4. Deploying to AWS

**Option A: EC2 Instance**
```bash
# Launch EC2 instance (Ubuntu 20.04 LTS)
# Security group: allow 443 (HTTPS), 8080 (internal)

# SSH into instance
ssh -i key.pem ec2-user@instance-ip

# Install Java & Docker
sudo yum update -y
sudo yum install java-11-amazon-corretto -y
sudo yum install docker -y
sudo systemctl start docker

# Copy application
scp -i key.pem auth-service.jar ec2-user@instance-ip:/opt/app/

# Run with environment variables
java -jar /opt/app/auth-service.jar \
  -DJWT_SECRET=$JWT_SECRET \
  -Dspring.datasource.url=$DB_URL
```

**Option B: ECS Fargate**
```bash
# Create CloudFormation template or use AWS Console
# Container image: pushed to ECR

# Task Definition:
# - Image: 123456789.dkr.ecr.us-east-1.amazonaws.com/jwt-auth:latest
# - Port: 8080
# - Environment Variables: JWT_SECRET, DB_URL
# - Memory: 512 MB
# - CPU: 256

# Service:
# - Cluster: production
# - Launch Type: Fargate
# - Desired Count: 3 (auto-scale 2-5)
# - Load Balancer: ALB on port 443 → 8080
```

**Option C: RDS for Database**
```bash
# Create RDS PostgreSQL instance
# - Engine: PostgreSQL 13.1
# - Allocated storage: 100GB
# - Multi-AZ: Yes
# - Backup retention: 30 days
# - VPC security group: Allow EC2 security group on 5432

# Connection string:
# jdbc:postgresql://prod-db.c123.us-east-1.rds.amazonaws.com:5432/authdb
```

### 5. Load Balancer Configuration

**Nginx Reverse Proxy**
```nginx
upstream jwt_auth {
    server localhost:8080;
}

server {
    listen 443 ssl http2;
    server_name auth.example.com;

    ssl_certificate /etc/ssl/certs/server.crt;
    ssl_certificate_key /etc/ssl/private/server.key;

    location / {
        proxy_pass http://jwt_auth;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Health check endpoint
    location /actuator/health {
        proxy_pass http://jwt_auth;
    }
}
```

**AWS ALB Configuration**
```
Protocol: HTTPS (port 443)
Certificate: ACM certificate
Target Group:
  - Protocol: HTTP (port 8080)
  - Health Check: /actuator/health
  - Healthy threshold: 2
  - Unhealthy threshold: 3
  - Interval: 30 seconds
  - Timeout: 5 seconds
```

---

## Configuration Management

### Environment-Specific Configs

```
src/main/resources/
├── application.yml              (shared config)
├── application-dev.yml          (development)
├── application-staging.yml      (staging)
└── application-prod.yml         (production)
```

**application.yml (default, development)**
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: 123456  # OK for dev only

namnd:
  app:
    jwtSecret: bezKoderSecretKey  # OK for dev only
    jwtExpiration: 86400000
```

**application-prod.yml (production)**
```yaml
server:
  port: 8080
  compression:
    enabled: true
    min-response-size: 1024

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

namnd:
  app:
    jwtSecret: ${JWT_SECRET}  # Must be set via env var
    jwtExpiration: ${JWT_EXPIRATION:86400000}
```

**Activate Profile**
```bash
# Via command line
java -jar auth-service.jar --spring.profiles.active=prod

# Via environment variable
export SPRING_PROFILES_ACTIVE=prod
java -jar auth-service.jar

# Via Docker
docker run -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=xxxx \
  auth-service:latest
```

### Configuration Parameters

| Parameter | Default | Prod Override | Notes |
|-----------|---------|---------------|-------|
| server.port | 8080 | 8080 | Behind load balancer, internal only |
| jwt.secret | bezKoderSecretKey | ${JWT_SECRET} | Min 32 chars recommended |
| jwt.expiration | 86400000 (24h) | 86400000 | In milliseconds |
| db.url | localhost:5432/testdb | ${DB_URL} | Production: RDS/managed DB |
| db.username | postgres | ${DB_USER} | Use env var |
| db.password | 123456 | ${DB_PASSWORD} | Use vault/secrets manager |
| logging.level | debug | info | Reduce verbosity in prod |

### Spring Batch Configuration (payment-service)

Payment-service uses Spring Batch for daily payment reconciliation with Stripe. Configuration must be set before startup.

**application.yml (payment-service)**
```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always  # Auto-creates Spring Batch metadata tables on startup
    job:
      enabled: false              # Disable auto-run on startup; use @Scheduled instead

# Reconciliation configuration
reconciliation:
  cron: "0 2 * * *"              # Daily at 2 AM (Asia/Saigon timezone)
  auto-run: true                 # Enable scheduled reconciliation
  max-date-range-days: 31        # Max 31 days per reconciliation run

# Stripe configuration
stripe:
  api:
    key: ${STRIPE_API_KEY:}      # MUST be set via environment variable
```

**Environment Variables (Required for payment-service)**
```bash
# Stripe API key (test or live)
export STRIPE_API_KEY=sk_test_xxxxx  # or sk_live_xxxxx in production

# Database (paymentdb)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paymentdb
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=xxxxx

# JVM timezone (ensures cron uses correct timezone)
export TZ=Asia/Saigon  # or use JVM option: -Duser.timezone=Asia/Saigon
```

**Docker Compose Configuration**
```yaml
payment-service:
  environment:
    STRIPE_API_KEY: ${STRIPE_API_KEY}
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/paymentdb
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: postgres
    TZ: Asia/Saigon  # Timezone for scheduled jobs
```

**Batch Database Initialization**

Spring Batch auto-creates metadata tables on startup with `spring.batch.jdbc.initialize-schema=always`:
- BATCH_JOB_INSTANCE
- BATCH_JOB_EXECUTION
- BATCH_JOB_EXECUTION_PARAMS
- BATCH_JOB_EXECUTION_CONTEXT
- BATCH_STEP_EXECUTION
- BATCH_STEP_EXECUTION_CONTEXT
- BATCH_JOB_SEQ, BATCH_JOB_INSTANCE_SEQ, BATCH_STEP_EXECUTION_SEQ

No manual SQL setup required; tables auto-created on first run.

**Monitoring Batch Jobs**

Check batch execution status:
```sql
-- Connect to paymentdb
\c paymentdb

-- View job instances
SELECT * FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC LIMIT 10;

-- View job executions (with status)
SELECT * FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 10;

-- View reconciliation runs
SELECT * FROM reconciliation_runs ORDER BY created_at DESC LIMIT 5;
```

---

## Database Setup

### 1. Schema Initialization

**PostgreSQL Manual Setup**
```sql
-- Connect as postgres user
psql -U postgres

-- Create database
CREATE DATABASE authdb;

-- Connect to new database
\c authdb;

-- Create users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create roles table
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create junction table
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Insert default roles
INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_PM');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');

-- Create indices
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_roles_name ON roles(name);
```

### 2. Backup & Recovery

**PostgreSQL Backup**
```bash
# Full backup (custom format)
pg_dump -U postgres -F c authdb > authdb.dump

# Restore
pg_restore -U postgres -d authdb authdb.dump

# Text backup (smaller, human-readable)
pg_dump -U postgres authdb > authdb.sql

# Restore
psql -U postgres authdb < authdb.sql

# Backup schedule (daily)
0 2 * * * pg_dump -U postgres -F c authdb > /backups/authdb-$(date +\%Y\%m\%d).dump
```

**AWS RDS Backup**
```
AWS Console → RDS → Databases → Select instance
→ Maintenance & Backups tab
→ Backup retention: 30 days (minimum)
→ Auto minor version upgrade: Yes
→ Backup window: 02:00 UTC (off-peak)
```

### 3. Connection Pooling (HikariCP)

Default configuration (HikariCP via Spring Boot):
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # Connections in pool
      minimum-idle: 5            # Keep idle connections
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes
      connection-timeout: 30000  # 30 seconds
```

---

## Monitoring & Logging

### 1. Distributed Tracing (OpenTelemetry + Tempo)

**Pipeline:** Spring Boot → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP → OTel Collector → Grafana Tempo.

**Access in Grafana:**
```
http://localhost:3000   →   Explore   →   Tempo datasource
```

Direct Tempo API: `http://localhost:3200`

**Ports:**
- `4318` — OTLP/HTTP receiver (apps export here)
- `4317` — OTLP/gRPC receiver
- `13133` — OTel Collector health check
- `3200` — Tempo HTTP query API

**Features:**
- Visualize request traces across all microservices in Grafana Explore → Tempo
- Service Graph (node graph) renders inter-service edges automatically
- `tracesToLogsV2`: click a span → opens Loki with `{service="…"} |= "<traceId>"`
- `tracesToMetrics`: click a span → opens Prometheus latency/rate panels
- Cross-service Kafka traces propagated via W3C `traceparent` headers (auto)

**Configuration:**
```yaml
# Each service application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (change via TRACING_SAMPLING_PROBABILITY env var)
  otlp:
    tracing:
      endpoint: http://${OTEL_COLLECTOR_HOST:localhost}:4318/v1/traces
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      deployment.environment: ${DEPLOYMENT_ENV:dev}
```

**Production Tuning:**
- Reduce sampling to 10-20% for high-traffic systems: `TRACING_SAMPLING_PROBABILITY=0.1`
- Traces auto-include service-to-service (Feign), Kafka producers/consumers, database operations
- traceId/spanId auto-injected into logs via MDC for cross-log correlation
- Tempo retention: 24h (compactor) — adjust `block_retention` in `monitoring/tempo/tempo.yaml`

### 2. Structured Logging

**Log Output Example**
```json
{
  "timestamp": "2026-02-10T15:30:45.123Z",
  "level": "INFO",
  "thread": "http-nio-8080-exec-1",
  "logger": "com.namnd.cinema.controller.AuthController",
  "message": "User login successful",
  "user_id": 123,
  "username": "john",
  "ip_address": "192.168.1.100",
  "duration_ms": 145,
  "traceId": "a1b2c3d4e5f6",
  "spanId": "x7y8z9a0"
}
```

Note: traceId and spanId are auto-injected via MDC (Micrometer Tracing) and visible in JSON logs and Loki queries.

**Enable JSON Logging**
```xml
<!-- Add to pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>6.6</version>
</dependency>
```

### 2. Log Aggregation (ELK Stack)

**Logstash Configuration (optional, Phase 3)**
```conf
input {
  tcp {
    port => 5000
    codec => json
  }
}

filter {
  if [logger] =~ /com.namnd.cinema/ {
    # Parse custom fields
    mutate {
      add_field => { "service" => "jwt-auth" }
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "jwt-auth-%{+YYYY.MM.dd}"
  }
}
```

### 3. Metrics & Health Checks

**Health Check Endpoint (Future)**
```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "livenessState": {"status": "UP"},
    "readinessState": {"status": "UP"}
  }
}
```

**Basic Monitoring Checks (Recommended)**
```bash
# Login endpoint response time
time curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# Token validation response time
time curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/protected

# Database connectivity
curl http://localhost:8080/actuator/health/db
```

See [Deployment Troubleshooting](./deployment-troubleshooting.md) for troubleshooting, rollback procedures, checklists, and post-deployment validation.
