---
title: "Phase 3 — Update Service Dependencies"
status: pending
priority: P1
effort: 2h
---

# Phase 3 — Update Service Dependencies

## Context Links
- [Plan overview](plan.md)
- [Phase 2 — Split shared libs](phase-02-split-shared-libs.md)

## Overview

Before splitting services, update each service's pom.xml to replace `${project.version}` references to shared libs with explicit `1.0.0` version and add GitHub Packages `<repositories>` blocks. Verify compilation in monorepo before splitting.

## Key Insights

### Current Inter-Module Dependency Map

| Service | Depends on kafka-events | Depends on jwt-starter |
|---------|------------------------|----------------------|
| auth-service | YES | NO |
| notification-service | YES | NO |
| movie-service | YES | YES |
| booking-service | YES | YES |
| payment-service | YES | YES |
| api-gateway | NO | NO |
| eureka-server | NO | NO |
| config-server | NO | NO |

### Version References to Replace

All internal deps currently use `<version>${project.version}</version>` which resolves to `0.0.1-SNAPSHOT`. Must change to `<version>1.0.0</version>`.

## Requirements

### Functional
- Replace `${project.version}` for kafka-events dep with `1.0.0` in: auth-service, notification-service, movie-service, booking-service, payment-service
- Replace `${project.version}` for jwt-auth-spring-boot-starter dep with `1.0.0` in: movie-service, booking-service, payment-service
- Add `<repositories>` block pointing to GitHub Packages in each consuming service pom.xml
- Replicate root pom's `<dependencyManagement>` entries into each service pom.xml

### Non-Functional
- Each service must still compile via `mvn clean package` (either from monorepo or standalone)

## Architecture

### POM Changes Per Service

**auth-service/pom.xml** changes:
```xml
<!-- BEFORE -->
<dependency>
    <groupId>com.namnd</groupId>
    <artifactId>kafka-events</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- AFTER -->
<dependency>
    <groupId>com.namnd</groupId>
    <artifactId>kafka-events</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Each service pom.xml** add:
```xml
<repositories>
    <repository>
        <id>github-kafka-events</id>
        <url>https://maven.pkg.github.com/OWNER/cinema-kafka-events</url>
    </repository>
    <repository>
        <id>github-jwt-starter</id>
        <url>https://maven.pkg.github.com/OWNER/cinema-jwt-starter</url>
    </repository>
</repositories>
```

### dependencyManagement to Replicate

Each service standalone pom.xml needs these from root:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2024.0.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.8.4</version>
        </dependency>
        <!-- JJWT versions (only for auth-service) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.6</version>
        </dependency>
        <!-- ... jjwt-impl, jjwt-jackson ... -->
        <dependency>
            <groupId>net.logstash.logback</groupId>
            <artifactId>logstash-logback-encoder</artifactId>
            <version>8.0</version>
        </dependency>
        <dependency>
            <groupId>com.github.loki4j</groupId>
            <artifactId>loki-logback-appender</artifactId>
            <version>1.5.2</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Related Code Files

| Action | File |
|--------|------|
| Modify | `auth-service/pom.xml` |
| Modify | `notification-service/pom.xml` |
| Modify | `movie-service/pom.xml` |
| Modify | `booking-service/pom.xml` |
| Modify | `payment-service/pom.xml` |
| Modify | `api-gateway/pom.xml` |
| Modify | `eureka-server/pom.xml` |
| Modify | `config-server/pom.xml` |

## Implementation Steps

1. For each service that depends on `kafka-events`:
   - Replace `<version>${project.version}</version>` with `<version>1.0.0</version>`
2. For each service that depends on `jwt-auth-spring-boot-starter`:
   - Replace `<version>${project.version}</version>` with `<version>1.0.0</version>`
3. For each service pom.xml:
   - Change `<parent>` from monorepo to `spring-boot-starter-parent:3.4.3`
   - Add `<properties>` block with java.version, spring-cloud.version, etc.
   - Add `<dependencyManagement>` with Spring Cloud BOM + other managed deps
   - Add `<repositories>` for GitHub Packages
   - Add `<distributionManagement>` (optional, for future GHCR)
4. `mvn clean package -pl auth-service` — verify compilation
5. Repeat verification for each service
6. Commit updated pom.xml files to monorepo (reference commit before split)

## Todo List

- [ ] Update auth-service pom.xml (kafka-events 1.0.0, standalone parent)
- [ ] Update notification-service pom.xml (kafka-events 1.0.0, standalone parent)
- [ ] Update movie-service pom.xml (kafka-events + jwt-starter 1.0.0, standalone parent)
- [ ] Update booking-service pom.xml (kafka-events + jwt-starter 1.0.0, standalone parent)
- [ ] Update payment-service pom.xml (kafka-events + jwt-starter 1.0.0, standalone parent)
- [ ] Update api-gateway pom.xml (standalone parent, no internal deps)
- [ ] Update eureka-server pom.xml (standalone parent)
- [ ] Update config-server pom.xml (standalone parent)
- [ ] Verify `mvn clean package` for each service

## Success Criteria

- All 8 services compile with standalone pom.xml
- No references to `com.namnd:spring-jwt` parent remain
- No `${project.version}` references for inter-module deps

## Risk Assessment

- **Build break during transition**: run `mvn clean package` after each pom change
- **Missing managed dep versions**: compare each service's effective pom to catch unresolved versions
