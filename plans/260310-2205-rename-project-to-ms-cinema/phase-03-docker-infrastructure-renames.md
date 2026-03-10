# Phase 03: Docker and Infrastructure Renames

## Context Links
- [plan.md](plan.md)
- [docker-compose.yml](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/docker-compose.yml)
- [Dockerfile (root)](/Users/admin/Desktop/DEV/BACK_END/jwt-spring-security/Dockerfile)

## Overview
- **Priority:** Medium (no code deps, but affects container naming and network)
- **Status:** pending
- **Description:** Docker network `my-net` is generic enough to keep. Docker compose does not reference `jwt-spring-security` or `spring-jwt` in container/image names (verified via grep). Dockerfiles also clean. Only the auto-generated Docker network name `jwt-spring-security_my-net` derives from the project directory name, which changes only when the folder itself is renamed.

## Key Insights
- Grep of `docker-compose.yml` for `jwt-spring-security` and `spring-jwt` returned **zero matches**
- Grep of all 9 Dockerfiles for same patterns returned **zero matches**
- Docker Compose auto-prefixes network/container names with directory name (`jwt-spring-security_my-net`); this changes automatically when parent folder is renamed
- `monitoring/prometheus/prometheus.yml` and Grafana configs have **zero matches** for project name patterns
- Network name `my-net` is generic -- no rename needed

## Requirements
- Verify no Docker/infra files reference old project names (DONE -- confirmed zero matches)
- If parent directory gets renamed to `ms-cinema/`, Docker Compose auto-prefix changes to `ms-cinema_my-net`
- Document that existing Docker volumes/networks from old name must be cleaned up manually

## Related Code Files

| File | Status | Action |
|------|--------|--------|
| `docker-compose.yml` | Clean | No changes needed |
| `Dockerfile` (root) | Clean | No changes needed |
| `auth-service/Dockerfile` | N/A | Does not exist (root Dockerfile used) |
| `eureka-server/Dockerfile` | Clean | No changes needed |
| `config-server/Dockerfile` | Clean | No changes needed |
| `api-gateway/Dockerfile` | Clean | No changes needed |
| `movie-service/Dockerfile` | Clean | No changes needed |
| `booking-service/Dockerfile` | Clean | No changes needed |
| `payment-service/Dockerfile` | Clean | No changes needed |
| `notification-service/Dockerfile` | Clean | No changes needed |
| `cinema-frontend/Dockerfile` | Clean | No changes needed |
| `monitoring/prometheus/prometheus.yml` | Clean | No changes needed |
| `monitoring/grafana/provisioning/**` | Clean | No changes needed |

## Implementation Steps

1. **No file changes required** -- all Docker/infra files are already clean of old project name references.

2. **Optional: Rename parent directory** (outside this plan's scope, but documents impact):
   ```bash
   # If user renames folder:
   mv /Users/admin/Desktop/DEV/BACK_END/jwt-spring-security /Users/admin/Desktop/DEV/BACK_END/ms-cinema
   ```

3. **Cleanup old Docker resources** (run after directory rename if applicable):
   ```bash
   # Remove old containers/networks from previous directory name
   docker compose down
   docker network rm jwt-spring-security_my-net 2>/dev/null || true
   # Rebuild with new name prefix
   docker compose up -d --build
   ```

## Todo List

- [x] Verify `docker-compose.yml` has no old-name references
- [x] Verify all Dockerfiles have no old-name references
- [x] Verify monitoring configs have no old-name references
- [ ] Document cleanup steps for old Docker networks/volumes (in Phase 04 docs)

## Success Criteria
- No file modifications needed in this phase (already clean)
- After directory rename + `docker compose up`, all services start with new network prefix

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Old Docker volumes persist | Low | `docker compose down -v` removes volumes; document in deployment guide |
| CI/CD pipeline references old directory | Low | Out of scope; no CI/CD config in repo |
