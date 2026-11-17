package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
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
 * Full-stack test, on real Postgres, of linking a counter-created profile to a PATIENT account and
 * of the card and identity actions. Accounts are inserted with plain SQL (the auth module has no
 * staff registration). Every test uses its own random identity number and phone: the database is
 * shared (see {@link AbstractIntegrationTest}).
 */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class PatientAccountAndCardIntegrationTest extends AbstractIntegrationTest {

  private static final String PASSWORD = "Password123!";
  private static final ParameterizedTypeReference<Map<String, Object>> BODY =
      new ParameterizedTypeReference<>() {};

  @Autowired private JdbcTemplate jdbc;

  @Test
  void linkAccount_shouldClaimWalkInProfileOnce_andHideMismatches() {
    String staff = login("MEDICAL_STAFF");
    String patientA = login("PATIENT");
    String patientB = login("PATIENT");
    String cccd = randomDigits(12);
    String phone = "09" + randomDigits(8);
    String publicId = createWalkIn(staff, cccd, phone);

    // Right document, wrong phone: same 404 as an unknown document.
    var wrongPhone =
        post("/api/v1/patients/link", patientB, linkBody(cccd, "09" + randomDigits(8)));
    assertThat(wrongPhone.getStatusCode().value()).isEqualTo(404);
    assertThat(wrongPhone.getBody()).containsEntry("code", "PATIENT_LINK_NOT_MATCHED");
    var unknown = post("/api/v1/patients/link", patientB, linkBody(randomDigits(12), phone));
    assertThat(unknown.getBody()).containsEntry("code", "PATIENT_LINK_NOT_MATCHED");

    var linked = post("/api/v1/patients/link", patientA, linkBody(cccd, phone));
    assertThat(linked.getStatusCode().value()).isEqualTo(200);
    assertThat(data(linked)).containsEntry("publicId", publicId);

    var me = get("/api/v1/patients/me", patientA);
    assertThat(me.getStatusCode().value()).isEqualTo(200);
    assertThat(data(me)).containsEntry("publicId", publicId);

    // The profile is taken now, and the first account already has one.
    var taken = post("/api/v1/patients/link", patientB, linkBody(cccd, phone));
    assertThat(taken.getStatusCode().value()).isEqualTo(404);
    var again = post("/api/v1/patients/link", patientA, linkBody(cccd, phone));
    assertThat(again.getStatusCode().value()).isEqualTo(409);
    assertThat(again.getBody()).containsEntry("code", "PATIENT_PROFILE_EXISTS");
  }

  @Test
  void card_shouldRevokeRenewAndReinstate_andRejectNonStaff() {
    String staff = login("MEDICAL_STAFF");
    String patient = login("PATIENT");
    String publicId = createWalkIn(staff, randomDigits(12), "09" + randomDigits(8));
    String base = "/api/v1/patients/" + publicId + "/card/";
    Instant expiry =
        Instant.parse(
            (String) data(get("/api/v1/patients/" + publicId, staff)).get("cardExpiresAt"));

    var revoked = post(base + "revoke", staff, null);
    assertThat(revoked.getStatusCode().value()).isEqualTo(200);
    assertThat(data(revoked)).containsEntry("status", "REVOKED");
    assertThat(post(base + "revoke", staff, null).getBody())
        .containsEntry("code", "PATIENT_CARD_REVOKED");
    assertThat(post(base + "renew", staff, null).getBody())
        .containsEntry("code", "PATIENT_CARD_REVOKED");

    var reinstated = post(base + "reinstate", staff, null);
    assertThat(data(reinstated)).containsEntry("status", "ACTIVE");
    assertThat(post(base + "reinstate", staff, null).getBody())
        .containsEntry("code", "PATIENT_CARD_NOT_REVOKED");

    var renewed = post(base + "renew", staff, null);
    assertThat(renewed.getStatusCode().value()).isEqualTo(200);
    assertThat(Instant.parse((String) data(renewed).get("expiresAt"))).isAfter(expiry);

    // A PATIENT may read cards but never change them.
    assertThat(post(base + "renew", patient, null).getStatusCode().value()).isEqualTo(403);
  }

  @Test
  void updateIdentity_shouldRejectADocumentUsedByAnotherPatient() {
    String staff = login("MEDICAL_STAFF");
    String first = randomDigits(12);
    String second = randomDigits(12);
    createWalkIn(staff, first, "09" + randomDigits(8));
    String secondId = createWalkIn(staff, second, "09" + randomDigits(8));
    String url = "/api/v1/patients/" + secondId + "/identity";

    var duplicate = put(url, staff, identityBody("CCCD", first));
    assertThat(duplicate.getStatusCode().value()).isEqualTo(409);
    assertThat(duplicate.getBody()).containsEntry("code", "PATIENT_IDENTITY_EXISTS");

    // Sending the patient's own current document again is not a duplicate.
    assertThat(put(url, staff, identityBody("CCCD", second)).getStatusCode().value())
        .isEqualTo(200);

    var changed = put(url, staff, identityBody("PASSPORT", "b" + randomDigits(7)));
    assertThat(changed.getStatusCode().value()).isEqualTo(200);
    assertThat(data(changed)).containsEntry("identityType", "PASSPORT");
    assertThat((String) data(changed).get("identityNumber")).startsWith("B");
  }

  // ---- helpers ----

  private String login(String role) {
    String username = role.toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 8);
    ScheduleTestData.user(jdbc, username, PASSWORD, role);
    var response =
        restTemplate.exchange(
            "/api/v1/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(Map.of("username", username, "password", PASSWORD)),
            BODY);
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    return (String) data(response).get("accessToken");
  }

  private String createWalkIn(String staffToken, String cccd, String phone) {
    var created =
        post(
            "/api/v1/patients",
            staffToken,
            Map.of(
                "fullName", "Nguyễn Văn Test",
                "dateOfBirth", "1990-05-20",
                "gender", "MALE",
                "phone", phone,
                "address", "Quận 3",
                "identityType", "CCCD",
                "identityNumber", cccd,
                "identityIssuer", "Cục CSQLHC"));
    assertThat(created.getStatusCode().value()).isEqualTo(201);
    return (String) data(created).get("publicId");
  }

  private static Map<String, Object> linkBody(String cccd, String phone) {
    return Map.of(
        "identityType",
        "CCCD",
        "identityNumber",
        cccd,
        "identityIssuer",
        "Cục CSQLHC",
        "phone",
        phone);
  }

  private static Map<String, Object> identityBody(String type, String number) {
    return Map.of("identityType", type, "identityNumber", number, "identityIssuer", "Cục CSQLHC");
  }

  private ResponseEntity<Map<String, Object>> get(String url, String token) {
    return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers(token)), BODY);
  }

  private ResponseEntity<Map<String, Object>> post(String url, String token, Object body) {
    return restTemplate.exchange(
        url, HttpMethod.POST, new HttpEntity<>(body, headers(token)), BODY);
  }

  private ResponseEntity<Map<String, Object>> put(String url, String token, Object body) {
    return restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers(token)), BODY);
  }

  private static HttpHeaders headers(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    return headers;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> data(ResponseEntity<Map<String, Object>> response) {
    return (Map<String, Object>) response.getBody().get("data");
  }

  private static String randomDigits(int length) {
    StringBuilder digits = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      digits.append(ThreadLocalRandom.current().nextInt(10));
    }
    return digits.toString();
  }
}
