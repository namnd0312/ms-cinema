#!/usr/bin/env bash
# =============================================================================
# run-single-jmeter-test-plan-with-html-report.sh
# Runs one JMeter test plan in CLI mode and generates an HTML dashboard report.
#
# Usage:
#   ./run-single-jmeter-test-plan-with-html-report.sh <test-name> [threads] [ramp-up] [duration]
#
# Arguments:
#   test-name   Name matching a file in ../jmx/<test-name>-load-test.jmx
#   threads     Concurrent virtual users      (default: 100)
#   ramp-up     Ramp-up period in seconds     (default: 60)
#   duration    Sustained load duration (s)   (default: 300)
#
# Example:
#   ./run-single-jmeter-test-plan-with-html-report.sh auth 1000 300 900
# =============================================================================
set -euo pipefail

# ---------------------------------------------------------------------------
# Validate arguments
# ---------------------------------------------------------------------------
if [[ $# -lt 1 ]]; then
    echo "Usage: $0 <test-name> [threads] [ramp-up] [duration]"
    echo "Example: $0 auth 1000 300 900"
    exit 1
fi

TEST_NAME="${1}"
THREADS="${2:-100}"
RAMP_UP="${3:-60}"
DURATION="${4:-300}"

# ---------------------------------------------------------------------------
# Resolve paths relative to this script's location
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOAD_TESTS_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JMX_FILE="${LOAD_TESTS_DIR}/jmx/${TEST_NAME}-load-test.jmx"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
RESULTS_DIR="${LOAD_TESTS_DIR}/results/${TEST_NAME}-${TIMESTAMP}"
JTL_FILE="${RESULTS_DIR}/${TEST_NAME}.jtl"
HTML_REPORT_DIR="${RESULTS_DIR}/html-report"
LOG_FILE="${RESULTS_DIR}/jmeter.log"

# ---------------------------------------------------------------------------
# Validate JMX file exists
# ---------------------------------------------------------------------------
if [[ ! -f "${JMX_FILE}" ]]; then
    echo "ERROR: Test plan not found: ${JMX_FILE}"
    echo "Available test plans:"
    ls "${LOAD_TESTS_DIR}/jmx/"*.jmx 2>/dev/null | xargs -I{} basename {} || echo "  (none)"
    exit 1
fi

# ---------------------------------------------------------------------------
# Detect JMeter binary
# ---------------------------------------------------------------------------
if command -v jmeter &>/dev/null; then
    JMETER_BIN="jmeter"
elif [[ -n "${JMETER_HOME:-}" && -x "${JMETER_HOME}/bin/jmeter" ]]; then
    JMETER_BIN="${JMETER_HOME}/bin/jmeter"
else
    echo "ERROR: JMeter not found. Install it or set JMETER_HOME."
    exit 1
fi

# ---------------------------------------------------------------------------
# Create results directory
# ---------------------------------------------------------------------------
mkdir -p "${RESULTS_DIR}" "${HTML_REPORT_DIR}"

echo "============================================================"
echo "  JMeter Load Test"
echo "  Plan      : ${TEST_NAME}"
echo "  Threads   : ${THREADS}"
echo "  Ramp-up   : ${RAMP_UP}s"
echo "  Duration  : ${DURATION}s"
echo "  Results   : ${RESULTS_DIR}"
echo "============================================================"

# ---------------------------------------------------------------------------
# Run JMeter (CLI / non-GUI mode)
# Heap is set to 4 GB to handle large result sets without OOM.
# ---------------------------------------------------------------------------
JVM_ARGS="-Xms512m -Xmx4g -XX:MaxMetaspaceSize=256m" \
    "${JMETER_BIN}" \
    -n \
    -t  "${JMX_FILE}" \
    -l  "${JTL_FILE}" \
    -j  "${LOG_FILE}" \
    -Jthreads="${THREADS}" \
    -Jrampup="${RAMP_UP}" \
    -Jduration="${DURATION}" \
    -e \
    -o  "${HTML_REPORT_DIR}"

EXIT_CODE=$?

echo ""
echo "============================================================"
if [[ ${EXIT_CODE} -eq 0 ]]; then
    echo "  TEST COMPLETE"
    echo "  JTL results : ${JTL_FILE}"
    echo "  HTML report : ${HTML_REPORT_DIR}/index.html"
else
    echo "  TEST FAILED (exit code ${EXIT_CODE})"
    echo "  Check log   : ${LOG_FILE}"
fi
echo "============================================================"

exit ${EXIT_CODE}
