# Phase 01 — JSON Logging Config

## Context Links
- Parent plan: [plan.md](./plan.md)
- Code standards: `docs/code-standards.md`
- Codebase summary: `docs/codebase-summary.md`

## Overview

- **Date:** 2026-03-09
- **Priority:** P2
- **Status:** pending
- **Description:** Add `logstash-logback-encoder` dependency to all services and create `logback-spring.xml` with structured JSON output.

## Key Insights

- Spring Boot 3.4.3 uses Logback by default — no need to add logback-core/classic
- `logstash-logback-encoder` provides `LogstashEncoder` / `LoggingEventCompositeJsonEncoder` for JSON output
- `logback-spring.xml` (not `logback.xml`) allows Spring Boot profile-aware config (`<springProfile>`)
- Current log output is plain text; no existing logback XML found in any service
- MDC fields (correlationId, method, url, status, duration) will auto-appear in JSON output from logstash encoder
- api-gateway uses `spring-cloud-starter-gateway-mvc` — servlet stack, same as other services

## Requirements

- All 6 services output JSON logs to stdout
- JSON must include: `timestamp`, `level`, `service`, `logger`, `message`, `thread`, plus any MDC fields
- No log file rotation needed (Docker/stdout approach; container log driver handles shipping)
- Existing log levels preserved (DEBUG for JwtService, Hibernate, etc.)

## Architecture

```
logback-spring.xml (each service)
├── Console Appender (stdout)
│   └── LogstashEncoder (net.logstash.logback.encoder.LogstashEncoder)
│       ├── Auto-includes: timestamp, level, logger, thread, message
│       ├── Auto-includes: MDC fields (correlationId, method, url, etc.)
│       └── customFields: {"service": "<service-name>"}
└── Root logger: INFO
    └── Service-specific loggers: DEBUG where needed
```

**JSON output example:**
```json
{
  "@timestamp": "2026-03-09T20:53:36.902+07:00",
  "@version": "1",
  "message": "POST /api/auth/login -> 200 in 145ms",
  "logger_name": "com.namnd.springjwt.config.filter.HttpLoggingFilter",
  "thread_name": "http-nio-8081-exec-1",
  "level": "INFO",
  "level_value": 20000,
  "service": "auth-service",
  "correlationId": "abc-123-xyz",
  "method": "POST",
  "url": "/api/auth/login",
  "status": 200,
  "durationMs": 145,
  "clientIp": "172.22.0.1",
  "userAgent": "Mozilla/5.0..."
}
```

## Related Code Files

**Create:**
- `auth-service/src/main/resources/logback-spring.xml`
- `api-gateway/src/main/resources/logback-spring.xml`
- `movie-service/src/main/resources/logback-spring.xml`
- `booking-service/src/main/resources/logback-spring.xml`
- `payment-service/src/main/resources/logback-spring.xml`
- `notification-service/src/main/resources/logback-spring.xml`

**Modify:**
- `pom.xml` (root) — add logstash-logback-encoder to `<dependencyManagement>`
- `auth-service/pom.xml`
- `api-gateway/pom.xml`
- `movie-service/pom.xml`
- `booking-service/pom.xml`
- `payment-service/pom.xml`
- `notification-service/pom.xml`

## Implementation Steps

### Step 1 — Root pom.xml: add to `<dependencyManagement>`

```xml
<!-- Structured JSON logging -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

Also add a `<logstash-logback-encoder.version>8.0</logstash-logback-encoder.version>` property.

### Step 2 — Each service pom.xml: add dependency (no version, managed by root)

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
```

### Step 3 — Create `logback-spring.xml` for each service

Template (replace `SERVICE_NAME` per service):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <springProperty scope="context" name="APP_NAME" source="spring.application.name" defaultValue="SERVICE_NAME"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${APP_NAME}"}</customFields>
            <fieldNames>
                <timestamp>@timestamp</timestamp>
                <version>[ignore]</version>
            </fieldNames>
        </encoder>
    </appender>

    <!-- Framework noise: INFO -->
    <logger name="org.springframework" level="INFO"/>
    <logger name="org.hibernate" level="WARN"/>
    <logger name="com.netflix" level="WARN"/>
    <logger name="org.apache.kafka" level="WARN"/>

    <!-- Service-specific DEBUG loggers (per existing setup) -->
    <!-- auth-service only: -->
    <!-- <logger name="com.namnd.springjwt" level="DEBUG"/> -->

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**Per-service logger customizations:**
- `auth-service`: add `<logger name="com.namnd.springjwt" level="DEBUG"/>`
- Other services: keep at INFO (no DEBUG needed unless specified)

## Todo List

- [ ] Add `logstash-logback-encoder.version=8.0` property to root pom.xml
- [ ] Add logstash-logback-encoder to root pom.xml `<dependencyManagement>`
- [ ] Add dependency to auth-service/pom.xml
- [ ] Add dependency to api-gateway/pom.xml
- [ ] Add dependency to movie-service/pom.xml
- [ ] Add dependency to booking-service/pom.xml
- [ ] Add dependency to payment-service/pom.xml
- [ ] Add dependency to notification-service/pom.xml
- [ ] Create logback-spring.xml for auth-service
- [ ] Create logback-spring.xml for api-gateway
- [ ] Create logback-spring.xml for movie-service
- [ ] Create logback-spring.xml for booking-service
- [ ] Create logback-spring.xml for payment-service
- [ ] Create logback-spring.xml for notification-service
- [ ] Verify `mvn clean compile` passes for all modules

## Success Criteria

- All services output JSON to stdout on startup
- JSON contains `@timestamp`, `level`, `service`, `message`, `logger_name`
- Existing functionality not broken

## Risk Assessment

- **Low risk**: logstash-logback-encoder is a well-tested library; only adds JSON formatting
- Spring Boot auto-config uses `logback-spring.xml` automatically — no code changes needed for basic config

## Security Considerations

- No sensitive data logged at this phase (just JSON format setup)
- `logback-spring.xml` does not contain secrets

## Next Steps

→ Phase 02: HttpLoggingFilter implementation
