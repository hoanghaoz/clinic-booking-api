package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.shared.DbErrors;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proves that REAL PostgreSQL constraint violations come out of the API as 4xx with a {@code code},
 * never as a generic 500. The violations are provoked on purpose through a test-only endpoint that
 * bypasses the service-level pre-checks, i.e. the situation after a race between two requests.
 *
 * <p>The service-level translation (unique violation -> {@code SPECIALTY_CODE_EXISTS}) is covered
 * by {@code SpecialtyServiceTest}; this class covers the global safety net and {@link DbErrors}
 * against the real driver/Hibernate exception chain.
 */
@Import(DbConstraintErrorIntegrationTest.ViolationEndpoints.class)
class DbConstraintErrorIntegrationTest extends AbstractIntegrationTest {

  private static final ParameterizedTypeReference<Map<String, Object>> ERROR =
      new ParameterizedTypeReference<>() {};

  @TestConfiguration(proxyBeanMethods = false)
  static class ViolationEndpoints {

    @org.springframework.context.annotation.Bean
    DbViolationController dbViolationController(JdbcTemplate jdbc) {
      return new DbViolationController(jdbc);
    }
  }

  @RestController
  static class DbViolationController {

    private final JdbcTemplate jdbc;

    DbViolationController(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @GetMapping("/test-only/db/{kind}")
    String provoke(@PathVariable String kind) {
      String code = "DBTEST_" + UUID.randomUUID().toString().substring(0, 8);
      switch (kind) {
        case "unique" -> {
          jdbc.update("insert into specialties (code, name) values (?, 'a')", code);
          jdbc.update("insert into specialties (code, name) values (?, 'b')", code);
        }
        case "not-null" ->
            jdbc.update("insert into specialties (code, name) values (?, null)", code);
        case "too-long" ->
            jdbc.update("insert into specialties (code, name) values (?, 'a')", "X".repeat(80));
        default -> throw new IllegalArgumentException(kind);
      }
      return "no violation";
    }
  }

  @Autowired private SpecialtyRepository specialtyRepository;

  private ResponseEntity<Map<String, Object>> provoke(String kind) {
    return restTemplate.exchange("/test-only/db/" + kind, HttpMethod.GET, null, ERROR);
  }

  @Test
  void uniqueViolation_shouldBe409DuplicateResource_notGeneric500() {
    ResponseEntity<Map<String, Object>> response = provoke("unique");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("code", "DUPLICATE_RESOURCE");
  }

  @Test
  void notNullViolation_shouldBe400InvalidRequest() {
    ResponseEntity<Map<String, Object>> response = provoke("not-null");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "INVALID_REQUEST");
  }

  @Test
  void valueTooLong_shouldBe400InvalidRequest() {
    ResponseEntity<Map<String, Object>> response = provoke("too-long");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsEntry("code", "INVALID_REQUEST");
  }

  @Test
  void errorBody_shouldNotLeakDriverMessageOrValues() {
    ResponseEntity<Map<String, Object>> response = provoke("unique");

    assertThat(response.getBody().toString())
        .doesNotContain("DBTEST_")
        .doesNotContain("specialties")
        .doesNotContain("Key (");
  }

  @Test
  void dbErrors_shouldRecogniseUniqueViolation_inRealJpaExceptionChain() {
    String code = "DBTEST_" + UUID.randomUUID().toString().substring(0, 8);
    specialtyRepository.saveAndFlush(new Specialty(code, "Một", null));

    assertThatThrownBy(() -> specialtyRepository.saveAndFlush(new Specialty(code, "Hai", null)))
        .isInstanceOfSatisfying(
            DataIntegrityViolationException.class,
            e -> {
              assertThat(DbErrors.isUniqueViolation(e)).isTrue();
              assertThat(DbErrors.sqlState(e)).isEqualTo(DbErrors.UNIQUE_VIOLATION);
              assertThat(DbErrors.constraintName(e)).isEqualTo("specialties_code_key");
            });
  }
}
