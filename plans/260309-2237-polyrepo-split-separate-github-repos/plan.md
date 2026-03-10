---
title: "Polyrepo Split — Separate GitHub Repositories"
description: "Split monorepo into 12 independent GitHub repos with GitHub Packages for shared libs"
status: pending
priority: P1
effort: 12h
branch: master
tags: [infrastructure, devops, maven, github-packages, polyrepo]
created: 2026-03-09
---

# Polyrepo Split Plan

## Overview

Split the current 11-module Maven monorepo into 12 independent GitHub repositories. Shared libraries publish to GitHub Packages Maven registry; services consume them as external dependencies.

## Dependency Graph (split order matters)

```
kafka-events (no internal deps)
jwt-auth-spring-boot-autoconfigure (no internal deps)
jwt-auth-spring-boot-starter (depends on autoconfigure)
  |
  v -- consumers --
auth-service (kafka-events)
notification-service (kafka-events)
movie-service (jwt-starter, kafka-events)
booking-service (jwt-starter, kafka-events)
payment-service (jwt-starter, kafka-events)
api-gateway (no internal deps)
eureka-server (no internal deps)
config-server (no internal deps)
cinema-frontend (Angular, no Java deps)
cinema-infra (docker-compose, monitoring, docs)
```

## Phases

| # | Phase | Status | Effort |
|---|-------|--------|--------|
| 1 | [Preparation](phase-01-preparation.md) — standalone pom.xml, .gitignore, GitHub Packages setup | pending | 2h |
| 2 | [Split shared libs](phase-02-split-shared-libs.md) — kafka-events, jwt-starter | pending | 2h |
| 3 | [Update service deps](phase-03-update-service-dependencies.md) — replace `${project.version}` with explicit versions | pending | 2h |
| 4 | [Split services](phase-04-split-services.md) — 8 Java services via git subtree | pending | 3h |
| 5 | [Split frontend](phase-05-split-frontend.md) — Angular cinema-frontend | pending | 1h |
| 6 | [Create infra repo](phase-06-create-infra-repo.md) — docker-compose, monitoring, docs | pending | 2h |

## Key Decisions

1. `git subtree split --prefix=<dir>` preserves commit history per module
2. GitHub Packages Maven registry at `https://maven.pkg.github.com/OWNER/REPO`
3. Shared libs versioned at `1.0.0` (semver from day one)
4. docker-compose switches from `build:` to `image: ghcr.io/OWNER/<service>:latest`
5. Each repo gets standalone pom.xml (spring-boot-starter-parent, no monorepo parent)
6. CI/CD: each repo gets its own GitHub Actions workflow

## Risk Assessment

- **Breaking builds**: mitigated by splitting libs first, publishing, then updating consumers
- **Git history loss**: mitigated by using `git subtree split` (preserves history)
- **Credential leak**: GitHub Packages requires PAT; use repository secrets in CI
