---
title: "Rename project from jwt-spring-security to ms-cinema"
description: "Rename all project references, packages, Docker configs from jwt-spring-security to ms-cinema"
status: pending
priority: P2
effort: 2h
branch: master
tags: [rename, refactor, project-identity]
created: 2026-03-10
---

# Rename Project: jwt-spring-security -> ms-cinema

## Rename Mapping

| Old | New | Scope |
|-----|-----|-------|
| `jwt-spring-security` (project name) | `ms-cinema` | docs, README, references |
| `spring-jwt` (Maven artifactId/name) | `ms-cinema` | root pom.xml, all child pom.xml parent refs |
| `com.namnd.springjwt` (Java package) | `com.namnd.cinema` | 57 Java files + directories + imports + configs |
| `SpringJwtApplication` (main class) | `CinemaAuthApplication` | class name, test, pom.xml mainClass |
| `SpringJwtApplicationTests` (test) | `CinemaAuthApplicationTests` | test class |
| `spring-jwt.jar` (artifact) | `auth-service.jar` | docs only (already changed in Dockerfile) |

## Phases

| # | Phase | Status | Effort | File |
|---|-------|--------|--------|------|
| 1 | Maven/pom.xml renames | pending | 20min | [phase-01](phase-01-maven-pom-renames.md) |
| 2 | Java package rename | pending | 45min | [phase-02](phase-02-java-package-rename.md) |
| 3 | Docker/infrastructure renames | pending | 15min | [phase-03](phase-03-docker-infrastructure-renames.md) |
| 4 | Documentation and config updates | pending | 40min | [phase-04](phase-04-documentation-config-updates.md) |

## Execution Order

1. Phase 1 (Maven) -- must go first; child modules reference parent artifactId
2. Phase 2 (Java packages) -- directory moves + import updates
3. Phase 3 (Docker) -- no code deps, can overlap with Phase 4
4. Phase 4 (Docs) -- final pass, update all text references

## Key Risks

- **Package rename breaks imports**: 57 Java files need directory move + package statement + import updates
- **Spring component scan**: `@SpringBootApplication` default scan uses package of main class; must match new package
- **pom.xml mainClass**: auth-service pom.xml hardcodes `com.namnd.springjwt.SpringJwtApplication`
- **logback-spring.xml**: logger name references `com.namnd.springjwt`

## Verification

After all phases: `mvn clean compile -f /path/to/root/pom.xml` must pass all 11 modules.

## Out of Scope

- Old plan files in `plans/` -- historical, not updated
- Docker network name `my-net` -- unrelated to project name
- `jwt-auth-spring-boot-*` module names -- these are JWT library names, not project identity
- `com.namnd.jwt.autoconfigure` package -- separate JWT library, not auth-service
