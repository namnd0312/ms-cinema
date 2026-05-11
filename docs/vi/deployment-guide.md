# Hướng Dẫn Triển Khai

**Dự án:** ms-cinema
**Phiên bản:** 0.0.1-SNAPSHOT
**Cập nhật:** 10 tháng 4 năm 2026

## Mục Lục

1. [Yêu cầu tiên quyết](#yêu-cầu-tiên-quyết)
2. [Thiết lập phát triển cục bộ](#thiết-lập-phát-triển-cục-bộ)
3. [Triển khai Docker Compose](#triển-khai-docker-compose)
4. [Triển khai Kubernetes Minikube](#triển-khai-kubernetes-minikube)
5. [Triển khai production](#triển-khai-production)
6. [Quản lý cấu hình](#quản-lý-cấu-hình)
7. [Thiết lập cơ sở dữ liệu](#thiết-lập-cơ-sở-dữ-liệu)
8. [Giám sát & Ghi log](#giám-sát--ghi-log)
9. [Xử lý sự cố](./deployment-troubleshooting.md#troubleshooting)
10. [Quy trình khôi phục](./deployment-troubleshooting.md#rollback-procedures)

---

## Yêu Cầu Tiên Quyết

### Yêu Cầu Hệ Thống

**Máy phát triển:**
- Hệ điều hành: Linux, macOS, hoặc Windows (với WSL2)
- Dung lượng ổ đĩa: Tối thiểu 5GB
- RAM: Tối thiểu 8GB
- Mạng: Truy cập Internet để tải Maven dependencies

**Cho Docker:**
- Docker 20.10+
- Docker Compose 1.29+
- Docker daemon đang chạy

**Cho Kubernetes (Giai đoạn 4):**
- kubectl 1.22+
- Helm 3.8+
- Quyền truy cập cụm Kubernetes 1.22+

### Phần Mềm Phụ Thuộc

```bash
# Java 21 LTS (phát triển cục bộ)
java -version
# Output: openjdk version "21" or higher

# Maven (build cục bộ)
mvn -version
# Output: Apache Maven 3.8.0 or higher

# PostgreSQL Client (tùy chọn, để truy cập DB trực tiếp)
psql --version
# Output: psql (PostgreSQL) 13.1 or higher

# Docker (triển khai container hóa)
docker --version
# Output: Docker version 20.10.0 or higher

docker-compose --version
# Output: Docker Compose version 1.29.0 or higher

# Docker Compose phải hỗ trợ services định dạng 3.8+ cho PostgreSQL, Kafka, Tempo, OTel Collector, audit-service
```

### Mạng & Tường Lửa

**Phát triển cục bộ:**
- Cổng 8080 khả dụng cho ứng dụng Spring Boot
- Cổng 5432 khả dụng cho PostgreSQL
- Không có tường lửa chặn localhost

**Production:**
- Cổng 8080 (HTTP) được phơi qua load balancer (khuyến nghị HTTPS/443)
- Cổng 5432 (PostgreSQL) KHÔNG được phơi ra internet, chỉ cho ứng dụng
- Cho phép lưu lượng đến cổng 8080 từ load balancer
- Lưu lượng đi cho các dịch vụ bên ngoài (email, nhà cung cấp OAuth2)

**Ví dụ AWS:**
```
Internet → Load Balancer (443) → Security Group (8080)
           └─ auth-service
              └─ Security Group (5432) ← PostgreSQL RDS
```

---

## Thiết Lập Phát Triển Cục Bộ

### 1. Clone Repository

```bash
git clone https://github.com/your-org/ms-cinema.git
cd ms-cinema
```

### 2. Cấu Hình Cơ Sở Dữ Liệu Cục Bộ

**Phương án A: Docker PostgreSQL (Khuyến nghị)**
```bash
docker run -d \
  --name jwt-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=123456 \
  -e POSTGRES_DB=testdb \
  -p 5432:5432 \
  postgres:13.1-alpine
```

**Phương án B: Cài đặt PostgreSQL cục bộ**
```bash
# macOS với Homebrew
brew install postgresql@13
brew services start postgresql@13

# Linux (Ubuntu)
sudo apt-get install postgresql-13
sudo service postgresql start

# Tạo cơ sở dữ liệu
createdb -U postgres testdb
```

### 3. Khởi Tạo Schema Cơ Sở Dữ Liệu

```bash
# Phương pháp 1: Dùng Docker Compose (tự động)
docker-compose up -d postgres

# Xác minh tất cả databases được tạo
docker exec postgres psql -U postgres -c "\l"

# Xác minh schema
docker exec postgres psql -U postgres -d testdb -c "\dt"
```

### 4. Build Ứng Dụng

```bash
# Build sạch
mvn clean install

# Output mong đợi:
# BUILD SUCCESS
# Target: target/auth-service.jar

# Xác minh compilation
mvn compile
# Không có lỗi nào xảy ra
```

### 5. Chạy Cục Bộ

**Phương án A: Maven Spring Boot Plugin**
```bash
mvn spring-boot:run
```

**Phương án B: Chạy trực tiếp JAR**
```bash
java -jar target/auth-service.jar
```

**Phương án C: Chạy từ IDE**
- IntelliJ IDEA: Nhấp chuột phải CinemaAuthApplication.java → Run
- Eclipse: Run as → Spring Boot App

**Xác minh đang chạy:**
```bash
curl http://localhost:8080/api/auth/register
# Mong đợi: 400 Bad Request (không có body)
# Điều này có nghĩa ứng dụng đang chạy và endpoint có thể truy cập
```

### 6. Test Authentication Flow

1. Register user: POST /api/auth/register
2. Login: POST /api/auth/login (returns JWT token)
3. Access protected endpoints: Include Authorization: Bearer {token} header

---

## Triển Khai Docker

### 1. Build Docker Image

```bash
docker build -t auth-service:latest .
docker images | grep auth-service  # verify
```

### 2. Chạy với Docker Compose (Khuyến nghị)

```bash
docker-compose up -d  # Start all services
docker-compose ps     # Verify running
docker-compose logs -f auth-service  # View logs
```

**Service Dependencies:** auth-service (postgres, redis, kafka, eureka, config-server), notification-service (kafka, redis, eureka, config-server)

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
docker rmi auth-service:latest  # Remove images
```

---

## Triển Khai Production

### 1. Danh Sách Kiểm Tra Trước Triển Khai

- [ ] Tất cả test đã pass (`mvn test`)
- [ ] Code review đã hoàn thành
- [ ] Quét bảo mật đã hoàn thành (OWASP, kiểm tra CVE)
- [ ] Secrets được lưu trong vault, không trong code
- [ ] Sao lưu cơ sở dữ liệu đã được cấu hình
- [ ] Giám sát/cảnh báo đã được cấu hình
- [ ] Load balancer/reverse proxy đã được cấu hình
- [ ] Chứng chỉ HTTPS hợp lệ
- [ ] Kế hoạch khôi phục đã được kiểm tra

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
# - Memory: 512 MB, CPU: 256

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

## Quản Lý Cấu Hình

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

### Cấu Hình Spring Batch (payment-service)

Payment-service sử dụng Spring Batch cho đối soát thanh toán hàng ngày với Stripe. Cấu hình phải được đặt trước khi khởi động.

**application.yml (payment-service)**
```yaml
spring:
  batch:
    jdbc:
      initialize-schema: always  # Tự động tạo bảng metadata Spring Batch khi khởi động
    job:
      enabled: false              # Vô hiệu hóa auto-run khi khởi động; dùng @Scheduled thay thế

# Cấu hình đối soát
reconciliation:
  cron: "0 2 * * *"              # Hàng ngày lúc 2 AM (múi giờ Asia/Saigon)
  auto-run: true                 # Bật đối soát theo lịch trình
  max-date-range-days: 31        # Tối đa 31 ngày cho mỗi lần đối soát

# Cấu hình Stripe
stripe:
  api:
    key: ${STRIPE_API_KEY:}      # PHẢI được đặt qua biến môi trường
```

**Biến Môi Trường (Bắt buộc cho payment-service)**
```bash
# Stripe API key (test hoặc live)
export STRIPE_API_KEY=sk_test_xxxxx  # hoặc sk_live_xxxxx trong production

# Cơ sở dữ liệu (paymentdb)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paymentdb
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=xxxxx

# Múi giờ JVM (đảm bảo cron dùng múi giờ đúng)
export TZ=Asia/Saigon  # hoặc dùng tùy chọn JVM: -Duser.timezone=Asia/Saigon
```

**Cấu Hình Docker Compose**
```yaml
payment-service:
  environment:
    STRIPE_API_KEY: ${STRIPE_API_KEY}
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/paymentdb
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: postgres
    TZ: Asia/Saigon  # Múi giờ cho tác vụ theo lịch trình
```

**Khởi Tạo Cơ Sở Dữ Liệu Batch**

Spring Batch tự động tạo bảng metadata khi khởi động với `spring.batch.jdbc.initialize-schema=always`:
- BATCH_JOB_INSTANCE
- BATCH_JOB_EXECUTION
- BATCH_JOB_EXECUTION_PARAMS
- BATCH_JOB_EXECUTION_CONTEXT
- BATCH_STEP_EXECUTION
- BATCH_STEP_EXECUTION_CONTEXT
- BATCH_JOB_SEQ, BATCH_JOB_INSTANCE_SEQ, BATCH_STEP_EXECUTION_SEQ

Không cần thiết lập SQL thủ công; bảng tự động tạo khi chạy lần đầu.

**Giám Sát Tác Vụ Batch**

Kiểm tra trạng thái thực thi batch:
```sql
-- Kết nối đến paymentdb
\c paymentdb

-- Xem job instances
SELECT * FROM BATCH_JOB_INSTANCE ORDER BY JOB_INSTANCE_ID DESC LIMIT 10;

-- Xem job executions (với trạng thái)
SELECT * FROM BATCH_JOB_EXECUTION ORDER BY JOB_EXECUTION_ID DESC LIMIT 10;

-- Xem các lần đối soát
SELECT * FROM reconciliation_runs ORDER BY created_at DESC LIMIT 5;
```

---

## Thiết Lập Cơ Sở Dữ Liệu

### 1. Khởi Tạo Schema

**Thiết lập thủ công PostgreSQL**
```sql
-- Kết nối với tư cách postgres user
psql -U postgres

-- Tạo cơ sở dữ liệu
CREATE DATABASE authdb;

-- Kết nối đến cơ sở dữ liệu mới
\c authdb;

-- Tạo bảng users
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tạo bảng roles
CREATE TABLE roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tạo bảng liên kết
CREATE TABLE user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Chèn các role mặc định
INSERT INTO roles (name) VALUES ('ROLE_USER');
INSERT INTO roles (name) VALUES ('ROLE_PM');
INSERT INTO roles (name) VALUES ('ROLE_ADMIN');

-- Tạo chỉ mục
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_roles_name ON roles(name);
```

### 2. Sao Lưu & Phục Hồi

**Sao lưu PostgreSQL**
```bash
# Sao lưu đầy đủ (định dạng custom)
pg_dump -U postgres -F c authdb > authdb.dump

# Phục hồi
pg_restore -U postgres -d authdb authdb.dump

# Sao lưu text (nhỏ hơn, đọc được)
pg_dump -U postgres authdb > authdb.sql

# Phục hồi
psql -U postgres authdb < authdb.sql

# Lịch sao lưu (hàng ngày)
0 2 * * * pg_dump -U postgres -F c authdb > /backups/authdb-$(date +\%Y\%m\%d).dump
```

**Sao lưu AWS RDS**
```
AWS Console → RDS → Databases → Chọn instance
→ Tab Maintenance & Backups
→ Thời gian giữ backup: 30 ngày (tối thiểu)
→ Tự động nâng cấp phiên bản nhỏ: Có
→ Cửa sổ sao lưu: 02:00 UTC (ngoài giờ cao điểm)
```

### 3. Connection Pooling (HikariCP)

Cấu hình mặc định (HikariCP qua Spring Boot):
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # Số kết nối trong pool
      minimum-idle: 5            # Giữ các kết nối nhàn rỗi
      idle-timeout: 600000       # 10 phút
      max-lifetime: 1800000      # 30 phút
      connection-timeout: 30000  # 30 giây
```

---

## Giám Sát & Ghi Log

### 1. Distributed Tracing (OpenTelemetry + Tempo)

**Pipeline:** Spring Boot → Micrometer Tracing → OpenTelemetry SDK → OTLP/HTTP → OTel Collector → Grafana Tempo.

**Truy cập trong Grafana:**
```
http://localhost:3000   →   Explore   →   Datasource Tempo
```

API trực tiếp Tempo: `http://localhost:3200`

**Cổng:**
- `4318` — Bộ thu OTLP/HTTP (apps xuất tới đây)
- `4317` — Bộ thu OTLP/gRPC
- `13133` — Health check OTel Collector
- `3200` — Tempo HTTP query API

**Tính năng:**
- Trực quan hóa request traces trên tất cả microservices trong Grafana Explore → Tempo
- Service Graph (đồ thị node) tự động hiển thị các kết nối liên dịch vụ
- `tracesToLogsV2`: click span → mở Loki với `{service="…"} |= "<traceId>"`
- `tracesToMetrics`: click span → mở panel độ trễ/tỷ lệ Prometheus
- Trace Kafka liên dịch vụ được lan truyền qua header W3C `traceparent` (tự động)

**Cấu hình:**
```yaml
# Mỗi service application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (thay đổi qua biến TRACING_SAMPLING_PROBABILITY)
  otlp:
    tracing:
      endpoint: http://${OTEL_COLLECTOR_HOST:localhost}:4318/v1/traces
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      deployment.environment: ${DEPLOYMENT_ENV:dev}
```

**Tinh chỉnh Production:**
- Giảm sampling xuống 10-20% cho hệ thống lưu lượng cao: `TRACING_SAMPLING_PROBABILITY=0.1`
- Traces tự động bao gồm service-to-service (Feign), Kafka producer/consumer, các thao tác cơ sở dữ liệu
- traceId/spanId tự động được inject vào log qua MDC cho tương quan log chéo
- Tempo retention: 24h (compactor) — chỉnh `block_retention` trong `monitoring/tempo/tempo.yaml`

### 2. Ghi Log Có Cấu Trúc

**Ví dụ đầu ra Log**
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

Lưu ý: traceId và spanId được tự động inject qua MDC (Micrometer Tracing) và hiển thị trong JSON log cũng như truy vấn Loki.

**Bật JSON Logging**
```xml
<!-- Thêm vào pom.xml -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>6.6</version>
</dependency>
```

### 2. Tổng Hợp Log (ELK Stack)

**Cấu hình Logstash (tùy chọn, Giai đoạn 3)**
```conf
input {
  tcp {
    port => 5000
    codec => json
  }
}

filter {
  if [logger] =~ /com.namnd.cinema/ {
    # Phân tích các trường tùy chỉnh
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

### 3. Metrics & Kiểm Tra Sức Khỏe

**Endpoint kiểm tra sức khỏe (Tương lai)**
```bash
curl http://localhost:8080/actuator/health
```

Phản hồi mong đợi:
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

**Kiểm tra giám sát cơ bản (Khuyến nghị)**
```bash
# Thời gian phản hồi endpoint đăng nhập
time curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test"}'

# Thời gian phản hồi xác thực token
time curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/protected

# Kết nối cơ sở dữ liệu
curl http://localhost:8080/actuator/health/db
```

Xem [Xử lý sự cố triển khai](./deployment-troubleshooting.md) để biết hướng dẫn xử lý sự cố, quy trình khôi phục, danh sách kiểm tra, và xác nhận sau triển khai.
