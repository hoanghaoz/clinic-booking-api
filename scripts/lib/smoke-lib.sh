#!/usr/bin/env bash
# Shared helpers for scripts/smoke-test.sh and scripts/edge-cases.sh (source this file).
set -uo pipefail


BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
PG_CONTAINER="${PG_CONTAINER:-clinic-booking-api-postgres-1}"
DB_USER="${DB_USERNAME:-clinic}"
DB_NAME="${DB_NAME:-clinic_booking}"
PASSWORD="${SMOKE_PASSWORD:-Smoke@12345}"
SUFFIX="$(date +%s)"
WORK_DATE="$(date -d '+3 days' +%F)"
VERBOSE="${VERBOSE:-0}" # VERBOSE=1 prints the full response body

PASS=0
FAIL=0
BODY=""
TOKEN=""

psql_q() { docker exec -i "$PG_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" -tA -c "$1"; }

# call METHOD PATH EXPECTED_STATUS [JSON_BODY] -> sets $BODY, counts pass/fail
call() {
  local method="$1" path="$2" expected="$3" data="${4:-}" out status excerpt
  local args=(-s -X "$method" "$API$path" -w $'\n%{http_code}' -H "Content-Type: application/json")
  [[ -n "$TOKEN" ]] && args+=(-H "Authorization: Bearer $TOKEN")
  [[ -n "$data" ]] && args+=(-d "$data")
  out="$(curl "${args[@]}")"
  status="${out##*$'\n'}"
  BODY="${out%$'\n'*}"
  if [[ "$VERBOSE" == 1 ]]; then excerpt="$BODY"; else excerpt="$(jq -c '.data // .code // .' <<<"$BODY" 2>/dev/null | head -c 150)"; fi
  if [[ "$status" == "$expected" ]]; then
    PASS=$((PASS + 1)); printf '  ok    %-6s %-58s %s  %s\n' "$method" "$path" "$status" "$excerpt"
  else
    FAIL=$((FAIL + 1)); printf '  FAIL  %-6s %-58s got %s, want %s\n        %s\n' \
      "$method" "$path" "$status" "$expected" "$(head -c 400 <<<"$BODY")"
  fi
}

# jq_of FILTER -> value from the last response body
jq_of() { jq -r "$1" <<<"$BODY"; }

# db LABEL SQL EXPECTED -> compares the single value SQL returns with EXPECTED
db() {
  local label="$1" sql="$2" expected="$3" actual
  actual="$(psql_q "$sql" | head -1)"
  if [[ "$actual" == "$expected" ]]; then
    PASS=$((PASS + 1)); printf '  db ok %-58s = %s\n' "$label" "$actual"
  else
    FAIL=$((FAIL + 1)); printf '  db FAIL %-56s db=%s api=%s\n' "$label" "$actual" "$expected"
  fi
}

login() {
  local out
  out="$(curl -s -X POST "$API/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"$1\",\"password\":\"$PASSWORD\"}")"
  TOKEN="$(jq -r '.data.accessToken // empty' <<<"$out")"
  [[ -n "$TOKEN" ]] || { echo "Login failed for $1: $out"; exit 1; }
}

# Compares the current package (API detail) with its current version row + items in the DB.
check_package() {
  local id="$1" label="$2"
  call GET "/medical-packages/$id" 200
  local pkg="(select id from medical_packages where public_id = '$id')"
  local cur="package_id = $pkg and effective_to is null"
  db "$label: current version" "select version from package_versions where $cur" "$(jq_of .data.version)"
  db "$label: price" "select unit_price::numeric from package_versions where $cur" "$(jq_of '.data.price' | awk '{printf "%.2f", $1}')"
  db "$label: status" "select status from package_versions where $cur" "$(jq_of .data.status)"
  db "$label: item count" "select count(*) from package_version_items where package_version_id = (select id from package_versions where $cur)" "$(jq_of '.data.medicalItems | length')"
}


# seed_base: creates the admin + a fresh doctor account, the SMOKE clinic and a fresh room.
# Sets DOCTOR_USER, DOCTOR_USER_ID, CLINIC_ID, ROOM_ID.
seed_base() {
  psql_q "create extension if not exists pgcrypto" >/dev/null 2>&1
  DOCTOR_USER="smoke_doctor_$SUFFIX"
  for acc in "smoke_admin:ADMIN" "$DOCTOR_USER:DOCTOR"; do
    psql_q "insert into users (created_at, updated_at, public_id, username, email, password_hashed, role, status)
            values (now(), now(), gen_random_uuid(), '${acc%%:*}', '${acc%%:*}@example.com',
                    crypt('$PASSWORD', gen_salt('bf', 10)), '${acc##*:}', 'ACTIVE')
            on conflict (username) do update set password_hashed = excluded.password_hashed" >/dev/null
  done
  psql_q "insert into clinics (created_at, updated_at, public_id, phone_number, code, name, address, status)
          values (now(), now(), gen_random_uuid(), '0900000000', 'SMOKE', 'Smoke Clinic', '1 Test St', 'OPENING')
          on conflict (code) do nothing" >/dev/null
  CLINIC_ID="$(psql_q "select id from clinics where code = 'SMOKE'")"
  psql_q "insert into rooms (clinic_id, created_at, updated_at, public_id, code, name, status)
          values ($CLINIC_ID, now(), now(), gen_random_uuid(), 'R$SUFFIX', 'Room $SUFFIX', 'OPENING')
          on conflict (clinic_id, code) do nothing" >/dev/null
  ROOM_ID="$(psql_q "select id from rooms where clinic_id = $CLINIC_ID and code = 'R$SUFFIX'")"
  DOCTOR_USER_ID="$(psql_q "select id from users where username = '$DOCTOR_USER'")"
  echo "  clinic=$CLINIC_ID room=$ROOM_ID doctorUser=$DOCTOR_USER_ID"
}

# Helpers shared by the edge/module scripts.
mkuser() { # mkuser USERNAME ROLE STATUS
  psql_q "insert into users (created_at, updated_at, public_id, username, email, password_hashed, role, status)
          values (now(), now(), gen_random_uuid(), '$1', '$1@example.com', crypt('$PASSWORD', gen_salt('bf', 10)), '$2', '$3')
          on conflict (username) do update set password_hashed = excluded.password_hashed, status = '$3'" >/dev/null
  psql_q "select id from users where username = '$1'"
}
future() { date -d "+$1 days" +%F; }
