package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Checks role enforcement on the real Spring method-security proxy. */
@SpringJUnitConfig(ScheduleAuthorizationTest.TestConfig.class)
class ScheduleAuthorizationTest {
  @Autowired private ScheduleController controller;
  @Autowired private ScheduleService service;

  @Test
  @WithMockUser(roles = "DOCTOR")
  void doctorCannotCreateAdminAssignment() {
    var request = new SaveScheduleRequest(1L, 2L, 3L, 4L, 5L, LocalDate.of(2026, 11, 3), 12, 8);

    assertThatThrownBy(() -> controller.create(request)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(service);
  }

  @Test
  @WithMockUser(roles = "PATIENT")
  void patientCannotReadDoctorsPatientQueue() {
    assertThatThrownBy(() -> controller.myPatients(null, UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(service);
  }

  @Test
  @WithAnonymousUser
  void anonymousUserCannotInspectAvailability() {
    assertThatThrownBy(
            () ->
                controller.available(
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 11, 3),
                    LocalDate.of(2026, 11, 3),
                    20,
                    null,
                    null))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    ScheduleService service() {
      return mock(ScheduleService.class);
    }

    @Bean
    ScheduleController controller(ScheduleService service) {
      return new ScheduleController(
          service,
          mock(ScheduleAvailabilityService.class),
          mock(AuthenticatedUserService.class),
          mock(DoctorSchedulingService.class));
    }
  }
}
