package com.se100.clinic.doctor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Checks role enforcement on doctor and specialty writes with the real method-security proxy. */
@SpringJUnitConfig(DoctorAuthorizationTest.TestConfig.class)
class DoctorAuthorizationTest {
  @Autowired private DoctorController doctors;
  @Autowired private SpecialtyController specialties;
  @Autowired private DoctorService doctorService;
  @Autowired private SpecialtyService specialtyService;

  @BeforeEach
  void resetMocks() {
    reset(doctorService, specialtyService);
  }

  @Test
  @WithMockUser(roles = "PATIENT")
  void patientCannotChangeDoctorStatus() {
    assertThatThrownBy(() -> doctors.changeStatus(1L, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(doctorService);
  }

  @Test
  @WithMockUser(roles = "DOCTOR")
  void doctorCannotDeactivateSpecialty() {
    assertThatThrownBy(() -> specialties.deactivate(1L)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(specialtyService);
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  void adminCanDeactivateSpecialty() {
    specialties.deactivate(1L);
  }

  @Test
  @WithAnonymousUser
  void anonymousUserCanReadSpecialties() {
    // Reads carry no method-level restriction; the public GET matcher lives in SecurityConfig.
    specialties.getById(1L);
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    DoctorService doctorService() {
      return mock(DoctorService.class);
    }

    @Bean
    SpecialtyService specialtyService() {
      return mock(SpecialtyService.class);
    }

    @Bean
    DoctorController doctorController(DoctorService service) {
      return new DoctorController(service);
    }

    @Bean
    SpecialtyController specialtyController(SpecialtyService service) {
      return new SpecialtyController(service);
    }
  }
}
