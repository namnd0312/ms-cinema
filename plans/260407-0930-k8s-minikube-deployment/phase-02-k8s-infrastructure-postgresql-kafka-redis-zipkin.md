# Phase 2: K8s Infrastructure — PostgreSQL, Kafka, Redis, Zipkin

## Context Links
- [Plan Overview](./plan.md)
- [Phase 1: Code Changes](./phase-01-remove-eureka-and-config-server-code-changes.md)
- [docker-compose.yml](/docker-compose.yml) — reference for image versions & config

## Overview
- **Priority:** High (all services depend on infra)
- **Status:** Pending
- **Description:** Deploy PostgreSQL (6 databases), Kafka (KRaft), Redis, and Zipkin as K8s workloads in `k8s/infra/` subdirectories

## Key Insights
- PostgreSQL needs init script to create 6 databases (testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb)
- Kafka 3.7.0 uses KRaft mode (no ZooKeeper) — single broker for dev
- Redis 7 — no auth, ephemeral OK for dev (token blacklist, locks)
- Zipkin — stateless trace collector
- PersistentVolumeClaim for PostgreSQL data persistence across restarts
- Kafka/Redis/Zipkin can use emptyDir (acceptable data loss for dev)

## Requirements
### Functional
- PostgreSQL accessible at `postgresql:5432` within cluster, 6 databases initialized
- Kafka accessible at `kafka:9092` within cluster, KRaft mode
- Redis accessible at `redis:6379` within cluster
- Zipkin accessible at `zipkin:9411` within cluster

### Non-functional (minimal — study/research only)
- PostgreSQL: 256Mi limit, PVC for data
- Kafka: 512Mi limit (KRaft minimum viable)
- Redis: 64Mi limit
- Zipkin: 128Mi limit
- No resource requests — only limits (best-effort QoS, saves memory)

## Architecture
```
k8s/infra/
├── postgresql/deployment.yml  → Deployment + Service:5432 + PVC + ConfigMap(init.sql)
├── kafka/deployment.yml       → Deployment + Service:9092 (KRaft, single broker)
├── redis/deployment.yml       → Deployment + Service:6379
└── zipkin/deployment.yml      → Deployment + Service:9411
```

## Implementation Steps

### Step 1: PostgreSQL (`k8s/infra/postgresql/deployment.yml`)

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: postgresql-init
  namespace: ms-cinema
data:
  init-databases.sql: |
    CREATE DATABASE moviedb;
    CREATE DATABASE bookingdb;
    CREATE DATABASE paymentdb;
    CREATE DATABASE notificationdb;
    CREATE DATABASE auditdb;
    -- testdb is the default POSTGRES_DB, created automatically
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgresql-pvc
  namespace: ms-cinema
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgresql
  namespace: ms-cinema
  labels:
    app.kubernetes.io/name: postgresql
    app.kubernetes.io/part-of: ms-cinema
    app.kubernetes.io/component: infrastructure
spec:
  replicas: 1
  strategy:
    type: Recreate  # PVC can't be shared
  selector:
    matchLabels:
      app.kubernetes.io/name: postgresql
  template:
    metadata:
      labels:
        app.kubernetes.io/name: postgresql
        app.kubernetes.io/part-of: ms-cinema
    spec:
      containers:
        - name: postgresql
          image: postgres:16-alpine
          ports:
            - containerPort: 5432
          env:
            - name: POSTGRES_USER
              value: "postgres"
            - name: POSTGRES_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: ms-cinema-secrets
                  key: DB_PASSWORD
            - name: POSTGRES_DB
              value: "testdb"
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
              subPath: pgdata
            - name: init-scripts
              mountPath: /docker-entrypoint-initdb.d
          resources:
            limits: { memory: "256Mi", cpu: "300m" }
          readinessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            exec:
              command: ["pg_isready", "-U", "postgres"]
            initialDelaySeconds: 15
            periodSeconds: 20
      volumes:
        - name: data
          persistentVolumeClaim:
            claimName: postgresql-pvc
        - name: init-scripts
          configMap:
            name: postgresql-init
---
apiVersion: v1
kind: Service
metadata:
  name: postgresql
  namespace: ms-cinema
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: postgresql
  ports:
    - port: 5432
      targetPort: 5432
```

**Note:** Services reference DB_HOST=`postgresql` (K8s DNS). Each service's datasource URL pattern: `jdbc:postgresql://postgresql:5432/<dbname>`.

