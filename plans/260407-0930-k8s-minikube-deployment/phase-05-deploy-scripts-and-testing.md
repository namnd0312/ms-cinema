# Phase 5: Deploy Scripts & Testing

## Context Links
- [Plan Overview](./plan.md)
- [Phase 2: Infrastructure](./phase-02-k8s-infrastructure-postgresql-kafka-redis-zipkin.md)
- [Phase 3: Service Manifests](./phase-03-k8s-base-configs-and-per-service-manifests.md)

## Overview
- **Priority:** High
- **Status:** Pending
- **Description:** Create deploy-all.sh and teardown.sh. Deploy order: base → infra → wait → services → frontend. Support per-service CI/CD deploy.

## Key Insights
- No Docker Compose needed — everything on K8s
- Deploy order: namespace/configs → infra (postgresql, kafka, redis, zipkin) → wait for infra → all services (parallel) → frontend
- No eureka or config-server in deploy sequence
- Each service independently deployable for CI/CD
- Total pods: 4 infra + 7 services + 1 frontend = 12 pods
- Estimated memory: ~5.5GB (recommend 10GB Minikube)

## Implementation Steps

### Step 1: Create `k8s/deploy-all.sh`
```bash
#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NAMESPACE="ms-cinema"
WAIT_TIMEOUT="180s"

echo -e "${GREEN}=== ms-cinema Full K8s Deployment ===${NC}"

# [1/7] Prerequisites
echo -e "\n${YELLOW}[1/7] Checking prerequisites...${NC}"
for cmd in minikube kubectl mvn docker; do
  command -v "$cmd" &>/dev/null || { echo -e "${RED}ERROR: $cmd not installed${NC}"; exit 1; }
done
if ! minikube status --format='{{.Host}}' 2>/dev/null | grep -q "Running"; then
  echo -e "${RED}ERROR: Minikube not running. Start with: minikube start --cpus=2 --memory=6144${NC}"
  exit 1
fi

# [2/7] Build Maven + Docker images
echo -e "\n${YELLOW}[2/7] Building Maven + Docker images...${NC}"
eval $(minikube docker-env)
cd "$PROJECT_ROOT"
mvn clean package -DskipTests -q

SERVICES=(api-gateway auth-service movie-service booking-service payment-service notification-service audit-service cinema-frontend)
for svc in "${SERVICES[@]}"; do
  echo "  Building ms-cinema/${svc}..."
  docker build -t "ms-cinema/${svc}:latest" "${PROJECT_ROOT}/${svc}" -q
done

# [3/7] Apply base configs
echo -e "\n${YELLOW}[3/7] Applying namespace & configs...${NC}"
kubectl apply -f "${SCRIPT_DIR}/base/"

# [4/7] Deploy infrastructure
echo -e "\n${YELLOW}[4/7] Deploying infrastructure...${NC}"
for infra in postgresql kafka redis zipkin; do
  echo "  Deploying ${infra}..."
  kubectl apply -f "${SCRIPT_DIR}/infra/${infra}/"
done

echo "  Waiting for infrastructure..."
for infra in postgresql kafka redis zipkin; do
  kubectl wait --for=condition=Ready pod -l "app.kubernetes.io/name=${infra}" \
    -n "$NAMESPACE" --timeout="$WAIT_TIMEOUT" || \
    echo -e "  ${RED}WARNING: ${infra} not ready within timeout${NC}"
done
echo -e "  ${GREEN}Infrastructure ready!${NC}"

# [5/7] Deploy services (parallel)
echo -e "\n${YELLOW}[5/7] Deploying services...${NC}"
for svc in api-gateway auth-service movie-service booking-service payment-service notification-service audit-service; do
  kubectl apply -f "${SCRIPT_DIR}/${svc}/"
done

# [6/7] Wait for services
echo -e "\n${YELLOW}[6/7] Waiting for services...${NC}"
for svc in api-gateway auth-service movie-service booking-service payment-service notification-service audit-service; do
  kubectl wait --for=condition=Ready pod -l "app.kubernetes.io/name=${svc}" \
    -n "$NAMESPACE" --timeout="$WAIT_TIMEOUT" 2>/dev/null || \
    echo -e "  ${RED}WARNING: ${svc} not ready${NC}"
done

# [7/7] Deploy frontend
echo -e "\n${YELLOW}[7/7] Deploying frontend...${NC}"
kubectl apply -f "${SCRIPT_DIR}/cinema-frontend/"
kubectl wait --for=condition=Ready pod -l app.kubernetes.io/name=cinema-frontend \
  -n "$NAMESPACE" --timeout="60s" 2>/dev/null || true

# Summary
echo -e "\n${GREEN}=== Deployment Complete ===${NC}"
kubectl get pods -n "$NAMESPACE"
echo ""
echo -e "${GREEN}Access (run 'minikube tunnel' in separate terminal):${NC}"
echo "  Frontend:    http://localhost"
echo "  API Gateway: http://localhost:8080"
echo "  Swagger UI:  http://localhost:8080/swagger-ui.html"
echo ""
echo -e "${YELLOW}Per-service redeploy (CI/CD):${NC}"
echo "  kubectl apply -f k8s/<service-name>/"
echo "  kubectl rollout restart deployment/<service-name> -n ms-cinema"
```

