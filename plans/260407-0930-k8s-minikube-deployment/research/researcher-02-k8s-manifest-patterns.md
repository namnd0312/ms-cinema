# K8s Manifest Patterns for Minikube Multi-Service Deployment

## 1. Namespace Creation Manifest
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ms-cinema
```
**Pattern**: Always namespace to isolate application resources and enable multi-tenant deployments.

---

## 2. ConfigMap & Secret Patterns

**ConfigMap** (shared environment variables, config files):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: ms-cinema
data:
  DATABASE_HOST: "postgres-service"
  DATABASE_PORT: "5432"
  LOG_LEVEL: "INFO"
```

**Secret** (sensitive data - base64 encoded at rest):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
  namespace: ms-cinema
type: Opaque
stringData:
  username: postgres
  password: "secretpassword"
```

**Pod reference**:
```yaml
envFrom:
  - configMapRef:
      name: app-config
  - secretRef:
      name: db-credentials
```
⚠️ Secrets are base64 only—use external managers (Vault, Sealed Secrets) for production.

---

## 3. Deployment Manifest Best Practices

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ms-cinema
  labels:
    app: order-service
    version: v1
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service  # MUST match selector
    spec:
      containers:
      - name: order-service
        image: ms-cinema/order-service:latest
        imagePullPolicy: Never  # CRITICAL for Minikube local images
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
```

**Key patterns**:
- Labels must match selector exactly (immutable after creation)
- `imagePullPolicy: Never` for local Minikube builds
- Recommended labels: `app`, `version`, `component` for tool discovery
- Don't overlap selectors across controllers

---

## 4. Service Types for Minikube

**ClusterIP** (internal service-to-service):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ms-cinema
spec:
  type: ClusterIP
  selector:
    app: order-service
  ports:
  - port: 80
    targetPort: 8080
```

**NodePort** (external access on Minikube):
```yaml
apiVersion: v1
kind: Service
metadata:
  name: api-gateway
  namespace: ms-cinema
spec:
  type: NodePort
  selector:
    app: api-gateway
  ports:
  - port: 80
    targetPort: 8080
    nodePort: 30080  # Optional; K8s auto-assigns if omitted
```

**Access**: `minikube service api-gateway -n ms-cinema` or `minikube ip`:30080

---

## 5. Deploy Script Pattern

```bash
#!/bin/bash
set -e

NAMESPACE="ms-cinema"
DOCKER_DRIVER=$(minikube docker-env | grep DOCKER_HOST)

echo "Building Maven JARs..."
mvn clean package -DskipTests

echo "Configuring Minikube Docker daemon..."
eval $(minikube docker-env)

echo "Building Docker images..."
docker build -t ms-cinema/order-service:latest ./order-service
docker build -t ms-cinema/payment-service:latest ./payment-service
docker build -t ms-cinema/api-gateway:latest ./api-gateway

echo "Creating namespace..."
kubectl apply -f k8s/namespace.yaml

echo "Applying manifests in dependency order..."
kubectl apply -f k8s/configmap.yaml -n $NAMESPACE
kubectl apply -f k8s/secret.yaml -n $NAMESPACE
kubectl apply -f k8s/payment-service-deployment.yaml -n $NAMESPACE
kubectl apply -f k8s/order-service-deployment.yaml -n $NAMESPACE
kubectl apply -f k8s/api-gateway-deployment.yaml -n $NAMESPACE

echo "Waiting for deployments..."
kubectl rollout status deployment/payment-service -n $NAMESPACE
kubectl rollout status deployment/order-service -n $NAMESPACE
kubectl rollout status deployment/api-gateway -n $NAMESPACE

echo "Services deployed successfully!"
kubectl get pods -n $NAMESPACE
```

**Key patterns**:
- Apply manifests in dependency order: ConfigMap/Secrets → Databases → Services → Gateway
- Use `minikube docker-env` to build directly in Minikube's Docker daemon
- Use `kubectl rollout status` to wait for deployments (avoids race conditions)
- `set -e` stops script on first error

---

## 6. Minikube-Specific Details

| Command | Purpose |
|---------|---------|
| `eval $(minikube docker-env)` | Use Minikube's Docker daemon for local builds |
| `minikube service <name> -n ns` | Get NodePort service URL (auto-opens browser) |
| `minikube service <name> -n ns --url` | Get URL only (no browser) |
| `minikube tunnel` | Access LoadBalancer services (requires separate terminal) |
| `minikube ip` | Get cluster IP for direct NodePort access |

---

## Key Constraints & Gotchas

1. **imagePullPolicy**: Must be `Never` or `IfNotPresent` for local Minikube images
2. **Label selectors**: Immutable after Deployment creation—must delete & recreate to change
3. **ConfigMap size**: Max 1 MiB per ConfigMap
4. **Secrets**: Base64 encoded only—NOT encrypted. Use external secret managers in production.
5. **Docker daemon**: Always `eval $(minikube docker-env)` before building images
6. **No overlapping selectors**: Don't share selectors across Deployments/StatefulSets

---

## Recommended File Structure

```
k8s/
├── namespace.yaml
├── configmap.yaml
├── secret.yaml
├── payment-service-deployment.yaml
├── payment-service-service.yaml
├── order-service-deployment.yaml
├── order-service-service.yaml
├── api-gateway-deployment.yaml
├── api-gateway-service.yaml
└── deploy.sh
```

Apply as: `./deploy.sh` or individual files with `kubectl apply -f k8s/`

---

## Sources
- [Kubernetes Configuration Good Practices](https://kubernetes.io/blog/2025/11/25/configuration-good-practices/)
- [Minikube: Pushing images](https://minikube.sigs.k8s.io/docs/handbook/pushing/)
- [Using Local Images With Minikube](https://octopus.com/blog/local-images-minikube/)
- [Kubernetes Deployments](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/)
- [Kubernetes Labels and Selectors](https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/)
- [Minikube: Accessing apps](https://minikube.sigs.k8s.io/docs/handbook/accessing/)
- [Eclipse JKube: kubernetes-maven-plugin](https://eclipse.dev/jkube/docs/kubernetes-maven-plugin/)