### Step 2: Kafka KRaft (`k8s/infra/kafka/deployment.yml`)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: kafka
  namespace: ms-cinema
  labels:
    app.kubernetes.io/name: kafka
    app.kubernetes.io/part-of: ms-cinema
    app.kubernetes.io/component: infrastructure
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: kafka
  template:
    metadata:
      labels:
        app.kubernetes.io/name: kafka
        app.kubernetes.io/part-of: ms-cinema
    spec:
      containers:
        - name: kafka
          image: apache/kafka:3.7.0
          ports:
            - containerPort: 9092
          env:
            - name: KAFKA_NODE_ID
              value: "0"
            - name: KAFKA_PROCESS_ROLES
              value: "broker,controller"
            - name: KAFKA_LISTENERS
              value: "PLAINTEXT://:9092,CONTROLLER://:9093"
            - name: KAFKA_ADVERTISED_LISTENERS
              value: "PLAINTEXT://kafka:9092"
            - name: KAFKA_CONTROLLER_LISTENER_NAMES
              value: "CONTROLLER"
            - name: KAFKA_LISTENER_SECURITY_PROTOCOL_MAP
              value: "CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT"
            - name: KAFKA_CONTROLLER_QUORUM_VOTERS
              value: "0@localhost:9093"
            - name: KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR
              value: "1"
            - name: KAFKA_TRANSACTION_STATE_LOG_MIN_ISR
              value: "1"
            - name: KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS
              value: "0"
            - name: CLUSTER_ID
              value: "MkU3OEVBNTcwNTJENDM2Qk"
          resources:
            limits: { memory: "512Mi", cpu: "300m" }
          readinessProbe:
            exec:
              command: ["/opt/kafka/bin/kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
            initialDelaySeconds: 30
            periodSeconds: 15
            timeoutSeconds: 10
          livenessProbe:
            exec:
              command: ["/opt/kafka/bin/kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
            initialDelaySeconds: 60
            periodSeconds: 30
            timeoutSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: kafka
  namespace: ms-cinema
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: kafka
  ports:
    - port: 9092
      targetPort: 9092
```

### Step 3: Redis (`k8s/infra/redis/deployment.yml`)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: ms-cinema
  labels:
    app.kubernetes.io/name: redis
    app.kubernetes.io/part-of: ms-cinema
    app.kubernetes.io/component: infrastructure
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: redis
  template:
    metadata:
      labels:
        app.kubernetes.io/name: redis
        app.kubernetes.io/part-of: ms-cinema
    spec:
      containers:
        - name: redis
          image: redis:7-alpine
          ports:
            - containerPort: 6379
          resources:
            limits: { memory: "64Mi", cpu: "100m" }
          readinessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            exec:
              command: ["redis-cli", "ping"]
            initialDelaySeconds: 10
            periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: ms-cinema
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: redis
  ports:
    - port: 6379
      targetPort: 6379
```

### Step 4: Zipkin (`k8s/infra/zipkin/deployment.yml`)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zipkin
  namespace: ms-cinema
  labels:
    app.kubernetes.io/name: zipkin
    app.kubernetes.io/part-of: ms-cinema
    app.kubernetes.io/component: infrastructure
spec:
  replicas: 1
  selector:
    matchLabels:
      app.kubernetes.io/name: zipkin
  template:
    metadata:
      labels:
        app.kubernetes.io/name: zipkin
        app.kubernetes.io/part-of: ms-cinema
    spec:
      containers:
        - name: zipkin
          image: openzipkin/zipkin:3.4
          ports:
            - containerPort: 9411
          resources:
            limits: { memory: "128Mi", cpu: "150m" }
          readinessProbe:
            httpGet: { path: /health, port: 9411 }
            initialDelaySeconds: 10
            periodSeconds: 10
          livenessProbe:
            httpGet: { path: /health, port: 9411 }
            initialDelaySeconds: 20
            periodSeconds: 15
---
apiVersion: v1
kind: Service
metadata:
  name: zipkin
  namespace: ms-cinema
spec:
  type: ClusterIP
  selector:
    app.kubernetes.io/name: zipkin
  ports:
    - port: 9411
      targetPort: 9411
```

## Todo List
- [ ] Create `k8s/infra/postgresql/deployment.yml` with init SQL
- [ ] Create `k8s/infra/kafka/deployment.yml` (KRaft mode)
- [ ] Create `k8s/infra/redis/deployment.yml`
- [ ] Create `k8s/infra/zipkin/deployment.yml`
- [ ] Verify PostgreSQL creates all 6 databases
- [ ] Verify Kafka broker ready and accepting connections
- [ ] Verify Redis responds to ping
- [ ] Verify Zipkin health endpoint

## Success Criteria
- All 4 infra pods Running + Ready
- `kubectl exec` into a test pod can connect to postgresql:5432, kafka:9092, redis:6379, zipkin:9411
- PostgreSQL has 6 databases: testdb, moviedb, bookingdb, paymentdb, notificationdb, auditdb
- Kafka accepts producer/consumer connections

## Risk Assessment
- **Risk:** PostgreSQL PVC not provisioned (no default StorageClass)
  - **Mitigation:** Minikube has default `standard` StorageClass; verify with `kubectl get sc`
- **Risk:** Kafka KRaft env var mismatch with image version
  - **Mitigation:** Match env vars exactly to docker-compose.yml (verified working)
- **Risk:** Kafka startup slow, services connect before ready
  - **Mitigation:** Services have init containers or retry logic; Kafka readiness probe gates traffic
- **Risk:** PostgreSQL init script runs only on first PVC mount
  - **Mitigation:** docker-entrypoint-initdb.d only runs when data dir is empty; teardown + redeploy recreates

## Security Considerations
- PostgreSQL password in K8s Secret, not plain text
- Redis no auth — acceptable for dev, not for prod
- All infra ClusterIP only — not accessible outside cluster

## Next Steps
- Phase 3: ConfigMap with migrated config-server values + per-service manifests (DB_HOST=`postgresql`, KAFKA_HOST=`kafka`, etc.)
