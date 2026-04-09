# Phase 6: Migrate from Minikube to OrbStack

## Context Links
- [Plan Overview](./plan.md)
- [Phase 5: Deploy Scripts](./phase-05-deploy-scripts-and-testing.md)

## Overview
- **Date:** 2026-04-09
- **Priority:** High
- **Implementation Status:** Completed
- **Review Status:** Pending
- **Description:** Update deploy-all.sh and teardown.sh to use OrbStack instead of Minikube. K8s manifests are standard and need no changes.

## Key Insights
- OrbStack provides native K8s on macOS — lighter than Minikube
- Native LoadBalancer support — no `minikube tunnel` needed
- Services accessible via `<svc>.<namespace>.svc.cluster.local` or `.k8s.orb.local`
- Docker images built in OrbStack context are directly available to K8s (same daemon)
- `imagePullPolicy: Never` continues to work — no registry needed
- All K8s manifests (deployments, services, configmaps) are standard and unchanged

## Requirements

### Functional
- Enable K8s in OrbStack
- Switch Docker context to OrbStack
- Update deploy-all.sh: remove Minikube commands, add OrbStack prereqs
- Update teardown.sh: remove Minikube reference
- All 12 pods deploy and reach Ready state
- Services accessible without tunnel

### Non-Functional
- Scripts remain idempotent
- Per-service CI/CD pattern still works

## Architecture
No architecture changes. Only the runtime platform changes from Minikube VM to OrbStack native K8s.

**OrbStack advantages:**
- Shared Docker daemon (no `eval $(minikube docker-env)`)
- Native LoadBalancer IPs on macOS
- Lower memory/CPU overhead
- Automatic `.k8s.orb.local` DNS for services

## Related Code Files

### Files to Modify
- `k8s/deploy-all.sh` — replace Minikube prereqs/commands with OrbStack
- `k8s/teardown.sh` — remove Minikube reference in echo message

### Files Unchanged
- All `k8s/*/deployment.yml` — standard K8s, no Minikube-specific config
- `k8s/base/configmap.yml`, `k8s/base/secrets.yml`, `k8s/base/namespace.yml`

## Implementation Steps

### Step 1: Enable OrbStack K8s
```bash
# Enable Kubernetes in OrbStack (one-time setup)
orbctl start k8s
# Verify
kubectl config get-contexts
kubectl get nodes
```

### Step 2: Switch Docker Context
```bash
# Switch Docker to OrbStack
docker context use orbstack
# Verify
docker info | grep "Context"
```

### Step 3: Update `k8s/deploy-all.sh`

**Changes:**
1. Prerequisites check: replace `minikube` with `orbctl`, remove minikube status check
2. Add OrbStack K8s check: `kubectl get nodes`
3. Remove `eval $(minikube docker-env)` — OrbStack Docker context handles this
4. Add `docker context use orbstack` to ensure correct context
5. Update summary: remove "minikube tunnel" instruction, show OrbStack access URLs
6. Add `.k8s.orb.local` DNS info in summary

**Updated prerequisite block:**
```bash
# [1/7] Prerequisites
for cmd in orbctl kubectl mvn docker; do
  command -v "$cmd" &>/dev/null || { echo -e "${RED}ERROR: $cmd not installed${NC}"; exit 1; }
done
# Ensure OrbStack K8s is running
if ! kubectl get nodes 2>/dev/null | grep -qi "running"; then
  echo -e "${YELLOW}Starting OrbStack Kubernetes...${NC}"
  orbctl start k8s
fi
# Ensure Docker context is OrbStack
docker context use orbstack 2>/dev/null
```

**Updated build block (remove `eval $(minikube docker-env)`):**
```bash
# [2/7] Build Maven + Docker images
cd "$PROJECT_ROOT"
mvn clean package -DskipTests -q
for svc in "${SERVICES[@]}"; do
  docker build -t "ms-cinema/${svc}:latest" "${PROJECT_ROOT}/${svc}" -q
done
```

**Updated summary block:**
```bash
echo -e "${GREEN}Access:${NC}"
echo "  Frontend:    http://localhost"
echo "  API Gateway: http://localhost:8080"
echo "  Swagger UI:  http://localhost:8080/swagger-ui.html"
echo ""
echo -e "${GREEN}OrbStack DNS (alternative):${NC}"
echo "  kubectl get svc -n ms-cinema"
```

### Step 4: Update `k8s/teardown.sh`
Change final echo from "Run 'minikube stop' to stop Minikube." to "Run 'orbctl stop k8s' to stop K8s."

### Step 5: Test Full Deployment
```bash
# Clean state test
./k8s/teardown.sh
./k8s/deploy-all.sh
kubectl get pods -n ms-cinema
kubectl get svc -n ms-cinema
# Verify LoadBalancer gets external IP (OrbStack assigns real IPs)
```

### Step 6: Verify Access
```bash
# No tunnel needed! Direct access:
curl http://localhost:8080/actuator/health
# Or via OrbStack DNS if LoadBalancer IP assigned
```

## Todo List
- [ ] Enable K8s in OrbStack (`orbctl start k8s`)
- [ ] Switch Docker context to OrbStack
- [ ] Update `k8s/deploy-all.sh` — remove Minikube, add OrbStack
- [ ] Update `k8s/teardown.sh` — remove Minikube reference
- [ ] Run `./k8s/deploy-all.sh` from clean state
- [ ] Verify all 12 pods Running/Ready
- [ ] Verify LoadBalancer services get IPs without tunnel
- [ ] Test E2E: login → browse → book → pay → notification
- [ ] Verify per-service independent deploy still works

## Success Criteria
- `kubectl get nodes` shows Running
- `./k8s/deploy-all.sh` completes without errors on OrbStack
- All 12 pods Running/Ready
- Services accessible via localhost (no tunnel)
- `./k8s/teardown.sh` cleans up everything
- Per-service `kubectl apply` works independently

## Risk Assessment
- **Risk:** Docker images not visible to OrbStack K8s
  - **Mitigation:** Ensure `docker context use orbstack` before building; `imagePullPolicy: Never` ensures K8s uses local images
- **Risk:** OrbStack LoadBalancer IP differs from localhost
  - **Mitigation:** Check `kubectl get svc -n ms-cinema` for actual IPs; OrbStack typically maps to localhost
- **Risk:** OrbStack K8s resource limits differ from Minikube
  - **Mitigation:** OrbStack shares host resources dynamically — actually better than Minikube's fixed allocation

## Security Considerations
- No new secrets introduced
- Same K8s Secret/ConfigMap approach
- OrbStack runs locally — no external exposure

## Next Steps
- After successful OrbStack deployment:
  - Remove any remaining Minikube references from docs
  - Consider Ingress controller for better routing
  - GitHub Actions CI/CD per-service pipelines
