package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.doctor.DoctorDtos.CreateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.DoctorResponse;
import com.se100.clinic.doctor.DoctorDtos.DoctorSpecialtyResponse;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorStatusRequest;
import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.ApiResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Full-stack test of {@code /api/v1/doctors} on real Postgres. Accounts, rooms and schedules belong
 * to other (not yet implemented) modules, so they are inserted with plain SQL. Every test tags its
 * data with a fresh {@code tag} (shared DB, see {@link AbstractIntegrationTest}).
 */
class DoctorControllerIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/doctors";

  private static final ParameterizedTypeReference<ApiResult<DoctorResponse>> ONE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<List<DoctorResponse>>> PAGE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<ApiResult<SpecialtyResponse>> SPECIALTY =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Object>> ERROR =
      new ParameterizedTypeReference<>() {};

  @Autowired private JdbcTemplate jdbc;

  private static String newTag() {
    return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
  }

  private long newDoctorAccount() {
    String name = "u" + UUID.randomUUID().toString().replace("-", "");
    return jdbc.queryForObject(
        """
        insert into users (public_id, username, email, password_hashed, role)
        values (gen_random_uuid(), ?, ?, 'x', 'DOCTOR') returning id
        """,
        Long.class,
        name,
        name + "@test.local");
  }

  private long newSpecialty(String code) {
    var response =
        restTemplate.exchange(
            "/api/v1/specialties",
            HttpMethod.POST,
            new HttpEntity<>(new CreateSpecialtyRequest(code, "Khoa " + code, null)),
            SPECIALTY);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().data().id();
  }

  private DoctorResponse create(CreateDoctorRequest request) {
    ResponseEntity<ApiResult<DoctorResponse>> response =
        restTemplate.exchange(BASE, HttpMethod.POST, new HttpEntity<>(request), ONE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return response.getBody().data();
  }

  private ResponseEntity<Map<String, Object>> send(HttpMethod method, String url, Object body) {
    return restTemplate.exchange(url, method, new HttpEntity<>(body), ERROR);
  }

  @Test
  void createThenGet_shouldRoundTrip_withSpecialtiesSortedByName() {
    String tag = newTag();
    long userId = newDoctorAccount();
    long tim = newSpecialty(tag + "B");
    long noi = newSpecialty(tag + "A");

    ResponseEntity<ApiResult<DoctorResponse>> created =
        restTemplate.exchange(
            BASE,
            HttpMethod.POST,
            new HttpEntity<>(
                new CreateDoctorRequest(userId, tag, "Nguyễn Văn A", "ThS.BS", List.of(tim, noi))),
            ONE);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    DoctorResponse doctor = created.getBody().data();
    assertThat(created.getHeaders().getLocation()).hasPath(BASE + "/" + doctor.id());
    assertThat(doctor.status()).isEqualTo(EmployeeStatus.WORKING);

    DoctorResponse fetched =
        restTemplate.exchange(BASE + "/" + doctor.id(), HttpMethod.GET, null, ONE).getBody().data();
    assertThat(fetched.userId()).isEqualTo(userId);
    assertThat(fetched.specialties())
        .extracting(DoctorSpecialtyResponse::id)
        .containsExactly(noi, tim);
  }

  @Test
  void create_shouldMapDbConstraintsToModuleCodes() {
    String tag = newTag();
    long userId = newDoctorAccount();
    long specialty = newSpecialty(tag);
    create(new CreateDoctorRequest(userId, tag, "A", "BS", List.of(specialty)));

    // Unknown account -> FK fk_doctors_user_id.
    var unknownUser =
        send(
            HttpMethod.POST,
            BASE,
            new CreateDoctorRequest(-1L, tag + "X", "A", "BS", List.of(specialty)));
    assertThat(unknownUser.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(unknownUser.getBody()).containsEntry("code", "DOCTOR_USER_NOT_FOUND");

    var sameCode =
        send(
            HttpMethod.POST,
            BASE,
            new CreateDoctorRequest(newDoctorAccount(), tag, "B", "BS", List.of(specialty)));
    assertThat(sameCode.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(sameCode.getBody()).containsEntry("code", "DOCTOR_CODE_EXISTS");

    var sameAccount =
        send(
            HttpMethod.POST,
            BASE,
            new CreateDoctorRequest(userId, tag + "Y", "B", "BS", List.of(specialty)));
    assertThat(sameAccount.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(sameAccount.getBody()).containsEntry("code", "DOCTOR_USER_ALREADY_LINKED");

    var missingSpecialty =
        send(
            HttpMethod.POST,
            BASE,
            new CreateDoctorRequest(newDoctorAccount(), tag + "Z", "B", "BS", List.of(-5L)));
    assertThat(missingSpecialty.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(missingSpecialty.getBody()).containsEntry("code", "SPECIALTY_NOT_FOUND");
  }

  @Test
  void list_shouldFilterByKeywordSpecialtyAndStatus_andPaginate() {
    String tag = newTag();
    long noi = newSpecialty(tag + "N");
    long tim = newSpecialty(tag + "T");
    DoctorResponse a =
        create(
            new CreateDoctorRequest(newDoctorAccount(), tag + "1", "A " + tag, "BS", List.of(noi)));
    DoctorResponse b =
        create(
            new CreateDoctorRequest(
                newDoctorAccount(), tag + "2", "B " + tag, "BS", List.of(noi, tim)));
    DoctorResponse c =
        create(
            new CreateDoctorRequest(newDoctorAccount(), tag + "3", "C " + tag, "BS", List.of(tim)));
    send(
        HttpMethod.PATCH,
        BASE + "/" + c.id() + "/status",
        new UpdateDoctorStatusRequest(EmployeeStatus.NOT_WORKING));

    var byKeyword =
        restTemplate.exchange(
            BASE + "?keyword={tag}&size=2", HttpMethod.GET, null, PAGE, tag.toLowerCase());
    assertThat(byKeyword.getBody().data())
        .extracting(DoctorResponse::id)
        .containsExactly(a.id(), b.id());
    assertThat(byKeyword.getBody().meta().totalElements()).isEqualTo(3);
    assertThat(byKeyword.getBody().meta().totalPages()).isEqualTo(2);

    var bySpecialty =
        restTemplate.exchange(BASE + "?specialtyId={id}", HttpMethod.GET, null, PAGE, tim);
    assertThat(bySpecialty.getBody().data())
        .extracting(DoctorResponse::id)
        .containsExactly(b.id(), c.id());

    var working =
        restTemplate.exchange(
            BASE + "?specialtyId={id}&status=WORKING&sort=-code", HttpMethod.GET, null, PAGE, tim);
    assertThat(working.getBody().data()).extracting(DoctorResponse::id).containsExactly(b.id());
  }

  @Test
  void update_shouldReplaceSpecialties_includingReAddingInSameRequestFlow() {
    String tag = newTag();
    long s1 = newSpecialty(tag + "1");
    long s2 = newSpecialty(tag + "2");
    long s3 = newSpecialty(tag + "3");
    DoctorResponse doctor =
        create(new CreateDoctorRequest(newDoctorAccount(), tag, "A", "BS", List.of(s1, s2)));

    DoctorResponse updated =
        restTemplate
            .exchange(
                BASE + "/" + doctor.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateDoctorRequest("Tên mới", "TS.BS", List.of(s2, s3))),
                ONE)
            .getBody()
            .data();
    assertThat(updated.name()).isEqualTo("Tên mới");
    assertThat(updated.specialties())
        .extracting(DoctorSpecialtyResponse::id)
        .containsExactly(s2, s3);

    DoctorResponse back =
        restTemplate
            .exchange(
                BASE + "/" + doctor.id(),
                HttpMethod.PUT,
                new HttpEntity<>(new UpdateDoctorRequest("Tên mới", "TS.BS", List.of(s1))),
                ONE)
            .getBody()
            .data();
    assertThat(back.specialties()).extracting(DoctorSpecialtyResponse::id).containsExactly(s1);
  }

  @Test
  void update_removingSpecialtyUsedBySchedule_returns409SpecialtyInUse() {
    String tag = newTag();
    long s1 = newSpecialty(tag + "1");
    long s2 = newSpecialty(tag + "2");
    DoctorResponse doctor =
        create(new CreateDoctorRequest(newDoctorAccount(), tag, "A", "BS", List.of(s1, s2)));
    insertSchedule(tag, doctor.id(), s1);

    var response =
        send(
            HttpMethod.PUT,
            BASE + "/" + doctor.id(),
            new UpdateDoctorRequest("A", "BS", List.of(s2)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("code", "DOCTOR_SPECIALTY_IN_USE");
    DoctorResponse unchanged =
        restTemplate.exchange(BASE + "/" + doctor.id(), HttpMethod.GET, null, ONE).getBody().data();
    assertThat(unchanged.specialties())
        .extracting(DoctorSpecialtyResponse::id)
        .containsExactly(s1, s2);
  }

  @Test
  void addingDeactivatedSpecialty_returns409() {
    String tag = newTag();
    long active = newSpecialty(tag + "A");
    long inactive = newSpecialty(tag + "I");
    restTemplate.delete("/api/v1/specialties/" + inactive);

    var response =
        send(
            HttpMethod.POST,
            BASE,
            new CreateDoctorRequest(newDoctorAccount(), tag, "A", "BS", List.of(active, inactive)));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("code", "SPECIALTY_INACTIVE");
  }

  /** A schedule row referencing (doctor, specialty) — owned by the future schedule module. */
  private void insertSchedule(String tag, long doctorId, long specialtyId) {
    Long clinicId =
        jdbc.queryForObject(
            """
            insert into clinics (public_id, code, name, phone_number, address, status)
            values (gen_random_uuid(), ?, 'Cơ sở test', '0900000000', 'HCM', 'OPENING') returning id
            """,
            Long.class,
            tag);
    Long roomId =
        jdbc.queryForObject(
            """
            insert into rooms (public_id, clinic_id, code, name, status)
            values (gen_random_uuid(), ?, 'P1', 'Phòng 1', 'OPENING') returning id
            """,
            Long.class,
            clinicId);
    Long shiftId =
        jdbc.queryForObject(
            """
            insert into shifts (code, name, start_time, end_time)
            values (?, 'Ca sáng', '08:00', '12:00') returning id
            """,
            Long.class,
            tag);
    jdbc.update(
        """
        insert into doctor_schedules
          (public_id, doctor_id, specialty_id, room_id, shift_id, work_date, start_at, end_at)
        values (gen_random_uuid(), ?, ?, ?, ?, date '2026-11-02',
                timestamptz '2026-11-02 08:00+07', timestamptz '2026-11-02 12:00+07')
        """,
        doctorId,
        specialtyId,
        roomId,
        shiftId);
  }
}
