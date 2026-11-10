#!/usr/bin/env bash
# Tests the facility API (/api/v1/clinics and /rooms): happy paths, DB match, validation,
# status rules (schedules block closing, closed clinic blocks opening rooms) and roles.
# Each line asserts the status the API SHOULD return; FAIL lines are candidate bugs.
#
# Usage: scripts/facility-test.sh         (BASE_URL defaults to http://localhost:8080)
source "$(dirname "$0")/lib/smoke-lib.sh"

future() { date -d "+$1 days" +%F; }
str() { printf "$1%.0s" $(seq 1 "$2"); } # str CHAR COUNT
CL="/clinics"
valid_clinic() { # valid_clinic CODE [extra json fields]
  echo "{\"code\":\"$1\",\"name\":\"Clinic $1\",\"address\":\"1 Test St\",\"phoneNumber\":\"0900000000\"${2:+,$2}}"; }

echo "== Setup =="
seed_base
PATIENT_USER="fac_patient_$SUFFIX"; PATIENT_USER_ID="$(mkuser "$PATIENT_USER" PATIENT ACTIVE)"
login smoke_admin

echo "== Clinic: create =="
call POST $CL 201 "{\"code\":\"FC$SUFFIX\",\"name\":\"Phòng khám $SUFFIX\",\"address\":\"12 Lê Lợi, Q1\",\"phoneNumber\":\"0281234567\",\"email\":\"fc$SUFFIX@example.com\",\"description\":\"mô tả\"}"
C1="$(jq_of .data.publicId)"; C1_ID="$(jq_of .data.id)"
db "clinic row" "select code||'|'||name||'|'||address||'|'||phone_number||'|'||email||'|'||description||'|'||status from clinics where public_id = '$C1'" "$(jq_of '.data | "\(.code)|\(.name)|\(.address)|\(.phoneNumber)|\(.email)|\(.description)|\(.status)"')"
db "new clinic starts OPENING" "select status from clinics where public_id = '$C1'" "OPENING"
call POST $CL 201 "$(valid_clinic "  TR$SUFFIX  ")"; C_TRIM="$(jq_of .data.publicId)"
db "code is trimmed" "select code from clinics where public_id = '$C_TRIM'" "TR$SUFFIX"
call POST $CL 400 "$(valid_clinic "NB$SUFFIX" '"email":"   "')"                         # whitespace-only email is rejected by @Email
call POST $CL 201 "$(valid_clinic "NL$SUFFIX" '"description":"   "')"; C_NULL="$(jq_of .data.publicId)"
db "blank description stored as NULL" "select (email is null)::text||'|'||(description is null)::text from clinics where public_id = '$C_NULL'" "true|true"
call POST $CL 409 "$(valid_clinic "FC$SUFFIX")"
call POST $CL 409 "$(valid_clinic "  FC$SUFFIX  ")"                      # duplicate after trim
call POST $CL 400 '{}'
call POST $CL 400 'not json'
call POST $CL 400 "$(valid_clinic "$(str a 31)")"
call POST $CL 400 "{\"code\":\"V1$SUFFIX\",\"name\":\"$(str a 151)\",\"address\":\"a\",\"phoneNumber\":\"1\"}"
call POST $CL 400 "{\"code\":\"V2$SUFFIX\",\"name\":\"n\",\"address\":\"$(str a 501)\",\"phoneNumber\":\"1\"}"
call POST $CL 400 "{\"code\":\"V3$SUFFIX\",\"name\":\"n\",\"address\":\"a\",\"phoneNumber\":\"$(str 1 26)\"}"
call POST $CL 400 "$(valid_clinic "V4$SUFFIX" '"email":"not-an-email"')"
call POST $CL 400 "$(valid_clinic "V5$SUFFIX" "\"description\":\"$(str a 2001)\"")"
call POST $CL 400 "{\"code\":\"   \",\"name\":\"n\",\"address\":\"a\",\"phoneNumber\":\"1\"}"
call POST $CL 400 "{\"code\":\"V6$SUFFIX\",\"name\":\"n\",\"address\":\"a\"}"            # phone missing
call POST $CL 400 "$(valid_clinic "V7$SUFFIX" '"phoneNumber":"abc-not-a-phone"')"          # phone format
call POST $CL 201 "$(valid_clinic "UN$SUFFIX" '"description":"日本語 ✓ Tiếng Việt"')"; C_UNI="$(jq_of .data.publicId)"
db "unicode description round-trips" "select description from clinics where public_id = '$C_UNI'" "日本語 ✓ Tiếng Việt"
call POST $CL 201 "$(valid_clinic "<b>x$SUFFIX" '"name":"<script>alert(1)</script>"')"   # stored as plain text; FE must escape
db "failed creates left no rows" "select count(*) from clinics where code in ('V1$SUFFIX','V2$SUFFIX','V3$SUFFIX','V4$SUFFIX','V5$SUFFIX','V6$SUFFIX','V7$SUFFIX')" "0"

