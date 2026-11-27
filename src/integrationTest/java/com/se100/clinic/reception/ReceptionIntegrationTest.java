package com.se100.clinic.reception;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * End-to-end integration tests for the patient reception flow (REC-01..REC-04) over real HTTP, JWT,
 * and PostgreSQL, covering all required edge cases including clinic scope, card alerts,
 * multi-occurrence packages (e.g. 3-dose HPV), room routing, and concurrent double check-in.
 */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class ReceptionIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/receptions";
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  @Autowired private JdbcTemplate jdbc;

  private final class Fixture {
    final String suffix = UUID.randomUUID().toString().substring(0, 8);
    final String password = "reception-test-password";
    final long clinicA = insertClinic("CSA_" + suffix, "Cơ sở Quận 1 " + suffix);
    final long clinicB = insertClinic("CSB_" + suffix, "Cơ sở Quận 7 " + suffix);
    final long clinicalRoomA = insertRoom(clinicA, "PK01_" + suffix, "Phòng khám Nội 1");
    final long labRoomA = insertRoom(clinicA, "XN01_" + suffix, "Phòng Xét nghiệm Máu");
    final long imagingRoomA = insertRoom(clinicA, "CDHA01_" + suffix, "Phòng Siêu âm");
    final long vacRoomA = insertRoom(clinicA, "VAC01_" + suffix, "Phòng Tiêm chủng Vắc-xin");
    final long clinicalRoomB = insertRoom(clinicB, "PKB01_" + suffix, "Phòng khám Cơ sở B");

    final long specialtyId =
        jdbc.queryForObject(
            "insert into specialties (code, name) values (?, ?) returning id",
            Long.class,
            "SPR_" + suffix,
            "Nội tổng quát " + suffix);
    final String doctorUsername = "doc_rec_" + suffix;
    final long doctorUserId = ScheduleTestData.user(jdbc, doctorUsername, password, "DOCTOR");
    final long doctorId = insertDoctor(doctorUserId, specialtyId);

    final String staffAUsername = "staff_a_" + suffix;
    final long staffAUserId =
        ScheduleTestData.user(jdbc, staffAUsername, password, "MEDICAL_STAFF");
    final long staffAId = insertStaff(staffAUserId, "Lễ tân Cơ sở A", clinicA);

    final String staffBUsername = "staff_b_" + suffix;
    final long staffBUserId =
        ScheduleTestData.user(jdbc, staffBUsername, password, "MEDICAL_STAFF");
    final long staffBId = insertStaff(staffBUserId, "Lễ tân Cơ sở B", clinicB);

    final String staffAToken = login(staffAUsername);
    final String staffBToken = login(staffBUsername);
    final String doctorToken = login(doctorUsername);

    final LocalDate today = LocalDate.now(ZONE);
    final Instant now = Instant.now();

    long insertClinic(String code, String name) {
      return jdbc.queryForObject(
          """
          insert into clinics (public_id, code, name, address, phone_number, status)
          values (?, ?, ?, '123 Nguyễn Huệ', '0901000000', 'OPENING') returning id
          """,
          Long.class,
          UUID.randomUUID(),
          code,
          name);
    }

    long insertRoom(long clinicId, String code, String name) {
      return jdbc.queryForObject(
          """
          insert into rooms (public_id, clinic_id, code, name, status)
          values (?, ?, ?, ?, 'OPENING') returning id
          """,
          Long.class,
          UUID.randomUUID(),
          clinicId,
          code,
          name);
    }

    long insertDoctor(long userId, long specId) {
      long id =
          jdbc.queryForObject(
              """
              insert into doctors (public_id, user_id, code, name, degree, status)
              values (?, ?, ?, 'BS. Nguyễn Minh', 'BS.CKI', 'WORKING') returning id
              """,
              Long.class,
              UUID.randomUUID(),
              userId,
              "BSR_" + UUID.randomUUID().toString().substring(0, 16));
      jdbc.update(
          "insert into doctor_specialities (doctor_id, specialty_id) values (?, ?)", id, specId);
      return id;
    }

    long insertStaff(long userId, String name, long clinicId) {
      long id =
          jdbc.queryForObject(
              """
              insert into staffs (public_id, user_id, name, status)
              values (?, ?, ?, 'WORKING') returning id
              """,
              Long.class,
              UUID.randomUUID(),
              userId,
              name);
      jdbc.update("insert into clinic_staffs (clinic_id, staff_id) values (?, ?)", clinicId, id);
      return id;
    }

    long insertPatient(
        String fullName, String phone, String cccd, Instant cardExpiresAt, Instant cardRevokedAt) {
      return jdbc.queryForObject(
          """
          insert into patients (public_id, full_name, date_of_birth, gender, identity_type,
            identity_number, identity_issuer, phone, address, patient_class,
            card_number, card_activated_at, card_expires_at, card_revoked_at)
          values (?, ?, '1995-04-12', 'FEMALE', 'CCCD', ?, 'VN', ?, 'TP.HCM', 'PERSONAL',
            ?, ?, ?, ?) returning id
          """,
          Long.class,
          UUID.randomUUID(),
          fullName,
          cccd,
          phone,
          "BN2026-"
              + UUID.randomUUID()
                  .toString()
                  .replace("-", "")
                  .substring(0, 8)
                  .toUpperCase(Locale.ROOT),
          Timestamp.from(now.minus(30, ChronoUnit.DAYS)),
          Timestamp.from(cardExpiresAt),
          cardRevokedAt == null ? null : Timestamp.from(cardRevokedAt));
    }

    long insertSchedule(long roomId, LocalDate workDate, Instant startAt, Instant endAt) {
      return jdbc.queryForObject(
          """
          insert into doctor_schedules (public_id, doctor_id, specialty_id, room_id, shift_id,
            work_date, start_at, end_at, status, general_capacity, specialist_capacity)
          values (?, ?, ?, ?, (select id from shifts where code = 'MORNING'), ?, ?, ?, 'OPEN', 12, 8)
          returning id
          """,
          Long.class,
          UUID.randomUUID(),
          doctorId,
          specialtyId,
          roomId,
          workDate,
          Timestamp.from(startAt),
          Timestamp.from(endAt));
    }

    record AppointmentSeed(long id, String publicId, String code) {}

    AppointmentSeed insertAppointment(
        long patientId, long scheduleId, Long packageVersionId, Instant startAt, String status) {
      String code = "AP-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
      UUID publicId = UUID.randomUUID();
      long id =
          jdbc.queryForObject(
              """
              insert into appointments (public_id, code, qr_token_hash, patient_id, schedule_id,
                package_version_id, start_at, end_at, status, price_snapshot, deposit_required,
                cancellation_policy_snapshot)
              values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1500000, 300000, '{}') returning id
              """,
              Long.class,
              publicId,
              code,
              sha256Hex(code),
              patientId,
              scheduleId,
              packageVersionId,
              Timestamp.from(startAt),
              Timestamp.from(startAt.plusSeconds(1200)),
              status);
      return new AppointmentSeed(id, publicId.toString(), code);
    }

    long createMultiOccurrencePackage() {
      long pkgId =
          jdbc.queryForObject(
              """
              insert into medical_packages (public_id, code, name)
              values (?, ?, ?) returning id
              """,
              Long.class,
              UUID.randomUUID(),
              "PKG_" + suffix,
              "Gói Tầm soát & HPV 3 mũi " + suffix);
      long versionId =
          jdbc.queryForObject(
              """
              insert into package_versions (package_id, version, code, name, class, status,
                consultation_minutes, unit_price, effective_from, target)
              values (?, 'v1.0', ?, ?, 'STANDARD', 'OPENING', 20, 8800000, now(), 'BOTH')
              returning id
              """,
              Long.class,
              pkgId,
              "PV_" + suffix,
              "Gói Tầm soát & HPV 3 mũi");

      long clinicalItem = insertItem("Khám tổng quát " + suffix, "CLINICAL", 200000);
      long labItem = insertItem("Xét nghiệm máu " + suffix, "LAB", 150000);
      long hpvItem = insertItem("Tiêm vắc-xin HPV 9 chủng " + suffix, "VACCINATION", 2900000);

      insertPackageItem(versionId, clinicalItem, "Khám tổng quát", 1, 1);
      insertPackageItem(versionId, labItem, "Xét nghiệm máu", 1, 2);
      insertPackageItem(versionId, hpvItem, "Tiêm vắc-xin HPV 9 chủng", 3, 3);
      return versionId;
    }

    long insertItem(String name, String type, long price) {
      return jdbc.queryForObject(
          """
          insert into medical_items (public_id, name, price, type, active)
          values (?, ?, ?, ?, true) returning id
          """,
          Long.class,
          UUID.randomUUID(),
          name,
          price,
          type);
    }

    void insertPackageItem(long versionId, long itemId, String name, int quantity, int position) {
      jdbc.update(
          """
          insert into package_version_items (package_version_id, item_id, item_name_snapshot,
            quantity, position)
          values (?, ?, ?, ?, ?)
          """,
          versionId,
          itemId,
          name,
          quantity,
          position);
    }

    String roomPublicId(long roomId) {
      return jdbc.queryForObject(
          "select public_id::text from rooms where id = ?", String.class, roomId);
    }
  }

  private String login(String username) {
    var response =
        call(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("username", username, "password", "reception-test-password"));
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    return (String) data(response).get("accessToken");
  }

  private ResponseEntity<Map<String, Object>> call(
      HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return restTemplate.exchange(
        path, method, new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() {});
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
    return (Map<String, Object>) response.getBody().get("data");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object obj) {
    return (List<Map<String, Object>>) obj;
  }

  private static String sha256Hex(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void fullReceptionFlow_lookupMultiOccurrenceRoutingAndDoctorQueueIntegration() {
    var f = new Fixture();
    long patientId =
        f.insertPatient(
            "Trần Thị Hoa",
            "0908" + f.suffix.replaceAll("[^0-9]", "1").substring(0, 6),
            "079095" + String.format("%06d", Math.abs(f.suffix.hashCode()) % 1_000_000),
            f.now.plus(10, ChronoUnit.DAYS), // EXPIRING_SOON -> warn, still allow check-in
            null);
    jdbc.update(
        """
        insert into patient_medical_histories (patient_id, type, description, recorded_at, updated_at)
        values (?, 'ALLERGY', 'Dị ứng hải sản', now(), now())
        """,
        patientId);

    long pkgVersionId = f.createMultiOccurrencePackage();
    long scheduleToday =
        f.insertSchedule(
            f.clinicalRoomA,
            f.today,
            f.now.minus(1, ChronoUnit.HOURS),
            f.now.plus(3, ChronoUnit.HOURS));
    // Appointment 1: 15 minutes ago (LATE, receivable because shift hasn't ended)
    var apptLate =
        f.insertAppointment(
            patientId,
            scheduleToday,
            pkgVersionId,
            f.now.minus(15, ChronoUnit.MINUTES),
            "CONFIRMED");
    // Appointment 2: 2 hours later (EARLY, receivable)
    var apptLater =
        f.insertAppointment(
            patientId, scheduleToday, null, f.now.plus(2, ChronoUnit.HOURS), "CONFIRMED");
    // Appointment 3: Cancelled (not receivable)
    f.insertAppointment(
        patientId, scheduleToday, null, f.now.plus(30, ChronoUnit.MINUTES), "CANCELLED");

    // 1. Lookup by QR -> profile, allergy history, expiring-soon warning, appointments ordered
    // nearest-first
    var lookupRes =
        call(HttpMethod.GET, BASE + "/lookup?qr=" + apptLate.code(), f.staffAToken, null);
    assertThat(lookupRes.getStatusCode().value()).isEqualTo(200);
    List<Map<String, Object>> patients = maps(data(lookupRes).get("patients"));
    assertThat(patients).hasSize(1);
    Map<String, Object> card = patients.getFirst();
    assertThat(card).containsEntry("cardAlert", "EXPIRING_SOON");
    @SuppressWarnings("unchecked")
    Map<String, Object> history = (Map<String, Object>) card.get("medicalHistory");
    assertThat(maps(history.get("allergies"))).hasSize(1);

    List<Map<String, Object>> appointments = maps(card.get("appointments"));
    assertThat(appointments).hasSize(3);
    assertThat(appointments.get(0))
        .containsEntry("appointmentPublicId", apptLate.publicId())
        .containsEntry("punctuality", "LATE")
        .containsEntry("receivable", true);
    assertThat(appointments.get(1))
        .containsEntry("appointmentPublicId", apptLater.publicId())
        .containsEntry("punctuality", "EARLY")
        .containsEntry("receivable", true);
    assertThat(appointments.get(2))
        .containsEntry("status", "CANCELLED")
        .containsEntry("receivable", false);

    // 2. Check-in apptLate -> creates 1 visit + 5 orders (1 clinical, 1 lab, 3 HPV doses)
    var checkInRes =
        call(
            HttpMethod.POST,
            BASE,
            f.staffAToken,
            Map.of("appointmentPublicId", apptLate.publicId()));
    assertThat(checkInRes.getStatusCode().value()).isEqualTo(201);
    assertThat(checkInRes.getHeaders().getLocation()).isNotNull();

    Map<String, Object> ticket = data(checkInRes);
    String visitPublicId = (String) ticket.get("visitPublicId");
    assertThat(ticket)
        .containsEntry("status", "WAITING")
        .containsEntry("appointmentStatus", "CHECKED_IN")
        .containsEntry("cardAlert", "EXPIRING_SOON");

    List<Map<String, Object>> orders = maps(ticket.get("orders"));
    assertThat(orders).hasSize(5);
    List<Map<String, Object>> hpvOrders =
        orders.stream().filter(o -> "VACCINATION".equals(o.get("itemType"))).toList();
    assertThat(hpvOrders).hasSize(3);
    assertThat(hpvOrders.get(0))
        .containsEntry("occurrenceNo", 1)
        .containsEntry("totalOccurrences", 3)
        .containsEntry("currentVisitRouted", true)
        .containsEntry("status", "WAITING");
    assertThat(hpvOrders.get(0).get("roomPublicId")).isEqualTo(f.roomPublicId(f.vacRoomA));
    assertThat(hpvOrders.get(0).get("queueNumber")).isEqualTo(1);
    assertThat(hpvOrders.get(1))
        .containsEntry("occurrenceNo", 2)
        .containsEntry("currentVisitRouted", false)
        .containsEntry("status", "ORDERED")
        .containsEntry("roomPublicId", null);
    assertThat(hpvOrders.get(2))
        .containsEntry("occurrenceNo", 3)
        .containsEntry("currentVisitRouted", false)
        .containsEntry("status", "ORDERED")
        .containsEntry("roomPublicId", null);

    // 3. Re-route lab order to imaging room via PUT /routing
    String labOrderId =
        (String)
            orders.stream()
                .filter(o -> "LAB".equals(o.get("itemType")))
                .findFirst()
                .orElseThrow()
                .get("orderPublicId");
    var rerouted =
        call(
            HttpMethod.PUT,
            BASE + "/" + visitPublicId + "/orders/" + labOrderId + "/routing",
            f.staffAToken,
            Map.of("roomPublicId", f.roomPublicId(f.imagingRoomA)));
    assertThat(rerouted.getStatusCode().value()).isEqualTo(200);
    assertThat(data(rerouted))
        .containsEntry("roomPublicId", f.roomPublicId(f.imagingRoomA))
        .containsEntry("status", "WAITING")
        .containsEntry("queueNumber", 1);

    // 4. Doctor's examination queue (REC-05) immediately lists the newly checked-in visit
    var doctorQueue = call(HttpMethod.GET, "/api/v1/medical-visits/my-queue", f.doctorToken, null);
    assertThat(doctorQueue.getStatusCode().value()).isEqualTo(200);
    assertThat(maps(doctorQueue.getBody().get("data")))
        .extracting(item -> item.get("visitPublicId"))
        .contains(visitPublicId);
  }

  @Test
  void edgeCases_invalidLookupClinicScopeRevokedCardAndConcurrentDoubleCheckIn() throws Exception {
    var f = new Fixture();

    // 1. Invalid QR & unknown phone -> 404
    assertThat(
            call(
                    HttpMethod.GET,
                    BASE + "/lookup?qr=AP-00000000000000000000000000000000",
                    f.staffAToken,
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(404);
    assertThat(
            call(HttpMethod.GET, BASE + "/lookup?phone=0999888777", f.staffAToken, null)
                .getStatusCode()
                .value())
        .isEqualTo(404);

    // 2. Role guard: unauthenticated -> 401, DOCTOR -> 403
    assertThat(call(HttpMethod.GET, BASE + "/clinics", null, null).getStatusCode().value())
        .isEqualTo(401);
    assertThat(call(HttpMethod.GET, BASE + "/clinics", f.doctorToken, null).getStatusCode().value())
        .isEqualTo(403);

    // 3. Staff at Clinic A blocked from Clinic B appointment (lookup QR + check-in -> 403)
    long patientB =
        f.insertPatient(
            "Lê Văn B",
            "0912" + String.format("%06d", Math.abs((f.suffix + "b").hashCode()) % 1_000_000),
            "079091" + String.format("%06d", Math.abs((f.suffix + "b").hashCode()) % 1_000_000),
            f.now.plus(90, ChronoUnit.DAYS),
            null);
    long scheduleB =
        f.insertSchedule(
            f.clinicalRoomB,
            f.today,
            f.now.minus(30, ChronoUnit.MINUTES),
            f.now.plus(3, ChronoUnit.HOURS));
    var apptB =
        f.insertAppointment(
            patientB, scheduleB, null, f.now.plus(10, ChronoUnit.MINUTES), "CONFIRMED");

    var crossLookup =
        call(HttpMethod.GET, BASE + "/lookup?qr=" + apptB.code(), f.staffAToken, null);
    assertThat(crossLookup.getStatusCode().value()).isEqualTo(403);
    assertThat(crossLookup.getBody()).containsEntry("code", "CLINIC_ACCESS_DENIED");

    var crossCheckIn =
        call(HttpMethod.POST, BASE, f.staffAToken, Map.of("appointmentPublicId", apptB.publicId()));
    assertThat(crossCheckIn.getStatusCode().value()).isEqualTo(403);
    assertThat(crossCheckIn.getBody()).containsEntry("code", "CLINIC_ACCESS_DENIED");

    // 4. Revoked card blocks check-in with 409 PATIENT_CARD_REVOKED
    long revokedPatient =
        f.insertPatient(
            "Phạm Thu C",
            "0913" + String.format("%06d", Math.abs((f.suffix + "c").hashCode()) % 1_000_000),
            "079092" + String.format("%06d", Math.abs((f.suffix + "c").hashCode()) % 1_000_000),
            f.now.plus(90, ChronoUnit.DAYS),
            f.now.minus(1, ChronoUnit.DAYS));
    long scheduleA =
        f.insertSchedule(
            f.clinicalRoomA,
            f.today,
            f.now.minus(30, ChronoUnit.MINUTES),
            f.now.plus(3, ChronoUnit.HOURS));
    var apptRevoked =
        f.insertAppointment(
            revokedPatient, scheduleA, null, f.now.plus(15, ChronoUnit.MINUTES), "CONFIRMED");
    var revokedRes =
        call(
            HttpMethod.POST,
            BASE,
            f.staffAToken,
            Map.of("appointmentPublicId", apptRevoked.publicId()));
    assertThat(revokedRes.getStatusCode().value()).isEqualTo(409);
    assertThat(revokedRes.getBody()).containsEntry("code", "PATIENT_CARD_REVOKED");

    // 5. Concurrent check-in by 2 requests at once -> exactly one 201 and one 409, only 1 visit row
    long validPatient =
        f.insertPatient(
            "Võ Minh D",
            "0914" + String.format("%06d", Math.abs((f.suffix + "d").hashCode()) % 1_000_000),
            "079093" + String.format("%06d", Math.abs((f.suffix + "d").hashCode()) % 1_000_000),
            f.now.minus(5, ChronoUnit.DAYS), // EXPIRED -> warns, still allowed
            null);
    var apptConcurrent =
        f.insertAppointment(
            validPatient, scheduleA, null, f.now.plus(20, ChronoUnit.MINUTES), "CONFIRMED");

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      Future<ResponseEntity<Map<String, Object>>> r1 =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return call(
                    HttpMethod.POST,
                    BASE,
                    f.staffAToken,
                    Map.of("appointmentPublicId", apptConcurrent.publicId()));
              });
      Future<ResponseEntity<Map<String, Object>>> r2 =
          pool.submit(
              () -> {
                ready.countDown();
                go.await();
                return call(
                    HttpMethod.POST,
                    BASE,
                    f.staffAToken,
                    Map.of("appointmentPublicId", apptConcurrent.publicId()));
              });
      ready.await();
      go.countDown();

      List<Integer> statuses =
          List.of(r1.get().getStatusCode().value(), r2.get().getStatusCode().value());
      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }

    Integer visitCount =
        jdbc.queryForObject(
            "select count(*) from medical_visits where appointment_id = ?",
            Integer.class,
            apptConcurrent.id());
    assertThat(visitCount).isEqualTo(1);
  }
}