### Step 2: Create `k8s/teardown.sh`
```bash
#!/usr/bin/env bash
set -euo pipefail
NAMESPACE="ms-cinema"
echo "Deleting namespace ${NAMESPACE} (all resources)..."
kubectl delete namespace "$NAMESPACE" --timeout=120s 2>/dev/null || echo "Namespace not found."
echo "Done. PVCs deleted with namespace. Run 'minikube stop' to stop Minikube."
```

### Step 3: Per-Service CI/CD Deploy Pattern
```bash
# Independent service deploy:
eval $(minikube docker-env)
docker build -t ms-cinema/<service>:latest ./<service>
kubectl apply -f k8s/<service>/deployment.yml
kubectl rollout restart deployment/<service> -n ms-cinema
kubectl rollout status deployment/<service> -n ms-cinema --timeout=120s
```

### Step 4: Cleanup Old Files
Remove if they exist:
- `k8s/namespace.yml`, `k8s/configmap.yml`, `k8s/secrets.yml` (moved to `k8s/base/`)
- `k8s/eureka-server.yml`, `k8s/config-server.yml` (removed)
- All old flat `k8s/*.yml` service files
- `k8s/k8s-deploy.sh`, `k8s/k8s-teardown.sh` (renamed)

### Step 5: Resource Estimation (minimal — study/research)
| Component | Count | Memory Limit | Total |
|-----------|-------|-------------|-------|
| PostgreSQL | 1 | 256Mi | 256Mi |
| Kafka | 1 | 512Mi | 512Mi |
| Redis | 1 | 64Mi | 64Mi |
| Zipkin | 1 | 128Mi | 128Mi |
| api-gateway | 1 | 256Mi | 256Mi |
| Business services | 6 | 256Mi | 1.5Gi |
| Frontend | 1 | 64Mi | 64Mi |
| **Total** | **12** | | **~2.8GB** |

No resource requests — best-effort QoS. Actual usage will be lower than limits.
**Recommended Minikube: 6GB RAM, 2 CPUs**

## Todo List
- [ ] Create `k8s/deploy-all.sh`
- [ ] Create `k8s/teardown.sh`
- [ ] `chmod +x` both scripts
- [ ] Remove old flat K8s files
- [ ] Test full deploy from clean state
- [ ] Test teardown + re-deploy (idempotent)
- [ ] Test per-service independent deploy
- [ ] Verify all 12 pods Running
- [ ] Test with `minikube tunnel`
- [ ] End-to-end: login → browse → book → pay → notification

## Success Criteria
- `./k8s/deploy-all.sh` completes without errors
- All 12 pods Running/Ready within 7 minutes
- Per-service `kubectl apply -f k8s/<service>/` works independently
- `./k8s/teardown.sh` removes everything
- E2E flow works through browser

## Risk Assessment
- **Risk:** 12 pods exceed Minikube memory
  - **Mitigation:** Minimal limits (~2.8GB total), 6GB Minikube sufficient; monitor with `kubectl top pods`
- **Risk:** Kafka startup slow (30-60s), services timeout
  - **Mitigation:** Init containers with `nc -z` wait; Kafka readiness probe
- **Risk:** PostgreSQL PVC prevents clean redeploy
  - **Mitigation:** Teardown deletes namespace (includes PVCs); fresh data on redeploy

## Security Considerations
- Scripts contain no secrets
- Safe to commit to git (secrets.yml in .gitignore)

## Next Steps
- After successful deployment, consider:
  - GitHub Actions CI/CD per-service pipelines
  - Helm charts for parameterized deployments
  - Ingress controller instead of LoadBalancer
  - Optional monitoring stack (Prometheus, Grafana, Loki) as Phase 6