echo "== Clinic: read / update =="
call GET "$CL/$C1" 200
db "GET matches db" "select name from clinics where public_id = '$C1'" "$(jq_of .data.name)"
call GET "$CL/00000000-0000-0000-0000-000000000000" 404
call GET "$CL/not-a-uuid" 400
call PUT "$CL/$C1" 200 "{\"code\":\"FC$SUFFIX\",\"name\":\"Đã đổi tên\",\"address\":\"34 Hai Bà Trưng\",\"phoneNumber\":\"0287654321\"}"
db "clinic after PUT (email/description cleared)" "select name||'|'||address||'|'||phone_number||'|'||coalesce(email,'null')||'|'||coalesce(description,'null') from clinics where public_id = '$C1'" "Đã đổi tên|34 Hai Bà Trưng|0287654321|null|null"
db "PUT keeps status" "select status from clinics where public_id = '$C1'" "OPENING"
call PUT "$CL/$C1" 409 "$(valid_clinic "NL$SUFFIX")"                                      # code of another clinic
call PUT "$CL/$C1" 400 '{"code":"","name":"","address":"","phoneNumber":""}'
call PUT "$CL/00000000-0000-0000-0000-000000000000" 404 "$(valid_clinic "ZZ$SUFFIX")"
db "PUT 409 did not change the clinic" "select code from clinics where public_id = '$C1'" "FC$SUFFIX"

echo "== Clinic: list / filter =="
call GET "$CL?page=1&size=5" 200
db "list total = db count" "select count(*) from clinics" "$(jq_of .meta.totalElements)"
call GET "$CL?keyword=FC$SUFFIX" 200
db "keyword matches code" "select count(*) from clinics where code ilike '%FC$SUFFIX%' or name ilike '%FC$SUFFIX%'" "$(jq_of .meta.totalElements)"
call GET "$CL?keyword=fc$SUFFIX" 200                                                      # case-insensitive
db "keyword is case-insensitive" "select count(*) from clinics where code ilike '%fc$SUFFIX%'" "$(jq_of .meta.totalElements)"
call GET "$CL?keyword=%25" 200                                                            # literal %, must not match everything
db "keyword % is literal" "select count(*) from clinics where code like '%\%%' or name like '%\%%'" "$(jq_of .meta.totalElements)"
call GET "$CL?keyword=_" 200
db "keyword _ is literal" "select count(*) from clinics where code like '%\_%' or name like '%\_%'" "$(jq_of .meta.totalElements)"
call GET "$CL?keyword=%27%3B%20drop%20table%20clinics%3B--" 200
db "clinics table still exists" "select count(*) > 0 from clinics" "t"
call GET "$CL?status=OPENING" 200
db "status filter" "select count(*) from clinics where status = 'OPENING'" "$(jq_of .meta.totalElements)"
call GET "$CL?status=BOGUS" 400
call GET "$CL?page=0" 400
call GET "$CL?size=101" 400
call GET "$CL?page=99999" 200
call GET "$CL?sort=-createdAt,name" 200
call GET "$CL?sort=bogus" 400

