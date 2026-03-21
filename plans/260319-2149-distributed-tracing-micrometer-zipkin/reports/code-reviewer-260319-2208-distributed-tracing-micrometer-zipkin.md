# Code Review: Distributed Tracing — Micrometer + Zipkin

**Date:** 2026-03-19
**Reviewer:** code-reviewer agent
**Plan:** `plans/260319-2149-distributed-tracing-micrometer-zipkin/`

---

## Scope

- **Files reviewed:** 14
  - `pom.xml` (root)
  - `eureka-server/pom.xml`, `config-server/pom.xml`, `api-gateway/pom.xml`
  - `auth-service/pom.xml`, `movie-service/pom.xml`, `booking-service/pom.xml`, `payment-service/pom.xml`, `notification-service/pom.xml`
  - `config-server/src/main/resources/config-repo/application.yml`
  - `eureka-server/src/main/resources/application.yml`
  - `config-server/src/main/resources/application.yml`
  - `docker-compose.yml`
  - `monitoring/grafana/provisioning/datasources/datasources.yml`
- **LOC analyzed:** ~850
- **Review focus:** Correctness, consistency, missing config, security
- **Test status:** All 20 tests PASS (tester report: `tester-260319-2203-full-test-suite-results.md`)
- **Updated plans:** `plan.md` (phase statuses updated)

---

## Overall Assessment

Implementation is correct and well-structured. Spring Boot 3.4.3 BOM manages all tracing dependency versions — no version pinning needed in `dependencyManagement`, which is the right call. Config is consistent across all 8 services. Three minor issues to address: `openzipkin/zipkin:latest` image tag in Docker, one missing `depends_on` hint, and sampling probability at `1.0` needs a prod-safety note.

---

## Critical Issues

None.

---

## High Priority Findings

### H1 — `openzipkin/zipkin:latest` is unpinned

**File:** `docker-compose.yml` line 49

```yaml
image: openzipkin/zipkin:latest
```

`latest` can silently pull breaking changes on `docker-compose pull` or CI rebuilds. Other infrastructure images in the same file use pinned versions (`postgres:16-alpine`, `redis:7-alpine`, `apache/kafka:3.7.0`, `grafana/loki:3.0.0`). Inconsistency is a risk.

**Fix:** Pin to the current stable release:
```yaml
image: openzipkin/zipkin:3.4
```

(As of March 2026, 3.x is stable. Verify current tag at hub.docker.com/r/openzipkin/zipkin/tags.)

---

## Medium Priority Improvements

### M1 — Sampling probability `1.0` has no prod safety guard

**Files:** `config-repo/application.yml`, `eureka-server/application.yml`, `config-server/application.yml`

```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
```

Default of `1.0` (100% sampling) is correct for dev/local. However, if `TRACING_SAMPLING_PROBABILITY` env var is never set in a prod deployment (easy oversight), Zipkin receives every span, which can cause memory pressure in the in-memory Zipkin container and increased latency on hot paths.

The env var hook is already there — this is just a documentation/ops concern. The plan already calls this out under Risk. No code change needed, but add comment inline:

```yaml
management:
  tracing:
    sampling:
      # 1.0 = 100% — dev only. Set TRACING_SAMPLING_PROBABILITY=0.1 in production.
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
```

### M2 — `grafana` `depends_on` does not include `zipkin`

**File:** `docker-compose.yml` lines 276-279

```yaml
grafana:
  depends_on:
    - prometheus
    - loki
```

Grafana's Zipkin datasource will show "connection refused" briefly at startup if Zipkin hasn't started yet and Grafana performs a connection health check on boot. Low impact since exporters retry, but adding `zipkin` to `depends_on` aligns with how `prometheus` and `loki` are already handled.

**Fix:**
```yaml
grafana:
  depends_on:
    - prometheus
    - loki
    - zipkin
```

### M3 — Tracing config duplicated in `eureka-server` and `config-server` local YAMLs

**Files:** `eureka-server/src/main/resources/application.yml`, `config-server/src/main/resources/application.yml`

Both independently reproduce:
```yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLING_PROBABILITY:1.0}
  zipkin:
    tracing:
      endpoint: http://${ZIPKIN_HOST:localhost}:9411/api/v2/spans
```

This is architecturally intentional (these two services bootstrap before config-server), but the duplication means future changes to sampling probability or endpoint path must be applied in 3 places. This is a known constraint of the bootstrap architecture — acceptable as-is, but worth a comment.

**No code change required.** Consider adding a comment:
```yaml
# Note: duplicated from config-repo/application.yml because this service
# does not use config-server (bootstrap ordering constraint).
```

### M4 — `api-gateway/application.yml` has no tracing config

**File:** `api-gateway/src/main/resources/application.yml`

The gateway loads its config from config-server (`spring.config.import: optional:configserver:...`), so `management.tracing` and `management.zipkin.tracing.endpoint` will be picked up from `config-repo/application.yml` at runtime. This is correct.

