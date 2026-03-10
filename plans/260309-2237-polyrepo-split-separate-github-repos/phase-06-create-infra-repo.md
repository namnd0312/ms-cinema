---
title: "Phase 6 — Create Infrastructure Repo"
status: pending
priority: P2
effort: 2h
---

# Phase 6 — Create Infrastructure Repo

## Context Links
- [Plan overview](plan.md)
- [Docker Compose](/docker-compose.yml)

## Overview

Create `cinema-infra` repo containing: updated docker-compose.yml (image-based, not build-based), monitoring configs, init-databases.sql, and project docs/plans.

## Key Insights

- Current docker-compose uses `build: context:` for each service — must change to `image: ghcr.io/OWNER/<service>:latest`
- Monitoring configs (prometheus.yml, grafana provisioning) are infra-only — no service code
- `init-databases.sql` creates databases for all services; stays in infra
- `config-server` has a `config-repo/` with shared YAML — this could live in infra or in config-server repo

## Requirements

### Target Repo

| Repo | Contents |
|------|----------|
| `cinema-infra` | docker-compose.yml, monitoring/, init-databases.sql, docs/, plans/, .env.example |

## Architecture

### Repo Structure

```
cinema-infra/
├── .gitignore
├── .env.example                      # template for secrets
├── docker-compose.yml                # image-based (no build contexts)
├── init-databases.sql
├── monitoring/
│   ├── prometheus/prometheus.yml
│   ├── grafana/provisioning/
│   │   ├── datasources/datasources.yml
│   │   └── dashboards/
│   │       ├── dashboards.yml
│   │       └── json/*.json
│   └── loki/loki-config.yml
├── docs/
│   ├── project-overview-pdr.md
│   ├── codebase-summary.md
│   ├── system-architecture.md
│   ├── code-standards.md
│   ├── deployment-guide.md
│   └── api-documentation.md
└── plans/                            # historical plans archive
```

### Updated docker-compose.yml (key changes)

```yaml
services:
  # Infrastructure (unchanged — use public images)
  postgres-service:
    image: 'postgres:16-alpine'
  kafka:
    image: 'apache/kafka:3.7.0'
  redis-service:
    image: 'redis:7-alpine'

  # Services (CHANGED: image instead of build)
  eureka-server:
    image: ghcr.io/OWNER/cinema-eureka-server:latest
  config-server:
    image: ghcr.io/OWNER/cinema-config-server:latest
  api-gateway:
    image: ghcr.io/OWNER/cinema-api-gateway:latest
  auth-service:
    image: ghcr.io/OWNER/cinema-auth-service:latest
  movie-service:
    image: ghcr.io/OWNER/cinema-movie-service:latest
  booking-service:
    image: ghcr.io/OWNER/cinema-booking-service:latest
  payment-service:
    image: ghcr.io/OWNER/cinema-payment-service:latest
  notification-service:
    image: ghcr.io/OWNER/cinema-notification-service:latest
  cinema-frontend:
    image: ghcr.io/OWNER/cinema-frontend:latest
    ports:
      - "4200:80"

  # Monitoring (unchanged)
  prometheus:
    image: prom/prometheus:latest
  grafana:
    image: grafana/grafana-oss:latest
  loki:
    image: grafana/loki:3.0.0
```

### .env.example

```bash
# GitHub Container Registry
GHCR_OWNER=your-github-username

# Database
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# Auth service
JWT_SECRET=your-jwt-secret

# Notification service
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Payment service
STRIPE_API_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

## Implementation Steps

1. Create GitHub repo: `gh repo create OWNER/cinema-infra --public`
2. Copy files to new repo:
   - `docker-compose.yml` (modify: replace `build:` with `image:`)
   - `init-databases.sql`
   - `monitoring/` (entire directory)
   - `docs/` (entire directory)
   - `plans/` (entire directory as archive)
3. Create `.env.example` with placeholder secrets
4. Update `docker-compose.yml`:
   - Replace all `build:` blocks with `image: ghcr.io/OWNER/<service>:latest`
   - Add `cinema-frontend` service (nginx image on port 4200:80)
   - Keep infrastructure services unchanged (postgres, redis, kafka)
5. Add `.gitignore` (`.env`, `*.log`)
6. Update `docs/deployment-guide.md` with new polyrepo setup instructions
7. Push

## Todo List

- [ ] Create cinema-infra GitHub repo
- [ ] Copy docker-compose.yml + rewrite build→image
- [ ] Copy monitoring/ directory
- [ ] Copy init-databases.sql
- [ ] Copy docs/ directory
- [ ] Copy plans/ directory (archive)
- [ ] Create .env.example
- [ ] Update deployment guide for polyrepo
- [ ] Add .gitignore
- [ ] Push + verify `docker compose config` validates

## Success Criteria

- `docker compose config` validates without errors
- `docker compose pull` fetches all images from GHCR
- `docker compose up` starts full stack
- Monitoring dashboards load in Grafana

## Risk Assessment

- **GHCR image visibility**: images must be public or docker-compose host needs `docker login ghcr.io`
- **config-repo location**: if config-server loads from local filesystem, the config-repo dir must be in config-server image — not in infra. Verify config-server's `spring.cloud.config.server.native.searchLocations` setting.

## Next Steps (Post-Split)

- Archive monorepo (make read-only or rename to `cinema-monorepo-archive`)
- Update all READMEs to cross-link repos
- Set up GitHub Organization for grouping repos under `cinema-*` namespace
- Consider Renovate Bot for automated dependency updates across repos

## Unresolved Questions

1. Should `config-repo/` live in `cinema-config-server` or `cinema-infra`? Depends on whether config-server uses native filesystem or git backend.
2. Should monorepo be archived or deleted after split is verified stable?
3. GHCR image visibility policy — public or org-private?
