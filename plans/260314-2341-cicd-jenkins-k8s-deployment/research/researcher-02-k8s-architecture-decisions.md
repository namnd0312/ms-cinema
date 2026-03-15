# K8s Architecture Decisions: Spring Boot Microservices Deployment

**Date:** 2026-03-14
**Focus:** Eureka, ConfigMaps, Gateways, Kustomize, Kafka, PostgreSQL, Resource Limits
**Scope:** Local clusters (Minikube/Kind)

---

## 1. Eureka vs K8s Native Discovery

**Recommendation: DROP EUREKA, use K8s DNS + spring-cloud-kubernetes-discovery**

**Rationale:**
- Spring Cloud Netflix officially end-of-life (2023). Spring Cloud team removed Netflix integrations.
- K8s Service DNS (`<service-name>.<namespace>.svc.cluster.local`) provides server-side discovery out-of-box.
- `spring-cloud-kubernetes-discovery` auto-discovers services via K8s API.

**Implementation:**
- Remove Eureka server & client dependencies.
- Add `spring-cloud-starter-kubernetes-client` (handles discovery + config).
- Services resolve via K8s DNS; no additional registry overhead.

**Caveat:** Ensure ServiceAccount has `list/get` permissions on Endpoints/Services.

---

## 2. Config Server vs ConfigMaps

**Recommendation: REPLACE with K8s ConfigMaps + Secrets (no external Config Server)**

**Rationale:**
- ConfigMaps native to K8s, zero external dependency.
- Mount as volume or inject via environment variables.
- Profile-specific configs: use separate ConfigMaps per profile (dev/prod).

**Implementation Pattern:**
```yaml
# ConfigMap for app config
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  application.yml: |
    spring:
      profiles:
        active: dev
      datasource:
        url: jdbc:postgresql://postgres:5432/mydb
  application-prod.yml: |
    # Override for prod
```

Mount via volume in Pod spec:
```yaml
volumeMounts:
  - name: config
    mountPath: /config
    readOnly: true
volumes:
  - name: config
    configMap:
      name: app-config
```

Set Spring env: `SPRING_CONFIG_LOCATION=file:/config/application.yml`

---

## 3. API Gateway vs Ingress

**Recommendation: Use NGINX Ingress ONLY (drop Spring Cloud Gateway)**

**Rationale:**
- Spring Cloud Gateway: lower K8s integration, poor native service discovery.
- NGINX Ingress: production-ready, handles HTTP/HTTPS, SSE streaming natively.
- If REST + SSE coexist: NGINX routes both seamlessly (no special config needed).

**Warning:** NGINX Ingress formal deprecation announced; retirement planned March 2026. Evaluate Gateway API migration for future-proofing.

**For SSE:** NGINX supports streaming via `proxy_buffering: off` + `proxy_cache: off` annotations.

---

## 4. Kustomize Structure (8+ Microservices)

**Recommended Layout:**
```
k8s/
├── base/
│   ├── service-a/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   └── kustomization.yaml
│   ├── service-b/
│   └── ...
├── overlays/
│   ├── dev/
│   │   ├── kustomization.yaml (patches replicas=1, resource limits)
│   │   └── patches/
│   ├── prod/
│   │   ├── kustomization.yaml (patches replicas=3, resource limits higher)
│   │   └── patches/
│   └── test/
└── envconfig/
    ├── dev-config.yaml
    └── prod-config.yaml
```

**Kustomization Pattern:**
- Base: common labels, namespace, resource definitions.
- Overlays: `patchesStrategicMerge` for replicas/env vars, `replacements` for image tags.
- Set `spring.profiles.active` via `env` patches per overlay.

**Scaling:** Each microservice gets base + shared overlay patches. No duplication.

---

## 5. Kafka on K8s

**Recommendation: Strimzi Operator + KRaft mode (NOT raw StatefulSet)**

**Rationale:**
- Raw StatefulSet: error-prone, manual config, hard to scale/upgrade.
- Strimzi: operator-managed, automates provisioning, upgrades, config changes.
- KRaft: eliminates ZooKeeper dependency, simpler for local dev (dual-role nodes).

**For Local Dev (Minikube):**
```yaml
apiVersion: kafka.strimzi.io/v1beta2
kind: Kafka
metadata:
  name: local-kafka
spec:
  kafka:
    version: 3.6.0
    replicas: 1  # Single broker for Minikube
    storage:
      type: ephemeral
    config:
      process.roles: broker,controller  # KRaft dual-role
      node.id: 1
  cruiseControl:
    enabled: false  # Skip for local dev
```

Install Strimzi: `helm repo add strimzi && helm install strimzi strimzi/strimzi-kafka-operator`

---

## 6. PostgreSQL on K8s

**Recommendation: External managed DB (RDS/CloudSQL) for prod; StatefulSet + PVC for local dev**

**For Local Dev (Minikube):**
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 5Gi
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15
        env:
        - name: POSTGRES_DB
          valueFrom:
            configMapKeyRef:
              name: db-config
              key: db-name
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: [ReadWriteOnce]
      resources:
        requests:
          storage: 5Gi
