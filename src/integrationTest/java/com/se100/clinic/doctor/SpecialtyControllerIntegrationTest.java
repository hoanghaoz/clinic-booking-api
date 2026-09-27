package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test THẬT — khởi toàn bộ Spring context, chạy Flyway migration thật, và query
 * Postgres thật bên trong 1 container Docker do Testcontainers tự khởi/tự huỷ. Chạy bằng {@code
 * ./gradlew integrationTest} (KHÔNG chạy khi gõ {@code ./gradlew test}) — cần Docker daemon đang
 * chạy.
 *
 * <p>{@code @ServiceConnection} (tính năng của Spring Boot Testcontainers integration): tự động trỏ
 * {@code spring.datasource.*} vào container Postgres bên dưới, không cần tự viết
 * {@code @DynamicPropertySource} thủ công.
 *
 * <p>{@code @AutoConfigureTestRestTemplate}: từ Spring Boot 4, bean {@link TestRestTemplate} không
 * còn tự có trong mọi {@code @SpringBootTest} — phải bật tường minh bằng annotation này.
 */
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class SpecialtyControllerIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:17.11-alpine"));

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void createThenGet_shouldRoundTripThroughRealDatabase() {
    var request = new CreateSpecialtyRequest("NHI_KHOA", "Nhi khoa", "Khám nhi");

    ResponseEntity<SpecialtyResponse> createResponse =
        restTemplate.postForEntity("/api/v1/specialties", request, SpecialtyResponse.class);
    assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(createResponse.getBody()).isNotNull();
    Long id = createResponse.getBody().id();

    ResponseEntity<SpecialtyResponse> getResponse =
        restTemplate.getForEntity("/api/v1/specialties/" + id, SpecialtyResponse.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(getResponse.getBody().code()).isEqualTo("NHI_KHOA");
    assertThat(getResponse.getBody().active()).isTrue();
  }

  @Test
  void create_shouldReturn409_whenCodeAlreadyExists() {
    var request = new CreateSpecialtyRequest("TAI_MUI_HONG", "Tai Mũi Họng", null);
    restTemplate.postForEntity("/api/v1/specialties", request, SpecialtyResponse.class);

    ResponseEntity<String> secondAttempt =
        restTemplate.postForEntity("/api/v1/specialties", request, String.class);

    assertThat(secondAttempt.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void getById_shouldReturn404_whenNotFound() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v1/specialties/999999", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
