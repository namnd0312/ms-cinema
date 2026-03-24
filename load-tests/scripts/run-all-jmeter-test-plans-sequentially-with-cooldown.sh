#!/usr/bin/env bash
# =============================================================================
# run-all-jmeter-test-plans-sequentially-with-cooldown.sh
# Runs every JMeter test plan in ../jmx/ one after another with a 60-second
# cooldown between runs so services can recover before the next test.
#
# Usage:
#   ./run-all-jmeter-test-plans-sequentially-with-cooldown.sh [threads] [ramp-up] [duration]
#
# Defaults: 100 threads, 60s ramp-up, 300s duration per plan
# =============================================================================
set -euo pipefail

THREADS="${1:-100}"
RAMP_UP="${2:-60}"
DURATION="${3:-300}"
COOLDOWN_SECONDS=60

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="${SCRIPT_DIR}/run-single-jmeter-test-plan-with-html-report.sh"

if [[ ! -x "${RUNNER}" ]]; then
    chmod +x "${RUNNER}"
fi

# Run plans in a logical dependency order.
# Each entry is the JMX base name without the trailing "-load-test.jmx".
TEST_PLANS=(
    "auth-service-login-register-refresh-logout"
    "movie-service-browse-rate-comment-showtimes"
    "booking-service-reserve-check-cancel-seats"
    "payment-service-reserve-fake-success-verify-booking-confirmed"
    "full-user-journey-login-browse-book-pay-logout-e2e"
)

TOTAL="${#TEST_PLANS[@]}"
PASSED=0
FAILED=0
FAILED_PLANS=()

echo "============================================================"
echo "  Full JMeter Load Test Suite"
echo "  Plans     : ${TOTAL}"
echo "  Threads   : ${THREADS}"
echo "  Ramp-up   : ${RAMP_UP}s"
echo "  Duration  : ${DURATION}s"
echo "  Cooldown  : ${COOLDOWN_SECONDS}s between plans"
echo "  Started   : $(date)"
echo "============================================================"
echo ""

for i in "${!TEST_PLANS[@]}"; do
    PLAN="${TEST_PLANS[$i]}"
    PLAN_NUM=$((i + 1))

    echo "------------------------------------------------------------"
    echo "  [${PLAN_NUM}/${TOTAL}] Running: ${PLAN}"
    echo "  Time: $(date)"
    echo "------------------------------------------------------------"

    if "${RUNNER}" "${PLAN}" "${THREADS}" "${RAMP_UP}" "${DURATION}"; then
        PASSED=$((PASSED + 1))
        echo "  [${PLAN}] PASSED"
    else
        FAILED=$((FAILED + 1))
        FAILED_PLANS+=("${PLAN}")
        echo "  [${PLAN}] FAILED — continuing with next plan"
    fi

    # Cooldown between plans (skip after last plan)
    if [[ ${PLAN_NUM} -lt ${TOTAL} ]]; then
        echo ""
        echo "  Cooling down for ${COOLDOWN_SECONDS}s before next plan..."
        sleep "${COOLDOWN_SECONDS}"
    fi
done

echo ""
echo "============================================================"
echo "  Suite Complete — $(date)"
echo "  Passed : ${PASSED}/${TOTAL}"
echo "  Failed : ${FAILED}/${TOTAL}"
if [[ ${FAILED} -gt 0 ]]; then
    echo "  Failed plans:"
    for p in "${FAILED_PLANS[@]}"; do
        echo "    - ${p}"
    done
fi
echo "============================================================"

exit $([[ ${FAILED} -eq 0 ]] && echo 0 || echo 1)
