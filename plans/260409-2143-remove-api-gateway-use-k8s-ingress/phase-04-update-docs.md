# Phase 04: Update Documentation

## Context Links
- [plan.md](plan.md)
- [README.md](../../README.md)
- [docs/codebase-summary.md](../../docs/codebase-summary.md)
- [docs/system-architecture.md](../../docs/system-architecture.md)

## Overview
- **Priority:** P3 (non-blocking but important for accuracy)
- **Status:** pending
- **Description:** Remove all api-gateway references from README and docs; document K8s Ingress routing

## Key Insights
- README, codebase-summary, system-architecture all reference api-gateway as infrastructure module
- Architecture diagrams show gateway as single entry point — update to show Ingress
- Module count changes: 9 → 8 Maven modules (api-gateway removed)
- Swagger aggregation docs need removal; note per-service Swagger access

## Requirements

### Functional
- All docs accurately reflect new architecture (no api-gateway)
- K8s Ingress routing documented
- Docker-compose direct routing documented

### Non-Functional
- Docs concise, no stale references

## Architecture

New high-level diagram for docs:
```
                    CLIENT (Web/Mobile)
                          │
              ┌───────────▼────────────┐
              │   K8s NGINX Ingress    │  (or nginx in docker-compose)
              │   path-based routing   │
              └───────────┬────────────┘
                          │
     ┌──────┬──────┬──────┼──────┬──────┬──────┐
     ▼      ▼      ▼      ▼      ▼      ▼      ▼
   auth   movie  booking  pay   notif  audit  frontend
   :8081  :8082  :8083   :8084  :8085  :8086   :80
```

## Related Code Files

### Modify
- `README.md` — remove api-gateway references, update architecture section, port list, access URLs
- `docs/codebase-summary.md` — change "9-module" to "8-module", remove api-gateway section, update module tree
- `docs/system-architecture.md` — replace gateway diagram with Ingress, update module architecture section
- `docs/code-standards.md` — no api-gateway references expected; verify
- `docs/deployment-guide.md` — update K8s deployment steps if exists
- `docs/project-changelog.md` — add entry for api-gateway removal if exists

## Implementation Steps

1. **Update `README.md`**:
   - Remove api-gateway from module list
   - Update architecture diagram (replace gateway box with Ingress)
   - Update port table (remove :8080 api-gateway)
   - Update "Access" URLs (remove `http://localhost:8080/swagger-ui.html`)
   - Update docker-compose instructions
   - Add note: Swagger UI available per-service at `http://<service>:<port>/swagger-ui.html`

2. **Update `docs/codebase-summary.md`**:
   - Change "9-module" → "8-module" in header
   - Remove "Infrastructure (1 module) → api-gateway" from tree
   - Remove api-gateway details section
   - Add brief note about K8s Ingress for routing

3. **Update `docs/system-architecture.md`**:
   - Replace api-gateway section with K8s Ingress section
   - Update high-level diagram
   - Remove "Infrastructure Services (1 module)" section about gateway
   - Document Ingress routing rules
   - Note docker-compose uses nginx.conf direct routing

4. **Check and update other docs** if they reference api-gateway:
   - `docs/deployment-guide.md`
   - `docs/project-changelog.md`
   - `docs/development-roadmap.md`

## Todo List

- [ ] Update README.md — remove gateway, add Ingress info
- [ ] Update docs/codebase-summary.md — 9→8 modules, remove gateway section
- [ ] Update docs/system-architecture.md — replace gateway with Ingress diagram
- [ ] Check docs/deployment-guide.md for gateway references
- [ ] Add changelog entry for gateway removal
- [ ] Grep all docs/ for "api-gateway" and "api_gateway" — fix any remaining refs

## Success Criteria
- `grep -ri "api-gateway" README.md docs/` returns zero results (except changelog noting removal)
- Architecture diagrams show Ingress, not gateway
- Module count accurate (8 modules)

## Risk Assessment
- **Stale references**: Easy to miss a doc file; use grep to verify
- **External docs**: If wiki or Confluence exists, those need manual update (out of scope)

## Security Considerations
- No security impact; documentation-only changes

## Next Steps
- Plan complete after this phase
- Future consideration: add TLS termination to Ingress for production
