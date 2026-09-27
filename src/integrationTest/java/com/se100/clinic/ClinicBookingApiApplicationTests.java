package com.se100.clinic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// Starts a real Postgres via Testcontainers, so it lives in integrationTest, not the fast unit
// `test` task.
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class ClinicBookingApiApplicationTests {

  @Test
  void contextLoads() {}
}
