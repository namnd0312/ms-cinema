# Phase 03: Compile and Test

## Context Links
- [Phase 01](./phase-01-add-service-name-filter-to-shared-library.md)
- [Code Standards - Compilation](../docs/code-standards.md) (`mvn clean compile && mvn test`)

## Overview
- **Priority:** P2
- **Status:** pending
- **Description:** Compile all modules, verify filter works, run existing tests

## Key Insights
- Must compile shared lib first (parent POM multi-module build handles this)
- All 6 services depend on shared lib -- one `mvn clean compile` from root suffices
- No new tests needed for a simple header filter (YAGNI), but verify existing tests pass

## Requirements

### Functional
- All modules compile without errors
- Existing tests pass
- `X-Service-Name` header present in HTTP responses

### Non-Functional
- No regressions in existing functionality

## Architecture
Build order (Maven reactor): `jwt-auth-autoconfigure` -> `kafka-events` -> 6 services

## Related Code Files

### Verify
- All files modified in Phase 01

## Implementation Steps

### Step 1: Compile from project root
```bash
cd /Users/admin/Desktop/DEV/BACK_END/ms-cinema
mvn clean compile
```
Fix any compilation errors.

### Step 2: Run tests
```bash
mvn test
```
All existing tests must pass. No new tests required for this simple filter.

### Step 3: Manual verification (optional, post-deploy)
Start any service locally and verify:
```bash
curl -v http://localhost:8082/api/movies 2>&1 | grep -i x-service-name
# Expected: X-Service-Name: movie-service
```

### Step 4: Commit
```bash
git add jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/ServiceNameHeaderFilter.java \
        jwt-auth-autoconfigure/src/main/java/com/namnd/jwt/autoconfigure/JwtAutoConfiguration.java
git commit -m "feat: add X-Service-Name response header via shared library filter"
```

## Todo List
- [ ] `mvn clean compile` -- no errors
- [ ] `mvn test` -- all tests pass
- [ ] (Optional) Manual curl verification
- [ ] Commit changes

## Success Criteria
- Zero compilation errors across all modules
- All existing tests pass
- Header verified via curl or browser Network tab

## Risk Assessment
| Risk | Impact | Mitigation |
|------|--------|------------|
| Shared lib change breaks consumer compile | Medium | Compile all modules together |
| FilterRegistrationBean conflicts with service filters | Low | Uses unique bean name, @ConditionalOnMissingBean |

## Security Considerations
- N/A for compile/test phase

## Next Steps
- Deploy to K8s and verify header in browser Network tab
- If JS header access needed later, add CORS `Access-Control-Expose-Headers` config
