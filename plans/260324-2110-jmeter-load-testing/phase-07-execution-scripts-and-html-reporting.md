# Phase 7: Execution Scripts & HTML Reporting

## Context
- Parent plan: [plan.md](./plan.md)
- Depends on: Phases 1-6 (all test plans created)

## Overview
- **Priority**: P1
- **Status**: pending
- **Description**: Shell scripts for CLI execution, HTML report generation, and integration with Grafana monitoring

## Key Insights
- JMeter GUI mode is for building tests; CLI mode for execution (much better performance)
- JMeter generates `.jtl` result files → convertible to HTML dashboard
- Grafana already configured with Prometheus — can correlate load test with service metrics
- Run tests in sequence: auth → movie → booking → payment → e2e

## Requirements

### Functional
- One-command test execution per test plan
- One-command full suite execution
- Automatic HTML report generation after each test
- Test data cleanup script

### Non-Functional
- Scripts portable (macOS/Linux)
- Results timestamped for comparison between runs

## Implementation Steps

### 1. Run Script (`scripts/run-load-test.sh`)
```bash
#!/bin/bash
# Usage: ./run-load-test.sh <test-name> [threads] [duration]
# Example: ./run-load-test.sh auth 1000 900

TEST_NAME=${1:-"auth"}
THREADS=${2:-1000}
DURATION=${3:-900}
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
RESULTS_DIR="results/${TEST_NAME}_${TIMESTAMP}"

mkdir -p "${RESULTS_DIR}"

jmeter -n \
  -t "jmx/${TEST_NAME}-load-test.jmx" \
  -l "${RESULTS_DIR}/results.jtl" \
  -j "${RESULTS_DIR}/jmeter.log" \
  -Jthreads=${THREADS} \
  -Jduration=${DURATION} \
  -Jbase_url=http://localhost:8080 \
  -e -o "${RESULTS_DIR}/report"

echo "Results: ${RESULTS_DIR}/report/index.html"
```

### 2. Full Suite Script (`scripts/run-full-suite.sh`)
```bash
#!/bin/bash
# Runs all load tests sequentially with cooldown between

TESTS=("auth" "movie" "booking" "payment" "full-journey-e2e")
COOLDOWN=60  # seconds between tests

for test in "${TESTS[@]}"; do
  echo "=== Running ${test} load test ==="
  ./scripts/run-load-test.sh "${test}"
  echo "Cooldown ${COOLDOWN}s..."
  sleep ${COOLDOWN}
done

echo "=== All tests complete ==="
```

### 3. Seed Data Script (`scripts/seed-test-data.sql`)
- Insert 1000 users with pre-computed BCrypt hashes
- Insert movies, theaters, showtimes
- Assign ROLE_USER to all test users

### 4. Cleanup Script (`scripts/cleanup-test-data.sh`)
```bash
#!/bin/bash
# Remove load test data from databases
psql -h localhost -U postgres -d testdb -c "DELETE FROM users WHERE email LIKE 'loadtest_%'"
psql -h localhost -U postgres -d bookingdb -c "DELETE FROM bookings WHERE created_at > NOW() - INTERVAL '1 day'"
psql -h localhost -U postgres -d moviedb -c "DELETE FROM movie_comments WHERE content LIKE 'Load test%'"
```

### 5. HTML Report Structure
JMeter generates dashboard with:
- Response time distribution
- Throughput over time
- Error percentage
- Top 5 errors by sampler
- Response time percentiles (p50, p90, p95, p99)
- Active threads over time
- Latency vs request summary

### 6. Grafana Correlation
- During load test, observe Grafana dashboards:
  - JVM Micrometer: memory, GC pressure, thread count
  - Spring Boot HTTP: request rate, error rate, latency
  - Custom business metrics: booking.created, payment.completed counters
- Screenshot dashboards during peak load for report

## Todo
- [ ] Create `run-load-test.sh` with parameterized execution
- [ ] Create `run-full-suite.sh` for sequential execution
- [ ] Create `seed-test-data.sql` for database population
- [ ] Create `cleanup-test-data.sh` for post-test cleanup
- [ ] Add `.gitignore` entries for `results/` directory
- [ ] Document how to read JMeter HTML reports
- [ ] Document Grafana dashboard correlation steps

## Success Criteria
- Single command runs any load test and produces HTML report
- Full suite completes all 5 tests with reports
- Cleanup script removes all test data without affecting real data
- Reports clearly show pass/fail against performance thresholds

## Risk Assessment
- **Risk**: JMeter OOM on large result files
  - **Mitigation**: Use `-Jjmeter.save.saveservice.response_data=false` to skip response bodies
- **Risk**: Test results directory grows large (GBs)
  - **Mitigation**: `.gitignore`, cleanup old results regularly

## Security Considerations
- Never commit results (may contain JWT tokens)
- Cleanup script uses `LIKE 'loadtest_%'` to avoid deleting real users

## Next Steps
- Execute tests, analyze results, document findings
