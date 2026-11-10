#!/usr/bin/env bash
# Edge / negative cases for every module against a running app (JWT enabled).
# Each line asserts the status the API SHOULD return; FAIL lines are candidate bugs.
#
# Usage: scripts/edge-cases.sh            (BASE_URL defaults to http://localhost:8080)
source "$(dirname "$0")/lib/smoke-lib.sh"

echo "== Setup =="
seed_base
OTHER_USER="smoke_other_$SUFFIX"; OTHER_USER_ID="$(mkuser "$OTHER_USER" DOCTOR ACTIVE)"
NODOC_USER="smoke_nodoc_$SUFFIX"; NODOC_USER_ID="$(mkuser "$NODOC_USER" DOCTOR ACTIVE)"
PATIENT_USER="smoke_patient_$SUFFIX"; PATIENT_USER_ID="$(mkuser "$PATIENT_USER" PATIENT ACTIVE)"
DISABLED_USER="smoke_disabled_$SUFFIX"; mkuser "$DISABLED_USER" DOCTOR DISABLED >/dev/null
psql_q "insert into clinics (created_at, updated_at, public_id, phone_number, code, name, address, status)
        values (now(), now(), gen_random_uuid(), '0900000001', 'SMOKE2', 'Smoke Clinic 2', '2 Test St', 'OPENING') on conflict (code) do nothing" >/dev/null
CLINIC2_ID="$(psql_q "select id from clinics where code = 'SMOKE2'")"
psql_q "insert into rooms (clinic_id, created_at, updated_at, public_id, code, name, status)
        values ($CLINIC2_ID, now(), now(), gen_random_uuid(), 'R$SUFFIX', 'Room2 $SUFFIX', 'OPENING') on conflict do nothing" >/dev/null
ROOM_OTHER_CLINIC_ID="$(psql_q "select id from rooms where clinic_id = $CLINIC2_ID and code = 'R$SUFFIX'")"
psql_q "insert into rooms (clinic_id, created_at, updated_at, public_id, code, name, status)
        values ($CLINIC_ID, now(), now(), gen_random_uuid(), 'B$SUFFIX', 'Room B $SUFFIX', 'OPENING') on conflict do nothing" >/dev/null
ROOM_B_ID="$(psql_q "select id from rooms where clinic_id = $CLINIC_ID and code = 'B$SUFFIX'")"
psql_q "insert into rooms (clinic_id, created_at, updated_at, public_id, code, name, status)
        values ($CLINIC_ID, now(), now(), gen_random_uuid(), 'C$SUFFIX', 'Room C $SUFFIX', 'CLOSING') on conflict do nothing" >/dev/null
ROOM_CLOSED_ID="$(psql_q "select id from rooms where clinic_id = $CLINIC_ID and code = 'C$SUFFIX'")"

login smoke_admin
call POST /specialties 201 "{\"code\":\"E$SUFFIX\",\"name\":\"Edge $SUFFIX\"}"; SPEC="$(jq_of .data.id)"
call POST /specialties 201 "{\"code\":\"F$SUFFIX\",\"name\":\"Edge2 $SUFFIX\"}"; SPEC2="$(jq_of .data.id)"
call POST /specialties 201 "{\"code\":\"I$SUFFIX\",\"name\":\"Inactive $SUFFIX\"}"; SPEC_INACTIVE="$(jq_of .data.id)"
call DELETE "/specialties/$SPEC_INACTIVE" 204
call POST /doctors 201 "{\"userId\":$DOCTOR_USER_ID,\"code\":\"D$SUFFIX\",\"name\":\"Edge Doc\",\"degree\":\"ThS\",\"specialtyIds\":[$SPEC]}"; DOC="$(jq_of .data.id)"
call POST /doctors 201 "{\"userId\":$OTHER_USER_ID,\"code\":\"O$SUFFIX\",\"name\":\"Other Doc\",\"degree\":\"ThS\",\"specialtyIds\":[$SPEC]}"; DOC2="$(jq_of .data.id)"
call POST /medical-items 201 "{\"name\":\"Edge item $SUFFIX\",\"type\":\"LAB\",\"price\":100}"; ITEM="$(jq_of .data.publicId)"
call POST /medical-items 201 "{\"name\":\"Edge item B $SUFFIX\",\"type\":\"LAB\",\"price\":100}"; ITEM_B="$(jq_of .data.publicId)"
PKG_BODY="\"status\":\"OPENING\",\"price\":1000,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":1}]}"
call POST /medical-packages 201 "{\"code\":\"E$SUFFIX\",\"name\":\"Edge pkg\",$PKG_BODY}"; PKG="$(jq_of .data.publicId)"
SCHED_DATE="$(future 5)"
SCHED_BODY="\"doctorId\":$DOC,\"specialtyId\":$SPEC,\"clinicId\":$CLINIC_ID,\"roomId\":$ROOM_ID,\"shiftId\":1,\"workDate\":\"$SCHED_DATE\",\"generalCapacity\":6,\"specialistCapacity\":2"
call POST /doctor-schedules 201 "{$SCHED_BODY}"; SCHED="$(jq_of .data.publicId)"

