# Code Review: Prometheus & Grafana Monitoring

**Date:** 2026-03-03
**Plan:** `/plans/260303-2248-prometheus-grafana-monitoring/`

---

## Code Review Summary

### Scope
- Files reviewed: 7 pom.xml, 7 application.yml, 2 SecurityConfig.java, 1 AuthController.java, 1 BookingServiceImpl.java, 1 PaymentServiceImpl.java, 3 MetricsConfig.java, 1 docker-compose.yml, 1 prometheus.yml, 1 datasources.yml, 1 dashboards.yml, 2 dashboard JSON files
- Lines of code analyzed: ~850
- Review focus: Monitoring integration (Phase 1-4) — security, counter naming, Docker Compose correctness, runtime correctness, SecurityConfig fix validity

---

### Overall Assessment

Implementation is solid and functionally correct. All 7 services compile and the monitoring stack is wired consistently. Four specific issues found — one high (security), one medium (counter naming), one medium (dashboard query variable mismatch), one low (Docker image pinning).

---

### Critical Issues

None.

---

### High Priority Findings

#### H1: Actuator endpoints exposed to public internet via host port bindings

**Problem:** All service ports (8081-8084, 8080, 8761, 8888) are bound to `0.0.0.0` on the host. Prometheus scrapes via Docker-internal hostnames (`auth-service:8081`), which is correct. However, `/actuator/prometheus` is also reachable publicly at `http://host-ip:8081/actuator/prometheus` because the host port is bound.

For auth-service specifically, `SecurityConfig.java` now has:
```java
.requestMatchers("/api/auth/**", "/actuator/**").permitAll()
```
This means **all actuator endpoints** (not just `/prometheus`) are unauthenticated and reachable from the internet as long as port 8081 is open. This includes `/actuator/env`, `/actuator/beans`, `/actuator/heapdump`, `/actuator/threaddump`, etc. — which expose sensitive runtime info.

**Current exposure:** The `management.endpoints.web.exposure.include` is scoped to `health,info,prometheus` — so only those three endpoints are actually enabled. This partially mitigates the risk: `/actuator/env` etc. are disabled by default and won't respond. However, `/actuator/health` with `show-details: when-authorized` and `/actuator/info` are still open to all internet traffic, no auth required.

**Recommended fix (two options):**
1. Scope the security rule more tightly — only permit the three explicitly enabled endpoints:
   ```java
   .requestMatchers("/api/auth/**", "/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
   ```
2. Or keep `/actuator/**` permissive but bind service ports only to loopback/internal network in docker-compose (remove host port bindings for non-gateway services in production).

The wider `/actuator/**` wildcard is a net improvement over the prior state (where Prometheus was getting 401), but the gap to close is distinguishing "open to Docker network" from "open to host network."

---

### Medium Priority Findings

#### M1: Counter naming deviates from Micrometer conventions

**Problem:** Micrometer recommends dot-separated metric names, which is correct (`.` in Java → `_` in Prometheus format via registry). The names chosen are fine except for inconsistency in the `application` tag placement logic.

More concretely: the counters are defined **without** an explicit `tag("application", ...)` call:
```java
Counter.builder("auth.login.success")
    .description("Successful login attempts")
    .register(registry);
```
They rely entirely on the global `management.metrics.tags.application` common tag. This is correct and works — the common tag is applied to all registered meters automatically. No functional bug, but worth noting this is an implicit dependency on the YAML config being correct across all services.

**Separate naming concern:** The business metrics panels in the dashboard hardcode `application="auth-service"`, `application="booking-service"`, etc. — these are literal strings, not using the `$application` variable. This is intentional (business metrics only exist per-service), but it means the panels are always active regardless of the `$application` dropdown selection, which can confuse users who expect the dropdown to filter all panels. Consider adding a note in the dashboard title row or make the hardcoded labels explicit in panel titles.

#### M2: Grafana `$application` template variable query may return no values until services are up

