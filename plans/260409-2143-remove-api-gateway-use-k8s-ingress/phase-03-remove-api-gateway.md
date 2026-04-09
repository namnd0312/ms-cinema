# Phase 03: Remove API Gateway Module

## Context Links
- [plan.md](plan.md)
- [Root pom.xml](../../pom.xml)
- [docker-compose.yml](../../docker-compose.yml)
- [api-gateway directory](../../api-gateway/)
- [K8s api-gateway](../../k8s/api-gateway/)
- [deploy-all.sh](../../k8s/deploy-all.sh)
- [teardown.sh](../../k8s/teardown.sh)

## Overview
- **Priority:** P1
- **Status:** pending
- **Description:** Delete entire api-gateway module, remove all references from build/deploy configs

## Key Insights
- api-gateway is a Maven module with Spring Cloud Gateway MVC, HTTP logging filter, Swagger aggregation
- All routing now handled by K8s Ingress (phase 01) or nginx.conf (phase 02)
- Swagger aggregation lost; each service already has its own `/swagger-ui.html`
- HttpLoggingFilter replaced by K8s-level logging (ingress access logs)

## Requirements

### Functional
- api-gateway directory fully deleted
- No build errors after removal
- docker-compose starts without api-gateway service
- K8s deploys without api-gateway

### Non-Functional
- `mvn clean compile` succeeds
- `docker compose up` succeeds without api-gateway
- `k8s/deploy-all.sh` runs without api-gateway references

## Architecture

After removal:
```
ms-cinema/
├── kafka-events (shared lib)
├── jwt-auth-autoconfigure (shared lib)
├── auth-service
├── movie-service
├── booking-service
├── payment-service
├── notification-service
├── audit-service
└── cinema-frontend
```

## Related Code Files

### Delete
- `api-gateway/` — entire directory (Maven module)
- `k8s/api-gateway/` — entire directory (K8s manifests)

### Modify
- `pom.xml` (root) — remove `<module>api-gateway</module>`
- `docker-compose.yml` — remove api-gateway service block, update cinema-frontend `depends_on`
- `k8s/deploy-all.sh` — remove api-gateway from SERVICES array and wait loop
- `k8s/teardown.sh` — remove api-gateway references (if any)

## Implementation Steps

1. **Delete `api-gateway/` directory**
   ```bash
   rm -rf api-gateway/
   ```

2. **Delete `k8s/api-gateway/` directory**
   ```bash
   rm -rf k8s/api-gateway/
   ```

3. **Update root `pom.xml`** — remove line:
   ```xml
   <module>api-gateway</module>
   ```

4. **Update `docker-compose.yml`**:
   - Remove entire `api-gateway:` service block (lines ~68-79)
   - Update `cinema-frontend` service: remove `depends_on: - api-gateway`
   - Add `depends_on` for the backend services cinema-frontend actually needs (or remove depends_on entirely — nginx will retry upstream)

5. **Update `k8s/deploy-all.sh`**:
   - Remove `api-gateway` from `SERVICES=()` array
   - Remove `api-gateway` from both deploy and wait loops
   - Remove "API Gateway" from summary output lines
   - Add ingress apply step: `kubectl apply -f "${SCRIPT_DIR}/ingress.yml"` after services deploy

6. **Update `k8s/teardown.sh`** — remove api-gateway references if present

7. **Verify build**: `mvn clean compile -q`

## Todo List

- [ ] Delete `api-gateway/` directory
- [ ] Delete `k8s/api-gateway/` directory
- [ ] Remove `<module>api-gateway</module>` from root pom.xml
- [ ] Remove api-gateway service from docker-compose.yml
- [ ] Remove cinema-frontend depends_on api-gateway
- [ ] Update k8s/deploy-all.sh (remove api-gateway, add ingress apply)
- [ ] Update k8s/teardown.sh if needed
- [ ] Run `mvn clean compile` — must pass
- [ ] Run `docker compose config` — must be valid

## Success Criteria
- `api-gateway/` and `k8s/api-gateway/` directories do not exist
- `mvn clean compile` passes with no errors
- `docker compose config` shows no api-gateway service
- `grep -r "api-gateway" pom.xml docker-compose.yml k8s/` returns nothing

## Risk Assessment
- **Missing references**: Other files may reference api-gateway (README, docs); handled in phase 04
- **Build failure**: If other modules depend on api-gateway; unlikely as gateway depends on nothing and no module depends on it

## Security Considerations
- No security impact; gateway had no auth logic (pass-through only)
- Services already validate JWT independently via jwt-auth-autoconfigure

## Next Steps
- Phase 04: Update README and docs to reflect new architecture