echo "== Auth edge cases =="
TOKEN=""
call POST /auth/login 400 '{}'
call POST /auth/login 400 '{"username":"","password":""}'
call POST /auth/login 400 'not json'
call POST /auth/login 401 "{\"username\":\"$DISABLED_USER\",\"password\":\"$PASSWORD\"}"
call POST /auth/login 401 "{\"username\":\"nobody-$SUFFIX\",\"password\":\"$PASSWORD\"}"
call POST /auth/login 400 "{\"username\":\"smoke_admin\",\"password\":\"$(printf 'a%.0s' {1..80})\"}"
call POST /auth/login 400 "{\"username\":\"smoke_admin\",\"password\":\"$(printf 'a%.0s' {1..73})\"}"
TOKEN="garbage.token.value"; call GET /specialties 401
login smoke_admin; GOOD="$TOKEN"
TOKEN="${GOOD%?}X"; call GET /specialties 401          # tampered signature
TOKEN="${GOOD:0:20}"; call GET /specialties 401        # truncated
TOKEN="$GOOD"
call GET /actuator-nope 404

echo "== Pagination / path edge cases =="
call GET "/specialties?page=0" 400
call GET "/specialties?size=0" 400
call GET "/specialties?page=-1" 400
call GET "/specialties?page=abc" 400
call GET "/specialties?page=99999" 200
call GET "/specialties?sort=bogusField" 400
call GET "/specialties?sort=-createdAt,name" 200
call GET "/specialties/abc" 400
call GET "/specialties/0" 404
call GET "/specialties/-1" 404
call GET "/specialties/99999999999999999999" 400
call GET "/specialties?keyword=%27%3B%20drop%20table%20users%3B--" 200
db "users table still exists" "select count(*) > 0 from users" "t"
call GET "/medical-items/not-a-uuid" 405    # no GET-by-id endpoint exists for items

echo "== Specialty edge cases =="
call POST /specialties 400 "{\"code\":\"$(printf 'a%.0s' {1..51})\",\"name\":\"x\"}"
call POST /specialties 400 "{\"code\":\"X$SUFFIX\",\"name\":\"$(printf 'a%.0s' {1..201})\"}"
call POST /specialties 400 '{"code":"   ","name":"   "}'
call POST /specialties 400 '{"name":"missing code"}'
call POST /specialties 201 "{\"code\":\"U$SUFFIX\",\"name\":\"Unicode ✓ 日本 $SUFFIX\"}"
call POST /specialties 201 "{\"code\":\"u$SUFFIX\",\"name\":\"case differs\"}"   # codes are case-sensitive today (U... and u... both allowed)
call PUT /specialties/999999 404 '{"name":"x"}'
call PUT "/specialties/$SPEC" 400 '{"name":""}'
call DELETE /specialties/999999 404
call DELETE "/specialties/$SPEC_INACTIVE" 204     # delete is idempotent
call PUT "/specialties/$SPEC_INACTIVE" 200 '{"name":"update inactive"}'   # editing a deactivated specialty is allowed today

