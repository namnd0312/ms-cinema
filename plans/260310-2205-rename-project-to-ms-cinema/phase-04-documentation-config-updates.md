# Phase 04: Documentation and Config Updates

## Context Links
- [plan.md](plan.md)
- [docs/](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/docs/)
- [README.md](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/README.md)

## Overview
- **Priority:** Medium (no build impact; cosmetic/accuracy)
- **Status:** pending
- **Description:** Update all documentation files, README, and config references from old project name to ms-cinema. Includes `jwt-spring-security`, `spring-jwt`, `SpringJwt`, `springjwt` text references.

## Key Insights
- 8 docs files contain old project name references
- README.md has 2 references (`springjwt` package path, `SpringJwtApplication` class)
- `docs/deployment-guide.md` is heaviest: ~15 references to `spring-jwt.jar`, `jwt-spring-security` project name, `com.namnd.springjwt`
- `docs/deployment-troubleshooting.md` has ~7 references to `spring-jwt.jar`
- `docs/migration-java21.md` has ~5 references to `spring-jwt.jar`
- `docs/code-standards.md` has package name examples using `com.namnd.springjwt`
- Old plan/report files in `plans/` are historical -- NOT updated

## Requirements
- All `**Project:** jwt-spring-security` headers -> `**Project:** ms-cinema`
- All `spring-jwt.jar` refs -> `auth-service.jar` (already the actual artifact name post-migration)
- All `com.namnd.springjwt` text refs -> `com.namnd.cinema`
- All `SpringJwtApplication` text refs -> `CinemaAuthApplication`
- All `SpringJwtApplicationTests` text refs -> `CinemaAuthApplicationTests`
- All `jwt-spring-security/` directory refs -> `ms-cinema/`

## Related Code Files

### docs/ Files

| File | # Refs | Changes |
|------|--------|---------|
| `docs/codebase-summary.md` | ~15 | Project header, package paths, class names, module structure diagram |
| `docs/system-architecture.md` | ~5 | Project header, package logger ref, title references |
| `docs/code-standards.md` | ~6 | Project header, package examples (`com.namnd.springjwt`), logger config example |
| `docs/deployment-guide.md` | ~18 | Project header, git clone URL, jar name refs, `com.namnd.springjwt` logger, network name |
| `docs/deployment-troubleshooting.md` | ~8 | Project header, `spring-jwt.jar` command examples |
| `docs/api-documentation.md` | ~1 | Project header |
| `docs/project-roadmap.md` | ~1 | Project header |
| `docs/migration-java21.md` | ~5 | `spring-jwt.jar` refs in Dockerfile examples, build output |

### Root Files

| File | # Refs | Changes |
|------|--------|---------|
| `README.md` | ~2 | Package path `com/namnd/springjwt/`, `SpringJwtApplication` class name |

### Config Files (already handled in Phase 02, listed for completeness)

| File | Status |
|------|--------|
| `auth-service/src/main/resources/logback-spring.xml` | Handled in Phase 02 |
| `auth-service/pom.xml` mainClass | Handled in Phase 02 |

## Implementation Steps

### Step 1: Update docs/ project headers (6 files)

Replace `**Project:** jwt-spring-security` with `**Project:** ms-cinema` in:
- `docs/codebase-summary.md` (line 3)
- `docs/system-architecture.md` (line 3)
- `docs/code-standards.md` (line 3)
- `docs/deployment-guide.md` (line 3)
- `docs/deployment-troubleshooting.md` (line 3)
- `docs/api-documentation.md` (line 3)
- `docs/project-roadmap.md` (line 3)

### Step 2: Update docs/codebase-summary.md

- Line 3: project header (Step 1)
- Line 13: `jwt-spring-security/` -> `ms-cinema/`
- Line 42: `auth-service/src/main/java/com/namnd/springjwt/` -> `com/namnd/cinema/`
- Line 43: `SpringJwtApplication.java` -> `CinemaAuthApplication.java`
- Line 110: `**SpringJwtApplication.java**` -> `**CinemaAuthApplication.java**`
- Line 112: `com.namnd.springjwt` -> `com.namnd.cinema`
- Line 492: `SpringJwtApplicationTests` -> `CinemaAuthApplicationTests`
- All other `com.namnd.springjwt` text mentions