echo "== Room: create / read / update =="
call POST "$CL/$C1/rooms" 201 "{\"code\":\"R1\",\"name\":\"Phòng 1\"}"; R1="$(jq_of .data.publicId)"
db "room row" "select code||'|'||name||'|'||status||'|'||clinic_id from rooms where public_id = '$R1'" "R1|Phòng 1|OPENING|$C1_ID"
db "room links to clinic publicId" "select public_id::text from clinics where id = (select clinic_id from rooms where public_id = '$R1')" "$(jq_of .data.clinicPublicId)"
call POST "$CL/$C1/rooms" 201 "{\"code\":\"  R2  \",\"name\":\"  Phòng 2  \"}"; R2="$(jq_of .data.publicId)"
db "room code/name trimmed" "select code||'|'||name from rooms where public_id = '$R2'" "R2|Phòng 2"
call POST "$CL/$C1/rooms" 409 '{"code":"R1","name":"dup"}'
call POST "$CL/$C1/rooms" 409 '{"code":" R1 ","name":"dup after trim"}'
call POST "$CL/$C_NULL/rooms" 201 '{"code":"R1","name":"same code, other clinic"}'; R_OTHER="$(jq_of .data.publicId)"
call POST "$CL/$C1/rooms" 400 '{}'
call POST "$CL/$C1/rooms" 400 "{\"code\":\"$(str a 31)\",\"name\":\"x\"}"
call POST "$CL/$C1/rooms" 400 "{\"code\":\"RX\",\"name\":\"$(str a 151)\"}"
call POST "$CL/$C1/rooms" 400 '{"code":"  ","name":"x"}'
call POST "$CL/00000000-0000-0000-0000-000000000000/rooms" 404 '{"code":"R9","name":"x"}'
call POST "$CL/not-a-uuid/rooms" 400 '{"code":"R9","name":"x"}'
call GET "$CL/$C1/rooms/$R1" 200
call GET "$CL/$C1/rooms/$R_OTHER" 404                                                     # room of another clinic
call GET "$CL/$C_NULL/rooms/$R1" 404
call GET "$CL/$C1/rooms/00000000-0000-0000-0000-000000000000" 404
call GET "$CL/00000000-0000-0000-0000-000000000000/rooms/$R1" 404
call PUT "$CL/$C1/rooms/$R1" 200 '{"code":"R1","name":"Phòng 1 đổi tên"}'
db "room after PUT" "select name from rooms where public_id = '$R1'" "Phòng 1 đổi tên"
call PUT "$CL/$C1/rooms/$R1" 409 '{"code":"R2","name":"x"}'
call PUT "$CL/$C1/rooms/$R_OTHER" 404 '{"code":"R1","name":"x"}'                          # cannot edit another clinic's room
call PUT "$CL/$C1/rooms/$R1" 400 '{"code":"","name":""}'
call GET "$CL/$C1/rooms" 200
db "room list total = db (only this clinic)" "select count(*) from rooms where clinic_id = $C1_ID" "$(jq_of .meta.totalElements)"
call GET "$CL/$C1/rooms?keyword=r2" 200
db "room keyword" "select count(*) from rooms where clinic_id = $C1_ID and (code ilike '%r2%' or name ilike '%r2%')" "$(jq_of .meta.totalElements)"
call GET "$CL/$C1/rooms?status=CLOSING" 200
db "room status filter" "select count(*) from rooms where clinic_id = $C1_ID and status = 'CLOSING'" "$(jq_of .meta.totalElements)"
call GET "$CL/$C1/rooms?status=BOGUS" 400
call GET "$CL/00000000-0000-0000-0000-000000000000/rooms" 404

echo "== Status changes (no schedules) =="
call PATCH "$CL/$C1/rooms/$R1/status" 200 '{"status":"CLOSING"}'
db "room closed" "select status from rooms where public_id = '$R1'" "CLOSING"
call PATCH "$CL/$C1/rooms/$R1/status" 200 '{"status":"CLOSING"}'                          # same status: idempotent
call PATCH "$CL/$C1/rooms/$R1/status" 200 '{"status":"OPENING"}'
db "room reopened" "select status from rooms where public_id = '$R1'" "OPENING"
call PATCH "$CL/$C1/rooms/$R1/status" 400 '{"status":"BOGUS"}'
call PATCH "$CL/$C1/rooms/$R1/status" 400 '{}'
call PATCH "$CL/$C1/rooms/00000000-0000-0000-0000-000000000000/status" 404 '{"status":"CLOSING"}'
call PATCH "$CL/$C1/rooms/$R_OTHER/status" 404 '{"status":"CLOSING"}'
call PATCH "$CL/$C_UNI/status" 200 '{"status":"CLOSING"}'
db "clinic closed" "select status from clinics where public_id = '$C_UNI'" "CLOSING"
call POST "$CL/$C_UNI/rooms" 201 '{"code":"RC","name":"room in closed clinic"}'; R_IN_CLOSED="$(jq_of .data.publicId)"
db "room created in closed clinic inherits CLOSING" "select status from rooms where public_id = '$R_IN_CLOSED'" "CLOSING"
call PATCH "$CL/$C_UNI/rooms/$R_IN_CLOSED/status" 409 '{"status":"OPENING"}'              # CLINIC_CLOSED
call PATCH "$CL/$C_UNI/status" 200 '{"status":"OPENING"}'
call PATCH "$CL/$C_UNI/rooms/$R_IN_CLOSED/status" 200 '{"status":"OPENING"}'
call PATCH "$CL/$C_UNI/status" 200 '{"status":"CLOSING"}'
call GET "$CL/$C_UNI/rooms/$R_IN_CLOSED" 200
db "closing a clinic closes its open rooms (no room stays OPENING)" "select status from rooms where public_id = '$R_IN_CLOSED'" "CLOSING"
call PATCH "$CL/00000000-0000-0000-0000-000000000000/status" 404 '{"status":"CLOSING"}'
call PATCH "$CL/$C1/status" 400 '{"status":"BOGUS"}'

