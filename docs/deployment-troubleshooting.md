# Deployment Troubleshooting & Operations

**Project:** ms-cinema
**Updated:** April 2026
**Related:** [Deployment Guide](./deployment-guide.md)

## Table of Contents

1. [Troubleshooting](#troubleshooting)
2. [Rollback Procedures](#rollback-procedures)
3. [Deployment Checklist](#deployment-checklist)
4. [Post-Deployment Validation](#post-deployment-validation)

---

## Troubleshooting

### Issue: Connection Refused (Port 8080)

**Cause:** Port already in use or firewall blocking

**Solution:**
```bash
# Find process using port 8080
lsof -i :8080

# Kill process
kill -9 <PID>

# Or use different port
java -jar auth-service.jar --server.port=8081

# Check firewall (macOS)
sudo lsof -i :8080
```

### Issue: Database Connection Failed

**Cause:** PostgreSQL not running or wrong credentials

**Check:**
```bash
# Is PostgreSQL running?
docker ps | grep postgres

# Test connection
psql -U postgres -d testdb -c "SELECT 1"

# Check credentials in application.yml
cat src/main/resources/application.yml | grep datasource
```

**Fix:**
```bash
# Start PostgreSQL
docker-compose up postgres-service

# Wait for startup
sleep 10

# Verify
docker exec jwt-postgres psql -U postgres -c "SELECT 1"
```

### Issue: JWT Token Invalid

**Cause:** Secret key mismatch or token expired

**Check:**
```bash
# Verify secret in config
echo $JWT_SECRET

# Check token expiration
# Decode token: https://jwt.io/
# Verify "exp" claim is future timestamp

# Check system time (token validation requires accurate time)
date -u
```

**Fix:**
```bash
# Ensure JWT_SECRET matches between services
export JWT_SECRET="your-secret-key"
java -jar auth-service.jar

# Or sync system time
sudo ntpdate -s time.nist.gov  # Linux
```

### Issue: Out of Memory Error

**Cause:** Heap size too small for load

**Solution:**
```bash
# Increase heap size
java -Xmx1024m -Xms512m -jar auth-service.jar

# For Docker
docker run -e "JAVA_OPTS=-Xmx1024m" ms-authentication-service:latest
```

### Issue: Slow Login Response

**Cause:** BCrypt hashing is slow by design, or database slow

**Diagnose:**
```bash
# Enable SQL logging in application.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# Check network latency
ping -c 1 localhost  # Should be < 1ms
```

### Issue: Can't Access from Remote Machine

**Cause:** Firewall, binding to localhost, or wrong IP

**Check:**
```bash
# Is app listening on all interfaces?
netstat -tlnp | grep 8080

# Verify network connectivity
telnet <server-ip> 8080

# Check firewall rules
sudo ufw status  # Linux
sudo pfctl -sr  # macOS
```

**Fix:**
```bash
# Ensure app listens on 0.0.0.0 (all interfaces)
java -jar auth-service.jar --server.address=0.0.0.0

# Or in application.yml
server:
  address: 0.0.0.0
  port: 8080
```

---

## Rollback Procedures

### Rollback Strategy

**Deployment Versioning:**
```bash
# Tag each release
git tag -a v0.0.1-prod -m "Production release"
git push origin v0.0.1-prod

# Docker image versioning
docker tag ms-authentication-service:latest \
  ms-authentication-service:0.0.1-prod

docker push registry.example.com/ms-authentication-service:0.0.1-prod
```

### Rollback Steps

**For Docker Compose:**
```bash
# Stop current version
docker-compose down

# Start previous version
docker-compose up -d

# Or explicitly
docker run -d \
  --name ms-auth \
  -p 8080:8080 \
  -e JWT_SECRET=$JWT_SECRET \
  ms-authentication-service:0.0.0-previous
```

**For Kubernetes (Phase 4):**
```bash
# View rollout history
kubectl rollout history deployment/jwt-auth

# Rollback to previous version
kubectl rollout undo deployment/jwt-auth

# Rollback to specific revision
kubectl rollout undo deployment/jwt-auth --to-revision=3
```

**For EC2/VM:**
```bash
# Stop current service
sudo systemctl stop jwt-auth

# Restore previous JAR
cp /opt/app/backups/auth-service.jar /opt/app/auth-service.jar

# Start service
sudo systemctl start jwt-auth

# Verify
curl http://localhost:8080/api/auth/register
```

### Database Rollback

**If schema migration fails:**
```bash
# Restore from backup
pg_restore -U postgres -d authdb \
  /backups/authdb-2026-02-10.dump

# Verify schema
psql -U postgres -d authdb -c "\dt"
```

**Note:** Currently using manual schema (no migrations). When Flyway is added (Phase 3), versioned migrations will support automated rollback.

### Issue: Stripe Reconciliation Job Fails

**Cause:** Stripe API key invalid, network timeout, or database connection error

**Solution:**
```bash
# Verify Stripe API key is set
echo $STRIPE_API_KEY

# Check reconciliation job logs
docker-compose logs payment-service | grep -i reconcil

# Check database connection (paymentdb)
psql -U postgres -d paymentdb -c "SELECT COUNT(*) FROM reconciliation_runs;"

# Manually trigger reconciliation with small date range
curl -X POST http://localhost:8084/api/payments/reconciliation/trigger \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"startDate":"2026-03-01","endDate":"2026-03-05"}'
```

**Check Batch Job Status:**
```bash
# Query Spring Batch metadata tables
psql -U postgres -d paymentdb -c "SELECT * FROM batch_job_execution ORDER BY CREATE_TIME DESC LIMIT 5;"
```

---

## Deployment Checklist

Before deploying to production, verify:

- [ ] Code committed and pushed to main branch
- [ ] All tests passing locally and in CI
- [ ] Security scan completed (no critical CVEs)
- [ ] Database backups taken
- [ ] Load balancer configured and tested
- [ ] SSL/TLS certificates valid
- [ ] Environment variables set correctly
- [ ] Database schema initialized
- [ ] Connection pooling configured
- [ ] Monitoring/alerting configured
- [ ] Log aggregation working
- [ ] Rollback plan reviewed and tested
- [ ] Team members notified
- [ ] Deployment window scheduled
- [ ] Health check endpoint responding
- [ ] Sample authentication flow tested

---

## Post-Deployment Validation

```bash
# 1. Health check
curl https://auth.example.com/actuator/health

# 2. Register test user (no password field - deferred to activation)
curl -X POST https://auth.example.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "test_deploy",
    "email": "test@example.com",
    "fullName": "Test User",
    "roles": [{"name": "ROLE_USER"}]
  }'

# 3. Activate with password (use token from email)
curl -X POST https://auth.example.com/api/auth/activate-with-password \
  -H "Content-Type: application/json" \
  -d '{
    "token": "{activation_token_from_email}",
    "password": "Test@12345",
    "confirmPassword": "Test@12345"
  }'

# 4. Login with activated credentials
TOKEN=$(curl -s -X POST https://auth.example.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test@12345"}' \
  | jq -r '.token')

# 5. Test protected endpoint
curl -H "Authorization: Bearer $TOKEN" \
  https://auth.example.com/api/protected

# 6. Check logs for authentication events and errors

# 7. Monitor metrics
curl https://auth.example.com/actuator/metrics | jq .
```

---

## Support & Documentation

- [README.md](../README.md) - Quick start guide
- [System Architecture](./system-architecture.md) - Technical design
- [Code Standards](./code-standards.md) - Development guidelines
- [Project Roadmap](./project-roadmap.md) - Future features
