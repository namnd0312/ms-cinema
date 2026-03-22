# Hướng Dẫn Triển Khai

**Dự án:** ms-cinema
**Phiên bản:** 0.0.1-SNAPSHOT
**Cập nhật:** Tháng 2 năm 2026

## Mục Lục

1. [Yêu cầu tiên quyết](#yêu-cầu-tiên-quyết)
2. [Thiết lập phát triển cục bộ](#thiết-lập-phát-triển-cục-bộ)
3. [Triển khai Docker](#triển-khai-docker)
4. [Triển khai production](#triển-khai-production)
5. [Quản lý cấu hình](#quản-lý-cấu-hình)
6. [Thiết lập cơ sở dữ liệu](#thiết-lập-cơ-sở-dữ-liệu)
7. [Giám sát & Ghi log](#giám-sát--ghi-log)
8. [Xử lý sự cố](./deployment-troubleshooting.md#troubleshooting)
9. [Quy trình khôi phục](./deployment-troubleshooting.md#rollback-procedures)

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

# Docker Compose phải hỗ trợ services định dạng 3.8+ cho PostgreSQL, Kafka, Zipkin, audit-service
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
# docker-compose.yml chạy init-databases.sql tự động khi khởi động PostgreSQL
docker-compose up -d postgres

# Đợi postgres khởi động (30-60 giây)
docker-compose logs postgres | grep "database system is ready"

# Xác minh tất cả 6 databases được tạo
docker exec postgres psql -U postgres -c "\l"
# Output: testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb
```

**Xác minh Schema Chi Tiết:**
```bash
# Xác minh auth-service (testdb)
docker exec postgres psql -U postgres -d testdb -c "\dt"
# Output: users, roles, user_roles, refresh_tokens, password_reset_tokens, activation_tokens, blacklisted_tokens, password_history, user_oauth_providers

# Xác minh movie-service (moviedb)
docker exec postgres psql -U postgres -d moviedb -c "\dt"
# Output: movies, theaters, seats, showtimes, movie_ratings, movie_comments, comment_reactions

# Xác minh audit-service (auditdb)
docker exec postgres psql -U postgres -d auditdb -c "\dt"
# Output: audit_logs
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

### 6. Kiểm Tra Luồng Xác Thực

```bash
# 1. Đăng ký người dùng
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@1234",
    "fullName": "Test User",
    "roles": [{"name": "ROLE_USER"}]
  }'
# Mong đợi: 200 OK "User registered successfully!"

# 2. Đăng nhập
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "Test@1234"
  }'
# Mong đợi: 200 OK với JWT token trong phản hồi

# 3. Truy cập endpoint được bảo vệ với token
TOKEN="eyJhbGc..." # Từ phản hồi đăng nhập
curl -X GET http://localhost:8080/api/protected \
  -H "Authorization: Bearer $TOKEN"
# Mong đợi: 200 OK hoặc phản hồi theo endpoint cụ thể
```

---

## Triển Khai Docker

### 1. Build Docker Image

```bash
# Build cục bộ
docker build -t auth-service:latest .

# Hoặc với tag phiên bản
docker build -t auth-service:0.0.1-SNAPSHOT .

# Xác minh image
docker images | grep auth-service
```

### 2. Chạy với Docker Compose (Khuyến nghị)

```bash
# Khởi động tất cả dịch vụ (postgres, redis, kafka, eureka, config-server, zipkin, kafdrop, tất cả 9 dịch vụ + hạ tầng)
docker-compose up -d

# Xác minh các dịch vụ đang chạy
docker-compose ps
# Output: Tất cả dịch vụ (auth-service, movie-service, booking-service, payment-service, notification-service, audit-service, postgres, redis, kafka, zipkin, kafdrop, v.v.) hiển thị UP

# Xem log
docker-compose logs -f auth-service
docker-compose logs -f notification-service

# Truy cập giao diện giám sát
# Zipkin (distributed tracing): http://localhost:9411/zipkin
# Kafdrop (Kafka topics): http://localhost:9000

# Kiểm tra ứng dụng
curl http://localhost:8080/api/auth/register
```

**Phụ thuộc dịch vụ:**
- auth-service: postgres, redis (token blacklist), kafka, eureka, config-server
- notification-service: postgres (notificationdb), kafka, redis (event dedup), eureka, config-server
- audit-service: postgres (auditdb), kafka, eureka, config-server
- grafana: depends_on prometheus, zipkin (cho việc cung cấp tracing datasource)
- Thứ tự khởi động: postgres → redis → kafka → eureka → config-server → tất cả 9 dịch vụ

### 3. Biến Môi Trường cho Docker

Tạo file `.env` ở thư mục gốc dự án:
```env
# Cơ sở dữ liệu (chung cho tất cả instance PostgreSQL)
POSTGRES_USER=postgres
POSTGRES_PASSWORD=secure_password_here
POSTGRES_DB=testdb

# Databases (sẽ được tạo bởi init-databases.sql)
# testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb

# Cấu hình JWT
JWT_SECRET=your-secret-key-here
JWT_EXPIRATION=86400000

# Server
SERVER_PORT=8080

# Ghi log
LOG_LEVEL=DEBUG

# Kafka
KAFKA_BROKERS=kafka:9092

# Zipkin (tracing)
ZIPKIN_ENDPOINT=http://zipkin:9411/api/v2/spans
TRACING_SAMPLING_PROBABILITY=1.0
```

**Cập nhật docker-compose.yml** để dùng env file:
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

### 4. Dừng & Dọn Dẹp

```bash
# Dừng tất cả dịch vụ
docker-compose down

# Xóa volumes (CẢNH BÁO: xóa dữ liệu)
docker-compose down -v

# Xóa images
docker rmi auth-service:latest
```

### 5. Docker Networking

```bash
# Xác minh mạng
docker network ls | grep my-net

# Kiểm tra mạng
docker network inspect ms-cinema_my-net

# Các dịch vụ có thể giao tiếp: postgres-service:5432 từ auth-service
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

### 2. Quản Lý Secrets

**KHÔNG commit secrets vào git:**
```bash
# SAI - Không bao giờ làm điều này
# application.yml
namnd:
  app:
    jwtSecret: "my-production-secret"

# ĐÚNG - Sử dụng biến môi trường
namnd:
  app:
    jwtSecret: ${JWT_SECRET}  # Được inject tại thời điểm chạy
```

**Secrets qua Biến Môi Trường:**
```bash
# Đặt trước khi chạy
export JWT_SECRET="production-secret-key-min-32-chars"
export SPRING_DATASOURCE_PASSWORD="db-password"
export SPRING_DATASOURCE_URL="jdbc:postgresql://prod-db.internal:5432/authdb"

java -jar auth-service.jar
```

**Secrets qua Docker Secrets (Swarm):**
```bash
# Tạo secret
echo "production-secret-key" | docker secret create jwt_secret -

# Sử dụng trong compose
secrets:
  jwt_secret:
    external: true

services:
  app:
    environment:
      - JWT_SECRET=/run/secrets/jwt_secret
```

**Secrets qua Kubernetes Secrets:**
```bash
# Tạo secret
kubectl create secret generic jwt-auth-secret \
  --from-literal=jwt-secret="production-secret-key"

# Tham chiếu trong deployment
env:
  - name: JWT_SECRET
    valueFrom:
      secretKeyRef:
        name: jwt-auth-secret
        key: jwt-secret
```

### 3. Triển Khai Lên VM/Server

```bash
# 1. Sao chép JAR lên server
scp target/auth-service.jar user@prod-server:/opt/app/

# 2. Tạo systemd service (để tự khởi động)
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

# 3. Khởi động dịch vụ
sudo systemctl start jwt-auth
sudo systemctl enable jwt-auth  # Tự khởi động khi reboot

# 4. Kiểm tra trạng thái
sudo systemctl status jwt-auth
```

### 4. Triển Khai Lên AWS

**Phương án A: EC2 Instance**
```bash
# Khởi tạo EC2 instance (Ubuntu 20.04 LTS)
# Security group: cho phép 443 (HTTPS), 8080 (nội bộ)

# SSH vào instance
ssh -i key.pem ec2-user@instance-ip

# Cài đặt Java & Docker
sudo yum update -y
sudo yum install java-11-amazon-corretto -y
sudo yum install docker -y
sudo systemctl start docker

# Sao chép ứng dụng
scp -i key.pem auth-service.jar ec2-user@instance-ip:/opt/app/

# Chạy với biến môi trường
java -jar /opt/app/auth-service.jar \
  -DJWT_SECRET=$JWT_SECRET \
  -Dspring.datasource.url=$DB_URL
```

**Phương án B: ECS Fargate**
```bash
# Tạo CloudFormation template hoặc dùng AWS Console
# Container image: đã push lên ECR

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

**Phương án C: RDS cho Cơ Sở Dữ Liệu**
```bash
# Tạo RDS PostgreSQL instance
# - Engine: PostgreSQL 13.1
# - Dung lượng lưu trữ: 100GB
# - Multi-AZ: Có
# - Thời gian giữ backup: 30 ngày
# - VPC security group: Cho phép EC2 security group trên cổng 5432

# Chuỗi kết nối:
# jdbc:postgresql://prod-db.c123.us-east-1.rds.amazonaws.com:5432/authdb
```

### 5. Cấu Hình Load Balancer

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

        # Thời gian chờ
        proxy_connect_timeout 60s;
        proxy_send_timeout 60s;
        proxy_read_timeout 60s;
    }

    # Endpoint kiểm tra sức khỏe
    location /actuator/health {
        proxy_pass http://jwt_auth;
    }
}
```

**Cấu hình AWS ALB**
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

### Cấu Hình Theo Môi Trường

```
src/main/resources/
├── application.yml              (cấu hình chung)
├── application-dev.yml          (phát triển)
├── application-staging.yml      (staging)
└── application-prod.yml         (production)
```

**application.yml (mặc định, phát triển)**
```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/testdb
    username: postgres
    password: 123456  # Chỉ OK cho dev

namnd:
  app:
    jwtSecret: bezKoderSecretKey  # Chỉ OK cho dev
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
    jwtSecret: ${JWT_SECRET}  # Phải được đặt qua biến môi trường
    jwtExpiration: ${JWT_EXPIRATION:86400000}
```

**Kích hoạt Profile**
```bash
# Qua dòng lệnh
java -jar auth-service.jar --spring.profiles.active=prod

# Qua biến môi trường
export SPRING_PROFILES_ACTIVE=prod
java -jar auth-service.jar

# Qua Docker
docker run -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=xxxx \
  auth-service:latest
```

### Tham Số Cấu Hình

| Tham số | Mặc định | Ghi đè Production | Ghi chú |
|---------|----------|-------------------|---------|
| server.port | 8080 | 8080 | Đằng sau load balancer, chỉ nội bộ |
| jwt.secret | bezKoderSecretKey | ${JWT_SECRET} | Khuyến nghị tối thiểu 32 ký tự |
| jwt.expiration | 86400000 (24h) | 86400000 | Tính bằng mili giây |
| db.url | localhost:5432/testdb | ${DB_URL} | Production: RDS/managed DB |
| db.username | postgres | ${DB_USER} | Dùng biến môi trường |
| db.password | 123456 | ${DB_PASSWORD} | Dùng vault/secrets manager |
| logging.level | debug | info | Giảm mức chi tiết log trong production |

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

### 1. Distributed Tracing (Zipkin)

**Truy cập giao diện Zipkin:**
```
http://localhost:9411/zipkin
```

**Tính năng:**
- Trực quan hóa request traces trên tất cả microservices
- Theo dõi độ trễ request đầu-cuối
- Xác định điểm nghẽn trong các cuộc gọi giữa dịch vụ
- Xem tương quan traceId trong log (Loki) và traces (Zipkin)

**Cấu hình:**
```yaml
# Tập trung trong config-server (application.yml)
management:
  tracing:
    sampling:
      probability: 1.0  # 100% sampling (thay đổi qua biến TRACING_SAMPLING_PROBABILITY)
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans

# Dự phòng: Tất cả 6 business services có bản sao application.yml cục bộ làm safeguard khi khởi động
# (khi config-server không khả dụng trong quá trình boot)
```

**Tinh chỉnh Production:**
- Giảm sampling xuống 10-20% cho hệ thống lưu lượng cao: `TRACING_SAMPLING_PROBABILITY=0.1`
- Traces tự động bao gồm service-to-service (Feign), Kafka, các thao tác cơ sở dữ liệu
- traceId/spanId tự động được inject vào log qua MDC cho tương quan log chéo

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