### Step 3: Update docs/system-architecture.md

- Line 3: project header (Step 1)
- Line 13: "JWT Spring Security" -> "MS Cinema"
- Line 799: `com.namnd.springjwt` logger ref -> `com.namnd.cinema`

### Step 4: Update docs/code-standards.md

- Line 3: project header (Step 1)
- Line 9: "jwt-spring-security project" -> "ms-cinema project"
- Line 33: `com.namnd.springjwt` package tree -> `com.namnd.cinema`
- Line 122: package example `com.namnd.springjwt.service` -> `com.namnd.cinema.service`
- Line 443: logger config `com.namnd.springjwt: debug` -> `com.namnd.cinema: debug`

### Step 5: Update docs/deployment-guide.md

- Line 3: project header (Step 1)
- Line 91: `git clone .../jwt-spring-security.git` -> `.../ms-cinema.git`
- Line 92: `cd jwt-spring-security` -> `cd ms-cinema`
- Lines 159, 175, 355, 393, 404, 438, 441, 583, 587: all `spring-jwt.jar` -> `auth-service.jar`
- Line 311: `jwt-spring-security_my-net` -> `ms-cinema_my-net`
- Line 712: `com.namnd.springjwt.controller` -> `com.namnd.cinema.controller`
- Line 743: `com.namnd.springjwt` -> `com.namnd.cinema`

### Step 6: Update docs/deployment-troubleshooting.md

- Line 3: project header (Step 1)
- Lines 31, 86, 99, 143: `spring-jwt.jar` -> `auth-service.jar`
- Line 206: `spring-jwt-0.0.0.jar` and `spring-jwt.jar` -> `auth-service.jar`

### Step 7: Update docs/migration-java21.md

- Lines 135, 216, 217, 223, 224: `spring-jwt.jar` -> `auth-service.jar`

### Step 8: Update README.md

- Line 254: `src/main/java/com/namnd/springjwt/` -> `src/main/java/com/namnd/cinema/`
- Line 255: `SpringJwtApplication.java` -> `CinemaAuthApplication.java`

### Step 9: Final grep verification

```bash
grep -r "springjwt\|spring-jwt\|jwt-spring-security\|SpringJwt" \
  --include="*.md" --include="*.yml" --include="*.xml" --include="*.java" \
  --exclude-dir=plans --exclude-dir=.git \
  /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/
```

Expected: zero matches (excluding `jwt-auth-spring-boot-*` module names which are kept).

## Todo List

- [ ] Update project headers in 7 docs files
- [ ] Update `docs/codebase-summary.md` package paths, class names, module diagram
- [ ] Update `docs/system-architecture.md` title and logger refs
- [ ] Update `docs/code-standards.md` package examples and logger config
- [ ] Update `docs/deployment-guide.md` (~15 replacements)
- [ ] Update `docs/deployment-troubleshooting.md` (~7 replacements)
- [ ] Update `docs/migration-java21.md` (~5 replacements)
- [ ] Update `README.md` (2 replacements)
- [ ] Run final grep verification -- zero matches outside plans/ and jwt-auth-* modules

## Success Criteria
- Zero references to `jwt-spring-security` as project name in active docs (plans/ excluded)
- Zero references to `spring-jwt.jar` in docs (replaced with `auth-service.jar`)
- Zero references to `com.namnd.springjwt` in docs (replaced with `com.namnd.cinema`)
- Zero references to `SpringJwtApplication` in docs (replaced with `CinemaAuthApplication`)
- `jwt-auth-spring-boot-*` module names remain unchanged (these are JWT library names)

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Missed reference in docs | Low | Final grep sweep catches stragglers |
| Broken markdown links | Low | No internal doc links reference project name in URL |
| Historical plans become inconsistent | None | Explicitly out of scope; plans are snapshots |