echo "== Status vs schedules =="
call POST /specialties 201 "{\"code\":\"FS$SUFFIX\",\"name\":\"Fac spec $SUFFIX\"}"; SPEC="$(jq_of .data.id)"
call POST /doctors 201 "{\"userId\":$DOCTOR_USER_ID,\"code\":\"FD$SUFFIX\",\"name\":\"Fac Doc\",\"degree\":\"ThS\",\"specialtyIds\":[$SPEC]}"; DOC="$(jq_of .data.id)"
ROOM1_ID="$(psql_q "select id from rooms where public_id = '$R1'")"
D="$(future 5)"
call POST /doctor-schedules 201 "{\"doctorId\":$DOC,\"specialtyId\":$SPEC,\"clinicId\":$C1_ID,\"roomId\":$ROOM1_ID,\"shiftId\":1,\"workDate\":\"$D\",\"generalCapacity\":4,\"specialistCapacity\":1}"; SCH="$(jq_of .data.publicId)"
call PATCH "$CL/$C1/rooms/$R1/status" 409 '{"status":"CLOSING"}'                          # FACILITY_HAS_SCHEDULES
db "room still open after 409" "select status from rooms where public_id = '$R1'" "OPENING"
call PATCH "$CL/$C1/status" 409 '{"status":"CLOSING"}'
db "clinic still open after 409" "select status from clinics where public_id = '$C1'" "OPENING"
call PATCH "$CL/$C1/rooms/$R2/status" 200 '{"status":"CLOSING"}'                          # room without schedules can close
call POST /doctor-schedules 409 "{\"doctorId\":$DOC,\"specialtyId\":$SPEC,\"clinicId\":$C1_ID,\"roomId\":$(psql_q "select id from rooms where public_id = '$R2'"),\"shiftId\":2,\"workDate\":\"$D\",\"generalCapacity\":4,\"specialistCapacity\":1}"   # schedule in a closed room
call PATCH "/doctor-schedules/$SCH/status" 200 '{"status":"CANCELLED"}'
call PATCH "$CL/$C1/rooms/$R1/status" 200 '{"status":"CLOSING"}'                          # cancelled schedules no longer block
call PATCH "$CL/$C1/status" 200 '{"status":"CLOSING"}'
call POST /doctor-schedules 409 "{\"doctorId\":$DOC,\"specialtyId\":$SPEC,\"clinicId\":$C1_ID,\"roomId\":$ROOM1_ID,\"shiftId\":2,\"workDate\":\"$D\",\"generalCapacity\":4,\"specialistCapacity\":1}"   # closed clinic/room
call PATCH "$CL/$C1/status" 200 '{"status":"OPENING"}'

echo "== Roles =="
for who in "$DOCTOR_USER" "$PATIENT_USER"; do
  login "$who"
  call GET $CL 403
  call POST $CL 403 "$(valid_clinic "RL$SUFFIX")"
  call GET "$CL/$C1" 403
  call PUT "$CL/$C1" 403 "$(valid_clinic "FC$SUFFIX")"
  call PATCH "$CL/$C1/status" 403 '{"status":"CLOSING"}'
  call GET "$CL/$C1/rooms" 403
  call POST "$CL/$C1/rooms" 403 '{"code":"RL","name":"x"}'
  call GET "$CL/$C1/rooms/$R1" 403
  call PUT "$CL/$C1/rooms/$R1" 403 '{"code":"R1","name":"x"}'
  call PATCH "$CL/$C1/rooms/$R1/status" 403 '{"status":"CLOSING"}'
done
TOKEN=""
call GET $CL 401
call POST $CL 401 "$(valid_clinic "RL$SUFFIX")"
TOKEN="garbage.token.value"; call GET $CL 401
db "no clinic created by unauthorized calls" "select count(*) from clinics where code = 'RL$SUFFIX'" "0"

echo
echo "Passed: $PASS   Failed: $FAIL"
[[ "$FAIL" -eq 0 ]]
