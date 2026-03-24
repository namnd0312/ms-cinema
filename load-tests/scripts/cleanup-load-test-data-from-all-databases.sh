#!/usr/bin/env bash
# =============================================================================
# cleanup-load-test-data-from-all-databases.sh
# Removes all load-test users, roles mappings, movies, theaters, seats, and
# showtimes that were created by the seed script from all ms-cinema databases.
#
# Usage:
#   ./cleanup-load-test-data-from-all-databases.sh [pg-host] [pg-port] [pg-user]
#
# Defaults: localhost 5432 postgres
# =============================================================================
set -euo pipefail

PG_HOST="${1:-localhost}"
PG_PORT="${2:-5432}"
PG_USER="${3:-postgres}"
PGPASSWORD="${PGPASSWORD:-postgres}"
export PGPASSWORD

PSQL="psql -h ${PG_HOST} -p ${PG_PORT} -U ${PG_USER} -v ON_ERROR_STOP=1"

echo "============================================================"
echo "  Cleanup Load Test Data"
echo "  Host : ${PG_HOST}:${PG_PORT}"
echo "  User : ${PG_USER}"
echo "  Time : $(date)"
echo "============================================================"

# ---------------------------------------------------------------------------
# testdb — remove load-test users (username prefix: loadtest_)
# ---------------------------------------------------------------------------
echo ""
echo "[1/3] Cleaning testdb (auth-service)..."

${PSQL} -d testdb <<'SQL'
-- Remove role mappings first (FK constraint)
DELETE FROM user_roles
WHERE user_id IN (
    SELECT id FROM users WHERE username LIKE 'loadtest_%'
);

-- Remove the users
DELETE FROM users WHERE username LIKE 'loadtest_%';

\echo '  testdb: load-test users removed'
SQL

# ---------------------------------------------------------------------------
# moviedb — remove movies 1-10, theaters 1-3, seats, showtimes 1-15
# ---------------------------------------------------------------------------
echo ""
echo "[2/3] Cleaning moviedb (movie-service)..."

${PSQL} -d moviedb <<'SQL'
-- Showtimes reference theaters and movies — delete first
DELETE FROM showtimes WHERE id BETWEEN 1 AND 15;

-- Seats reference theaters
DELETE FROM seats
WHERE theater_id IN (1, 2, 3);

-- Theaters
DELETE FROM theaters WHERE id IN (1, 2, 3);

-- Movies
DELETE FROM movies WHERE id BETWEEN 1 AND 10;

\echo '  moviedb: movies, theaters, seats, showtimes removed'
SQL

# ---------------------------------------------------------------------------
# bookingdb — remove any bookings made by load-test users
# ---------------------------------------------------------------------------
echo ""
echo "[3/3] Cleaning bookingdb (booking-service)..."

${PSQL} -d bookingdb <<'SQL'
-- Booked seats reference bookings — delete first
DELETE FROM booked_seats
WHERE booking_id IN (
    SELECT id FROM bookings WHERE user_email LIKE 'loadtest_%@test.com'
);

DELETE FROM bookings WHERE user_email LIKE 'loadtest_%@test.com';

\echo '  bookingdb: load-test bookings removed'
SQL

echo ""
echo "============================================================"
echo "  Cleanup complete — $(date)"
echo "============================================================"
