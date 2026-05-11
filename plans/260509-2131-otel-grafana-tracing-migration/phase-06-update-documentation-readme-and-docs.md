# Phase 06 — Update README and docs

## Context Links

- Parent: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/plan.md`
- Scout: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/plans/260509-2131-otel-grafana-tracing-migration/scout/scout-01-zipkin-references-inventory-across-poms-yaml-k8s-docs.md` (section 6)
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/README.md`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/system-architecture.md`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/deployment-guide.md`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/codebase-summary.md`
- Source: `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/project-changelog.md`

## Overview

- Date: 2026-05-09
- Priority: P3
- Status: pending
- Review: not-started
- Description: Update all live docs to reflect new tracing stack. Add changelog entry. Preserve historical Zipkin references in changelog and archived plans.

## Key Insights

- Live docs (`README.md`, `docs/system-architecture.md`, `docs/deployment-guide.md`, `docs/codebase-summary.md`) describe CURRENT state — must change.
- Historical artifacts (`docs/project-changelog.md`, `plans/260319-2149-distributed-tracing-micrometer-zipkin/`) are append-only. Add entry; do NOT rewrite history.
- README has 5+ Zipkin mentions including a services-table port row, monitoring stack list, and docker-compose section.

## Requirements

**Functional**
- Reader following `docs/deployment-guide.md` can stand up the new stack end-to-end.
- `docs/system-architecture.md` diagram/text reflects OTLP→Collector→Tempo flow.
- README "Distributed Tracing" claim accurate.

**Non-functional**
- Changelog entry follows existing format/version cadence.
- No broken internal links after edits.

## Architecture

```
Documentation update map:
README.md                       → tracing stack table + monitoring list + compose section
docs/system-architecture.md     → observability diagram + tracing pipeline narrative
docs/deployment-guide.md        → port table (drop 9411, add 3200/4317/4318), commands
docs/codebase-summary.md        → tracing stack one-liner
docs/project-changelog.md       → APPEND new entry "Migrated tracing Zipkin → OTel + Tempo"
```

## Related Code Files

**Modify**
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/README.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/system-architecture.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/deployment-guide.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/codebase-summary.md`
- `/Users/admin/Desktop/DEV/BACK_END/ms-cinema/docs/project-changelog.md` (append only)

**Create**
- none

**Delete**
- none (historical refs preserved)

## Implementation Steps

1. **README.md**:
   - Replace tracing stack line: `Distributed Tracing: Micrometer Tracing (OpenTelemetry bridge) + Zipkin exporter` → `Distributed Tracing: Micrometer Tracing → OTLP/HTTP → OpenTelemetry Collector → Grafana Tempo`.
   - Service ports table: remove row `Zipkin | 9411`. Add rows: `OTel Collector | 4317/4318`, `Tempo | 3200`.
   - Monitoring stack listing: replace "Zipkin" with "Tempo (traces) + OTel Collector".
   - Docker-compose section: any reference to launching/visiting `localhost:9411` → replace with `localhost:3000` (Grafana Explore → Tempo) and `localhost:3200` (direct Tempo).

2. **docs/system-architecture.md**:
   - Find observability section. Update flow diagram (mermaid or ASCII) to: `App → OTel Collector → Tempo`.
   - Update "Tracing" subsection text: explain OTLP/HTTP, Micrometer bridge, sampling, Kafka W3C propagation.
   - Add note on Grafana correlation (`tracesToLogsV2` via `service` Loki label).

3. **docs/deployment-guide.md**:
   - Port table: drop 9411; add 4318 (OTLP HTTP), 4317 (OTLP gRPC), 3200 (Tempo HTTP), 13133 (collector health).
   - Commands referencing `docker compose up zipkin` → replace with `docker compose up tempo otel-collector`.
   - K8s commands: replace `kubectl apply -f k8s/infra/zipkin/` with `kubectl apply -f k8s/infra/tempo/ -f k8s/infra/otel-collector/`.

4. **docs/codebase-summary.md**:
   - Replace tracing one-liner with new stack.

5. **docs/project-changelog.md** — APPEND new entry:
   ```markdown
   ## [Unreleased] — 2026-05-09
   ### Changed
   - Migrated distributed tracing pipeline from Zipkin to OpenTelemetry Collector (contrib) + Grafana Tempo. Spring Boot apps now export OTLP/HTTP on port 4318. Trace-to-logs correlation in Grafana via Loki `service` label and Tempo `service.name` span attribute.
   ### Removed
   - Zipkin service (docker-compose + k8s/infra/zipkin).
   - `opentelemetry-exporter-zipkin` Maven dependency from 6 services.
   - `ZIPKIN_HOST` and `MANAGEMENT_ZIPKIN_TRACING_ENDPOINT` env vars.
   - Zipkin Grafana datasource.
   ```

6. Verify no broken links: `grep -rE '\[.*\]\(.*zipkin' README.md docs/` should return empty.

7. Run any docs lint script if present (e.g. `markdownlint`).

## Todo List

- [ ] Update README.md tracing line + ports table + monitoring list + compose section
- [ ] Update docs/system-architecture.md observability diagram + narrative
- [ ] Update docs/deployment-guide.md port table + commands
- [ ] Update docs/codebase-summary.md tracing one-liner
- [ ] Append changelog entry
- [ ] Confirm no broken zipkin-anchored markdown links
- [ ] Confirm `git grep -i zipkin -- README.md docs/` returns ONLY changelog historical mentions

## Success Criteria

- A new contributor reading README + deployment-guide can deploy and access traces in Grafana without external context.
- `git grep -i zipkin README.md` → empty.
- `git grep -i zipkin docs/system-architecture.md docs/deployment-guide.md docs/codebase-summary.md` → empty.
- Changelog entry rendered correctly.
- All internal markdown links resolve.

## Risk Assessment

- **Stale screenshots/diagrams** still showing Zipkin — out of scope for text edits, flag in unresolved questions if found.
- **Cross-doc inconsistency** — same fact in 3 docs; mitigate with single-source-of-truth pointer (README links to system-architecture for tracing details).
- **Changelog conflict** if other PRs append same date — resolve in merge.

## Security Considerations

- Documentation reveals internal hostnames (`tempo`, `otel-collector`) — already public knowledge for OSS reference architectures, acceptable.
- No credentials in docs.

## Next Steps

- Done. Migration complete pending phase 07 sign-off.
- Future doc tasks: add tail-sampling guide once implemented.
