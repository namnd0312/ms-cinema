# Test Report: mvn clean test — All Modules
**Date:** 2026-03-02 22:55 (ICT)
**Runner:** tester agent
**Command:** `mvn clean test` from project root

---

## Test Results Overview

| Module | Tests Run | Passed | Failed | Skipped | Time |
|---|---|---|---|---|---|
| spring-jwt (parent pom) | — | — | — | — | 0.05 s |
| auth-service | 1 | 1 | 0 | 0 | ~10 s |
| jwt-auth-spring-boot-autoconfigure | 0 | — | — | — | 0.19 s |
| jwt-auth-spring-boot-starter | 0 | — | — | — | 0.01 s |
| eureka-server | 0 | — | — | — | 0.19 s |
| config-server | 0 | — | — | — | 0.14 s |
| api-gateway | 0 | — | — | — | 0.10 s |
| **TOTAL** | **1** | **1** | **0** | **0** | **~11 s** |

**BUILD: SUCCESS**

---

## Coverage Metrics

No coverage plugin configured (JaCoCo not present). Coverage data not collected.

---

## Failed Tests

None.

---

## Performance Metrics

- Total build time: ~11 s
- auth-service context load dominates (~10 s) due to full `@SpringBootTest` context startup (PostgreSQL, Redis, Eureka client, Mail).
- No slow test threshold violations.

---

## Build Status

All 7 modules compiled and tested successfully. Zero Maven-level `[WARNING]` lines.

**Compiler note (non-blocking):** javac emits annotation-processing advisory on `jwt-auth-spring-boot-autoconfigure`:
```
Annotation processing is enabled because one or more processors were found on the class path.
A future release of javac may disable annotation processing unless explicitly specified.
Use -proc:none to disable annotation processing.
```
Not a build failure; suppressed by adding `-proc:none` or `-Xlint:-options` if desired.

**Mockito note (non-blocking):** Mockito self-attaching inline-mock-maker produces JDK21 dynamic-agent warning:
```
WARNING: A Java agent has been loaded dynamically (byte-buddy-agent-*.jar)
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```
Resolved by adding `-javaagent:` to Surefire config or upgrading to Mockito 5.x static agent mode.

---

## Test Inventory

### auth-service
- **File:** `auth-service/src/test/java/com/namnd/springjwt/SpringJwtApplicationTests.java`
- **Test:** `contextLoads()` — bare `@SpringBootTest`, verifies the full Spring context boots without error.
- **Infrastructure required:** PostgreSQL (`localhost:5432/testdb`), Redis (`localhost:6379`), Eureka (`localhost:8761` — connection refused logged as WARN but test still passes because Eureka retry is non-fatal).

### Other modules
No test sources exist; Surefire reports "No tests to run." for all five modules.

---

## Critical Issues

None blocking. Build is green.

---

## Recommendations

1. **Coverage plugin** — add JaCoCo to parent `pom.xml` to track line/branch coverage:
   ```xml
   <plugin>
     <groupId>org.jacoco</groupId>
     <artifactId>jacoco-maven-plugin</artifactId>
     <executions>
       <execution><goals><goal>prepare-agent</goal><goal>report</goal></goals></execution>
     </executions>
   </plugin>
   ```

2. **auth-service test isolation** — `contextLoads()` hits live PostgreSQL + Redis. Replace with:
   - Testcontainers (`org.testcontainers:postgresql`, `testcontainers:redis`) for reproducible CI runs, **or**
   - `@SpringBootTest` + `application-test.yml` using H2 (in-memory) and embedded Redis (`it.ozimov:embedded-redis`).

3. **Unit tests for business logic** — zero unit tests exist for services/controllers/JWT utilities. High-value targets:
   - `JwtService` (token generation, validation, expiry)
   - `AuthService` (login, register, activation flow)
   - `TokenBlacklistService` (Redis blacklist write/read)
   - `UserDetailsServiceImpl`

4. **Mockito agent** — add to `auth-service/pom.xml` Surefire config to silence JDK21 warning:
   ```xml
   <argLine>-javaagent:${settings.localRepository}/net/bytebuddy/byte-buddy-agent/${bytebuddy.version}/byte-buddy-agent-${bytebuddy.version}.jar</argLine>
   ```

5. **Test stubs for infrastructure modules** — `eureka-server`, `config-server`, `api-gateway` each have zero tests. At minimum add a `contextLoads()` smoke test per module.

---

## Next Steps (prioritized)

1. Add JaCoCo coverage plugin (parent pom) — quick win.
2. Create `application-test.yml` for auth-service with H2 + embedded Redis so tests are infra-independent.
3. Write unit tests for `JwtService` and `AuthService` (highest business-value coverage).
4. Add smoke `contextLoads()` tests to eureka-server, config-server, api-gateway.
5. Address Mockito JDK21 dynamic-agent warning before it becomes a hard failure on JDK25+.

---

## Unresolved Questions

- Is PostgreSQL (`localhost:5432/testdb`) guaranteed available in CI? If not, `contextLoads()` will fail in pipeline — needs Testcontainers or mock datasource.
- Redis (`localhost:6379`) same concern: CI must have Redis running or test must mock it.
- Should `jwt-auth-spring-boot-autoconfigure` test coverage cover the `JwtAuthAutoConfiguration` bean registration? Currently untested.
