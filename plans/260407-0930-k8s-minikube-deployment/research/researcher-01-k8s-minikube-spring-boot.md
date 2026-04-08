# K8s Minikube Deployment for Spring Boot Microservices

## 1. Spring Boot on Minikube Best Practices

**Core Setup:**
- Use `imagePullPolicy: IfNotPresent` to ensure Minikube searches locally before Docker Hub
- Containerize each service separately; avoid shared databases between services
- Enable CI/CD automation for deployments (manual processes fail at scale)

**Key Principles:**
- Each microservice owns its data (avoid tight coupling via shared DBs)
- Invest in logging, metrics, tracing from day one (observability essential)
- Implement retries with exponential backoff + circuit breakers (network unreliability)

## 2. Connecting to Host-Machine Services (PostgreSQL, Kafka, Redis)

**`host.minikube.internal` Mechanism:**
- Minikube v1.10+ provides this hostname entry to access host machine services
- Services must bind to `0.0.0.0` or the VM's bridged IP (binding to `127.0.0.1` fails)
- DNS resolution differs by driver; test connectivity early

**Kafka Consideration:**
- Add `host.minikube.internal` to `advertised.listeners` in server.properties
- Ensures brokers advertise reachable addresses to pods

**Usage Example:**
```
PostgreSQL: jdbc:postgresql://host.minikube.internal:5432/dbname
Redis: redis://host.minikube.internal:6379
Kafka: bootstrap.servers=host.minikube.internal:9092
```

## 3. Init Containers vs Readiness Probes for Startup Ordering

**Startup Probe (Recommended):**
- Kubernetes delays liveness/readiness checks until startup probe succeeds
- Prevents premature pod termination during long initialization
- Built-in ordering for Eureka → Config Server → Business Services

**Init Containers (Alternative):**
- Run sequential containers before main app starts
- Useful for validating external dependencies exist
- More heavyweight than probes; use when pre-flight checks needed

**Best Practice:** Use startup probes for Spring Boot (cleaner, native Actuator integration)

## 4. Spring Boot Actuator Health Probes

**Key Endpoints:**
- `/actuator/health/liveness` — process viability (triggers restarts if fails)
- `/actuator/health/readiness` — ready to serve traffic (holds traffic until UP)
- `/actuator/health` — overall system health

**Auto-Registration:**
- Spring Boot 2.3+ auto-registers `LivenessStateHealthIndicator` & `ReadinessStateHealthIndicator` in K8s
- Only enabled automatically in K8s; elsewhere use `management.endpoint.health.probes.enabled=true`

**Probe Configuration (K8s Deployment):**
```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 5
```

## 5. JVM Memory Management for Minikube

**Java Container Awareness (Java 10+):**
- `-XX:+UseContainerSupport` enabled by default; JVM respects cgroup limits
- Heap set to fraction of container limit (typically 50% via `-XX:MaxRAMPercentage=50.0`)

**Minikube Resource Configuration:**
```yaml
resources:
  requests:
    memory: "512Mi"
    cpu: "250m"
  limits:
    memory: "1Gi"
    cpu: "500m"
```

**Key Constraints:**
- JVM respects heap limits but not non-heap memory (metaspace, threads, etc.)
- Monitor actual memory via `/actuator/metrics` (java.lang metrics)
- Conservative estimates: 256MB base + application heap

## 6. `eval $(minikube docker-env)` Workflow

**Direct Image Building to Minikube Docker:**
```bash
eval $(minikube docker-env)  # Point Docker client to Minikube daemon
docker build -t my-service:latest .
# No registry push needed; image accessible inside cluster
```

**Advantages:**
- Eliminates image registry push/pull overhead
- Fast iteration in development
- Set `imagePullPolicy: IfNotPresent` in deployment

**Limitations:**
- Only works with Minikube (not production-ready)
- Images lost when cluster restarts (use volume persistence if needed)

## Sources
- [Running Spring Boot Applications With Minikube | Baeldung](https://www.baeldung.com/spring-boot-minikube)
- [Mastering Microservices: Deploying Java Spring Boot Applications on Kubernetes with Minikube | Medium](https://medium.com/bb-tutorials-and-thoughts/mastering-microservices-deploying-java-spring-boot-applications-on-kubernetes-with-minikube-105cac51913c)
- [Minikube Host Access | Kubernetes Documentation](https://minikube.sigs.k8s.io/docs/handbook/host-access/)
- [Liveness and Readiness Probes in Spring Boot | Baeldung](https://www.baeldung.com/spring-liveness-readiness-probes)
- [Managing Java Heap size in Kubernetes | Medium](https://medium.com/marionete/managing-java-heap-size-in-kubernetes-3807159e2438)
- [Kubernetes Probes: Ensuring Spring Boot App Health | OneUptime](https://oneuptime.com/blog/post/2026-01-25-health-probes-kubernetes-spring-boot/view)

## Unresolved Questions
- Exact Java 21-specific optimizations for container environments (sources didn't emphasize version differences)
- Circuit breaker integration patterns in service-to-service communication via readiness probes
