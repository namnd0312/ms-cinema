# Xử Lý Sự Cố Triển Khai & Vận Hành

**Dự án:** ms-cinema
**Cập nhật:** Tháng 2, 2026
**Liên quan:** [Hướng dẫn Triển khai](../deployment-guide.md)

## Mục Lục

1. [Xử lý sự cố](#xử-lý-sự-cố)
2. [Quy trình Rollback](#quy-trình-rollback)
3. [Danh sách kiểm tra Triển khai](#danh-sách-kiểm-tra-triển-khai)
4. [Xác thực sau Triển khai](#xác-thực-sau-triển-khai)

---

## Xử Lý Sự Cố

### Sự cố: Connection Refused (Port 8080)

**Nguyên nhân:** Port đã được sử dụng hoặc bị firewall chặn

**Giải pháp:**
```bash
# Tìm tiến trình đang sử dụng port 8080
lsof -i :8080

# Dừng tiến trình
kill -9 <PID>

# Hoặc sử dụng port khác
java -jar auth-service.jar --server.port=8081

# Kiểm tra firewall (macOS)
sudo lsof -i :8080
```

### Sự cố: Kết nối Database thất bại

**Nguyên nhân:** PostgreSQL chưa chạy hoặc sai thông tin đăng nhập

**Kiểm tra:**
```bash
# PostgreSQL đã chạy chưa?
docker ps | grep postgres

# Kiểm tra kết nối
psql -U postgres -d testdb -c "SELECT 1"

# Kiểm tra thông tin đăng nhập trong application.yml
cat src/main/resources/application.yml | grep datasource
```

**Sửa lỗi:**
```bash
# Khởi động PostgreSQL
docker-compose up postgres-service

# Chờ khởi động
sleep 10

# Xác nhận
docker exec jwt-postgres psql -U postgres -c "SELECT 1"
```

### Sự cố: JWT Token không hợp lệ

**Nguyên nhân:** Secret key không khớp hoặc token đã hết hạn

**Kiểm tra:**
```bash
# Xác nhận secret trong cấu hình
echo $JWT_SECRET

# Kiểm tra thời hạn token
# Giải mã token: https://jwt.io/
# Xác nhận claim "exp" là timestamp trong tương lai

# Kiểm tra thời gian hệ thống (xác thực token yêu cầu thời gian chính xác)
date -u
```

**Sửa lỗi:**
```bash
# Đảm bảo JWT_SECRET khớp giữa các service
export JWT_SECRET="your-secret-key"
java -jar auth-service.jar

# Hoặc đồng bộ thời gian hệ thống
sudo ntpdate -s time.nist.gov  # Linux
```

### Sự cố: Lỗi hết bộ nhớ (Out of Memory)

**Nguyên nhân:** Heap size quá nhỏ cho tải hiện tại

**Giải pháp:**
```bash
# Tăng heap size
java -Xmx1024m -Xms512m -jar auth-service.jar

# Cho Docker
docker run -e "JAVA_OPTS=-Xmx1024m" ms-authentication-service:latest
```

### Sự cố: Phản hồi đăng nhập chậm

**Nguyên nhân:** BCrypt hashing chậm theo thiết kế, hoặc database chậm

**Chẩn đoán:**
```bash
# Bật SQL logging trong application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# Kiểm tra độ trễ mạng
ping -c 1 localhost  # Nên < 1ms
```

### Sự cố: Không thể truy cập từ máy khác

**Nguyên nhân:** Firewall, bind vào localhost, hoặc sai IP

**Kiểm tra:**
```bash
# Ứng dụng có đang lắng nghe trên tất cả interface không?
netstat -tlnp | grep 8080

# Xác nhận kết nối mạng
telnet <server-ip> 8080

# Kiểm tra quy tắc firewall
sudo ufw status  # Linux
sudo pfctl -sr  # macOS
```

**Sửa lỗi:**
```bash
# Đảm bảo ứng dụng lắng nghe trên 0.0.0.0 (tất cả interface)
java -jar auth-service.jar --server.address=0.0.0.0

# Hoặc trong application.yml
server:
  address: 0.0.0.0
  port: 8080
```

---

## Quy Trình Rollback

### Chiến lược Rollback

**Quản lý phiên bản triển khai:**
```bash
# Đánh tag mỗi bản phát hành
git tag -a v0.0.1-prod -m "Production release"
git push origin v0.0.1-prod

# Quản lý phiên bản Docker image
docker tag ms-authentication-service:latest \
  ms-authentication-service:0.0.1-prod

docker push registry.example.com/ms-authentication-service:0.0.1-prod
```

### Các bước Rollback

**Cho Docker Compose:**
```bash
# Dừng phiên bản hiện tại
docker-compose down

# Khởi động phiên bản trước
docker-compose up -d

# Hoặc chỉ định cụ thể
docker run -d \
  --name ms-auth \
  -p 8080:8080 \
  -e JWT_SECRET=$JWT_SECRET \
  ms-authentication-service:0.0.0-previous
```

**Cho Kubernetes (Giai đoạn 4):**
```bash
# Xem lịch sử rollout
kubectl rollout history deployment/jwt-auth

# Rollback về phiên bản trước
kubectl rollout undo deployment/jwt-auth

# Rollback về phiên bản cụ thể
kubectl rollout undo deployment/jwt-auth --to-revision=3
```

**Cho EC2/VM:**
```bash
# Dừng service hiện tại
sudo systemctl stop jwt-auth

# Khôi phục JAR trước đó
cp /opt/app/backups/auth-service.jar /opt/app/auth-service.jar

# Khởi động service
sudo systemctl start jwt-auth

# Xác nhận
curl http://localhost:8080/api/auth/register
```

### Rollback Database

**Nếu migration schema thất bại:**
```bash
# Khôi phục từ bản sao lưu
pg_restore -U postgres -d authdb \
  /backups/authdb-2026-02-10.dump

# Xác nhận schema
psql -U postgres -d authdb -c "\dt"
```

**Lưu ý:** Hiện tại đang dùng schema thủ công (chưa có migration). Khi Flyway được thêm vào (Giai đoạn 3), migration có phiên bản sẽ hỗ trợ rollback tự động.

---

## Danh Sách Kiểm Tra Triển Khai

Trước khi triển khai lên production, xác nhận:

- [ ] Code đã commit và push lên nhánh chính
- [ ] Tất cả test đã pass trên local và CI
- [ ] Quét bảo mật hoàn tất (không có CVE nghiêm trọng)
- [ ] Đã sao lưu database
- [ ] Load balancer đã cấu hình và kiểm tra
- [ ] Chứng chỉ SSL/TLS còn hiệu lực
- [ ] Biến môi trường đã cài đặt đúng
- [ ] Schema database đã khởi tạo
- [ ] Connection pooling đã cấu hình
- [ ] Giám sát/cảnh báo đã cấu hình
- [ ] Tổng hợp log đang hoạt động
- [ ] Kế hoạch rollback đã xem xét và kiểm tra
- [ ] Các thành viên nhóm đã được thông báo
- [ ] Khung thời gian triển khai đã lên lịch
- [ ] Endpoint health check đang phản hồi
- [ ] Luồng xác thực mẫu đã kiểm tra

---

## Xác Thực Sau Triển Khai

```bash
# 1. Kiểm tra sức khỏe
curl https://auth.example.com/actuator/health

# 2. Đăng ký user thử nghiệm
curl -X POST https://auth.example.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_deploy",
    "password": "Test@12345",
    "fullName": "Test User",
    "roles": [{"name": "ROLE_USER"}]
  }'

# 3. Đăng nhập
TOKEN=$(curl -s -X POST https://auth.example.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test_deploy","password":"Test@12345"}' \
  | jq -r '.token')

# 4. Kiểm tra endpoint được bảo vệ
curl -H "Authorization: Bearer $TOKEN" \
  https://auth.example.com/api/protected

# 5. Kiểm tra log cho các sự kiện xác thực và lỗi

# 6. Giám sát metrics
curl https://auth.example.com/actuator/metrics | jq .
```

---

## Hỗ Trợ & Tài Liệu

- [README.md](../../README.md) - Hướng dẫn bắt đầu nhanh
- [Kiến trúc hệ thống](../system-architecture.md) - Thiết kế kỹ thuật
- [Quy chuẩn code](../code-standards.md) - Hướng dẫn phát triển
- [Lộ trình dự án](../project-roadmap.md) - Tính năng tương lai