**Problem (runtime):** The template variable query is:
```
label_values(up{job=~".+"}, application)
```
The `application` label is populated only if the `management.metrics.tags.application` tag reaches Prometheus. Until services are scraped at least once, the dropdown will be empty. On fresh stack startup (services take 30-60s to start), users opening Grafana immediately may see an empty `$application` dropdown and blank panels.

**Mitigation already noted in the plan risk section.** No code change needed — this is expected behavior documented in Phase 4 risks. Just confirming it's a real runtime behavior to be aware of.

#### M3: Dashboard JSON schemaVersion mismatch between the two dashboards

`jvm-micrometer.json` has `"schemaVersion": 38`, `"__requires"` Grafana version `10.0.0`.
`spring-boot-http-overview.json` has `"schemaVersion": 36`, `"__requires"` Grafana version `9.0.0`.

Both use `grafana/grafana-oss:latest` which is currently ≥10.x, so both load correctly — newer Grafana is backwards-compatible with older schema versions. No functional issue, but inconsistency makes it harder to know the minimum Grafana version required.

**Recommendation:** Update `spring-boot-http-overview.json` `__requires` and `schemaVersion` to match `jvm-micrometer.json` (v10.0.0 / schema 38).

---

### Low Priority Suggestions

#### L1: Use pinned image versions instead of `latest` for Prometheus and Grafana

`docker-compose.yml`:
```yaml
image: prom/prometheus:latest
image: grafana/grafana-oss:latest
```

`latest` tags can cause silent breaking changes on `docker compose pull`. Pin to specific versions:
```yaml
image: prom/prometheus:v2.51.2
image: grafana/grafana-oss:11.4.0
```
The plan documents that `latest` is an intentional decision by the user — acceptable for dev, flag for production.

#### L2: Prometheus Grafana service has no `depends_on` health-check condition

```yaml
grafana:
  depends_on:
    - prometheus
```

This only waits for the Prometheus container to **start**, not for it to be **healthy**. Grafana may try to connect to Prometheus before Prometheus is ready to accept queries. In practice this is fine since Grafana retries datasource connections, but adding a health check would make the startup sequence more reliable:
```yaml
prometheus:
  healthcheck:
    test: ["CMD", "wget", "-qO-", "http://localhost:9090/-/ready"]
    interval: 10s
    timeout: 5s
    retries: 5
grafana:
  depends_on:
    prometheus:
      condition: service_healthy
```

#### L3: Auth-service `application.yml` has hardcoded email credentials

```yaml
spring:
  mail:
    username: nghiemducnam0312@gmail.com
    password: sdxm fmia vuzf bvmq
```

This is a pre-existing issue, not introduced by this monitoring PR. But since this review touches `application.yml` files, flagging it: these should be environment variables `${MAIL_USERNAME}` / `${MAIL_PASSWORD}` and **must not be committed** to the git repository.

---

### Positive Observations

- **SecurityConfig fix was correct and necessary.** Without adding `/actuator/**` to `permitAll()`, Prometheus would receive 401 from auth-service (which has Spring Security active). movie-service fix from `/actuator/health` to `/actuator/**` is also correct — the JWT starter's `public-paths` only covers the starter filter, not Spring Security's own filter chain. Both fixes align with how each service's security is layered.

- **Counter injection pattern.** `BookingServiceImpl` and `PaymentServiceImpl` use constructor injection for counters — correct pattern. `AuthController` uses `@Autowired` field injection, consistent with the pre-existing code style in that file. No inconsistency introduced.

- **Micrometer common tags correctly set.** `management.metrics.tags.application: ${spring.application.name}` across all 7 services ensures the `application` label is present on every metric, enabling Grafana's `$application` variable to work. This is exactly the right approach.

- **Business metric naming is appropriate.** `auth.login.success`, `booking.created`, `payment.initiated` etc. are self-describing, follow the `domain.action.qualifier` pattern, and translate cleanly to Prometheus names (`auth_login_success_total`, `booking_created_total`, `payment_initiated_total`).

