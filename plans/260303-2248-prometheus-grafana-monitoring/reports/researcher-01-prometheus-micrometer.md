# Research: Prometheus Integration with Spring Boot 3.4.x & Micrometer

## 1. Micrometer-Registry-Prometheus Dependency

**Version Management:** Spring Boot 3.4.3 manages Micrometer versions via BOM. No explicit version needed.

```xml
<!-- In service pom.xml (version inherited from parent BOM) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Also add actuator for metric endpoints -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**Note:** Uses Prometheus Client 1.x (new default). For legacy 0.x: use `micrometer-registry-prometheus-simpleclient` (deprecated, will be removed in Boot 3.5).

---

## 2. Application.yml Configuration (Spring Boot 3.x)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus  # Expose /actuator/prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
      environment: ${app.environment:dev}
```

**Key Props:**
- `management.endpoints.web.exposure.include=prometheus` - Minimum required
- `/actuator/prometheus` endpoint returns metrics in Prometheus text format
- Spring Boot 3.x uses `management.*` prefix (not `endpoints.*` like 2.x)

---

## 3. Prometheus Static Scrape Config (Docker Compose)

**prometheus.yml:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']

  - job_name: 'auth-service'
    scrape_interval: 10s
    static_configs:
      - targets: ['auth-service:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'api-gateway'
    scrape_interval: 10s
    static_configs:
      - targets: ['api-gateway:8080']
    metrics_path: '/actuator/prometheus'

  - job_name: 'movie-service'
    scrape_interval: 10s
    static_configs:
      - targets: ['movie-service:8081']
    metrics_path: '/actuator/prometheus'

  - job_name: 'booking-service'
    scrape_interval: 10s
    static_configs:
      - targets: ['booking-service:8082']
    metrics_path: '/actuator/prometheus'
```

---

## 4. Docker Compose Setup for Prometheus

```yaml
services:
  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
    networks:
      - app-network
    depends_on:
      - auth-service
      - api-gateway

volumes:
  prometheus_data:

networks:
  app-network:
    driver: bridge
```

**Volume Mount:** `./prometheus.yml` → `/etc/prometheus/prometheus.yml` (read-only config)

---

## 5. JVM Metrics (Auto-Instrumented by Default)

When Spring Boot Actuator + Micrometer are on classpath:

| Metric | Type | Description |
|--------|------|-------------|
| `jvm.memory.*` | Gauge | Heap/non-heap memory usage (bytes) |
| `jvm.gc.memory.allocated` | Counter | Memory allocated since JVM start (bytes) |
| `jvm.gc.max.data.size` | Gauge | Max GC data size (bytes) |
| `jvm.gc.live.data.size` | Gauge | Live data set size after GC (bytes) |
| `jvm.threads.*` | Gauge | Live/peak/daemon thread counts |
| `process.cpu.usage` | Gauge | CPU usage (0-1 scale) |
| `process.files.*` | Gauge | Open file descriptor counts |

**No explicit config needed** - auto-enabled via `JvmMeterBinder` when actuator detected.

---

## 6. HikariCP Database Connection Pool Metrics

**Auto-instrumented:** YES. Spring Boot auto-registers `JdbcPoolMetrics`.

Metrics exposed under `jdbc.connections.*` (generic) + `hikaricp.*` (pool-specific):

```
hikaricp.connections             (total)
hikaricp.connections.active      (currently in use)
hikaricp.connections.idle        (available)
hikaricp.connections.pending     (waiting for connection)
hikaricp.connections.max         (max pool size)
hikaricp.connections.min         (min idle connections)
```

**No config required** if using Spring Data JPA with HikariCP (default connection pool in Boot 3.4).

---

## 7. Redis (Lettuce) Metrics

**Auto-instrumented:** YES. `LettuceMetricsAutoConfiguration` auto-registers when:
- Lettuce on classpath (via `spring-boot-starter-data-redis`)
- `MeterRegistry` bean present
- `spring-boot-starter-actuator` added

Metrics collected via `MicrometerCommandLatencyRecorder`:

```
lettuce.command.firstresponse    (timer - latency to first response)
lettuce.command.completion       (timer - latency to full completion)
  Tags: command, endpoint
```

**No config needed.** Auto-enabled for Spring Data Redis connections.

---

## 8. Kafka Metrics (Spring Kafka)

**Auto-instrumented:** YES. `KafkaMetricsAutoConfiguration` auto-configures when:
- `spring-kafka` on classpath
- `spring-boot-starter-actuator` present
- `MeterRegistry` bean available

Auto-registered listeners:
- `MicrometerConsumerListener` → consumer lag, record count, latency
- `MicrometerProducerListener` → send latency, send count
- `KafkaStreamsMicrometerListener` (for Kafka Streams apps)

Metrics exposed as:
```
spring.kafka.listener        (timers for successful/failed records)
spring.kafka.template        (timers for KafkaTemplate sends)
  Tags: topic, partition, consumer-group, client-id
```

**Config option (optional):**
```yaml
spring:
  kafka:
    listener:
      micrometer:
        enabled: true
    producer:
      micrometer:
        enabled: true
```

Default enabled if Boot detects Micrometer. **No explicit setup needed.**

---

## 9. Spring Cloud Gateway Route-Level Metrics

**Auto-instrumented:** YES. `GatewayMetricsAutoConfiguration` creates `GatewayMetricsFilter` when:
- `spring-cloud-gateway-core` on classpath
- `spring-boot-starter-actuator` present
- `MeterRegistry` bean available

Exposed metrics:

```
spring.cloud.gateway.requests       (histogram/timer - request latency per route)
spring.cloud.gateway.routes.count   (gauge - total route count)
  Tags: routeId, routeUri, outcome, status, method
```

**Control via property:**
```yaml
spring:
  cloud:
    gateway:
      metrics:
        enabled: true  # Default true if actuator+micrometer present
```

**No custom config required** for basic route-level observability. Routes tagged by ID for fine-grained monitoring.

---

## Summary: What's Auto-Configured

| Component | Auto-Config | Config Needed | Explicit Bean |
|-----------|-------------|---------------|---------------|
| JVM Metrics | ✅ | None | Via `JvmMeterBinder` |
| HikariCP | ✅ | None | Via `JdbcPoolMetrics` |
| Redis/Lettuce | ✅ | None | Via `LettuceMetricsAutoConfiguration` |
| Kafka Consumer/Producer | ✅ | Optional property | Via `KafkaMetricsAutoConfiguration` |
| Gateway Routes | ✅ | Optional property | Via `GatewayMetricsAutoConfiguration` |
| Prometheus Registry | ✅ | `/actuator/prometheus` config | Via `PrometheusConfig` |

---

## Sources

- [Maven Repository: io.micrometer » micrometer-registry-prometheus](https://mvnrepository.com/artifact/io.micrometer/micrometer-registry-prometheus)
- [Micrometer Prometheus Reference](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Spring Boot 3.4 Metrics Documentation](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- [Spring Boot Actuator Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [LettuceMetricsAutoConfiguration (Spring Boot 3.4 API)](https://docs.spring.io/spring-boot/api/java/org/springframework/boot/actuate/autoconfigure/metrics/redis/LettuceMetricsAutoConfiguration.html)
- [KafkaMetricsAutoConfiguration (Spring Boot API)](https://docs.spring.io/spring-boot/docs/current/api/org/springframework/boot/actuate/autoconfigure/metrics/KafkaMetricsAutoConfiguration.html)
- [Spring Cloud Gateway Metrics](https://dashaun.com/posts/spring-cloud-gateway-micrometer/)
- [Prometheus with Docker Compose Guide](https://spacelift.io/blog/prometheus-docker-compose)
- [JVM Metrics - Micrometer Reference](https://docs.micrometer.io/micrometer/reference/reference/jvm.html)
- [Spring Boot 3.3/3.4 Observability Guide](https://www.baeldung.com/spring-boot-3-observability)
