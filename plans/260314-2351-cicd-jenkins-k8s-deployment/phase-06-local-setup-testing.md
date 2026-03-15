# Phase 6: Local Setup + Testing

## Context Links
- [Parent Plan](./plan.md)
- Dependencies: All previous phases
- [Jenkins Research](./research/researcher-01-jenkins-pipeline-patterns.md)
- [K8s Research](./research/researcher-02-k8s-architecture-decisions.md)

## Overview
- **Date:** 2026-03-14
- **Priority:** P1
- **Status:** pending
- **Effort:** 2h
- **Review status:** not started

Create setup scripts, secret creation scripts, deployment scripts, and testing instructions for local Minikube environment. Includes Strimzi operator install, namespace creation, end-to-end smoke tests, and troubleshooting guide.

## Key Insights
- Minikube needs at least 8GB RAM and 4 CPUs for 8 Spring Boot services + infra
- Strimzi CRDs must be installed before Kafka CR can be applied
- Secrets must be created before any service deployment
- Order: Minikube -> Strimzi -> Namespaces -> Secrets -> Infrastructure (PG, Redis, Kafka) -> Services
- Health endpoints provide fastest smoke test validation

## Requirements

### Functional
- Single script to bootstrap Minikube with all addons
- Script to create K8s Secrets from user-provided values
- Script to deploy entire stack in correct order
- Smoke test commands to validate all services healthy
- Troubleshooting guide for common issues

### Non-Functional
- Scripts idempotent (safe to re-run)
- Clear console output with progress indicators
- Works on macOS and Linux

## Architecture

### Script Structure
```
k8s/scripts/
  setup-minikube.sh       # Start Minikube + install addons + Strimzi
  create-secrets.sh       # Create K8s Secrets from env vars or prompts
  deploy.sh               # Deploy full stack in order
  smoke-test.sh           # Validate all services
  teardown.sh             # Clean up (optional)
```

### Deployment Order
```
1. Namespace creation
2. Secrets
3. PostgreSQL (wait for ready)
4. Redis (wait for ready)
5. Kafka via Strimzi (wait for brokers ready)
6. Business services (auth, movie, booking, payment, notification)
7. API Gateway
8. Cinema Frontend
9. Ingress
```

## Related Code Files

### Files to Create
- `k8s/scripts/setup-minikube.sh`
- `k8s/scripts/create-secrets.sh`
- `k8s/scripts/deploy.sh`
- `k8s/scripts/smoke-test.sh`

### Files to Modify
- None

## Implementation Steps

### Step 1: setup-minikube.sh
```bash
#!/bin/bash
set -euo pipefail

echo "=== Starting Minikube ==="
minikube start --memory=8192 --cpus=4 --driver=docker

echo "=== Enabling addons ==="
minikube addons enable ingress
minikube addons enable metrics-server
minikube addons enable registry

echo "=== Installing Strimzi Kafka Operator ==="
kubectl create namespace strimzi-system --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f 'https://strimzi.io/install/latest?namespace=strimzi-system' -n strimzi-system
kubectl wait --for=condition=Ready pod -l name=strimzi-cluster-operator -n strimzi-system --timeout=120s

echo "=== Adding /etc/hosts entries ==="
MINIKUBE_IP=$(minikube ip)
echo "Add to /etc/hosts: ${MINIKUBE_IP} cinema-dev.local cinema.local"

echo "=== Setup complete ==="
```

### Step 2: create-secrets.sh
```bash
#!/bin/bash
set -euo pipefail

NAMESPACE=${1:-cinema-dev}

echo "=== Creating secrets in namespace: ${NAMESPACE} ==="
kubectl create namespace ${NAMESPACE} --dry-run=client -o yaml | kubectl apply -f -

kubectl create secret generic cinema-secrets \
  --namespace=${NAMESPACE} \
  --from-literal=JWT_SECRET="${JWT_SECRET:-kBJb8FEOvTCWEcfZB6RLMM5BLoI8p0FWOWEu7FSZBYn+ItVi7mHRePYCvum5Ic6l4M2nFw+kdl8du99Bxnb7zg==}" \
  --from-literal=POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}" \
  --from-literal=POSTGRES_USER="${POSTGRES_USER:-postgres}" \
  --from-literal=REDIS_PASSWORD="${REDIS_PASSWORD:-}" \
  --from-literal=STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-sk_test_placeholder}" \
  --from-literal=STRIPE_WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-whsec_placeholder}" \
  --from-literal=MAIL_USERNAME="${MAIL_USERNAME:-test@gmail.com}" \
  --from-literal=MAIL_PASSWORD="${MAIL_PASSWORD:-placeholder}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "=== Secrets created ==="
```

