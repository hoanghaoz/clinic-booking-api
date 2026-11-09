package com.se100.clinic.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import java.net.HttpCookie;
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

@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class AuthFlowIntegrationTest extends AbstractIntegrationTest {
  @Autowired private JdbcTemplate jdbc;

  private ResponseEntity<Map<String, Object>> postJson(
      String path, Object body, String refreshCookie) {
    HttpHeaders headers = new HttpHeaders();
    if (refreshCookie != null) {
      headers.add(HttpHeaders.COOKIE, "refresh_token=" + refreshCookie);
    }
    return restTemplate.exchange(
        path,
        HttpMethod.POST,
        new HttpEntity<>(body, headers),
        new ParameterizedTypeReference<>() {});
  }

  private static String extractRefreshTokenFromSetCookie(HttpHeaders headers) {
    List<String> setCookies = headers.get(HttpHeaders.SET_COOKIE);
    assertThat(setCookies).isNotEmpty();
    String header =
        setCookies.stream().filter(h -> h.startsWith("refresh_token=")).findFirst().orElseThrow();
    assertThat(header).containsIgnoringCase("HttpOnly");
    assertThat(header).contains("Path=/api/v1/auth");
    List<HttpCookie> parsed = HttpCookie.parse(header);
    return parsed.getFirst().getValue();
  }

  @Test
  void register_allowsPatientAndCompany_blocksStaffRoles_andRejectsDuplicateEmailWith409() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String patientEmail = "patient_" + suffix + "@example.com";
    String companyEmail = "company_" + suffix + "@example.com";

    var patientCreated =
        postJson(
            "/api/v1/auth/register",
            Map.of(
                "username",
                "pat_" + suffix,
                "email",
                patientEmail,
                "password",
                "Password123!",
                "role",
                "PATIENT"),
            null);
    assertThat(patientCreated.getStatusCode().value()).isEqualTo(201);
    assertThat(patientCreated.getHeaders().getLocation()).isNotNull();
    @SuppressWarnings("unchecked")
    Map<String, Object> patientData = (Map<String, Object>) patientCreated.getBody().get("data");
    assertThat(patientData)
        .containsEntry("email", patientEmail)
        .containsEntry("role", "PATIENT")
        .containsEntry("status", "ACTIVE");

    var companyCreated =
        postJson(
            "/api/v1/auth/register",
            Map.of("email", companyEmail, "password", "Password123!", "role", "COMPANY"),
            null);
    assertThat(companyCreated.getStatusCode().value()).isEqualTo(201);

    var duplicateEmail =
        postJson(
            "/api/v1/auth/register",
            Map.of(
                "username",
                "other_" + suffix,
                "email",
                patientEmail.toUpperCase(),
                "password",
                "Password123!",
                "role",
                "PATIENT"),
            null);
    assertThat(duplicateEmail.getStatusCode().value()).isEqualTo(409);
    assertThat(duplicateEmail.getBody()).containsEntry("code", "EMAIL_EXISTS");

    for (String forbiddenRole : List.of("DOCTOR", "MEDICAL_STAFF", "ADMIN")) {
      var denied =
          postJson(
              "/api/v1/auth/register",
              Map.of(
                  "email",
                  forbiddenRole.toLowerCase() + "_" + suffix + "@example.com",
                  "password",
                  "Password123!",
                  "role",
                  forbiddenRole),
              null);
      assertThat(denied.getStatusCode().value()).isEqualTo(400);
      assertThat(denied.getBody()).containsEntry("code", "VALIDATION_ERROR");
    }
  }

  @Test
  void loginRefreshRotationReuseDetectionAndLogoutFlow() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String email = "flow_" + suffix + "@example.com";
    String password = "StrongPassword123!";

    assertThat(
            postJson(
                    "/api/v1/auth/register",
                    Map.of("email", email, "password", password, "role", "PATIENT"),
                    null)
                .getStatusCode()
                .value())
        .isEqualTo(201);

    // 1. Login session 1 (e.g. Device A) and session 2 (e.g. Device B)
    var login1 =
        postJson("/api/v1/auth/login", Map.of("username", email, "password", password), null);
    assertThat(login1.getStatusCode().value()).isEqualTo(200);
    String refresh1 = extractRefreshTokenFromSetCookie(login1.getHeaders());
    assertThat(refresh1).isNotBlank();

    var login2 =
        postJson("/api/v1/auth/login", Map.of("username", email, "password", password), null);
    assertThat(login2.getStatusCode().value()).isEqualTo(200);
    String refresh2 = extractRefreshTokenFromSetCookie(login2.getHeaders());

    // Raw token is never stored in DB; only SHA-256 hash is stored
    Integer rawCount =
        jdbc.queryForObject(
            "select count(*) from refresh_tokens where token_hash = ?", Integer.class, refresh1);
    Integer hashedCount =
        jdbc.queryForObject(
            "select count(*) from refresh_tokens where token_hash = ?",
            Integer.class,
            AuthService.hashRefreshToken(refresh1));
    assertThat(rawCount).isZero();
    assertThat(hashedCount).isEqualTo(1);

    // 2. Rotate refresh1 -> refresh1_rotated
    var rotated = postJson("/api/v1/auth/refresh", null, refresh1);
    assertThat(rotated.getStatusCode().value()).isEqualTo(200);
    String refresh1Rotated = extractRefreshTokenFromSetCookie(rotated.getHeaders());
    assertThat(refresh1Rotated).isNotBlank().isNotEqualTo(refresh1);

    // 3. Reuse the already-revoked refresh1 -> 401 AND revokes all active sessions (including
    // refresh1Rotated & refresh2)
    var reuseAttempt = postJson("/api/v1/auth/refresh", null, refresh1);
    assertThat(reuseAttempt.getStatusCode().value()).isEqualTo(401);
    assertThat(reuseAttempt.getBody()).containsEntry("code", "INVALID_REFRESH_TOKEN");

    // Both refresh1Rotated and refresh2 must now be revoked in DB and rejected
    assertThat(postJson("/api/v1/auth/refresh", null, refresh1Rotated).getStatusCode().value())
        .isEqualTo(401);
    assertThat(postJson("/api/v1/auth/refresh", null, refresh2).getStatusCode().value())
        .isEqualTo(401);

    // 4. Fresh login + Logout revokes current token and clears cookie
    var login3 =
        postJson("/api/v1/auth/login", Map.of("username", email, "password", password), null);
    String refresh3 = extractRefreshTokenFromSetCookie(login3.getHeaders());

    ResponseEntity<Void> logoutResponse =
        restTemplate.exchange(
            "/api/v1/auth/logout",
            HttpMethod.POST,
            new HttpEntity<>(
                null,
                new HttpHeaders() {
                  {
                    add(HttpHeaders.COOKIE, "refresh_token=" + refresh3);
                  }
                }),
            Void.class);
    assertThat(logoutResponse.getStatusCode().value()).isEqualTo(204);
    assertThat(logoutResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
        .contains("refresh_token=;")
        .contains("Max-Age=0");

    assertThat(postJson("/api/v1/auth/refresh", null, refresh3).getStatusCode().value())
        .isEqualTo(401);
  }
}
