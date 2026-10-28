package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
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

@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class ScheduleJwtIntegrationTest extends AbstractIntegrationTest {
  @Autowired private JdbcTemplate jdbc;

  private ResponseEntity<Map<String, Object>> request(
      HttpMethod method, String path, String token, Object body) {
    HttpHeaders headers = new HttpHeaders();
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return restTemplate.exchange(
        path, method, new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() {});
  }

  private String login(String username, String password) {
    var response =
        request(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("username", username, "password", password));
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getCacheControl()).contains("no-store");
    return (String) ((Map<?, ?>) response.getBody().get("data")).get("accessToken");
  }

  @Test
  void realLoginEnforcesAdminDoctorAndScheduleOwnership() {
    var data = ScheduleTestData.create(jdbc);
    String adminName = "admin_" + UUID.randomUUID().toString().substring(0, 8);
    ScheduleTestData.user(jdbc, adminName, data.password(), "ADMIN");
    String admin = login(adminName, data.password());
    String doctor = login(data.doctorUsername(), data.password());
    Long shiftId = jdbc.queryForObject("select id from shifts where code = 'MORNING'", Long.class);
    var body =
        new ScheduleDtos.SaveScheduleRequest(
            data.doctorId(),
            data.specialtyId(),
            data.clinicId(),
            data.roomId(),
            shiftId,
            data.workDate(),
            12,
            8);
    String base = "/api/v1/doctor-schedules";
    assertThat(request(HttpMethod.GET, base + "/my", null, null).getStatusCode().value())
        .isEqualTo(401);
    var denied = request(HttpMethod.POST, base, doctor, body);
    assertThat(denied.getStatusCode().value()).isEqualTo(403);
    assertThat(denied.getBody()).containsEntry("code", "FORBIDDEN");
    var created = request(HttpMethod.POST, base, admin, body);
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    String publicId = (String) ((Map<?, ?>) created.getBody().get("data")).get("publicId");
    assertThat(request(HttpMethod.GET, base + "/my", doctor, null).getBody().get("data"))
        .asList()
        .hasSize(1);
    assertThat(
            request(HttpMethod.GET, base + "/my/" + publicId + "/patients", doctor, null)
                .getStatusCode()
                .value())
        .isEqualTo(200);
    long foreignId =
        data.schedule(
            data.secondDoctorId(),
            data.secondRoomId(),
            "MORNING",
            data.start(),
            data.start().plusSeconds(14400),
            "OPEN");
    String foreignPublicId =
        jdbc.queryForObject(
            "select public_id::text from doctor_schedules where id = ?", String.class, foreignId);
    var foreign =
        request(HttpMethod.GET, base + "/my/" + foreignPublicId + "/patients", doctor, null);
    assertThat(foreign.getStatusCode().value()).isEqualTo(404);
    assertThat(foreign.getBody()).containsEntry("code", "SCHEDULE_NOT_ASSIGNED");
    jdbc.update("update users set status = 'DISABLED' where username = ?", data.doctorUsername());
    assertThat(request(HttpMethod.GET, base + "/my", doctor, null).getStatusCode().value())
        .isEqualTo(401);
  }

  @Test
  void invalidCredentialsAndTokensReturn401() {
    var data = ScheduleTestData.create(jdbc);
    var failed =
        request(
            HttpMethod.POST,
            "/api/v1/auth/login",
            null,
            Map.of("username", data.doctorUsername(), "password", "incorrect"));
    assertThat(failed.getStatusCode().value()).isEqualTo(401);
    assertThat(failed.getBody()).containsEntry("code", "INVALID_CREDENTIALS");
    assertThat(
            request(
                    HttpMethod.POST,
                    "/api/v1/auth/login",
                    null,
                    Map.of("username", data.doctorUsername(), "password", "ậ".repeat(50)))
                .getStatusCode()
                .value())
        .isEqualTo(401);
    assertThat(
            request(HttpMethod.GET, "/api/v1/doctor-schedules/my", "invalid.jwt.token", null)
                .getStatusCode()
                .value())
        .isEqualTo(401);
  }
}
