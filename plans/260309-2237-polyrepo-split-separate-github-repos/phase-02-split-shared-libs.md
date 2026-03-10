---
title: "Phase 2 — Split Shared Libraries"
status: pending
priority: P1
effort: 2h
---

# Phase 2 — Split Shared Libraries

## Context Links
- [Plan overview](plan.md)
- [Phase 1 — Preparation](phase-01-preparation.md)

## Overview

Extract `kafka-events`, `jwt-auth-spring-boot-autoconfigure`, and `jwt-auth-spring-boot-starter` into two new GitHub repos using `git subtree split`. Publish `1.0.0` to GitHub Packages.

## Key Insights

- `kafka-events` has zero internal deps (only jackson-annotations, jackson-databind, jackson-datatype-jsr310)
- `jwt-auth-spring-boot-autoconfigure` has zero internal deps (JJWT + Spring Boot starters as optional)
- `jwt-auth-spring-boot-starter` depends only on `autoconfigure` — must ship together in same repo
- Split order: kafka-events first (no deps), then jwt-starter (autoconfigure + starter together)

## Requirements

### Target Repos

| Repo Name | Source Dirs | Version |
|-----------|------------|---------|
| `cinema-kafka-events` | `kafka-events/` | 1.0.0 |
| `cinema-jwt-starter` | `jwt-auth-spring-boot-autoconfigure/` + `jwt-auth-spring-boot-starter/` | 1.0.0 |

### Functional
- Preserve git commit history for each module
- Publish `1.0.0` jar to GitHub Packages Maven registry
- Include GitHub Actions workflow for future releases

### Non-Functional
- `mvn clean install` must pass in isolation
- Other repos can resolve artifacts via `<repositories>` config

## Architecture

### cinema-kafka-events repo structure

```
cinema-kafka-events/
├── .github/workflows/publish.yml
├── .gitignore
├── pom.xml                          # standalone, version 1.0.0
└── src/main/java/com/namnd/kafka/   # existing package structure
```

### cinema-jwt-starter repo structure

```
cinema-jwt-starter/
├── .github/workflows/publish.yml
├── .gitignore
├── pom.xml                          # parent pom (packaging: pom, 2 modules)
├── jwt-auth-spring-boot-autoconfigure/
│   ├── pom.xml                      # child, version from parent
│   └── src/...
└── jwt-auth-spring-boot-starter/
    ├── pom.xml                      # child, depends on autoconfigure
    └── src/... (empty, thin wrapper)
```

**Note:** jwt-starter is a 2-module Maven project because `starter` depends on `autoconfigure`. Keeping them in the same repo avoids circular publishing.

## Related Code Files

| Action | File |
|--------|------|
| Split | `kafka-events/` → `cinema-kafka-events` |
| Split | `jwt-auth-spring-boot-autoconfigure/` → `cinema-jwt-starter/jwt-auth-spring-boot-autoconfigure/` |
| Split | `jwt-auth-spring-boot-starter/` → `cinema-jwt-starter/jwt-auth-spring-boot-starter/` |
| Create | `cinema-kafka-events/pom.xml` (standalone) |
| Create | `cinema-jwt-starter/pom.xml` (parent pom) |
| Modify | `cinema-jwt-starter/jwt-auth-spring-boot-starter/pom.xml` — version `1.0.0` for autoconfigure dep |

## Implementation Steps

### cinema-kafka-events

1. Create GitHub repo `cinema-kafka-events`
2. `git subtree split --prefix=kafka-events -b split/kafka-events` in monorepo
3. Clone new repo, add monorepo as remote, cherry-pick split branch
4. Replace `pom.xml` with standalone version (from Phase 1 template)
5. `mvn clean install` — verify it compiles
6. Add `.github/workflows/publish.yml`
7. Tag `v1.0.0`, push — triggers publish to GitHub Packages
8. Verify artifact at `https://maven.pkg.github.com/OWNER/cinema-kafka-events`

### cinema-jwt-starter

1. Create GitHub repo `cinema-jwt-starter`
2. Extract autoconfigure:
   ```bash
   git subtree split --prefix=jwt-auth-spring-boot-autoconfigure -b split/jwt-autoconfigure
   ```
3. Extract starter:
   ```bash
   git subtree split --prefix=jwt-auth-spring-boot-starter -b split/jwt-starter
   ```
4. In new repo, create parent pom.xml (packaging: pom) with 2 modules
5. Import split branches into subdirectories via `git read-tree` or manual copy
6. Update child pom.xml files:
   - `autoconfigure/pom.xml` — standalone with JJWT version management
   - `starter/pom.xml` — depends on `autoconfigure:1.0.0` (same reactor, use `${project.version}`)
7. `mvn clean install` — verify both modules compile
8. Add `.github/workflows/publish.yml`
9. Tag `v1.0.0`, push

### Alternative (simpler, loses per-file history)

If `git subtree split` proves complex for merging two prefixes:
```bash
# Just copy files, init fresh repo
gh repo create OWNER/cinema-jwt-starter --public
git init cinema-jwt-starter && cd cinema-jwt-starter
cp -r ../jwt-auth-spring-boot-autoconfigure .
cp -r ../jwt-auth-spring-boot-starter .
# Create parent pom.xml, .gitignore, workflow
git add -A && git commit -m "feat: initial polyrepo split from monorepo"
git tag v1.0.0 && git push origin main --tags
```

Trade-off: loses commit-level history but simpler execution. Monorepo remains as archive.

## Todo List

- [ ] Create `cinema-kafka-events` GitHub repo
- [ ] `git subtree split` kafka-events
- [ ] Standalone pom.xml for kafka-events
- [ ] `mvn clean install` passes for kafka-events
- [ ] GitHub Actions publish workflow for kafka-events
- [ ] Tag + publish kafka-events v1.0.0
- [ ] Create `cinema-jwt-starter` GitHub repo
- [ ] Extract autoconfigure + starter into cinema-jwt-starter
- [ ] Parent pom.xml + child pom.xml files
- [ ] `mvn clean install` passes for jwt-starter
- [ ] GitHub Actions publish workflow for jwt-starter
- [ ] Tag + publish jwt-starter v1.0.0

## Success Criteria

- `com.namnd:kafka-events:1.0.0` resolvable from GitHub Packages
- `com.namnd:jwt-auth-spring-boot-starter:1.0.0` resolvable from GitHub Packages
- Both repos build independently with `mvn clean package`

## Risk Assessment

- **git subtree complexity**: mitigated by alternative copy approach
- **GitHub Packages auth**: requires `settings.xml` with PAT for first publish; CI uses GITHUB_TOKEN
- **Version mismatch**: lock to `1.0.0` everywhere; bump only when API changes