```

**Multi-database Init:** Use init container with SQL scripts mounted via ConfigMap:
```yaml
initContainers:
- name: init-db
  image: postgres:15
  command: ["/bin/sh", "-c"]
  args: ["psql -f /scripts/init.sql"]
  volumeMounts:
  - name: init-scripts
    mountPath: /scripts
volumes:
- name: init-scripts
  configMap:
    name: db-init-scripts
```

---

## 7. Resource Limits for Minikube Spring Boot

**Recommended Defaults (adjust per service load):**

```yaml
resources:
  requests:
    memory: "256Mi"
    cpu: "250m"
  limits:
    memory: "512Mi"
    cpu: "500m"
```

**Rationale:**
- Minikube typically has 2-4 CPUs, 4-8GB RAM total.
- Spring Boot base footprint: ~150-200MB heap.
- 256Mi request = safe floor; 512Mi limit = reasonable cap for 8+ services.

**JVM Tuning for K8s:**
```yaml
env:
- name: JAVA_OPTS
  value: "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
```

This auto-adjusts heap to 75% of container memory limit (no manual Xmx needed).

**Monitoring:** Use `kubectl top pod` to validate actual usage; adjust overlays as needed.

---

## Summary Table

| Component | Decision | Rationale |
|-----------|----------|-----------|
| Service Discovery | K8s DNS + spring-cloud-kubernetes-discovery | Native, no Eureka overhead |
| Config | K8s ConfigMaps + Secrets | Built-in, zero external dependency |
| Gateway | NGINX Ingress only | Better K8s integration, SSE support |
| Package | Kustomize base + overlays | Scales to 8+ services, no duplication |
| Kafka | Strimzi + KRaft | Operator-managed, simpler than StatefulSet |
| PostgreSQL | StatefulSet + PVC (local), external (prod) | Dev simplicity, prod reliability |
| Resources | 256Mi request / 512Mi limit per service | Safe for Minikube; tune via overlays |

---

## Unresolved Questions

1. **Service mesh complexity:** Should Istio/Linkerd be considered for advanced traffic management? (Out of scope for local dev but relevant for multi-cluster scenarios.)
2. **ConfigMap size limits:** How to handle large configs (>1MB)? Solution: split into multiple ConfigMaps or use external storage.
3. **Persistent data in local dev:** How to preserve PostgreSQL data across pod restarts? Answer: hostPath volumes (Minikube only; use StorageClass in production).
4. **ArgoCD integration:** Should GitOps-based deployment be added? (Recommended but outside current research scope.)

---

## Sources

- [Service Discovery in 2025: Do You Still Need Eureka with Spring Boot Microservices?](https://medium.com/@himanshu675/service-discovery-in-2025-do-you-still-need-eureka-with-spring-boot-microservices-f909f1e350de)
- [Spring Cloud Kubernetes - Kubernetes native service discovery](https://docs.spring.io/spring-cloud-kubernetes/reference/discovery-kubernetes-native.html)
- [Netflix Eureka in 2025: Is It Time to Say Goodbye?](https://pravesh-sharma.medium.com/netflix-eureka-in-2025-is-it-time-to-say-goodbye-20901d15a287)
- [Spring Cloud Kubernetes ConfigMap PropertySource](https://docs.spring.io/spring-cloud-kubernetes/reference/property-source-config/configmap-propertysource.html)
- [Configuring Spring Boot on Kubernetes with ConfigMap](https://developers.redhat.com/blog/2017/10/03/configuring-spring-boot-kubernetes-configmap)
- [Kustomize Best Practices](https://kubernetes.io/docs/tasks/manage-kubernetes-objects/kustomization/)
- [Kustomize Examples - Spring Boot](https://github.com/kubernetes-sigs/kustomize/blob/master/examples/springboot/README.md)
- [Modern Microservices Deployment: Balancing Helm and Kustomize for GitOps](https://rurutia1027.medium.com/modern-microservices-deployment-balancing-helm-and-kustomize-for-gitops-friendly-architecture-7dc81ed432db)
- [Spring Cloud Gateway for Kubernetes](https://spring.io/blog/2021/05/04/spring-cloud-gateway-for-kubernetes)
- [Kubernetes Ingress Vs Gateway API](https://praneethreddybilakanti.medium.com/kubernetes-ingress-vs-gateway-api-4bb7bb3aa15f)
- [How Do I Choose? API Gateway vs. Ingress Controller](https://www.nginx.com/blog/how-do-i-choose-api-gateway-vs-ingress-controller-vs-service-mesh)
- [Operating Kafka in Kubernetes with Strimzi](https://www.adaltas.com/en/2023/03/07/operating-kafka-in-kubernetes-the-strimzi-way/)
- [Strimzi Overview](https://strimzi.io/docs/operators/latest/overview)
- [Kafka on Kubernetes: Deployment & Best Practices](https://www.automaq.com/blog/kafka-on-kubernetes-deployment-best-practices/)
- [Comparison of Kafka Deployment Options on Kubernetes](https://platformatory.io/blog/kafka-on-kubernetes/)
