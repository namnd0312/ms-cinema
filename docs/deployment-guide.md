# Deployment Guide

**Project:** ms-cinema
**Version:** 0.0.1-SNAPSHOT
**Updated:** February 2026

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Local Development Setup](#local-development-setup)
3. [Docker Deployment](#docker-deployment)
4. [Production Deployment](#production-deployment)
5. [Configuration Management](#configuration-management)
6. [Database Setup](#database-setup)
7. [Monitoring & Logging](#monitoring--logging)
8. [Troubleshooting](./deployment-troubleshooting.md#troubleshooting)
9. [Rollback Procedures](./deployment-troubleshooting.md#rollback-procedures)

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

```bash
# 1. Register user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@1234",
    "fullName": "Test User",
    "roles": [{"name": "ROLE_USER"}]
  }'
# Expected: 200 OK "User registered successfully!"

# 2. Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@1234"
  }'
# Expected: 200 OK with JWT token in response

# 3. Access protected endpoint with token
TOKEN="eyJhbGc..." # From login response
curl -X GET http://localhost:8080/api/protected \
  -H "Authorization: Bearer $TOKEN"
# Expected: 200 OK or endpoint-specific response
```

---

## Docker Deployment

### 1. Build Docker Image

```bash
# Build locally
docker build -t auth-service:latest .

# Or with version tag
docker build -t auth-service:0.0.1-SNAPSHOT .

# Verify image
docker images | grep auth-service
```

### 2. Run with Docker Compose (Recommended)

```bash
# Start all services (postgres, redis, kafka, eureka, config-server, zipkin, kafdrop, all 8 services)
docker-compose up -d

# Verify services running
docker-compose ps
# Output: All services (auth-service, notification-service, postgres, redis, kafka, zipkin, kafdrop, etc.) should show UP

# View logs
docker-compose logs -f auth-service
docker-compose logs -f notification-service

# Access monitoring UIs
# Zipkin (distributed tracing): http://localhost:9411/zipkin
# Kafdrop (Kafka topics): http://localhost:9000

# Test app
curl http://localhost:8080/api/auth/register
```

**Service Dependencies:**
- auth-service: postgres, redis (token blacklist), kafka, eureka, config-server
- notification-service: kafka, redis (event dedup), eureka, config-server
- grafana: depends_on zipkin (for tracing datasource provisioning)

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
# Stop all services
docker-compose down

# Remove volumes (WARNING: deletes data)
docker-compose down -v

# Remove images
docker rmi auth-service:latest
```

### 5. Docker Networking

```bash
# Verify network
docker network ls | grep my-net

# Inspect network
docker network inspect ms-cinema_my-net

# Services can communicate: postgres-service:5432 from auth-service
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

**DO NOT commit secrets to git:**
```bash
# WRONG - Never do this
# application.yml
namnd:
  app:
    jwtSecret: "my-production-secret"

# RIGHT - Use environment variables
namnd:
  app:
    jwtSecret: ${JWT_SECRET}  # Injected at runtime
```

**Secrets via Environment Variables:**
```bash
# Set before running
export JWT_SECRET="production-secret-key-min-32-chars"
export SPRING_DATASOURCE_PASSWORD="db-password"
export SPRING_DATASOURCE_URL="jdbc:postgresql://prod-db.internal:5432/authdb"

java -jar auth-service.jar
```

**Secrets via Docker Secrets (Swarm):**
```bash
# Create secret
echo "production-secret-key" | docker secret create jwt_secret -

# Use in compose
secrets:
  jwt_secret:
    external: true

services:
  app:
    environment:
      - JWT_SECRET=/run/secrets/jwt_secret
```

**Secrets via Kubernetes Secrets:**
```bash
# Create secret
kubectl create secret generic jwt-auth-secret \
  --from-literal=jwt-secret="production-secret-key"

# Reference in deployment
env:
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-auth-secret
        key: jwt-secret
```

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

### 1. Distributed Tracing (Zipkin)

**Access Zipkin UI:**
```
http://localhost:9411/zipkin
```

**Features:**
- Visualize request traces across all microservices
- Track end-to-end request latency
- Identify bottlenecks in service-to-service calls
- View traceId correlation in logs (Loki) and traces (Zipkin)

**Configuration:**
```yaml
# Centralized in config-server (application.yml)
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (change via TRACING_SAMPLING_PROBABILITY env var)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans

# Fallback: All 6 business services have local application.yml copies as startup safeguard
# (when config-server is unavailable during boot)
```

**Production Tuning:**
- Reduce sampling to 10-20% for high-traffic systems: `TRACING_SAMPLING_PROBABILITY=0.1`
- Traces auto-include service-to-service (Feign), Kafka, database operations
- traceId/spanId auto-injected into logs via MDC for cross-log correlation

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