echo "== Doctor edge cases =="
call POST /doctors 400 "{\"userId\":$PATIENT_USER_ID,\"code\":\"P$SUFFIX\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[]}"
call POST /doctors 400 "{\"code\":\"P$SUFFIX\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC]}"
call POST /doctors 409 "{\"userId\":$NODOC_USER_ID,\"code\":\"P$SUFFIX\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC_INACTIVE]}"
call POST /doctors 404 "{\"userId\":$NODOC_USER_ID,\"code\":\"P$SUFFIX\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[999999]}"
call POST /doctors 201 "{\"userId\":$NODOC_USER_ID,\"code\":\"N$SUFFIX\",\"name\":\"Dup specs\",\"degree\":\"x\",\"specialtyIds\":[$SPEC,$SPEC]}"; DOC3="$(jq_of .data.id)"
db "duplicate specialtyIds stored once" "select count(*) from doctor_specialities where doctor_id = ${DOC3:-0}" "1"
call POST /doctors 400 "{\"userId\":$PATIENT_USER_ID,\"code\":\"PT$SUFFIX\",\"name\":\"Patient as doctor\",\"degree\":\"x\",\"specialtyIds\":[$SPEC]}"   # account is role PATIENT
call POST /doctors 400 "{\"userId\":$DOCTOR_USER_ID,\"code\":\"$(printf 'a%.0s' {1..51})\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC]}"
call GET /doctors/999999 404
call PUT /doctors/999999 404 "{\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC]}"
call PUT "/doctors/$DOC" 400 '{"name":"x","degree":"x","specialtyIds":[]}'
call PUT "/doctors/$DOC" 409 "{\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC2]}"   # SPEC is used by a schedule
call PATCH "/doctors/$DOC/status" 400 '{"status":"BOGUS"}'
call PATCH "/doctors/$DOC/status" 400 '{}'
call PATCH /doctors/999999/status 404 '{"status":"WORKING"}'

echo "== Medical item edge cases =="
IT='"type":"LAB"'
call POST /medical-items 400 "{\"name\":\"neg $SUFFIX\",$IT,\"price\":-1}"
call POST /medical-items 201 "{\"name\":\"zero $SUFFIX\",$IT,\"price\":0}"
call POST /medical-items 400 "{\"name\":\"$(printf 'a%.0s' {1..201})\",$IT,\"price\":1}"
call POST /medical-items 400 "{\"name\":\"bad type $SUFFIX\",\"type\":\"NOPE\",\"price\":1}"
call POST /medical-items 400 "{\"name\":\"no price $SUFFIX\",$IT}"
call POST /medical-items 409 "{\"name\":\"Edge item $SUFFIX\",$IT,\"price\":5}"          # duplicate name
call POST /medical-items 409 "{\"name\":\"EDGE ITEM $SUFFIX\",$IT,\"price\":5}"          # duplicate name, other case
call POST /medical-items 400 "{\"name\":\"huge $SUFFIX\",$IT,\"price\":1234567890123456}"
call POST /medical-items/batch 400 '{"items":[]}'
call POST /medical-items/batch 400 "{\"items\":[{\"name\":\"ok $SUFFIX\",$IT,\"price\":1},{\"name\":\"\",$IT,\"price\":1}]}"
db "batch is all-or-nothing (no partial insert)" "select count(*) from medical_items where name = 'ok $SUFFIX'" "0"
call POST /medical-items/batch 400 "{\"items\":[{\"name\":\"dup in batch $SUFFIX\",$IT,\"price\":1},{\"name\":\"dup in batch $SUFFIX\",$IT,\"price\":1}]}"
db "duplicate-in-batch left no rows" "select count(*) from medical_items where name = 'dup in batch $SUFFIX'" "0"
BIG="$(jq -nc --arg s "$SUFFIX" '{items:[range(101)|{name:("b\(.)-"+$s),type:"LAB",price:1}]}')"
call POST /medical-items/batch 400 "$BIG"
call PUT /medical-items/00000000-0000-0000-0000-000000000000 404 "{\"name\":\"x\",$IT,\"price\":1}"
call PUT "/medical-items/$ITEM" 409 "{\"name\":\"Edge item B $SUFFIX\",$IT,\"price\":1}"   # rename onto existing name
call DELETE /medical-items/00000000-0000-0000-0000-000000000000 404
call POST /medical-items/00000000-0000-0000-0000-000000000000/activate 404
call POST "/medical-items/$ITEM_B/activate" 200                                           # already active: idempotent?
call GET "/medical-items?minPrice=500&maxPrice=100" 400
call GET "/medical-items?type=NOPE" 400
call GET "/medical-items?active=false" 200

