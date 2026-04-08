# Phase 3: K8s Base Configs & Per-Service Manifests

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1: Code Changes](./phase-01-remove-eureka-and-config-server-code-changes.md)
- [Phase 2: Infrastructure](./phase-02-k8s-infrastructure-postgresql-kafka-redis-zipkin.md)
- [config-server config-repo](/config-server/src/main/resources/config-repo/application.yml)

## Overview
- **Priority:** High
- **Status:** Pending
- **Description:** Create shared ConfigMap (with migrated config-server values), Secrets, and per-service Deployment+Service manifests. No eureka-server, no config-server deployments.

## Key Insights
- ConfigMap now includes values previously served by config-server (Kafka config, Zipkin, JWT alias)
- DB_HOST changes from `host.minikube.internal` to `postgresql` (K8s DNS — infra now in cluster)
- KAFKA_HOST → `kafka`, REDIS_HOST → `redis`, ZIPKIN_HOST → `zipkin`
- `EUREKA_CLIENT_ENABLED=false` and `SPRING_CLOUD_CONFIG_ENABLED=false` in ConfigMap
- Each service in own directory for independent CI/CD
- auth-service needs dedicated Dockerfile

## Requirements
### Functional
- Each service deployable independently: `kubectl apply -f k8s/<service>/`
- All config-server values available via env vars
- Services connect to in-cluster infra (postgresql, kafka, redis, zipkin)

### Non-functional (minimal — study/research only)
- 256Mi RAM limit per service, no requests (best-effort QoS)
- Single replica, `imagePullPolicy: Never` (Minikube local Docker)

## Implementation Steps

### Step 1: Create `k8s/base/namespace.yml`
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ms-cinema
  labels:
    app.kubernetes.io/part-of: ms-cinema
    environment: development
```

### Step 2: Create `k8s/base/configmap.yml`
Includes all config-server migrated values + infra hosts:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ms-cinema-config
  namespace: ms-cinema
data:
  # Spring profile — disables Eureka routes, uses static URIs
  SPRING_PROFILES_ACTIVE: "k8s"
  # Disable Eureka and config-server
  EUREKA_CLIENT_ENABLED: "false"
  SPRING_CLOUD_CONFIG_ENABLED: "false"
  # Infrastructure hosts (in-cluster K8s DNS)
  DB_HOST: "postgresql"
  REDIS_HOST: "redis"
  KAFKA_HOST: "kafka"
  ZIPKIN_HOST: "zipkin"
  # --- Migrated from config-server config-repo ---
  # Kafka configuration
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
  SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET: "earliest"
  SPRING_KAFKA_CONSUMER_VALUE_DESERIALIZER: "org.springframework.kafka.support.serializer.JsonDeserializer"
  SPRING_KAFKA_PRODUCER_VALUE_SERIALIZER: "org.springframework.kafka.support.serializer.JsonSerializer"
  SPRING_KAFKA_PROPERTIES_SPRING_JSON_TRUSTED_PACKAGES: "com.namnd.kafka.events.*"
  # Zipkin tracing
  MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: "http://zipkin:9411/api/v2/spans"
  # JWT alias (non-secret)
  NAMND_APP_JWTALIAS: "namnd-cinema"
  # Inter-service URLs (for FeignClient)
  MOVIE_SERVICE_URL: "http://movie-service:8082"
  # Java timezone
  TZ: "Asia/Ho_Chi_Minh"
  JAVA_OPTS: "-Duser.timezone=Asia/Ho_Chi_Minh"
```

### Step 3: Create `k8s/base/secrets.yml`
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ms-cinema-secrets
  namespace: ms-cinema
type: Opaque
stringData:
  # Database credentials
  DB_PASSWORD: "postgres"
  DB_USERNAME: "postgres"
  SPRING_DATASOURCE_USERNAME: "postgres"
  SPRING_DATASOURCE_PASSWORD: "postgres"
  # JWT secret (migrated from config-server)
  NAMND_APP_JWTSECRET: "REPLACE_WITH_REAL_JWT_SECRET"
  # Auth service datasource
  AUTH_DATASOURCE_URL: "jdbc:postgresql://postgresql:5432/testdb"
  # Stripe
  STRIPE_API_KEY: "sk_test_placeholder"
  STRIPE_WEBHOOK_SECRET: "whsec_placeholder"
  # Mail
  MAIL_USERNAME: "placeholder@gmail.com"
  MAIL_PASSWORD: "placeholder"
  # Google OAuth
  GOOGLE_CLIENT_ID: "placeholder"
  GOOGLE_CLIENT_SECRET: "placeholder"
```

### Step 4: Add to `.gitignore`
```
k8s/base/secrets.yml
```

### Step 5: Create `auth-service/Dockerfile`
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY target/auth-service.jar auth-service.jar
ENTRYPOINT ["java", "-jar", "auth-service.jar"]
```

