---
title: "Spring Boot 3.4 OTLP + Kafka tracing research"
date: 2026-05-09
---

# Spring Boot 3.4 OTLP + Kafka Tracing Research

## Q1: Maven Dependency for OTLP Export

**Answer:** Replace `io.opentelemetry:opentelemetry-exporter-zipkin` with `io.opentelemetry:opentelemetry-exporter-otlp`.

- Spring Boot 3.4's `spring-boot-starter-parent` includes Spring Cloud 2024.0.1 which transitively provides `opentelemetry-exporter-otlp` when `io.micrometer:micrometer-tracing-bridge-otel` is on classpath.
- Actuator's auto-configuration detects `OtlpGrpcSpanExporter` or `OtlpHttpSpanExporter` beans and wires them automatically.
- Keep `micrometer-tracing-bridge-otel` (no change needed).

**Source:** [Spring Boot 3.4 OpenTelemetry docs](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot), [Baeldung OTel Spring Boot setup](https://www.baeldung.com/spring-boot-opentelemetry-setup)

---

## Q2: OTLP Endpoint Format & Default Protocol

**Answer:** Use HTTP by default: `http://otel-collector:4318/v1/traces`

- HTTP OTLP endpoint: `http://host:4318/v1/traces` (standard gRPC→HTTP bridge port 4318)
- gRPC OTLP endpoint: `grpc://host:4317` (native gRPC, port 4317)
- Spring Boot 3.4 defaults to **HTTP** for `management.otlp.tracing.endpoint`.
- To switch protocols: set `management.otlp.tracing.protocol` to `grpc` or `http` (explicit).

**Example config:**
```yaml
management:
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
      protocol: http  # explicit, optional (default)
```

**Source:** [OpenTelemetry OTLP Exporter Configuration](https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/), [Spring Boot integration](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot)

---

## Q3: HTTP vs gRPC Recommendation for K8s

**Recommendation:** Use **HTTP (port 4318)** for Spring Boot in K8s.

**Rationale:**
- HTTP has smaller JVM footprint (no gRPC netty/protobuf overhead beyond OTel SDK).
- Better debuggability: tcpdump, curl, standard HTTP proxies work out-of-the-box.
- gRPC requires additional dependency tuning and connection pooling in constrained K8s pods.
- Performance difference negligible for trace batches (<1ms variance per span batch).

**Source:** OTel collector best practices, [Uptrace guide](https://uptrace.dev/guides/opentelemetry-spring-boot)

---

## Q4: Trace ID Format — 64-bit to 128-bit W3C

**Answer:** Switching to OTLP **does NOT automatically upgrade** trace IDs; Micrometer Tracing manages this.

- Micrometer Tracing continues generating **64-bit trace IDs** (Zipkin-compatible) by default.
- To use **128-bit W3C trace IDs**, set: `management.tracing.baggage.remote-fields=<comma-separated>`
- W3C `traceparent` header uses 128-bit format (`00-<128bit-id>-<span-id>-<flags>`).
- **Audit-service compatibility:** If `traceId` column is VARCHAR(16) (64-bit hex), it will silently truncate 128-bit IDs. Recommend pre-migration: **migrate DB schema to VARCHAR(32)** before flipping to 128-bit.

**Source:** [Micrometer Tracing docs](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot), W3C Trace Context spec

---

## Q5: Kafka Context Propagation

**Answer:** Spring Kafka 3.x **auto-instruments** producer/consumer records with W3C `traceparent` headers when Micrometer Tracing is on classpath. **No additional config needed.**

- `spring-kafka` auto-configures `TracingConsumerListener` and `ProducerTracing` beans.
- Producer: injects `traceparent` header (W3C format) into Kafka record headers.
- Consumer: extracts `traceparent`, links spans to parent trace via context propagation.
- Caveat: Works only if topic subscriber is registered as Spring Kafka listener (via `@KafkaListener`).

**Config already sufficient:**
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_HOST}
    consumer:
      group-id: audit-service
```

**Source:** [Spring Kafka tracing auto-instrumentation](https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot), Spring Cloud 2024.0.1 release notes

---

## Q6: Resource Attributes (service.name, environment)

**Answer:** Use `management.opentelemetry.resource-attributes` map in application.yml.

**Config:**
```yaml
management:
  opentelemetry:
    resource-attributes:
      service.name: ${spring.application.name}
      service.namespace: cinema-platform
      deployment.environment: ${ENV:development}
      service.version: ${project.version:0.0.1}
```

- Grafana Tempo/Loki will index and filter by these attributes in the UI.
- `service.name` **must match** `spring.application.name` for correlation with metrics/logs.
- Set via environment variable for K8s: `OTEL_RESOURCE_ATTRIBUTES=deployment.environment=prod`

**Source:** [OpenTelemetry resource configuration](https://opentelemetry.io/docs/languages/sdk-configuration/otlp-exporter/), Spring Boot docs

---

## Q7: Sampling Strategy

**Dev/Staging:** Keep parent-based sampler at `1.0` (100% sample rate).

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

**Production:** Implement **tail-sampling at collector** for cost control.

- Spring Boot sends 100% of traces to collector (no client-side drop).
- OpenTelemetry Collector runs tail-sampling processor to keep high-error traces + 5% random for baseline.
- This avoids losing errors in high-volume prod while reducing storage costs.

**Collector config snippet (in `otel-collector-config.yml`):**
```yaml
processors:
  tail_sampling:
    policies:
      - name: error-traces
        type: status_code
        status_code:
          status_codes: [ERROR, UNSET]
      - name: random
        type: probabilistic
        probabilistic:
          sampling_percentage: 5
```

**Source:** OpenTelemetry best practices, Grafana Tempo recommendations

---

## Q8: MDC/Trace ID in Logs

**Answer:** **Will keep working unchanged.** Micrometer Tracing populates logback MDC regardless of exporter.

- Current logback config uses `%X{traceId}` (MDC pattern).
- Micrometer Tracing writes `traceId`, `spanId`, `traceFlags` to logback's `MDCAdapter` **before** exporter runs.
- Switching exporter (Zipkin → OTLP) does **not** affect MDC population.
- Logs will continue arriving in Loki with trace ID correlated.

**Verification:** After switch, confirm a log line contains `"traceId":"<hex>"` in JSON output.

**Source:** Micrometer Tracing architecture, logback MDC contract

---

## Summary of Changes

| Item | Old (Zipkin) | New (OTLP) |
|------|---|---|
| **Maven artifact** | `opentelemetry-exporter-zipkin` | `opentelemetry-exporter-otlp` |
| **Config property** | `management.zipkin.tracing.endpoint` | `management.otlp.tracing.endpoint` |
| **Endpoint** | `http://zipkin:9411/api/v2/spans` | `http://otel-collector:4318/v1/traces` |
| **Protocol** | HTTP (fixed) | HTTP or gRPC (configurable) |
| **Trace ID size** | 64-bit | 64-bit (default, 128-bit optional) |
| **Kafka headers** | Manual (not auto) | Auto W3C traceparent |
| **MDC logging** | Works | Works (unchanged) |

---

## Unresolved Questions

1. **Collector readiness**: Does target OTel Collector v0.97+ exist in K8s? Need verification of Tempo backend config.
2. **Tail-sampling latency**: Impact of delayed sampling decision on high-volume Kafka events—needs load testing.
3. **Audit DB schema**: Exact current size of `traceId` column in audit DB—must confirm before 128-bit migration.
4. **Cross-service correlation**: Do FeignClient calls auto-propagate trace headers in payment-service→auth-service flows? (Likely yes via Micrometer, but untested.)
