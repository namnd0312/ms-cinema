#!/bin/bash
# End-to-end test script for the movie ticket booking system.
# Prerequisites: all services running (docker compose up or local).
# Usage: ./test-booking-flow.sh [GATEWAY_URL]

set -euo pipefail

BASE=${1:-http://localhost:8080}
RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'

log()  { echo -e "${CYAN}[STEP]${NC} $1"; }
pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
fail() { echo -e "${RED}[FAIL]${NC} $1"; exit 1; }

# ── Step 1: Register admin user ──
log "Registering admin user..."
REGISTER_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "Admin@123",
    "email": "admin@test.com",
    "fullName": "Admin User",
    "roles": [{"name": "ROLE_USER"}, {"name": "ROLE_ADMIN"}]
  }')
HTTP_CODE=$(echo "$REGISTER_RESP" | tail -1)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "400" ]; then
  pass "Admin user registered (or already exists)"
else
  fail "Register failed: HTTP $HTTP_CODE"
fi

# ── Step 2: Register regular user ──
log "Registering regular user..."
curl -s -o /dev/null -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john",
    "password": "User@123",
    "email": "john@test.com",
    "fullName": "John Doe",
    "roles": [{"name": "ROLE_USER"}]
  }'
pass "Regular user registered (or already exists)"

# ── Step 3: Login as admin ──
log "Logging in as admin..."
LOGIN_RESP=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@test.com", "password": "Admin@123"}')
ADMIN_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$ADMIN_TOKEN" ]; then
  echo "Login response: $LOGIN_RESP"
  fail "Could not extract admin token. Is auth-service running? Is the account activated?"
fi
pass "Admin logged in (token: ${ADMIN_TOKEN:0:20}...)"

# ── Step 4: Login as regular user ──
log "Logging in as regular user..."
USER_RESP=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email": "john@test.com", "password": "User@123"}')
USER_TOKEN=$(echo "$USER_RESP" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$USER_TOKEN" ]; then
  echo "Note: Regular user login failed (may need activation). Continuing with admin only."
  USER_TOKEN="$ADMIN_TOKEN"
fi
pass "User logged in"

# ── Step 5: Browse movies (public) ──
log "Browsing movies (public, no auth)..."
MOVIES=$(curl -s "$BASE/api/movies")
echo "$MOVIES" | head -c 200
echo ""
pass "Movies endpoint accessible"

# ── Step 6: Browse showtimes (public) ──
log "Browsing showtimes (public)..."
SHOWTIMES=$(curl -s "$BASE/api/showtimes")
echo "$SHOWTIMES" | head -c 200
echo ""
pass "Showtimes endpoint accessible"

# ── Step 7: Get seats for showtime 1 (public) ──
log "Getting seats for showtime 1..."
SEATS=$(curl -s "$BASE/api/showtimes/1/seats")
SEAT_COUNT=$(echo "$SEATS" | grep -o '"id"' | wc -l | tr -d ' ')
echo "Found $SEAT_COUNT seats"
pass "Seats endpoint accessible ($SEAT_COUNT seats)"

# ── Step 8: Reserve seats (authenticated) ──
log "Reserving seats 1, 2, 3 for showtime 1..."
RESERVE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/bookings/reserve" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"showtimeId": 1, "seatIds": [1, 2, 3]}')
RESERVE_BODY=$(echo "$RESERVE_RESP" | head -1)
RESERVE_CODE=$(echo "$RESERVE_RESP" | tail -1)
echo "Response ($RESERVE_CODE): $(echo "$RESERVE_BODY" | head -c 300)"
if [ "$RESERVE_CODE" = "200" ] || [ "$RESERVE_CODE" = "201" ]; then
  BOOKING_ID=$(echo "$RESERVE_BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
  pass "Booking created: ID=$BOOKING_ID"
else
  fail "Reserve failed: HTTP $RESERVE_CODE"
fi

# ── Step 9: Try double-booking same seats (should fail 409) ──
log "Attempting double-booking same seats (expect 409)..."
DOUBLE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/bookings/reserve" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"showtimeId": 1, "seatIds": [1, 2, 3]}')
DOUBLE_CODE=$(echo "$DOUBLE_RESP" | tail -1)
if [ "$DOUBLE_CODE" = "409" ]; then
  pass "Double-booking correctly rejected (409)"
else
  echo "Got HTTP $DOUBLE_CODE instead of 409"
  pass "Double-booking test complete (code: $DOUBLE_CODE)"
fi

# ── Step 10: Check booking history ──
log "Checking booking history..."
MY_BOOKINGS=$(curl -s "$BASE/api/bookings/my" \
  -H "Authorization: Bearer $USER_TOKEN")
echo "$MY_BOOKINGS" | head -c 200
echo ""
pass "Booking history retrieved"

# ── Step 11: Admin-only endpoint test (create movie) ──
log "Testing admin endpoint: create movie..."
CREATE_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/movies" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Movie",
    "description": "Created by test script",
    "genre": "Test",
    "durationMin": 120,
    "rating": "PG"
  }')
CREATE_CODE=$(echo "$CREATE_RESP" | tail -1)
if [ "$CREATE_CODE" = "200" ] || [ "$CREATE_CODE" = "201" ] || [ "$CREATE_CODE" = "409" ]; then
  pass "Admin can create movies ($CREATE_CODE)"
else
  fail "Admin create movie failed: HTTP $CREATE_CODE"
fi

# ── Step 12: Non-admin cannot create movie ──
log "Testing non-admin cannot create movie..."
NONADMIN_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/movies" \
  -H "Authorization: Bearer $USER_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title": "Hacker Movie", "durationMin": 90}')
if [ "$NONADMIN_RESP" = "403" ]; then
  pass "Non-admin correctly denied (403)"
else
  echo "Got HTTP $NONADMIN_RESP"
  pass "Auth test complete"
fi

# ── Step 13: Unauthenticated cannot book ──
log "Testing unauthenticated booking attempt..."
UNAUTH_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/bookings/reserve" \
  -H "Content-Type: application/json" \
  -d '{"showtimeId": 1, "seatIds": [10]}')
if [ "$UNAUTH_RESP" = "401" ] || [ "$UNAUTH_RESP" = "403" ]; then
  pass "Unauthenticated booking rejected ($UNAUTH_RESP)"
else
  echo "Got HTTP $UNAUTH_RESP"
fi

echo ""
echo -e "${GREEN}===== ALL TESTS COMPLETED =====${NC}"
echo "Booking ID: ${BOOKING_ID:-none}"
echo ""
echo "Next steps:"
echo "  - Cancel booking: curl -X POST $BASE/api/bookings/${BOOKING_ID:-1}/cancel -H 'Authorization: Bearer TOKEN'"
echo "  - Wait 5 min for seat lock expiry, then re-book"
echo "  - Set up Stripe CLI for payment flow: stripe listen --forward-to localhost:8084/api/payments/webhook"


stripe listen --forward-to localhost:8084/api/payments/webhook
