package com.se100.clinic.examination;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * The doctor's examination flow over real HTTP, JWT and PostgreSQL: the received-patient list,
 * ownership, the clinical record with optimistic locking, and the patient's history.
 */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class ExaminationIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/medical-visits";

  @Autowired private JdbcTemplate jdbc;

  /** One doctor with a morning shift, a receptionist, and the two doctors' tokens. */
  private final class Fixture {
    final ScheduleTestData data = ScheduleTestData.create(jdbc);
    final long staffId = staff();
    final long scheduleId =
        data.schedule(
            data.doctorId(),
            data.roomId(),
            "MORNING",
            data.start(),
            data.start().plusSeconds(14400),
            "OPEN");
    final String doctorToken = login(data.doctorUsername());
    final String otherDoctorToken =
        login(
            jdbc.queryForObject(
                "select u.username from users u join doctors d on d.user_id = u.id where d.id = ?",
                String.class,
                data.secondDoctorId()));
    int appointments;

    private long staff() {
      String username = "staff_" + UUID.randomUUID().toString().substring(0, 8);
      long user = ScheduleTestData.user(jdbc, username, data.password(), "MEDICAL_STAFF");
      return jdbc.queryForObject(
          "insert into staffs (public_id, user_id, name, status) values (?, ?, 'Lễ tân', 'WORKING') returning id",
          Long.class,
          UUID.randomUUID(),
          user);
    }

    String schedulePublicId() {
      return jdbc.queryForObject(
          "select public_id::text from doctor_schedules where id = ?", String.class, scheduleId);
    }

    /** A new appointment in the doctor's shift for the fixture patient, 20 minutes apart. */
    long appointment(String status) {
      return appointmentOf(scheduleId, status);
    }

    long appointmentOf(long schedule, String status) {
      return data.appointment(
          schedule, data.start().plusSeconds(1200L * appointments++), 20, status);
    }

    /** Creates the visit the receptionist would create; returns its public id. */
    String visit(long appointmentId, Instant receivedAt, String status, Long primaryDoctorId) {
      return visitOf(data.patientId(), appointmentId, receivedAt, status, primaryDoctorId);
    }

    String visitOf(
        long patientId,
        long appointmentId,
        Instant receivedAt,
        String status,
        Long primaryDoctorId) {
      return jdbc.queryForObject(
          """
          insert into medical_visits (public_id, code, patient_id, appointment_id,
            received_by_staff_id, received_at, status, primary_doctor_id)
          values (?, ?, ?, ?, ?, ?, ?, ?) returning public_id::text
          """,
          String.class,
          UUID.randomUUID(),
          "PTN_" + UUID.randomUUID().toString().substring(0, 20),
          patientId,
          appointmentId,
          staffId,
          Timestamp.from(receivedAt),
          status,
          primaryDoctorId);
    }

    long visitId(String publicId) {
      return jdbc.queryForObject(
          "select id from medical_visits where public_id = ?::uuid", Long.class, publicId);
    }
  }

  private String login(String username) {
    var response =
        call(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("username", username, "password", "schedule-test-password"));
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
  private static List<Map<String, Object>> list(ResponseEntity<Map<String, Object>> response) {
    return (List<Map<String, Object>>) response.getBody().get("data");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object json) {
    return (Map<String, Object>) json;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object json) {
    return (List<Map<String, Object>>) json;
  }

  private static int status(ResponseEntity<?> response) {
    return response.getStatusCode().value();
  }

  private static Map<String, Object> record(Object version, Object... overrides) {
    Map<String, Object> body = new HashMap<>();
    body.put("version", version);
    body.put("symptoms", "Ho, sốt 2 ngày");
    body.put("initialDiagnosis", "Viêm họng cấp");
    body.put("advice", "Uống nhiều nước, nghỉ ngơi");
    body.put("temperatureC", 38.5);
    body.put("heartRate", 90);
    body.put("systolicBp", 120);
    body.put("diastolicBp", 80);
    for (int i = 0; i < overrides.length; i += 2) {
      body.put((String) overrides[i], overrides[i + 1]);
    }
    return body;
  }

  // ---- danh sách bệnh nhân (REC-05, EXAM-01) ----------------------------------------------

  @Test
  void queue_shouldListOnlyReceivedPatientsInReceptionOrder() {
    var f = new Fixture();
    Instant t = Instant.now().minus(2, ChronoUnit.HOURS);
    // Created out of order on purpose: the list must follow received_at, then id.
    String second = f.visit(f.appointment("CHECKED_IN"), t.plusSeconds(300), "WAITING", null);
    String first = f.visit(f.appointment("CHECKED_IN"), t, "WAITING", null);
    String tie =
        f.visit(f.appointment("CHECKED_IN"), t.plusSeconds(300), "IN_PROGRESS", doctorOf(f));
    f.appointment("CONFIRMED"); // booked but not received: no visit, must not appear
    f.visit(f.appointment("CANCELLED"), t, "WAITING", null); // appointment cancelled
    f.visit(f.appointment("CHECKED_IN"), t, "CANCELLED", null); // visit cancelled

    String url = BASE + "/my-queue?schedulePublicId=" + f.schedulePublicId();
    var response = call(HttpMethod.GET, url, f.doctorToken, null);

    assertThat(status(response)).isEqualTo(200);
    assertThat(list(response))
        .extracting(item -> item.get("visitPublicId"))
        .containsExactly(first, second, tie);
    assertThat(list(response)).extracting(item -> item.get("queueNumber")).containsExactly(1, 2, 3);
    // Same list when asked again: the order must not jump between reloads.
    assertThat(list(call(HttpMethod.GET, url, f.doctorToken, null)))
        .extracting(item -> item.get("visitPublicId"))
        .containsExactly(first, second, tie);
  }

  @Test
  void queue_shouldKeepQueueNumbers_whenFilteredByStatus() {
    var f = new Fixture();
    Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
    f.visit(f.appointment("CHECKED_IN"), t, "WAITING", null);
    String inProgress =
        f.visit(f.appointment("CHECKED_IN"), t.plusSeconds(60), "IN_PROGRESS", doctorOf(f));

    var response =
        call(
            HttpMethod.GET,
            BASE + "/my-queue?schedulePublicId=" + f.schedulePublicId() + "&status=IN_PROGRESS",
            f.doctorToken,
            null);

    assertThat(list(response)).hasSize(1);
    assertThat(list(response).getFirst()).containsEntry("visitPublicId", inProgress);
    assertThat(list(response).getFirst()).containsEntry("queueNumber", 2);
  }

  @Test
  void queue_shouldBeEmpty_whenNobodyIsReceivedYet() {
    var f = new Fixture();
    f.appointment("CONFIRMED");

    var forShift =
        call(
            HttpMethod.GET,
            BASE + "/my-queue?schedulePublicId=" + f.schedulePublicId(),
            f.doctorToken,
            null);
    var today = call(HttpMethod.GET, BASE + "/my-queue", f.doctorToken, null);

    assertThat(status(forShift)).isEqualTo(200);
    assertThat(list(forShift)).isEmpty();
    assertThat(list(today)).as("the fixture shift is next week, not today").isEmpty();
  }

  @Test
  void queue_shouldFilterByDate_andNeverShowAnotherDoctorsPatients() {
    var f = new Fixture();
    Instant t = Instant.now().minus(1, ChronoUnit.HOURS);
    String mine = f.visit(f.appointment("CHECKED_IN"), t, "WAITING", null);
    long otherSchedule =
        f.data.schedule(
            f.data.secondDoctorId(),
            f.data.secondRoomId(),
            "MORNING",
            f.data.start(),
            f.data.start().plusSeconds(14400),
            "OPEN");
    String theirs = f.visit(f.appointmentOf(otherSchedule, "CHECKED_IN"), t, "WAITING", null);

    var response =
        call(HttpMethod.GET, BASE + "/my-queue?date=" + f.data.workDate(), f.doctorToken, null);
    var other =
        call(
            HttpMethod.GET, BASE + "/my-queue?date=" + f.data.workDate(), f.otherDoctorToken, null);

    assertThat(list(response)).extracting(i -> i.get("visitPublicId")).containsExactly(mine);
    assertThat(list(other)).extracting(i -> i.get("visitPublicId")).containsExactly(theirs);
    assertThat(list(call(HttpMethod.GET, BASE + "/my-queue?date=2000-01-01", f.doctorToken, null)))
        .isEmpty();
  }

  // ---- mở hồ sơ + phân quyền theo bác sĩ ---------------------------------------------------

  @Test
  void open_shouldReturnPatientAppointmentScheduleAndOrders() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);
    insertOrder(f.visitId(visit), "Xét nghiệm máu", null);

    var response = call(HttpMethod.GET, BASE + "/" + visit, f.doctorToken, null);

    assertThat(status(response)).isEqualTo(200);
    Map<String, Object> detail = data(response);
    assertThat(detail).containsEntry("status", "WAITING").containsEntry("version", 0);
    assertThat(map(detail.get("patient"))).containsEntry("fullName", "Test patient");
    assertThat(map(detail.get("schedule"))).containsEntry("roomName", "Test room");
    assertThat((List<?>) detail.get("orders")).hasSize(1);
  }

  @Test
  void visitOfAnotherDoctor_shouldBeForbiddenToOpenStartSaveAndReadHistory() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);

    var open = call(HttpMethod.GET, BASE + "/" + visit, f.otherDoctorToken, null);
    var start = call(HttpMethod.POST, BASE + "/" + visit + "/start", f.otherDoctorToken, null);
    var save =
        call(HttpMethod.PUT, BASE + "/" + visit + "/examination", f.otherDoctorToken, record(0));
    var history =
        call(HttpMethod.GET, BASE + "/" + visit + "/patient-history", f.otherDoctorToken, null);

    for (var response : List.of(open, start, save, history)) {
      assertThat(status(response)).isEqualTo(403);
      assertThat(response.getBody()).containsEntry("code", "VISIT_NOT_ASSIGNED");
    }
  }

  @Test
  void primaryDoctor_shouldWin_overTheDoctorOfTheShift() {
    var f = new Fixture();
    // The receptionist handed this visit to the second doctor.
    String visit =
        f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", f.data.secondDoctorId());

    assertThat(status(call(HttpMethod.GET, BASE + "/" + visit, f.doctorToken, null)))
        .isEqualTo(403);
    assertThat(status(call(HttpMethod.GET, BASE + "/" + visit, f.otherDoctorToken, null)))
        .isEqualTo(200);
  }

  @Test
  void open_shouldReturn404_forUnknownVisit() {
    var f = new Fixture();

    var response = call(HttpMethod.GET, BASE + "/" + UUID.randomUUID(), f.doctorToken, null);

    assertThat(status(response)).isEqualTo(404);
    assertThat(response.getBody()).containsEntry("code", "VISIT_NOT_FOUND");
  }

  // ---- ghi khám + khóa lạc quan (EXAM-03) --------------------------------------------------

  @Test
  void examination_shouldStartVisitAndSaveClinicalRecord() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);

    var saved = call(HttpMethod.PUT, BASE + "/" + visit + "/examination", f.doctorToken, record(0));

    assertThat(status(saved)).isEqualTo(200);
    Map<String, Object> detail = data(saved);
    assertThat(detail).containsEntry("status", "IN_PROGRESS");
    assertThat(map(detail.get("clinical")))
        .containsEntry("symptoms", "Ho, sốt 2 ngày")
        .containsEntry("initialDiagnosis", "Viêm họng cấp")
        .containsEntry("advice", "Uống nhiều nước, nghỉ ngơi");
    assertThat(
            jdbc.queryForObject(
                "select primary_doctor_id from medical_visits where public_id = ?::uuid",
                Long.class,
                visit))
        .isEqualTo(f.data.doctorId());
    // Read back: the data really is in the database.
    assertThat(
            map(
                data(call(HttpMethod.GET, BASE + "/" + visit, f.doctorToken, null))
                    .get("clinical")))
        .containsEntry("symptoms", "Ho, sốt 2 ngày");
  }

  @Test
  void start_shouldBeIdempotent() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);

    var first = call(HttpMethod.POST, BASE + "/" + visit + "/start", f.doctorToken, null);
    var again = call(HttpMethod.POST, BASE + "/" + visit + "/start", f.doctorToken, null);

    assertThat(status(first)).isEqualTo(200);
    assertThat(data(first)).containsEntry("status", "IN_PROGRESS");
    assertThat(status(again)).isEqualTo(200);
    assertThat(data(again)).containsEntry("status", "IN_PROGRESS");
  }

  @Test
  void examination_shouldNotLoseData_whenSavedFromTwoTabs() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "IN_PROGRESS", doctorOf(f));
    Object version =
        data(call(HttpMethod.GET, BASE + "/" + visit, f.doctorToken, null)).get("version");

    // Both tabs loaded the same version; tab A saves first.
    var tabA =
        call(
            HttpMethod.PUT,
            BASE + "/" + visit + "/examination",
            f.doctorToken,
            record(version, "symptoms", "Từ tab A"));
    var tabB =
        call(
            HttpMethod.PUT,
            BASE + "/" + visit + "/examination",
            f.doctorToken,
            record(version, "symptoms", "Từ tab B"));

    assertThat(status(tabA)).isEqualTo(200);
    assertThat(status(tabB)).isEqualTo(409);
    assertThat(tabB.getBody()).containsEntry("code", "VISIT_VERSION_CONFLICT");
    var current = data(call(HttpMethod.GET, BASE + "/" + visit, f.doctorToken, null));
    assertThat(map(current.get("clinical"))).containsEntry("symptoms", "Từ tab A");
    // Tab B reloads, then its save is accepted.
    var retry =
        call(
            HttpMethod.PUT,
            BASE + "/" + visit + "/examination",
            f.doctorToken,
            record(current.get("version"), "symptoms", "Từ tab B sau khi tải lại"));
    assertThat(status(retry)).isEqualTo(200);
  }

  @Test
  void examination_shouldRejectClosedVisit_andLeaveItUnchanged() {
    var f = new Fixture();
    String done = f.visit(f.appointment("COMPLETED"), Instant.now(), "COMPLETED", doctorOf(f));
    String cancelled =
        f.visit(f.appointment("CHECKED_IN"), Instant.now(), "CANCELLED", doctorOf(f));

    for (String visit : List.of(done, cancelled)) {
      var save =
          call(HttpMethod.PUT, BASE + "/" + visit + "/examination", f.doctorToken, record(0));
      var start = call(HttpMethod.POST, BASE + "/" + visit + "/start", f.doctorToken, null);

      assertThat(status(save)).isEqualTo(409);
      assertThat(save.getBody()).containsEntry("code", "VISIT_CLOSED");
      assertThat(status(start)).isEqualTo(409);
      assertThat(
              jdbc.queryForObject(
                  "select symptoms from medical_visits where public_id = ?::uuid",
                  String.class,
                  visit))
          .isNull();
    }
  }

  @Test
  void examination_shouldRejectInvalidInput() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);
    String url = BASE + "/" + visit + "/examination";

    var noVersion = record(0);
    noVersion.remove("version");
    var hot = call(HttpMethod.PUT, url, f.doctorToken, record(0, "temperatureC", 50));
    var missing = call(HttpMethod.PUT, url, f.doctorToken, noVersion);
    var pressure =
        call(HttpMethod.PUT, url, f.doctorToken, record(0, "systolicBp", 80, "diastolicBp", 90));

    assertThat(status(hot)).isEqualTo(400);
    assertThat(status(missing)).isEqualTo(400);
    assertThat(status(pressure)).isEqualTo(400);
    assertThat(pressure.getBody()).containsEntry("code", "INVALID_VITAL_SIGNS");
  }

  // ---- tiền sử, lịch sử khám, kết quả cũ (EXAM-02) -----------------------------------------

  @Test
  void history_shouldReturnMedicalHistoryOlderVisitsAndOnlyVerifiedResults() {
    var f = new Fixture();
    jdbc.update(
        """
        insert into patient_medical_histories (patient_id, type, description, recorded_at, updated_at)
        values (?, 'ALLERGY', 'Dị ứng penicillin', now(), now())
        """,
        f.data.patientId());
    String old =
        f.visit(
            f.appointment("COMPLETED"),
            Instant.now().minus(30, ChronoUnit.DAYS),
            "COMPLETED",
            doctorOf(f));
    long oldId = f.visitId(old);
    insertOrder(oldId, "Công thức máu", "VERIFIED");
    insertOrder(oldId, "Siêu âm bụng", "DRAFT");
    f.visit(
        f.appointment("CANCELLED"), Instant.now().minus(20, ChronoUnit.DAYS), "CANCELLED", null);
    String current = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);

    var response =
        call(HttpMethod.GET, BASE + "/" + current + "/patient-history", f.doctorToken, null);

    assertThat(status(response)).isEqualTo(200);
    Map<String, Object> history = data(response);
    assertThat((List<?>) ((Map<?, ?>) history.get("medicalHistory")).get("allergies")).hasSize(1);
    List<Map<String, Object>> previous = maps(history.get("previousVisits"));
    assertThat(previous).as("older visit only: not the current, not the cancelled").hasSize(1);
    assertThat(previous.getFirst()).containsEntry("publicId", old);
    List<Map<String, Object>> results = maps(previous.getFirst().get("results"));
    assertThat(results).extracting(r -> r.get("itemName")).containsExactly("Công thức máu");
  }

  @Test
  void history_shouldBeEmpty_forPatientWithNothingRecorded() {
    var f = new Fixture();
    String current = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);

    var response =
        call(HttpMethod.GET, BASE + "/" + current + "/patient-history", f.doctorToken, null);

    assertThat(status(response)).isEqualTo(200);
    Map<String, Object> history = data(response);
    Map<?, ?> medical = (Map<?, ?>) history.get("medicalHistory");
    assertThat((List<?>) medical.get("personal")).isEmpty();
    assertThat((List<?>) medical.get("family")).isEmpty();
    assertThat((List<?>) medical.get("allergies")).isEmpty();
    assertThat((List<?>) history.get("previousVisits")).isEmpty();
  }

  // ---- đăng nhập và vai trò ---------------------------------------------------------------

  @Test
  void endpoints_shouldRequireLoginAndDoctorRole() {
    var f = new Fixture();
    String visit = f.visit(f.appointment("CHECKED_IN"), Instant.now(), "WAITING", null);
    String adminName = "admin_" + UUID.randomUUID().toString().substring(0, 8);
    ScheduleTestData.user(jdbc, adminName, f.data.password(), "ADMIN");
    String admin = login(adminName);

    assertThat(status(call(HttpMethod.GET, BASE + "/my-queue", null, null))).isEqualTo(401);
    assertThat(status(call(HttpMethod.GET, BASE + "/my-queue", admin, null))).isEqualTo(403);
    assertThat(status(call(HttpMethod.GET, BASE + "/" + visit, admin, null))).isEqualTo(403);
    assertThat(status(call(HttpMethod.PUT, BASE + "/" + visit + "/examination", admin, record(0))))
        .isEqualTo(403);
  }

  // ---- helpers ----------------------------------------------------------------------------

  private static Long doctorOf(Fixture f) {
    return f.data.doctorId();
  }

  private void insertOrder(long visitId, String itemName, String resultStatus) {
    long item =
        jdbc.queryForObject(
            """
            insert into medical_items (public_id, name, price, type)
            values (?, ?, 100000, 'LAB') returning id
            """,
            Long.class,
            UUID.randomUUID(),
            itemName + " " + UUID.randomUUID().toString().substring(0, 8));
    jdbc.update(
        """
        insert into medical_orders (public_id, code, visit_id, item_id, item_name_snapshot,
          unit_price_snapshot, ordered_at, source, status, result_status, result_text, performed_at)
        values (?, ?, ?, ?, ?, 100000, now(), 'SERVICE', 'COMPLETED', ?, 'Bình thường', now())
        """,
        UUID.randomUUID(),
        "ORD_" + UUID.randomUUID().toString().substring(0, 20),
        visitId,
        item,
        itemName,
        resultStatus);
  }
}