However, the `management:` block in `api-gateway/application.yml` only exposes `health,info,gateway,prometheus` — no `management.tracing` local override. This is fine because the shared config provides it, but it differs structurally from `booking-service/application.yml` which also has no local tracing config and relies on the same shared config. Consistent — no issue.

---

## Low Priority Suggestions

### L1 — `openzipkin/zipkin` has no `healthcheck` in docker-compose

Other production containers (postgres, kafka) could benefit from healthchecks too, but Zipkin is particularly relevant since spans are silently dropped when Zipkin is unreachable. Adding a healthcheck allows dependent services to know when Zipkin is ready:

```yaml
zipkin:
  image: openzipkin/zipkin:3.4
  ports:
    - "9411:9411"
  networks:
    - my-net
  restart: unless-stopped
  healthcheck:
    test: ["CMD", "wget", "--spider", "-q", "http://localhost:9411/health"]
    interval: 10s
    timeout: 5s
    retries: 3
```

### L2 — Grafana Zipkin datasource missing `jsonData` for version hint

**File:** `monitoring/grafana/provisioning/datasources/datasources.yml`

The Zipkin datasource entry works as-is. Optionally, adding `jsonData.httpMethod: POST` aligns with Zipkin's v2 API preference, though GET also works for queries. This is purely cosmetic for this Grafana version.

### L3 — No Loki derived field for traceId → Zipkin link

**File:** `monitoring/grafana/provisioning/datasources/datasources.yml`

Phase 04 lists this as optional. The Loki datasource could be extended with a derived field so traceId values in logs become clickable links to Zipkin. Not implemented — acceptable as optional enhancement.

---

## Positive Observations

1. **BOM version management is correct.** Tracing deps in each service pom.xml have no `<version>` tags — Spring Boot 3.4.3 parent BOM manages them. This prevents version skew.

2. **Perfect symmetry across 8 service pom.xml files.** Every service has exactly the same two tracing deps in the same order with the same comment. Consistent and readable.

3. **`ZIPKIN_HOST` env var pattern is consistent.** All 8 service containers in `docker-compose.yml` carry `ZIPKIN_HOST: zipkin`, and all application configs reference `${ZIPKIN_HOST:localhost}` with a sensible local fallback. Zero-config for local dev outside Docker.

4. **Shared config architecture is correct.** Tracing config placed in `config-repo/application.yml` is the right layer — it propagates to all 6 config-client services automatically. eureka-server and config-server correctly carry their own copy.

5. **`management.zipkin.tracing.endpoint` uses the correct Spring Boot 3.x key.** (The old `spring.zipkin.base-url` key was removed in Spring Boot 3.x; using `management.zipkin.tracing.endpoint` is correct.)

6. **All tests pass (20/20).** Build compiles cleanly with the new deps on classpath.

7. **Grafana provisioning approach is correct.** Declarative datasource provisioning means the Zipkin datasource is available on first Grafana boot without manual UI configuration.

---

## Recommended Actions

1. **[High]** Pin Zipkin Docker image: `openzipkin/zipkin:latest` → `openzipkin/zipkin:3.4` (or current stable tag).

2. **[Medium]** Add `zipkin` to Grafana's `depends_on` in `docker-compose.yml`.

3. **[Low — optional]** Add sampling probability comment in the 3 YAML files referencing `TRACING_SAMPLING_PROBABILITY` to warn about production use.

4. **[Low — optional]** Add Zipkin healthcheck to `docker-compose.yml`.

5. **[Low — optional]** Add Loki derived field for traceId → Zipkin link (phase 04 step 6).

6. **[Ops]** Phase 04 verification (manual smoke test) is still pending — requires a running Docker environment. All code-level items are complete and correct.

---

## Metrics

- Type Coverage: N/A (Java/YAML — no TS)
- Test Coverage: 20/20 tests pass; JaCoCo not configured (pre-existing gap)
- Linting Issues: 0 syntax errors; 1 unpinned image tag (H1)
- Build status: SUCCESS (per tester report)

---

## Unresolved Questions

1. **Kafka trace propagation:** `spring.kafka.listener.observation-enabled=true` may be needed for Kafka span auto-propagation in Spring Kafka 3.x. Cannot verify without a running environment. Phase 04 step 4 should explicitly test this. If broken, add to `config-repo/application.yml`:
   ```yaml
   spring:
     kafka:
       listener:
         observation-enabled: true
   ```

2. **Zipkin persistence:** In-memory Zipkin is fine for dev. Is there a requirement for persistent storage (e.g., Elasticsearch backend) for staging/prod?

3. **Production sampling rate:** No prod deployment config exists yet. When prod config is added, ensure `TRACING_SAMPLING_PROBABILITY` is set to `0.05`–`0.1`.
