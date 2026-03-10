---
title: "Phase 5 — Split Frontend"
status: pending
priority: P2
effort: 1h
---

# Phase 5 — Split Frontend

## Context Links
- [Plan overview](plan.md)

## Overview

Extract `cinema-frontend/` (Angular app) into its own GitHub repo. No Java/Maven deps — purely npm/Angular CLI.

## Key Insights

- Angular app with proxy config pointing to `localhost:8080` (api-gateway)
- Already self-contained — no shared code with Java services
- Has `dist/` and `node_modules/` that must not be committed

## Requirements

### Target Repo

| Repo | Source Dir | Tech |
|------|-----------|------|
| `cinema-frontend` | `cinema-frontend/` | Angular, Node.js |

## Architecture

### Repo Structure

```
cinema-frontend/
├── .github/workflows/build.yml
├── .gitignore
├── Dockerfile                    # nginx or node-based
├── angular.json
├── package.json
├── proxy.conf.json
├── src/
│   ├── app/
│   └── ...
├── tsconfig.json
└── tsconfig.app.json
```

### Dockerfile

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY --from=build /app/dist/cinema-frontend/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

### GitHub Actions

```yaml
name: Build Frontend
on:
  push:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: npm ci
      - run: npm run build -- --configuration production
      - uses: docker/build-push-action@v5
        with:
          push: true
          tags: ghcr.io/OWNER/cinema-frontend:latest
```

## Implementation Steps

1. Create GitHub repo: `gh repo create OWNER/cinema-frontend --public`
2. `git subtree split --prefix=cinema-frontend -b split/cinema-frontend`
3. Push split branch to new repo
4. Add `.gitignore` (node_modules/, dist/, .env)
5. Add Dockerfile + nginx.conf
6. Add `.github/workflows/build.yml`
7. `npm ci && npm run build` — verify
8. Push

## Todo List

- [ ] Create cinema-frontend GitHub repo
- [ ] git subtree split
- [ ] .gitignore for Node/Angular
- [ ] Dockerfile (multi-stage with nginx)
- [ ] nginx.conf for SPA routing
- [ ] GitHub Actions workflow
- [ ] Verify build passes

## Success Criteria

- `npm run build` succeeds
- Docker image serves app on port 80
- SPA routing works (nginx fallback to index.html)