echo "== Medical package edge cases =="
call POST /medical-packages 400 "{\"code\":\"BAD1$SUFFIX\",\"name\":\"p\",\"minAge\":151,$PKG_BODY}"
call POST /medical-packages 400 "{\"code\":\"BAD2$SUFFIX\",\"name\":\"p\",\"minAge\":-1,$PKG_BODY}"
call POST /medical-packages 400 "{\"code\":\"BAD3$SUFFIX\",\"name\":\"p\",\"minAge\":60,\"maxAge\":10,$PKG_BODY}"   # min > max
call POST /medical-packages 400 "{\"code\":\"BAD4$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":-5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":1}]}}"
call POST /medical-packages 400 "{\"code\":\"BAD5$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":0,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":1}]}}"
call POST /medical-packages 400 "{\"code\":\"BAD6$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[]}}"
call POST /medical-packages 400 "{\"code\":\"BAD7$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":0,\"position\":1}]}}"
call POST /medical-packages 404 "{\"code\":\"BAD8$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"00000000-0000-0000-0000-000000000000\",\"quantity\":1,\"position\":1}]}}"
db "failed creates left no package rows" "select count(*) from medical_packages where code like 'BAD_$SUFFIX'" "0"
call POST /medical-packages 400 "{\"code\":\"BAD9$SUFFIX\",\"name\":\"p\",\"status\":\"OPENING\",\"price\":5,\"packageClass\":\"STANDARD\",\"consultationMinutes\":20,\"medicalItems\":{\"items\":[{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":1},{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":2}]}}"   # same item twice
call POST "/medical-packages/$PKG/items" 409 "{\"medicalItemPublicId\":\"$ITEM\",\"quantity\":1,\"position\":2}"   # already in package
call POST "/medical-packages/$PKG/items" 404 '{"medicalItemPublicId":"00000000-0000-0000-0000-000000000000","quantity":1,"position":2}'
call POST /medical-packages/00000000-0000-0000-0000-000000000000/items 404 "{\"medicalItemPublicId\":\"$ITEM_B\",\"quantity\":1,\"position\":2}"
call POST "/medical-packages/$PKG/items" 409 "{\"medicalItemPublicId\":\"$ITEM_B\",\"quantity\":1,\"position\":1}"   # position already taken
call DELETE "/medical-items/$ITEM_B" 204
call POST "/medical-packages/$PKG/items" 409 "{\"medicalItemPublicId\":\"$ITEM_B\",\"quantity\":1,\"position\":2}"   # deactivated item into package
call POST "/medical-items/$ITEM_B/activate" 200
call PUT "/medical-packages/$PKG/items/$ITEM_B" 404 '{"quantity":1,"position":2}'     # not in package
call DELETE "/medical-packages/$PKG/items/$ITEM_B" 404
call PUT "/medical-packages/$PKG" 400 '{"price":-1}'
call PUT "/medical-packages/$PKG" 400 '{"minAge":60,"maxAge":10}'
call PUT "/medical-packages/$PKG" 400 '{"name":""}'
call PUT /medical-packages/00000000-0000-0000-0000-000000000000 404 '{"price":1}'
call GET "/medical-packages/$PKG/versions/v99.99" 404
call GET /medical-packages/00000000-0000-0000-0000-000000000000/versions 404
VERS_BEFORE="$(psql_q "select count(*) from package_versions where package_id = (select id from medical_packages where public_id = '$PKG')")"
call PUT "/medical-packages/$PKG" 200 '{}'
db "empty PUT creates no new version" "select count(*) from package_versions where package_id = (select id from medical_packages where public_id = '$PKG')" "$VERS_BEFORE"
call DELETE "/medical-packages/$PKG/items/$ITEM" 409      # removing the last item should be refused
call GET "/medical-packages?minPrice=500&maxPrice=100" 400
call GET "/medical-packages?status=NOPE" 400
db "exactly one current version" "select count(*) from package_versions where package_id = (select id from medical_packages where public_id = '$PKG') and effective_to is null" "1"

echo "== Schedule edge cases =="
call POST /doctor-schedules 409 "{$SCHED_BODY}"                                                          # exact duplicate
S() { # S doctor specialty clinic room shift date general specialist
  echo "{\"doctorId\":$1,\"specialtyId\":$2,\"clinicId\":$3,\"roomId\":$4,\"shiftId\":$5,\"workDate\":\"$6\",\"generalCapacity\":${7:-6},\"specialistCapacity\":${8:-2}}"; }
D="$SCHED_DATE"
call POST /doctor-schedules 409 "$(S $DOC $SPEC $CLINIC_ID $ROOM_B_ID 1 $D)"                              # same doctor, same shift, other room
call POST /doctor-schedules 409 "$(S $DOC2 $SPEC $CLINIC_ID $ROOM_ID 1 $D)"                              # other doctor, same room+shift
call POST /doctor-schedules 201 "$(S $DOC2 $SPEC $CLINIC_ID $ROOM_B_ID 1 $D)"; SCHED2="$(jq_of .data.publicId)"
call POST /doctor-schedules 201 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 2 $D)"; SCHED_AFT="$(jq_of .data.publicId)"   # different shift is fine
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(future 6) 0 2)"
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(future 6) 6 -1)"
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 not-a-date)"
call POST /doctor-schedules 400 '{}'
call POST /doctor-schedules 404 "$(S 999999 $SPEC $CLINIC_ID $ROOM_ID 1 $(future 6))"
call POST /doctor-schedules 404 "$(S $DOC 999999 $CLINIC_ID $ROOM_ID 1 $(future 6))"
call POST /doctor-schedules 404 "$(S $DOC $SPEC 999999 $ROOM_ID 1 $(future 6))"
call POST /doctor-schedules 404 "$(S $DOC $SPEC $CLINIC_ID 999999 1 $(future 6))"
call POST /doctor-schedules 404 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 999 $(future 6))"
call POST /doctor-schedules 409 "$(S $DOC $SPEC $CLINIC_ID $ROOM_OTHER_CLINIC_ID 1 $(future 6))"          # room not in that clinic
call POST /doctor-schedules 409 "$(S $DOC $SPEC $CLINIC_ID $ROOM_CLOSED_ID 1 $(future 6))"                # room closed
call POST /doctor-schedules 409 "$(S $DOC $SPEC2 $CLINIC_ID $ROOM_ID 1 $(future 6))"                      # doctor lacks specialty
call POST /doctor-schedules 409 "$(S $DOC $SPEC_INACTIVE $CLINIC_ID $ROOM_ID 1 $(future 6))"              # inactive specialty
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(future -3))"                      # date in the past
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(date +%F))"                       # today (morning shift may be over)
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(future 4000))"                    # absurdly far future
call POST /doctor-schedules 400 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $(future 6) 100000 100000)"        # capacity beyond what the shift can hold
call PATCH "/doctors/$DOC2/status" 200 '{"status":"NOT_WORKING"}'
call POST /doctor-schedules 409 "$(S $DOC2 $SPEC $CLINIC_ID $ROOM_ID 1 $(future 7))"                       # doctor not working
call PATCH "/doctors/$DOC2/status" 200 '{"status":"WORKING"}'
call GET /doctor-schedules/00000000-0000-0000-0000-000000000000 404
call GET /doctor-schedules/not-a-uuid 400
call PUT "/doctor-schedules/$SCHED" 409 "$(S $DOC $SPEC $CLINIC_ID $ROOM_B_ID 1 $D)"                      # move onto occupied room+shift
call PUT "/doctor-schedules/$SCHED" 200 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $D 4 1)"                    # edit own row: must not conflict with itself
call PUT /doctor-schedules/00000000-0000-0000-0000-000000000000 404 "$(S $DOC $SPEC $CLINIC_ID $ROOM_ID 1 $D)"
call PATCH "/doctor-schedules/$SCHED/capacity" 400 '{"generalCapacity":0,"specialistCapacity":1}'
call PATCH "/doctor-schedules/$SCHED/capacity" 400 '{"generalCapacity":1}'
call PATCH "/doctor-schedules/$SCHED/status" 400 '{"status":"BOGUS"}'
call PATCH "/doctor-schedules/$SCHED/slots/999999999/status" 404 '{"status":"BLOCKED"}'
call GET "/doctor-schedules/$SCHED/slots" 200; SLOT_OF_1="$(jq_of '.data[0].id')"
call PATCH "/doctor-schedules/$SCHED2/slots/$SLOT_OF_1/status" 404 '{"status":"BLOCKED"}'               # slot belongs to another schedule
call PATCH "/doctor-schedules/$SCHED/slots/$SLOT_OF_1/status" 400 '{"status":"BOGUS"}'
call GET "/doctor-schedules?fromDate=$(future 9)&toDate=$(future 1)" 400                                  # from > to
call GET "/doctor-schedules?status=BOGUS" 400
call GET "/doctor-schedules?doctorId=abc" 400
call GET "/doctor-schedules/availability?fromDate=$(future 9)&toDate=$(future 1)" 400
call GET "/doctor-schedules/availability?fromDate=$D&toDate=$D&durationMinutes=25" 400
call GET "/doctor-schedules/availability?fromDate=$D&toDate=$D&durationMinutes=0" 400
call GET "/doctor-schedules/availability?fromDate=$D&toDate=$D&durationMinutes=30" 200
call GET "/doctor-schedules/availability?fromDate=2020-01-01&toDate=2030-12-31" 400                       # unbounded range
call GET "/doctor-schedules/availability?fromDate=bad&toDate=$D" 400
call GET "/doctor-schedules/availability?fromDate=$D&toDate=$D&doctorId=999999" 200
db "no windows for unknown doctor" "select 0" "$(jq_of '.data | length')"
call PATCH "/doctor-schedules/$SCHED/status" 200 '{"status":"CLOSED"}'
call GET "/doctor-schedules/availability?doctorId=$DOC&fromDate=$D&toDate=$D&shiftId=1" 200
db "closed schedule not offered as available" "select 0" "$(jq_of '[.data[] | select(.schedule.publicId == "'"$SCHED"'")] | length')"
call PATCH "/doctor-schedules/$SCHED/capacity" 409 '{"generalCapacity":3,"specialistCapacity":1}'      # closed schedule is frozen
call PATCH "/doctor-schedules/$SCHED/status" 200 '{"status":"OPEN"}'                                    # reopen closed
call PATCH "/doctor-schedules/$SCHED/status" 200 '{"status":"CANCELLED"}'
call PATCH "/doctor-schedules/$SCHED/status" 409 '{"status":"OPEN"}'                                    # cancelled is final