### Step 6: Per-Service Manifests
All services follow this template pattern. Each in own directory.

**Init container** (wait for config-server removed — wait for infra instead):
```yaml
initContainers:
  - name: wait-for-postgresql
    image: busybox:1.36
    command: ['sh', '-c']
    args:
      - |
        until nc -z postgresql 5432; do
          echo "Waiting for postgresql..."
          sleep 3
        done
```

Services needing Kafka also wait for Kafka:
```yaml
  - name: wait-for-kafka
    image: busybox:1.36
    command: ['sh', '-c']
    args:
      - |
        until nc -z kafka 9092; do
          echo "Waiting for kafka..."
          sleep 3
        done
```

**Env injection pattern** — bulk ConfigMap + individual Secrets:
```yaml
envFrom:
  - configMapRef:
      name: ms-cinema-config
env:
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef: { name: ms-cinema-secrets, key: DB_USERNAME }
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef: { name: ms-cinema-secrets, key: DB_PASSWORD }
```

### Step 7: Per-Service Specifics

| Service | Port | Init Containers | Extra Secret Env |
|---------|------|-----------------|-----------------|
| api-gateway | 8080 | none (no DB/Kafka) | — |
| auth-service | 8081 | wait-postgresql, wait-kafka | AUTH_DATASOURCE_URL, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET |
| movie-service | 8082 | wait-postgresql, wait-kafka | DB_USERNAME, DB_PASSWORD |
| booking-service | 8083 | wait-postgresql, wait-kafka | DB_USERNAME, DB_PASSWORD |
| payment-service | 8084 | wait-postgresql, wait-kafka | DB_USERNAME, DB_PASSWORD, STRIPE_API_KEY, STRIPE_WEBHOOK_SECRET |
| notification-service | 8085 | wait-postgresql, wait-kafka | DB_USERNAME, DB_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD |
| audit-service | 8086 | wait-postgresql, wait-kafka | DB_USERNAME, DB_PASSWORD |

### Step 8: Docker Image Build Commands
```bash
eval $(minikube docker-env)
# 7 services (no eureka, no config-server)
docker build -t ms-cinema/api-gateway:latest ./api-gateway
docker build -t ms-cinema/auth-service:latest ./auth-service
docker build -t ms-cinema/movie-service:latest ./movie-service
docker build -t ms-cinema/booking-service:latest ./booking-service
docker build -t ms-cinema/payment-service:latest ./payment-service
docker build -t ms-cinema/notification-service:latest ./notification-service
docker build -t ms-cinema/audit-service:latest ./audit-service
```

### Step 9: Verify Env Var Names
```bash
grep -r 'DB_HOST\|SPRING_DATASOURCE\|KAFKA_HOST\|REDIS_HOST' --include="application*.yml"
```

## Todo List
- [ ] Create `k8s/base/namespace.yml`
- [ ] Create `k8s/base/configmap.yml` (with migrated config-server values)
- [ ] Create `k8s/base/secrets.yml` (with JWT secret)
- [ ] Add `k8s/base/secrets.yml` to `.gitignore`
- [ ] Create `auth-service/Dockerfile`
- [ ] Create `k8s/api-gateway/deployment.yml`
- [ ] Create `k8s/auth-service/deployment.yml`
- [ ] Create `k8s/movie-service/deployment.yml`
- [ ] Create `k8s/booking-service/deployment.yml`
- [ ] Create `k8s/payment-service/deployment.yml`
- [ ] Create `k8s/notification-service/deployment.yml`
- [ ] Create `k8s/audit-service/deployment.yml`
- [ ] Verify env var names match each service's application.yml
- [ ] Remove old flat `k8s/*.yml` files if they exist

## Success Criteria
- Each service deployable independently
- All pods Running + Ready, no eureka/config-server errors in logs
- Services connect to in-cluster PostgreSQL, Kafka, Redis, Zipkin
- `kubectl logs` shows successful DB connection + Kafka consumer registration

## Risk Assessment
- **Risk:** envFrom bulk injects all ConfigMap vars to all services
  - **Mitigation:** Harmless — unused vars ignored by Spring Boot
- **Risk:** Config-server Kafka properties not mapping correctly via env vars
  - **Mitigation:** Spring Boot relaxed binding handles `SPRING_KAFKA_*` → `spring.kafka.*`; verify with test
- **Risk:** auth-service Dockerfile breaks Docker Compose
  - **Mitigation:** Docker Compose uses root Dockerfile with `context: .`; both coexist

## Security Considerations
- JWT secret in K8s Secret, not ConfigMap
- Stripe/mail/OAuth in Secret
- No service exposed outside cluster except via api-gateway (Phase 4)

## Next Steps
- Phase 4: Frontend + LoadBalancer for external access
- Phase 5: Deploy scripts
