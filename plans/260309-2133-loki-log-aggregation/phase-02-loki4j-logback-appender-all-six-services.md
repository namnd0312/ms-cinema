---
title: "Phase 2 - loki4j Logback Appender Integration - All 6 Services"
status: pending
priority: P1
effort: 1h
---

# Phase 2: loki4j Logback Appender - All 6 Services

## Context Links
- Parent plan: [plan.md](./plan.md)
- Phase 1: [phase-01-loki-docker-infrastructure-and-grafana-datasource.md](./phase-01-loki-docker-infrastructure-and-grafana-datasource.md)
- Example logback: `auth-service/src/main/resources/logback-spring.xml`

## Overview

Add `loki-logback-appender` dependency to all 6 services and configure a `LokiAppender` in each `logback-spring.xml`. Logs already contain `service` field and MDC `correlationId` — loki4j will forward them as Loki labels for LogQL filtering.

## Key Insights

- loki4j version: `1.5.2` — latest stable, supports Loki 3.x HTTP API
- Group: `com.github.loki4j` / Artifact: `loki-logback-appender`
- loki4j uses HTTP batch push — no extra agent/sidecar needed
- Labels in Loki should be **low-cardinality** — use only `service` and `level` as labels; `correlationId` stays in log line body (high-cardinality, query via `|= "correlationId"`)
- All 6 services have identical `logback-spring.xml` pattern — just add LokiAppender alongside existing CONSOLE appender
- `LOKI_HOST` env var (default `loki`) allows override in docker-compose or local dev

## Requirements

- All 6 services push logs to `http://${LOKI_HOST}:3100/loki/api/v1/push`
- Labels: `service={APP_NAME}`, `level={level}` — low-cardinality
- Log format: JSON (reuse `LogstashEncoder` pattern for the message body)
- Batch: 1s timeout, 100 entries max — acceptable latency for dev
- Non-blocking: loki4j drops logs on overflow rather than blocking service threads
- `LOKI_HOST` defaults to `loki` (Docker), override to `localhost` for local dev

## Architecture

```
[Service JVM]
  → CONSOLE appender  (existing, stdout)
  → LOKI appender     (new, HTTP batch push)
       |
       v
  http://loki:3100/loki/api/v1/push
  Labels: {service="auth-service", level="ERROR"}
  Body: full JSON log line with correlationId, method, url, status, etc.
```

## Related Code Files

**Root pom:**
- `pom.xml` — add `loki-logback-appender` to `<dependencyManagement>`

**All 6 service poms:**
- `auth-service/pom.xml`
- `api-gateway/pom.xml`
- `movie-service/pom.xml`
- `booking-service/pom.xml`
- `payment-service/pom.xml`
- `notification-service/pom.xml`

**All 6 logback configs:**
- `auth-service/src/main/resources/logback-spring.xml`
- `api-gateway/src/main/resources/logback-spring.xml`
- `movie-service/src/main/resources/logback-spring.xml`
- `booking-service/src/main/resources/logback-spring.xml`
- `payment-service/src/main/resources/logback-spring.xml`
- `notification-service/src/main/resources/logback-spring.xml`

## Implementation Steps

### Step 1: Add loki4j to root pom.xml `<dependencyManagement>`

Add property in `<properties>`:
```xml
<loki4j.version>1.5.2</loki4j.version>
```

Add in `<dependencyManagement>/<dependencies>`:
```xml
<!-- Loki logback appender - push logs to Grafana Loki -->
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>${loki4j.version}</version>
</dependency>
```

### Step 2: Add dependency to each service pom.xml

In `<dependencies>` section of each of the 6 service poms:
```xml
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
</dependency>
```

### Step 3: Update logback-spring.xml in all 6 services

Each `logback-spring.xml` needs `LOKI_HOST` property and a `LOKI` appender added.
The `<root>` must reference both CONSOLE and LOKI.

