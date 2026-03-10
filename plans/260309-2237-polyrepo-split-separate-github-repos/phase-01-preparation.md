---
title: "Phase 1 — Preparation"
status: pending
priority: P1
effort: 2h
---

# Phase 1 — Preparation

## Context Links
- [Plan overview](plan.md)
- [Root pom.xml](/pom.xml)
- [Docker Compose](/docker-compose.yml)

## Overview

Prepare standalone pom.xml templates, .gitignore, and GitHub Packages publishing configuration before any repo splits occur. This phase produces reusable artifacts that phases 2-6 consume.

## Key Insights

- Current modules use `<parent>` pointing to monorepo root pom (`com.namnd:spring-jwt:0.0.1-SNAPSHOT`)
- Root pom provides: Spring Cloud BOM, JJWT version management, springdoc version, logstash/loki versions
- Each standalone pom must replicate relevant `<dependencyManagement>` entries
- `${project.version}` used for inter-module refs (kafka-events, jwt-starter) — must become explicit version

## Requirements

### Functional
- Standalone pom.xml template for shared libs (no spring-boot-maven-plugin)
- Standalone pom.xml template for services (with spring-boot-maven-plugin)
- GitHub Packages `<distributionManagement>` block for publishing
- GitHub Packages `<repositories>` block for consuming
- Shared `.gitignore` template

### Non-Functional
- All pom.xml must compile independently (no parent reference to monorepo)
- GitHub Actions workflow template for `mvn deploy` to GitHub Packages

## Architecture

### Standalone POM Structure (Shared Lib)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.3</version>
</parent>
<groupId>com.namnd</groupId>
<artifactId>kafka-events</artifactId>
<version>1.0.0</version>

<properties>
    <java.version>21</java.version>
    <jjwt.version>0.12.6</jjwt.version>
    <spring-cloud.version>2024.0.1</spring-cloud.version>
</properties>

<distributionManagement>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/OWNER/cinema-kafka-events</url>
    </repository>
</distributionManagement>
```

### Standalone POM Structure (Service)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.3</version>
</parent>
<groupId>com.namnd</groupId>
<artifactId>auth-service</artifactId>
<version>1.0.0</version>

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

### .gitignore Template

```
**/target/
*.class
*.jar
*.war
!.mvn/wrapper/maven-wrapper.jar
.idea/
*.iml
.vscode/
.DS_Store
node_modules/
dist/
.env
*.log
```

### GitHub Actions Workflow Template (lib publish)

```yaml
name: Publish to GitHub Packages
on:
  push:
    tags: ['v*']
jobs:
  publish:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn deploy -DskipTests
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### GitHub Actions Workflow Template (service build + GHCR push)

```yaml
name: Build & Push Docker Image
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - name: Configure Maven for GitHub Packages
        run: |
          mkdir -p ~/.m2
          cat > ~/.m2/settings.xml << 'EOF'
          <settings>
            <servers>
              <server><id>github-kafka-events</id><username>${env.GITHUB_ACTOR}</username><password>${env.GITHUB_TOKEN}</password></server>
              <server><id>github-jwt-starter</id><username>${env.GITHUB_ACTOR}</username><password>${env.GITHUB_TOKEN}</password></server>
            </servers>
          </settings>
          EOF
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      - run: mvn clean package -DskipTests
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/OWNER/${{ github.event.repository.name }}:latest
```

## Related Code Files

| Action | File |
|--------|------|
| Template | `.gitignore` (shared across all new repos) |
| Template | `pom.xml` per module (standalone, no monorepo parent) |
| Template | `.github/workflows/publish.yml` (lib) |
| Template | `.github/workflows/build.yml` (service) |
| Template | `~/.m2/settings.xml` (local dev, GitHub Packages auth) |

## Implementation Steps

1. Create `.gitignore` template file
2. Create standalone `pom.xml` for `kafka-events` — replicate only jackson deps from root
3. Create standalone `pom.xml` for `jwt-auth-spring-boot-autoconfigure` — replicate JJWT version mgmt
4. Create standalone `pom.xml` for `jwt-auth-spring-boot-starter` — depends on autoconfigure `1.0.0`
5. Create standalone `pom.xml` template for each service — include `<repositories>` for GitHub Packages
6. Create GitHub Actions workflow for lib publish (`mvn deploy`)
7. Create GitHub Actions workflow for service build + GHCR Docker push
8. Document local dev setup: `~/.m2/settings.xml` with GitHub PAT

## Todo List

- [ ] .gitignore template
- [ ] kafka-events standalone pom.xml
- [ ] jwt-auth-spring-boot-autoconfigure standalone pom.xml
- [ ] jwt-auth-spring-boot-starter standalone pom.xml
- [ ] Service pom.xml template (with GitHub Packages repos)
- [ ] GitHub Actions: lib publish workflow
- [ ] GitHub Actions: service build workflow
- [ ] Local dev: document settings.xml setup

## Success Criteria

- Each standalone pom.xml compiles when placed in an isolated directory (no monorepo parent)
- `mvn deploy` publishes jar to GitHub Packages
- `mvn clean package` for services can resolve shared libs from GitHub Packages

## Security Considerations

- GitHub PAT with `read:packages` scope needed for local dev
- CI uses `GITHUB_TOKEN` (auto-provided) — no manual secret management
- Never commit `settings.xml` with PAT to any repo
