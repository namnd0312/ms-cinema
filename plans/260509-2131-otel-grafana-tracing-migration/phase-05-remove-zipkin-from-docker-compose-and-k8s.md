# Phase 05 — Remove Zipkin from docker-compose and k8s

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (Removal Checklist)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docker-compose.yml`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/zipkin/`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/grafana/provisioning/datasources/datasources.yml`

## Overview

- Date: 2026-05-09
- Priority: P2
- Status: pending
- Review: not-started
- Description: Strip all remaining Zipkin references after phase 07 validates OTLP→Tempo flow. Cleans docker-compose, k8s manifests, grafana datasource, and configmap.

## Key Insights

- **MUST run only after phase 07 passes** — defensive; allows rollback if Tempo path breaks.
- Zipkin pom.xml deps already removed in phase 03 — this phase only handles infrastructure.
- Historical references in `docs/project-changelog.md` and `plans/260319-2149-distributed-tracing-micrometer-zipkin/` are KEPT (historical record).

## Requirements

**Functional**
- Zipkin container/pod no longer running.
- No service depends on `ZIPKIN_HOST` or `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT`.
- Grafana shows only Tempo (no Zipkin in Explore datasource picker).

**Non-functional**
- Reclaimed resources: ~256Mi mem, port 9411.
- Clean `git grep -i zipkin` returns ONLY historical doc/plan references.

## Architecture

```
Removed:
├── docker-compose.yml zipkin service block
├── k8s/infra/zipkin/* directory
├── ZIPKIN_HOST + MANAGEMENT_ZIPKIN_TRACING_ENDPOINT env vars
├── grafana Zipkin datasource block
└── grafana depends_on zipkin
```

## Related Code Files

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docker-compose.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/base/configmap.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/monitoring/grafana/provisioning/datasources/datasources.yml`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/deploy-all.sh`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/teardown.sh` (remove zipkin teardown lines)

**Create**
- none

**Delete**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/k8s/infra/zipkin/` (entire directory)

## Implementation Steps

1. **Pre-flight gate** — confirm phase 07 success criteria all green. Tempo has ≥24h of traces visible. Abort if not.

2. **docker-compose.yml**:
   - Delete `zipkin:` service block (lines 47-52 per scout).
   - Remove `ZIPKIN_HOST: zipkin` env entries (6 occurrences: lines 76, 99, 122, 145, 170, 191).
   - Remove `zipkin` from `grafana.depends_on` (line ~233).
   - Run `docker compose config` to validate.

3. **k8s/base/configmap.yml**:
   - Delete line 17: `ZIPKIN_HOST: "zipkin"`.
   - Delete line 26: `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: "http://zipkin:9411/api/v2/spans"`.

4. **Delete zipkin k8s manifests**:
   ```bash
   kubectl delete -f k8s/infra/zipkin/ --ignore-not-found
   rm -rf k8s/infra/zipkin/
   ```

5. **k8s/deploy-all.sh**: remove `kubectl apply -f k8s/infra/zipkin/` line.

6. **k8s/teardown.sh**: remove zipkin teardown lines.

7. **monitoring/grafana/provisioning/datasources/datasources.yml**: delete the `- name: Zipkin` block (lines 21-27).

8. **Grafana k8s configmap** (if separate): mirror the Zipkin block deletion.

9. **Verify cleanup**:
   ```bash
   git grep -in zipkin -- ':!plans' ':!docs/project-changelog.md'
   # Expected output: only README.md + docs/* (handled in phase 06)
   ```

10. **Restart**:
    - Compose: `docker compose down zipkin && docker compose up -d`
    - K8s: `kubectl rollout restart deploy -l tier=app && kubectl rollout restart deploy/grafana`

11. **Confirm pods/containers**: `docker ps | grep zipkin` and `kubectl get pods | grep zipkin` both empty.

## Todo List

- [ ] Verify phase 07 completed successfully
- [ ] Remove `zipkin:` service from docker-compose.yml
- [ ] Remove 6 `ZIPKIN_HOST` env entries from docker-compose.yml
- [ ] Remove zipkin from grafana `depends_on`
- [ ] Remove `ZIPKIN_HOST` from k8s configmap
- [ ] Remove `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` from k8s configmap
- [ ] `kubectl delete -f k8s/infra/zipkin/`
- [ ] `rm -rf k8s/infra/zipkin/`
- [ ] Update `k8s/deploy-all.sh` (remove zipkin apply)
- [ ] Update `k8s/teardown.sh` (remove zipkin teardown)
- [ ] Remove Zipkin datasource from `monitoring/grafana/provisioning/datasources/datasources.yml`
- [ ] Mirror datasource removal in k8s grafana ConfigMap
- [ ] `git grep zipkin` returns only docs/historical refs
- [ ] Restart compose + k8s
- [ ] Confirm no zipkin container/pod running

## Success Criteria

- `docker ps -a | grep zipkin` → empty.
- `kubectl get all -A | grep zipkin` → empty.
- `kubectl get configmap app-config -o yaml | grep -i zipkin` → empty.
- `git grep -i zipkin -- '*.yml' '*.xml'` → empty.
- Grafana Explore picker shows only: Loki, Prometheus, Tempo.
- All 6 services still healthy after restart.

## Risk Assessment

- **Premature removal** before traces flow → blind spot. Mitigation: gated by phase 07.
- **Stale port mapping** for 9411 in firewall rules / ingress → silent. Mitigation: `lsof -i :9411` should return empty post-cleanup.
- **Grafana dashboards referencing Zipkin uid** → broken panels. Mitigation: phase 06 grep + fix dashboards.
- **k8s configmap still mounts removed env vars** — apps don't read them, harmless. Cleanup is hygiene.

## Security Considerations

- No security regression — fewer exposed services = smaller attack surface.
- No secret rotation needed.
- Removed unauthenticated `:9411` port — net positive.

## Next Steps

- Phase 06 docs update can run in parallel.
- Future: tail-sampling at collector once prod load profile known.