**Template (replace `${APP_NAME}` default value per service):**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="auth-service"/>
    <!-- Override LOKI_HOST via env var for local dev (default: docker service name) -->
    <property name="LOKI_HOST" value="${LOKI_HOST:-loki}"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${APP_NAME}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
        </encoder>
    </appender>

    <!-- Loki appender: batch push JSON logs with service + level labels -->
    <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
        <http>
            <url>http://${LOKI_HOST}:3100/loki/api/v1/push</url>
        </http>
        <format>
            <label>
                <!-- Low-cardinality labels only -->
                <pattern>service=${APP_NAME},level=%level</pattern>
            </label>
            <message class="com.github.loki4j.logback.JsonLayout">
                <!-- Include MDC fields (correlationId, method, url, status, etc.) in log line -->
                <includeMdcProperties>true</includeMdcProperties>
            </message>
        </format>
        <!-- Non-blocking: drop overflow rather than blocking request threads -->
        <dropRatioOnQueueFull>0</dropRatioOnQueueFull>
    </appender>

    <!-- Suppress framework noise -->
    <logger name="org.springframework" level="INFO"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="com.netflix" level="WARN"/>
    <logger name="org.apache.kafka" level="WARN"/>
    <!-- app code: DEBUG for dev visibility -->
    <logger name="com.namnd" level="DEBUG"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOKI"/>
    </root>
</configuration>
```

**Per-service differences (only `defaultValue` and logger package):**

| Service | defaultValue | App logger package |
|---------|-------------|-------------------|
| auth-service | `auth-service` | `com.namnd.springjwt` |
| api-gateway | `api-gateway` | `com.namnd.apigateway` |
| movie-service | `movie-service` | `com.namnd.movieservice` |
| booking-service | `booking-service` | `com.namnd.bookingservice` |
| payment-service | `payment-service` | `com.namnd.paymentservice` |
| notification-service | `notification-service` | `com.namnd.notification` |

## Todo List

- [ ] Add `loki4j.version` property to root `pom.xml`
- [ ] Add `loki-logback-appender` to root `pom.xml` `<dependencyManagement>`
- [ ] Add `loki-logback-appender` dependency to `auth-service/pom.xml`
- [ ] Add `loki-logback-appender` dependency to `api-gateway/pom.xml`
- [ ] Add `loki-logback-appender` dependency to `movie-service/pom.xml`
- [ ] Add `loki-logback-appender` dependency to `booking-service/pom.xml`
- [ ] Add `loki-logback-appender` dependency to `payment-service/pom.xml`
- [ ] Add `loki-logback-appender` dependency to `notification-service/pom.xml`
- [ ] Update `auth-service/src/main/resources/logback-spring.xml`
- [ ] Update `api-gateway/src/main/resources/logback-spring.xml`
- [ ] Update `movie-service/src/main/resources/logback-spring.xml`
- [ ] Update `booking-service/src/main/resources/logback-spring.xml`
- [ ] Update `payment-service/src/main/resources/logback-spring.xml`
- [ ] Update `notification-service/src/main/resources/logback-spring.xml`
- [ ] Run `mvn compile` to verify no build errors

## Success Criteria

- `mvn compile` succeeds with no errors
- Service logs appear in Loki (`curl 'http://localhost:3100/loki/api/v1/labels'` returns `service`)
- LogQL `{service="auth-service"}` returns log entries in Grafana Explore

## Risk Assessment

- **loki4j HTTP failure**: If Loki is unreachable, loki4j logs a warning and drops batches — no impact on service operation
- **High-cardinality labels**: Do NOT use `correlationId` as a label (too many unique values → Loki cardinality limit); keep it in log body only
- **notification-service** has no HTTP filter, so no `correlationId` in MDC — still works, just won't have correlationId in logs

## Security Considerations

- Loki endpoint is internal (Docker network only) — no auth needed for dev
- No sensitive data in Loki labels (only `service` and `level`)

## Next Steps

→ Phase 3: Create Grafana Loki dashboard for log exploration