### Step 3: deploy.sh
```bash
#!/bin/bash
set -euo pipefail

OVERLAY=${1:-dev}
NAMESPACE="cinema-${OVERLAY}"

echo "=== Deploying to ${NAMESPACE} ==="

# Build images using Minikube Docker daemon
echo "=== Configuring Docker to use Minikube ==="
eval $(minikube docker-env)

# Build Maven (shared libs first, then services)
echo "=== Building Maven modules ==="
mvn clean install -pl kafka-events,jwt-auth-spring-boot-autoconfigure,jwt-auth-spring-boot-starter -DskipTests
mvn package -pl auth-service,movie-service,booking-service,payment-service,notification-service,api-gateway -DskipTests

# Build Docker images
echo "=== Building Docker images ==="
SERVICES=(auth-service movie-service booking-service payment-service notification-service api-gateway)
for svc in "${SERVICES[@]}"; do
  if [ "$svc" = "auth-service" ]; then
    docker build -f Dockerfile -t cinema-${svc}:latest .
  else
    docker build -f ${svc}/Dockerfile -t cinema-${svc}:latest ${svc}/
  fi
done
docker build -f cinema-frontend/Dockerfile -t cinema-frontend:latest cinema-frontend/

# Apply Kustomize overlay
echo "=== Applying K8s manifests ==="
kubectl apply -k k8s/overlays/${OVERLAY}/

# Wait for infrastructure
echo "=== Waiting for PostgreSQL ==="
kubectl wait --for=condition=Ready pod -l app=postgres -n ${NAMESPACE} --timeout=120s

echo "=== Waiting for Redis ==="
kubectl wait --for=condition=Ready pod -l app=redis -n ${NAMESPACE} --timeout=60s

echo "=== Waiting for Kafka ==="
kubectl wait kafka/local-kafka --for=condition=Ready -n ${NAMESPACE} --timeout=300s 2>/dev/null || echo "Kafka CR not ready yet, waiting..."

# Wait for services
echo "=== Waiting for services ==="
for svc in "${SERVICES[@]}" cinema-frontend; do
  kubectl rollout status deployment/${svc} -n ${NAMESPACE} --timeout=180s
done

echo "=== Deployment complete ==="
```

### Step 4: smoke-test.sh
```bash
#!/bin/bash
set -euo pipefail

NAMESPACE=${1:-cinema-dev}

echo "=== Smoke Testing (namespace: ${NAMESPACE}) ==="

SERVICES=("auth-service:8081" "movie-service:8082" "booking-service:8083" "payment-service:8084" "notification-service:8085" "api-gateway:8080")

FAILED=0
for entry in "${SERVICES[@]}"; do
  SVC=$(echo $entry | cut -d: -f1)
  PORT=$(echo $entry | cut -d: -f2)

  echo -n "Testing ${SVC}... "
  STATUS=$(kubectl exec -n ${NAMESPACE} deploy/${SVC} -- wget -qO- -T5 http://localhost:${PORT}/actuator/health 2>/dev/null | grep -o '"status":"[A-Z]*"' | head -1)

  if echo "$STATUS" | grep -q "UP"; then
    echo "OK"
  else
    echo "FAILED (${STATUS:-no response})"
    FAILED=$((FAILED + 1))
  fi
done

# Test via Ingress (if available)
MINIKUBE_IP=$(minikube ip 2>/dev/null || echo "")
if [ -n "$MINIKUBE_IP" ]; then
  echo -n "Testing Ingress (cinema-dev.local)... "
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 5 http://cinema-dev.local/ 2>/dev/null || echo "000")
  if [ "$HTTP_CODE" = "200" ]; then
    echo "OK"
  else
    echo "FAILED (HTTP ${HTTP_CODE})"
    FAILED=$((FAILED + 1))
  fi
fi

echo ""
if [ $FAILED -eq 0 ]; then
  echo "=== All smoke tests passed ==="
else
  echo "=== ${FAILED} test(s) failed ==="
  exit 1
fi
```

### Step 5: Troubleshooting Section (in deploy.sh comments or README)

Common issues:
1. **Pod stuck in Pending**: `kubectl describe pod <name> -n cinema-dev` — likely resource constraints. Increase Minikube resources.
2. **CrashLoopBackOff**: `kubectl logs <pod> -n cinema-dev` — check Java startup errors. Common: DB not ready, wrong config.
3. **ImagePullBackOff**: Image not in Minikube Docker. Re-run `eval $(minikube docker-env)` and rebuild.
4. **Kafka CR stuck**: Strimzi operator not installed. Run `setup-minikube.sh`.
5. **Ingress 404**: Addon not enabled or /etc/hosts not configured.
6. **Services can't find each other**: Check namespace, service names, and SPRING_PROFILES_ACTIVE=k8s.

## Todo List

- [ ] Create setup-minikube.sh
- [ ] Create create-secrets.sh
- [ ] Create deploy.sh
- [ ] Create smoke-test.sh
- [ ] Make all scripts executable (`chmod +x`)
- [ ] Test full flow: setup -> secrets -> deploy -> smoke test
- [ ] Document troubleshooting in script comments

## Success Criteria
- `setup-minikube.sh` starts Minikube with all addons and Strimzi
- `create-secrets.sh cinema-dev` creates secrets in correct namespace
- `deploy.sh dev` deploys entire stack to cinema-dev namespace
- `smoke-test.sh cinema-dev` reports all services healthy
- Scripts are idempotent (safe to re-run)

## Risk Assessment
- **Minikube resource limits**: 8 Spring Boot services + PG + Redis + Kafka may need >8GB. Mitigation: monitor with `kubectl top` and adjust.
- **Strimzi version compatibility**: Pinned URL may break. Mitigation: document specific version in script.
- **Docker daemon context**: `eval $(minikube docker-env)` only works in current shell. Mitigation: deploy.sh sets it internally.

## Security Considerations
- `create-secrets.sh` reads from environment variables (not files committed to git)
- Default secret values are development-only placeholders
- Production deployment should use different secret values
- Scripts should not echo secret values to console

## Next Steps
- After all phases complete: full end-to-end validation
- Future: Jenkins runs these scripts as part of initial cluster bootstrapping
- Future: Add monitoring stack (Prometheus/Grafana) to K8s manifests
