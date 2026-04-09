#!/usr/bin/env bash
set -euo pipefail

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
NAMESPACE="ms-cinema"
WAIT_TIMEOUT="300s"

echo -e "${GREEN}=== ms-cinema Full K8s Deployment ===${NC}"

# [1/7] Prerequisites
echo -e "\n${YELLOW}[1/7] Checking prerequisites...${NC}"
for cmd in orbctl kubectl mvn docker; do
  command -v "$cmd" &>/dev/null || { echo -e "${RED}ERROR: $cmd not installed${NC}"; exit 1; }
done
# Ensure OrbStack K8s is running
if ! kubectl get nodes &>/dev/null; then
  echo -e "${YELLOW}Starting OrbStack Kubernetes...${NC}"
  orbctl start k8s
  echo "  Waiting for K8s to be ready..."
  until kubectl get nodes &>/dev/null; do sleep 2; done
fi
# Ensure Docker context is OrbStack
docker context use orbstack 2>/dev/null

# [2/7] Build Maven + Docker images
echo -e "\n${YELLOW}[2/7] Building Maven + Docker images...${NC}"
cd "$PROJECT_ROOT"
mvn clean package -DskipTests -q

SERVICES=(api-gateway auth-service movie-service booking-service payment-service notification-service audit-service cinema-frontend)
for svc in "${SERVICES[@]}"; do
  echo "  Building ms-cinema/${svc}..."
  docker build -t "ms-cinema/${svc}:latest" "${PROJECT_ROOT}/${svc}" -q
done

# [3/7] Apply base configs (namespace first, then configs)
echo -e "\n${YELLOW}[3/7] Applying namespace & configs...${NC}"
kubectl apply -f "${SCRIPT_DIR}/base/namespace.yml"
kubectl wait --for=jsonpath='{.status.phase}'=Active namespace/"$NAMESPACE" --timeout=30s
kubectl apply -f "${SCRIPT_DIR}/base/"

# [4/7] Deploy infrastructure
echo -e "\n${YELLOW}[4/7] Deploying infrastructure...${NC}"
for infra in postgresql kafka redis zipkin loki promtail grafana; do
  echo "  Deploying ${infra}..."
  kubectl apply -f "${SCRIPT_DIR}/infra/${infra}/"
done

echo "  Waiting for infrastructure..."
for infra in postgresql kafka redis zipkin loki grafana; do
  kubectl wait --for=condition=Ready pod -l "app.kubernetes.io/name=${infra}" \
    -n "$NAMESPACE" --timeout="$WAIT_TIMEOUT" || \
    echo -e "  ${RED}WARNING: ${infra} not ready within timeout${NC}"
done
# Promtail is a DaemonSet — wait separately
kubectl wait --for=condition=Ready pod -l "app.kubernetes.io/name=promtail" \
  -n "$NAMESPACE" --timeout="$WAIT_TIMEOUT" || \
  echo -e "  ${RED}WARNING: promtail not ready within timeout${NC}"
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
echo -e "${GREEN}Access:${NC}"
echo "  Frontend:    http://localhost"
echo "  API Gateway: http://localhost:8080"
echo "  Swagger UI:  http://localhost:8080/swagger-ui.html"
echo "  Grafana:     http://localhost:3000"
echo ""
echo -e "${GREEN}OrbStack DNS (alternative):${NC}"
echo "  kubectl get svc -n ms-cinema"
echo ""
echo -e "${YELLOW}Per-service redeploy (CI/CD):${NC}"
echo "  kubectl apply -f k8s/<service-name>/"
echo "  kubectl rollout restart deployment/<service-name> -n ms-cinema"