echo "== Role / ownership edge cases =="
login "$DOCTOR_USER"
call GET "/doctor-schedules/my/$SCHED_AFT/patients" 200
call GET "/doctor-schedules/my/$SCHED2/patients" 404                          # another doctor's schedule (403 or 404)
call GET /doctor-schedules/my/00000000-0000-0000-0000-000000000000/patients 404
call GET "/doctor-schedules/my?fromDate=$(future 9)&toDate=$(future 1)" 400
call GET "/doctor-schedules/my?clinicId=$CLINIC2_ID" 200
db "doctor sees none of another clinic" "select 0" "$(jq_of '.data | length')"
call POST /specialties 403 '{"code":"ROLE1","name":"doctor must not create"}'
call PUT "/specialties/$SPEC" 403 '{"name":"doctor must not edit"}'
call DELETE "/specialties/$SPEC2" 403
call POST /doctors 403 "{\"userId\":$PATIENT_USER_ID,\"code\":\"R$SUFFIX\",\"name\":\"x\",\"degree\":\"x\",\"specialtyIds\":[$SPEC]}"
call PATCH "/doctors/$DOC/status" 403 '{"status":"NOT_WORKING"}'
call POST /medical-items 403 "{\"name\":\"role $SUFFIX\",\"type\":\"LAB\",\"price\":1}"
call DELETE "/medical-items/$ITEM" 403
call POST /medical-packages 403 "{\"code\":\"ROLE$SUFFIX\",\"name\":\"p\",$PKG_BODY}"
call PUT "/medical-packages/$PKG" 403 '{"price":1}'
call POST /doctor-schedules 403 "{$SCHED_BODY}"
call PATCH "/doctor-schedules/$SCHED2/status" 403 '{"status":"CLOSED"}'
login "$NODOC_USER"                                                           # DOCTOR account with doctor profile? (DOC3)
call GET /doctor-schedules/my 200
login "$PATIENT_USER"
call GET /doctor-schedules/my 403
call GET /doctor-schedules 403
call GET "/doctor-schedules/availability?fromDate=$D&toDate=$D" 200
login smoke_admin
call GET /doctor-schedules/my 403                                             # admin is not a doctor
call GET "/doctor-schedules/my/$SCHED2/patients" 403

echo
echo "Passed: $PASS   Failed: $FAIL"
[[ "$FAIL" -eq 0 ]]