- **Dashboard PromQL queries are correct.** `histogram_quantile(0.95, sum(rate(..._bucket[5m])) by (le, uri))` — the `by (le, uri)` clause is correctly structured. `rate(..._count) / rate(..._count) * 100` for error rate is correct.

- **prometheus.yml is clean and correct.** Static scrape targets match Docker Compose service names and ports exactly. Self-scrape target uses `localhost:9090` which is correct since Prometheus runs inside its own container.

- **Grafana provisioning structure is correct.** Datasource `access: proxy` is right (Grafana proxies queries to Prometheus), `url: http://prometheus:9090` uses the Docker network hostname.

- **`--storage.tsdb.retention.time=7d`** is a sensible default for dev to bound disk usage.

---

### Recommended Actions

1. **[High] Tighten auth-service SecurityConfig actuator permit scope** — change from `/actuator/**` to explicitly listing only the three exposed endpoints (`/actuator/health`, `/actuator/info`, `/actuator/prometheus`). This closes the surface area without blocking Prometheus scraping.

2. **[Medium] Fix email credentials in auth-service application.yml** — move to env vars before any commits reach a shared/remote repository.

3. **[Low] Align dashboard JSON schemaVersion** — update `spring-boot-http-overview.json` to `schemaVersion: 38` and `__requires.grafana.version: "10.0.0"`.

4. **[Low] Consider pinning image versions** once the stack is stable in dev — track which versions were validated.

5. **[Low] Add Prometheus health check in docker-compose** for more reliable startup sequencing.

---

### Task Completeness Verification

All plan phases and todos verified against implementation:

**Phase 1 todos:** All 20 items complete.
- All 7 pom.xml updated with correct dependencies
- All 7 application.yml updated with `management.metrics.tags.application`
- auth-service SecurityConfig updated (`/actuator/**` added to permitAll)
- movie-service SecurityConfig updated (plan gap fix, correctly added)
- 3 MetricsConfig.java created
- Counters injected into AuthController, BookingServiceImpl, PaymentServiceImpl
- `mvn clean compile` passed

**Phase 2 todos:** All 4 items complete.
- `monitoring/prometheus/prometheus.yml` created with 8 scrape configs (7 services + self)
- Prometheus service added to docker-compose.yml with volume, retention flag, network

**Phase 3 todos:** All 6 items complete.
- datasources.yml, dashboards.yml created
- jvm-micrometer.json created (custom implementation — not downloaded from ID 4701, but functionally equivalent)
- spring-boot-http-overview.json created with business metrics panels
- Grafana service added to docker-compose.yml

**Phase 4 todos:** NOT executed — requires running Docker stack. This is correct: Phase 4 is a runtime verification step, not a code artifact. The plan status should be updated to reflect implementation is complete pending runtime verification.

---

### Plan Status Update Required

The plan file (`plan.md`) has all phases showing `Pending`. These should be updated to reflect completion.

---

### Metrics

- Type Coverage: N/A (Java, no static type issues)
- Test Coverage: N/A (no test files in scope)
- Linting Issues: 0 (compile succeeded)
- Security Issues: 1 high (actuator over-permissive wildcard), 1 pre-existing (credentials in YAML)

---

### Unresolved Questions

1. The plan intended to download community dashboard ID 4701 from grafana.com. The implemented `jvm-micrometer.json` is a custom JSON (17 panels), not the official community dashboard. Was this intentional? The community dashboard is significantly more comprehensive. If the JVM dashboard needs more panels (GC pause times, class loading, buffer pools), consider fetching the actual ID 4701 JSON.

2. `movie-service SecurityConfig.java` now has `/actuator/**` in `permitAll()` alongside the JWT starter's `public-paths: [/actuator/health, /actuator/prometheus]`. The SecurityConfig rule is the effective gate (Spring Security runs first). The `public-paths` in the JWT starter only affects the JWT filter. Having both is redundant but not harmful — the SecurityConfig rule alone is sufficient. Consider removing `/actuator/health` and `/actuator/prometheus` from movie/booking/payment `public-paths` or keeping them explicitly for documentation clarity.
