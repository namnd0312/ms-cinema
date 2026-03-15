# Phase 1: K8s Base Manifests

## Context Links
- [Parent Plan](./plan.md)
- [K8s Architecture Research](./research/researcher-02-k8s-architecture-decisions.md)
- Dependencies: None (first phase)

## Overview
- **Date:** 2026-03-14
- **Priority:** P1
- **Status:** pending
- **Effort:** 4h
- **Review status:** not started

Create all Kubernetes base manifests under `k8s/base/`. Includes infrastructure (PostgreSQL, Redis, Kafka) and all 8 deployable services (api-gateway, auth, movie, booking, payment, notification, frontend). Uses Kustomize structure.

## Key Insights
- Each Spring Boot service uses `eclipse-temurin:21-jre-alpine` base image
- Services connect via env vars with defaults (`${KAFKA_HOST:localhost}`, `${REDIS_HOST:localhost}`, etc.)
- 5 PostgreSQL databases: testdb, moviedb, bookingdb, paymentdb, notificationdb
- Gateway uses `lb://service-name` URIs (Eureka load-balanced); K8s profile will use `http://service-name:port`
- Config Server shared config has JWT secret + Kafka settings; must replicate via ConfigMaps
- auth-service Dockerfile is at repo root (copies from `auth-service/target/`); others copy from `target/`

## Requirements

### Functional
- All 8 services deployable to K8s with `kubectl apply -k k8s/base/`
- PostgreSQL with 5 databases initialized
- Redis available for auth-service token blacklist + booking-service locks
- Kafka available for event streaming
- Health checks on all Spring Boot services

### Non-Functional
- Total resource usage fits Minikube (8GB RAM recommended)
- Pod startup within 120s
- No hardcoded secrets in manifests

## Architecture

### Directory Structure
```
k8s/base/
  kustomization.yaml
  namespace.yaml
  secrets.yaml              # TEMPLATE only (placeholders)
  postgres/
    statefulset.yaml
    service.yaml
    configmap-init-db.yaml  # SQL to create 5 databases
    pvc.yaml
  redis/
    deployment.yaml
    service.yaml
  kafka/
    kafka-cluster.yaml      # Strimzi Kafka CR
  auth-service/
    deployment.yaml
    service.yaml
    configmap.yaml
  movie-service/
    deployment.yaml
    service.yaml
    configmap.yaml
  booking-service/
    deployment.yaml
    service.yaml
    configmap.yaml
  payment-service/
    deployment.yaml
    service.yaml
    configmap.yaml
  notification-service/
    deployment.yaml
    service.yaml
    configmap.yaml
  api-gateway/
    deployment.yaml
    service.yaml
    configmap.yaml
  cinema-frontend/
    deployment.yaml
    service.yaml
```

### Service Template Pattern (each business service)
```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {service-name}
  labels:
    app: {service-name}
spec:
  replicas: 1
  selector:
    matchLabels:
      app: {service-name}
  template:
    metadata:
      labels:
        app: {service-name}
    spec:
      containers:
      - name: {service-name}
        image: {service-name}:latest  # overridden by Kustomize
        ports:
        - containerPort: {port}
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: JAVA_OPTS
          value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
        envFrom:
        - secretRef:
            name: cinema-secrets
        volumeMounts:
        - name: config
          mountPath: /config
          readOnly: true
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: {port}
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: {port}
          initialDelaySeconds: 30
          periodSeconds: 5
      volumes:
      - name: config
        configMap:
          name: {service-name}-config
```

### Secrets Template
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: cinema-secrets
type: Opaque
stringData:
  JWT_SECRET: "REPLACE_ME"
  POSTGRES_PASSWORD: "REPLACE_ME"
  REDIS_PASSWORD: ""
  STRIPE_SECRET_KEY: "REPLACE_ME"
  STRIPE_WEBHOOK_SECRET: "REPLACE_ME"
  MAIL_USERNAME: "REPLACE_ME"
  MAIL_PASSWORD: "REPLACE_ME"
