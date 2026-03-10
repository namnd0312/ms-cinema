# Phase 4: Docker Compose & Config Server Integration

## Context Links
- [Plan Overview](./plan.md)
- [Phase 2](./phase-02-notification-service-setup-consumer-and-email-sender.md)
- docker-compose.yml, config-server/src/main/resources/config-repo/

## Overview
- **Priority:** High
- **Status:** Pending
- **Description:** Wire notification-service into docker-compose, config-server, and Prometheus monitoring

## Key Insights
- Follow existing service patterns in docker-compose.yml (depends_on, network, env vars)
- Config server needs notification-service.yml for SMTP credentials
- Prometheus needs scrape job for :8085

## Related Code Files

**Modify:**
- `docker-compose.yml` — add notification-service container
- `config-server/src/main/resources/config-repo/notification-service.yml` — SMTP config (NEW)
- `config-server/src/main/resources/config-repo/auth-service.yml` — remove mail config
- `monitoring/prometheus/prometheus.yml` — add scrape target

## Implementation Steps

1. **Add to docker-compose.yml:**
   ```yaml
   notification-service:
     build:
       context: .
       dockerfile: notification-service/Dockerfile
     ports:
       - "8085:8085"
     environment:
       - SERVER_PORT=8085
       - EUREKA_HOST=eureka-server
       - CONFIG_SERVER_HOST=config-server
       - KAFKA_HOST=kafka
       - MAIL_USERNAME=${MAIL_USERNAME}
       - MAIL_PASSWORD=${MAIL_PASSWORD}
     depends_on:
       - eureka-server
       - config-server
       - kafka
     networks:
       - my-net
   ```

2. **Create config-server notification-service.yml:**
   ```yaml
   spring:
     mail:
       host: smtp.gmail.com
       port: 587
       username: ${MAIL_USERNAME:}
       password: ${MAIL_PASSWORD:}
       properties:
         mail.smtp.auth: true
         mail.smtp.starttls.enable: true
   ```

3. **Clean up auth-service.yml in config-server:** Remove mail-related properties (already handled in Phase 3)

4. **Add Prometheus scrape job:**
   ```yaml
   - job_name: 'notification-service'
     metrics_path: '/actuator/prometheus'
     static_configs:
       - targets: ['notification-service:8085']
   ```

5. **Add auth-service Kafka dependency in docker-compose:**
   - Add `kafka` to auth-service `depends_on` list (it now needs Kafka as producer)

## Todo List
- [ ] Add notification-service to docker-compose.yml
- [ ] Create notification-service.yml in config-repo
- [ ] Remove mail config from auth-service.yml in config-repo
- [ ] Add kafka to auth-service depends_on
- [ ] Add Prometheus scrape job
- [ ] Rebuild config-server JAR

## Success Criteria
- `docker compose up` starts notification-service alongside other services
- notification-service registers with Eureka
- notification-service fetches SMTP config from config-server
- Prometheus scrapes notification-service metrics

## Risk Assessment
- **Low:** Additive change to docker-compose, no existing services affected
- Ensure MAIL_USERNAME/MAIL_PASSWORD env vars passed to notification-service container

## Security Considerations
- SMTP credentials passed via env vars, not in config files
- notification-service not exposed via API Gateway (internal only)

## Next Steps
- Phase 5: end-to-end testing
