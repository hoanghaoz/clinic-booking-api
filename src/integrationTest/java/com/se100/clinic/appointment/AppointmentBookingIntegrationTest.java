package com.se100.clinic.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import com.se100.clinic.appointment.BookingDtos.CreateRequest;
import com.se100.clinic.auth.JwtTokenService;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import com.se100.clinic.schedule.ScheduleService;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class AppointmentBookingIntegrationTest extends AbstractIntegrationTest {
  private static final String BASE = "/api/v1/appointments";

  @Autowired private JdbcTemplate jdbc;
  @Autowired private JwtTokenService jwt;
  @Autowired private ScheduleService schedules;
  @Autowired private AppointmentBookingService booking;

  @Test
  void bookedPackageReturnsScannableTicketAndKeepsOriginalVersionAndPrice() throws Exception {
    var data = ScheduleTestData.create(jdbc);
    String patient = token("PATIENT", data.patientId());
    String otherPatient = token("PATIENT", ScheduleTestData.create(jdbc).patientId());
    var schedule = morning(data, data.workDate(), 12, 8);
    UUID packageId = insertPackage("OPENING", "MALE", 18, 80);
    String admin = token("ADMIN", null);
    var links =
        request(
            HttpMethod.PUT,
            "/api/v1/medical-packages/" + packageId + "/specialties",
            admin,
            Map.of("specialtyIds", List.of(data.specialtyId())));
    assertThat(links.getStatusCode().value()).isEqualTo(200);

    var available =
        request(
            HttpMethod.GET,
            availabilityPath(schedule) + "&packagePublicId=" + packageId,
            patient,
            null);
    var matches = (List<?>) available.getBody().get("data");
    assertThat(matches).hasSize(1);
    assertThat(((Map<?, ?>) matches.getFirst()).get("packageVersion")).isEqualTo("v1.0");
    var booked =
        request(HttpMethod.POST, BASE, patient, packageBooking(schedule, packageId, "v1.0"));
    assertThat(booked.getStatusCode().value()).isEqualTo(201);
    var ticket = data(booked);
    String code = (String) ticket.get("code");
    String location = booked.getHeaders().getLocation().toString();
    assertThat(booked.getHeaders().getCacheControl()).contains("no-store");
    assertThat(ticket.get("status")).isEqualTo("CONFIRMED");
    assertThat(ticket.get("clinicName")).isEqualTo("Test clinic");
    assertThat(ticket.get("doctorName")).isEqualTo("Test doctor");
    assertThat(decodeQr((String) ticket.get("qrPngBase64"))).isEqualTo(code);
    assertThat(booking.findByQr(code, data.clinicId()))
        .get()
        .extracting(BookingDtos.ReceptionReference::patientId)
        .isEqualTo(data.patientId());
    assertThat(booking.findByQr(code, data.clinicId() + 1)).isEmpty();
    assertThat(booking.findByQr("invalid", data.clinicId())).isEmpty();
    assertThat(slotCount(code)).isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                """
        select h.action from appointment_histories h join appointments a on a.id = h.appointment_id
        where a.code = ?
        """,
                String.class,
                code))
        .isEqualTo("CREATED");

    Long oldVersion =
        jdbc.queryForObject(
            """
        select v.id from package_versions v join medical_packages p on p.id = v.package_id
        where p.public_id = ?
        """,
            Long.class,
            packageId);
    jdbc.update("update package_versions set effective_to = now() where id = ?", oldVersion);
    jdbc.update(
        """
        insert into package_versions (package_id, version, code, name, unit_price, class, status,
                                      consultation_minutes, effective_from)
        select package_id, 'v1.1', code, name, 230000, class, status, 30, effective_to
        from package_versions where id = ?
        """,
        oldVersion);
    assertThat(
            jdbc.queryForObject(
                "select package_version_id from appointments where code = ?", Long.class, code))
        .isEqualTo(oldVersion);
    var retrieved = request(HttpMethod.GET, location, patient, null);
    assertThat(retrieved.getStatusCode().value()).isEqualTo(200);
    assertThat(((Number) data(retrieved).get("price")).intValue()).isEqualTo(120000);
    assertThat(request(HttpMethod.GET, location, otherPatient, null).getStatusCode().value())
        .isEqualTo(404);
    assertError(
        request(HttpMethod.POST, BASE, patient, packageBooking(schedule, packageId, "v1.0")),
        409,
        "MEDICAL_PACKAGE_VERSION_CHANGED");
  }

  @Test
  void accessDateAndPackageEligibilityAreEnforced() {
    var data = ScheduleTestData.create(jdbc);
    String patient = token("PATIENT", data.patientId());
    String unlinked = token("PATIENT", null);
    String doctor = token("DOCTOR", null);
    UUID service = insertService(20);
    var day30 = morning(data, data.workDate().minusDays(7).plusDays(30), 12, 8);
    var day31 = morning(data, day30.workDate().plusDays(1), 12, 8);
    var request30 = serviceBooking(day30, service, day30.startAt());
    assertThat(request(HttpMethod.POST, BASE, null, request30).getStatusCode().value())
        .isEqualTo(401);
    assertThat(request(HttpMethod.POST, BASE, doctor, request30).getStatusCode().value())
        .isEqualTo(403);
    assertError(
        request(HttpMethod.POST, BASE, unlinked, request30), 409, "PATIENT_PROFILE_REQUIRED");
    assertError(
        request(HttpMethod.POST, BASE, patient, serviceBooking(day31, service, day31.startAt())),
        400,
        "BOOKING_DATE_LIMIT");
    assertError(
        request(
            HttpMethod.POST,
            BASE,
            patient,
            serviceBooking(day30, service, Instant.now().minusSeconds(120))),
        400,
        "BOOKING_START_IN_PAST");
    var dates =
        request(
            HttpMethod.GET,
            availabilityPath(day30)
                .replace("toDate=" + day30.workDate(), "toDate=" + day31.workDate()),
            patient,
            null);
    assertThat((List<?>) dates.getBody().get("data")).hasSize(1);

    assertPackageRejected(
        patient,
        day30,
        insertPackage("CLOSING", "BOTH", null, null),
        "MEDICAL_PACKAGE_NOT_BOOKABLE");
    assertPackageRejected(
        patient,
        day30,
        insertPackage("OPENING", "FEMALE", null, null),
        "BOOKING_PRODUCT_INELIGIBLE");
    assertPackageRejected(
        patient, day30, insertPackage("OPENING", "BOTH", 90, null), "BOOKING_PRODUCT_INELIGIBLE");
    assertThat(request(HttpMethod.POST, BASE, patient, request30).getStatusCode().value())
        .isEqualTo(201);
  }

  @Test
  void bookingDiscoveryReturnsOpenClinicsAndSupportedServices() {
    var data = ScheduleTestData.create(jdbc);
    var closed = ScheduleTestData.create(jdbc);
    jdbc.update("update clinics set status = 'CLOSING' where id = ?", closed.clinicId());
    UUID supported = insertService(20);
    UUID unsupported = insertService(10);
    String patient = token("PATIENT", data.patientId());

    var clinics = request(HttpMethod.GET, BASE + "/clinics?size=100", patient, null);
    assertThat(clinics.getStatusCode().value()).isEqualTo(200);
    var clinicIds =
        ((List<?>) clinics.getBody().get("data"))
            .stream().map(item -> ((Number) ((Map<?, ?>) item).get("id")).longValue()).toList();
    assertThat(clinicIds).contains(data.clinicId()).doesNotContain(closed.clinicId());

    var services = request(HttpMethod.GET, BASE + "/services?size=100", patient, null);
    assertThat(services.getStatusCode().value()).isEqualTo(200);
    var serviceIds =
        ((List<?>) services.getBody().get("data"))
            .stream().map(item -> ((Map<?, ?>) item).get("publicId").toString()).toList();
    assertThat(serviceIds).contains(supported.toString()).doesNotContain(unsupported.toString());
  }

  @Test
  void activeBookingRestrictionBlocksOnlineCreationUntilRevoked() {
    var data = ScheduleTestData.create(jdbc);
    var schedule = morning(data, data.workDate(), 12, 8);
    Long scheduleId =
        jdbc.queryForObject(
            "select id from doctor_schedules where public_id = ?", Long.class, schedule.publicId());
    long missed = data.appointment(scheduleId, schedule.startAt(), 20, "NO_SHOW");
    Long violation =
        jdbc.queryForObject(
            """
        insert into patient_violations (public_id, code, patient_id, appointment_id, type,
            description, occurred_at, booking_restricted_from, booking_restricted_until)
        values (?, ?, ?, ?, 'NO_SHOW', 'Test restriction', now(), now(), now() + interval '1 day')
        returning id
        """,
            Long.class,
            UUID.randomUUID(),
            "V-" + UUID.randomUUID(),
            data.patientId(),
            missed);
    String patient = token("PATIENT", data.patientId());
    var body = serviceBooking(schedule, insertService(20), schedule.startAt());
    assertError(request(HttpMethod.POST, BASE, patient, body), 403, "ONLINE_BOOKING_RESTRICTED");
    jdbc.update("update patient_violations set revoked_at = now() where id = ?", violation);
    assertThat(request(HttpMethod.POST, BASE, patient, body).getStatusCode().value())
        .isEqualTo(201);
  }

  @Test
  void patientCannotOverlapOrUseTwoClinicsInSameShiftButAdjacentTimesAreValid() {
    var data = ScheduleTestData.create(jdbc);
    var other = ScheduleTestData.create(jdbc);
    String patient = token("PATIENT", data.patientId());
    UUID service = insertService(20);
    var schedule = morning(data, data.workDate(), 12, 8);
    var otherClinic = morning(other, data.workDate(), 12, 8);
    var first = serviceBooking(schedule, service, schedule.startAt());
    assertThat(request(HttpMethod.POST, BASE, patient, first).getStatusCode().value())
        .isEqualTo(201);
    assertError(request(HttpMethod.POST, BASE, patient, first), 409, "APPOINTMENT_OVERLAP");
    assertError(
        request(
            HttpMethod.POST,
            BASE,
            patient,
            serviceBooking(otherClinic, service, otherClinic.startAt().plusSeconds(1200))),
        409,
        "APPOINTMENT_OTHER_CLINIC_SAME_SHIFT");
    assertThat(
            request(
                    HttpMethod.POST,
                    BASE,
                    patient,
                    serviceBooking(schedule, service, schedule.startAt().plusSeconds(1200)))
                .getStatusCode()
                .value())
        .isEqualTo(201);
  }

  @Test
  void concurrentRequestsForOneWindowCreateOneAppointmentAndRollbackTheOther() throws Exception {
    var data = ScheduleTestData.create(jdbc);
    long other = ScheduleTestData.create(jdbc).patientId();
    var schedule = morning(data, data.workDate(), 12, 8);
    var body = serviceBooking(schedule, insertService(30), schedule.startAt());
    String first = token("PATIENT", data.patientId());
    String second = token("PATIENT", other);
    assertThat(race(first, body, second, body)).containsExactlyInAnyOrder(201, 409);
    assertThat(appointmentCount(data.patientId(), other)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                """
        select count(*) from appointment_histories h join appointments a on a.id = h.appointment_id
        where a.patient_id in (?, ?)
        """,
                Integer.class,
                data.patientId(),
                other))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                """
        select count(*) from appointment_slots s join appointments a on a.id = s.appointment_id
        where a.patient_id in (?, ?) and s.released_at is null
        """,
                Integer.class,
                data.patientId(),
                other))
        .isEqualTo(3);
  }

  @Test
  void concurrentRequestsFromOnePatientAcrossDifferentSchedulesAreSerialized() throws Exception {
    var data = ScheduleTestData.create(jdbc);
    var other = ScheduleTestData.create(jdbc);
    var schedule = morning(data, data.workDate(), 12, 8);
    var otherClinic = morning(other, data.workDate(), 12, 8);
    UUID service = insertService(20);
    String patient = token("PATIENT", data.patientId());
    assertThat(
            race(
                patient,
                serviceBooking(schedule, service, schedule.startAt()),
                patient,
                serviceBooking(otherClinic, service, otherClinic.startAt().plusSeconds(1200))))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(appointmentCount(data.patientId(), data.patientId())).isEqualTo(1);
  }

  @Test
  void blockedSlotRollsBackTheAppointmentAndMixedVisitsShareCapacity() {
    var data = ScheduleTestData.create(jdbc);
    var schedule = morning(data, data.workDate(), 1, 1);
    String patient = token("PATIENT", data.patientId());
    UUID general = insertService(20);
    UUID specialist = insertService(30);
    Long blocked =
        jdbc.queryForObject(
            """
        select s.id from doctor_schedule_slots s join doctor_schedules d on d.id = s.schedule_id
        where d.public_id = ? order by s.start_at offset 2 limit 1
        """,
            Long.class,
            schedule.publicId());
    schedules.changeSlotStatus(
        schedule.publicId(), blocked, com.se100.clinic.schedule.enums.SlotStatus.BLOCKED);
    assertError(
        request(
            HttpMethod.POST,
            BASE,
            patient,
            serviceBooking(schedule, specialist, schedule.startAt())),
        409,
        "SLOT_UNAVAILABLE");
    assertThat(appointmentCount(data.patientId(), data.patientId())).isZero();
    assertThat(
            request(
                    HttpMethod.POST,
                    BASE,
                    patient,
                    serviceBooking(schedule, general, schedule.startAt()))
                .getStatusCode()
                .value())
        .isEqualTo(201);
    assertError(
        request(
            HttpMethod.POST,
            BASE,
            patient,
            serviceBooking(schedule, general, schedule.startAt().plusSeconds(1800))),
        409,
        "SLOT_UNAVAILABLE");
    assertThat(
            request(
                    HttpMethod.POST,
                    BASE,
                    patient,
                    serviceBooking(schedule, specialist, schedule.startAt().plusSeconds(1800)))
                .getStatusCode()
                .value())
        .isEqualTo(201);
  }

  private void assertPackageRejected(
      String token, ScheduleResponse schedule, UUID packageId, String code) {
    jdbc.update(
        """
        insert into package_specialities (package_id, specialty_id)
        select id, ? from medical_packages where public_id = ?
        """,
        schedule.specialtyId(),
        packageId);
    assertError(
        request(HttpMethod.POST, BASE, token, packageBooking(schedule, packageId, "v1.0")),
        409,
        code);
  }

  private List<Integer> race(
      String first, CreateRequest firstBody, String second, CreateRequest secondBody)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var left =
          executor.submit(
              () -> {
                start.await();
                return request(HttpMethod.POST, BASE, first, firstBody).getStatusCode().value();
              });
      var right =
          executor.submit(
              () -> {
                start.await();
                return request(HttpMethod.POST, BASE, second, secondBody).getStatusCode().value();
              });
      start.countDown();
      return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
    }
  }

  private static String decodeQr(String encoded) throws Exception {
    var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
    int width = image.getWidth();
    int height = image.getHeight();
    int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
    return new QRCodeReader()
        .decode(
            new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels))))
        .getText();
  }

  private String token(String role, Long patientId) {
    long user = ScheduleTestData.user(jdbc, "booking_" + UUID.randomUUID(), "test-password", role);
    if (patientId != null) {
      jdbc.update("update patients set user_id = ? where id = ?", user, patientId);
    }
    String subject =
        jdbc.queryForObject("select public_id::text from users where id = ?", String.class, user);
    return jwt.issue(subject).accessToken();
  }

  private UUID insertService(int duration) {
    UUID publicId = UUID.randomUUID();
    jdbc.update(
        """
        insert into medical_services (public_id, name, price, duration_minutes)
        values (?, 'Khám lẻ', 100000, ?)
        """,
        publicId,
        duration);
    return publicId;
  }

  private UUID insertPackage(String status, String target, Integer minAge, Integer maxAge) {
    UUID publicId = UUID.randomUUID();
    String code = "PK-" + publicId;
    Long id =
        jdbc.queryForObject(
            """
        insert into medical_packages (public_id, code, name) values (?, ?, ?) returning id
        """,
            Long.class,
            publicId,
            code,
            code);
    jdbc.update(
        """
        insert into package_versions (package_id, version, code, name, unit_price, class, status,
            target, min_age, max_age, consultation_minutes, effective_from)
        values (?, 'v1.0', ?, ?, 120000, 'STANDARD', ?, ?, ?, ?, 30, now() - interval '1 day')
        """,
        id,
        code,
        code,
        status,
        target,
        minAge,
        maxAge);
    return publicId;
  }

  private ScheduleResponse morning(
      ScheduleTestData data, LocalDate date, int general, int specialist) {
    Long shiftId = jdbc.queryForObject("select id from shifts where code = 'MORNING'", Long.class);
    return schedules.create(
        new SaveScheduleRequest(
            data.doctorId(),
            data.specialtyId(),
            data.clinicId(),
            data.roomId(),
            shiftId,
            date,
            general,
            specialist));
  }

  private static CreateRequest serviceBooking(
      ScheduleResponse schedule, UUID service, Instant start) {
    return new CreateRequest(
        schedule.publicId(), schedule.clinicId(), schedule.doctorId(), start, null, null, service);
  }

  private static CreateRequest packageBooking(
      ScheduleResponse schedule, UUID packageId, String version) {
    return new CreateRequest(
        schedule.publicId(),
        schedule.clinicId(),
        schedule.doctorId(),
        schedule.startAt(),
        packageId,
        version,
        null);
  }

  private static String availabilityPath(ScheduleResponse schedule) {
    return BASE
        + "/availability?clinicId="
        + schedule.clinicId()
        + "&fromDate="
        + schedule.workDate()
        + "&toDate="
        + schedule.workDate();
  }

  private int slotCount(String code) {
    return jdbc.queryForObject(
        """
        select count(*) from appointment_slots s join appointments a on a.id = s.appointment_id
        where a.code = ? and s.released_at is null
        """,
        Integer.class,
        code);
  }

  private int appointmentCount(long firstPatient, long secondPatient) {
    return jdbc.queryForObject(
        "select count(*) from appointments where patient_id in (?, ?)",
        Integer.class,
        firstPatient,
        secondPatient);
  }

  private ResponseEntity<Map<String, Object>> request(
      HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return restTemplate.exchange(
        path, method, new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() {});
  }

  private static Map<?, ?> data(ResponseEntity<Map<String, Object>> response) {
    return (Map<?, ?>) response.getBody().get("data");
  }

  private static void assertError(
      ResponseEntity<Map<String, Object>> response, int status, String code) {
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody()).containsEntry("code", code);
  }
}