```

### PostgreSQL Init Script (ConfigMap)
```sql
CREATE DATABASE testdb;
CREATE DATABASE moviedb;
CREATE DATABASE bookingdb;
CREATE DATABASE paymentdb;
CREATE DATABASE notificationdb;
```

### Strimzi Kafka CR
- 1 broker (dev), KRaft mode, ephemeral storage
- Bootstrap: `local-kafka-kafka-bootstrap:9092`

## Related Code Files

### Files to Create
- `k8s/base/kustomization.yaml`
- `k8s/base/namespace.yaml`
- `k8s/base/secrets.yaml`
- `k8s/base/postgres/statefulset.yaml`
- `k8s/base/postgres/service.yaml`
- `k8s/base/postgres/configmap-init-db.yaml`
- `k8s/base/postgres/pvc.yaml`
- `k8s/base/redis/deployment.yaml`
- `k8s/base/redis/service.yaml`
- `k8s/base/kafka/kafka-cluster.yaml`
- `k8s/base/auth-service/deployment.yaml`
- `k8s/base/auth-service/service.yaml`
- `k8s/base/auth-service/configmap.yaml`
- `k8s/base/movie-service/deployment.yaml`
- `k8s/base/movie-service/service.yaml`
- `k8s/base/movie-service/configmap.yaml`
- `k8s/base/booking-service/deployment.yaml`
- `k8s/base/booking-service/service.yaml`
- `k8s/base/booking-service/configmap.yaml`
- `k8s/base/payment-service/deployment.yaml`
- `k8s/base/payment-service/service.yaml`
- `k8s/base/payment-service/configmap.yaml`
- `k8s/base/notification-service/deployment.yaml`
- `k8s/base/notification-service/service.yaml`
- `k8s/base/notification-service/configmap.yaml`
- `k8s/base/api-gateway/deployment.yaml`
- `k8s/base/api-gateway/service.yaml`
- `k8s/base/api-gateway/configmap.yaml`
- `k8s/base/cinema-frontend/deployment.yaml`
- `k8s/base/cinema-frontend/service.yaml`

### Files to Modify
- None (all new files)

### Files to Delete
- None

## Implementation Steps

1. Create `k8s/base/` directory tree
2. Create `namespace.yaml` with `cinema` namespace
3. Create `secrets.yaml` template with placeholder values + comment "DO NOT commit real values"
4. **PostgreSQL:**
   a. Create `configmap-init-db.yaml` with SQL to create 5 databases
   b. Create `pvc.yaml` (5Gi ReadWriteOnce)
   c. Create `statefulset.yaml` (postgres:16, mount PVC + init script, env from secrets for password)
   d. Create `service.yaml` (ClusterIP, port 5432)
5. **Redis:**
   a. Create `deployment.yaml` (redis:7-alpine, port 6379)
   b. Create `service.yaml` (ClusterIP, port 6379)
6. **Kafka:**
   a. Create `kafka-cluster.yaml` Strimzi CR (1 broker, KRaft, ephemeral, port 9092)
7. **For each business service** (auth, movie, booking, payment, notification):
   a. Create `configmap.yaml` with `application-k8s.yml` content (K8s DNS URLs for DB, Redis, Kafka)
   b. Create `deployment.yaml` following template pattern above
   c. Create `service.yaml` (ClusterIP, correct port)
8. **API Gateway:**
   a. Create `configmap.yaml` with K8s-profile routes (direct URLs instead of `lb://`)
   b. Create `deployment.yaml` (port 8080)
   c. Create `service.yaml` (ClusterIP, port 8080)
9. **Cinema Frontend:**
   a. Create `deployment.yaml` (nginx-based, port 80)
   b. Create `service.yaml` (ClusterIP, port 80)
10. Create `kustomization.yaml` referencing all resources

### ConfigMap Content per Service (application-k8s.yml)

| Service | DB URL | Redis | Kafka | Special |
|---------|--------|-------|-------|---------|
| auth-service | `jdbc:postgresql://postgres:5432/testdb` | `redis:6379` | `local-kafka-kafka-bootstrap:9092` | JWT from secret |
| movie-service | `jdbc:postgresql://postgres:5432/moviedb` | - | `local-kafka-kafka-bootstrap:9092` | - |
| booking-service | `jdbc:postgresql://postgres:5432/bookingdb` | `redis:6379` | `local-kafka-kafka-bootstrap:9092` | - |
| payment-service | `jdbc:postgresql://postgres:5432/paymentdb` | - | `local-kafka-kafka-bootstrap:9092` | Stripe from secret |
| notification-service | `jdbc:postgresql://postgres:5432/notificationdb` | - | `local-kafka-kafka-bootstrap:9092` | Mail from secret |
| api-gateway | - | - | - | Routes to K8s service DNS |

## Todo List

- [ ] Create directory structure
- [ ] PostgreSQL StatefulSet + init ConfigMap
- [ ] Redis Deployment + Service
- [ ] Strimzi Kafka CR
- [ ] auth-service manifests
- [ ] movie-service manifests
- [ ] booking-service manifests
- [ ] payment-service manifests
- [ ] notification-service manifests
- [ ] api-gateway manifests
- [ ] cinema-frontend manifests
- [ ] secrets.yaml template
- [ ] kustomization.yaml
- [ ] Validate with `kubectl kustomize k8s/base/`

## Success Criteria
- `kubectl kustomize k8s/base/` renders valid YAML without errors
- All Deployments/StatefulSets reference correct images and ports
- ConfigMaps contain correct K8s DNS URLs for service-to-service communication
- Secrets template has all required keys
- Health probes configured on all Spring Boot services

## Risk Assessment
- **Strimzi CRD not installed**: Kafka CR will fail to apply. Mitigation: Phase 6 covers Strimzi operator install.
- **Image not found**: Deployments will fail if images not built. Mitigation: Jenkins pipeline builds images first.
- **Resource pressure**: 8 Spring Boot services + infra may exceed Minikube defaults. Mitigation: recommend `minikube start --memory=8192 --cpus=4`.

## Security Considerations
- secrets.yaml is a TEMPLATE only; real values injected via `create-secrets.sh` script (Phase 6)
- No default JWT secret in K8s ConfigMaps (must come from Secret)
- PostgreSQL password from Secret, not ConfigMap
- Stripe keys from Secret

## Next Steps
- Phase 2: Kustomize overlays for dev/prod differentiation
- Phase 4: Spring Boot application-k8s.yml profiles (must complete before testing)
