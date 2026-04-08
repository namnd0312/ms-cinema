# Phase 4: Frontend & External Access

## Context Links
- [Plan Overview](./plan.md)
- [Phase 3: Service Manifests](./phase-03-k8s-base-configs-and-per-service-manifests.md)
- [cinema-frontend/nginx.conf](/cinema-frontend/nginx.conf)

## Overview
- **Priority:** Medium
- **Status:** Pending
- **Description:** Deploy Angular frontend and expose api-gateway + frontend via LoadBalancer + `minikube tunnel`

## Key Insights
- cinema-frontend: multi-stage build (node:20 + nginx:alpine), no Spring Boot
- nginx.conf proxies `/api/` → `api-gateway:8080`, `/ws/` → `booking-service:8083` — K8s DNS resolves
- LoadBalancer + `minikube tunnel` for clean `localhost` access
- No init containers needed for frontend
- No Eureka/config-server references in frontend

## Implementation Steps

### Step 1: Create `k8s/cinema-frontend/deployment.yml`
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cinema-frontend
  namespace: ms-cinema
  labels:
    app.kubernetes.io/name: cinema-frontend
    app.kubernetes.io/part-of: ms-cinema
    app.kubernetes.io/component: frontend
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: cinema-frontend
  template:
    metadata:
      labels:
        app.kubernetes.io/name: cinema-frontend
        app.kubernetes.io/part-of: ms-cinema
    spec:
      containers:
        - name: cinema-frontend
          image: ms-cinema/cinema-frontend:latest
          imagePullPolicy: Never
          ports:
            - containerPort: 80
          resources:
            limits: { memory: "64Mi", cpu: "50m" }
          readinessProbe:
            httpGet: { path: /, port: 80 }
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /, port: 80 }
            initialDelaySeconds: 10
            periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: cinema-frontend
  namespace: ms-cinema
spec:
  type: LoadBalancer
  selector:
    app.kubernetes.io/name: cinema-frontend
  ports:
    - port: 80
      targetPort: 80
```

### Step 2: Update api-gateway Service to LoadBalancer
In `k8s/api-gateway/deployment.yml`, Service type:
```yaml
spec:
  type: LoadBalancer
  selector:
    app.kubernetes.io/name: api-gateway
  ports:
    - port: 8080
      targetPort: 8080
```

### Step 3: Build + Access
```bash
eval $(minikube docker-env)
docker build -t ms-cinema/cinema-frontend:latest ./cinema-frontend

# Separate terminal:
minikube tunnel

# Access:
# Frontend: http://localhost
# API Gateway: http://localhost:8080
# Swagger: http://localhost:8080/swagger-ui.html
```

### Step 4: nginx.conf Compatibility
Existing nginx.conf uses short service names — resolves via K8s DNS. **No changes needed.**

## Todo List
- [ ] Create `k8s/cinema-frontend/deployment.yml`
- [ ] Ensure `k8s/api-gateway/deployment.yml` uses LoadBalancer
- [ ] Build frontend Docker image
- [ ] Test with `minikube tunnel`
- [ ] Verify frontend + API proxy + WebSocket

## Success Criteria
- Frontend loads in browser via `localhost`
- API calls proxied correctly
- `kubectl get svc -n ms-cinema` shows EXTERNAL-IP for LoadBalancer services

## Risk Assessment
- **Risk:** Port conflict with host services
  - **Mitigation:** Stop conflicting local services or use different ports
- **Risk:** CORS issues
  - **Mitigation:** api-gateway allows all origins in dev

## Next Steps
- Phase 5: Deploy scripts for full automation
