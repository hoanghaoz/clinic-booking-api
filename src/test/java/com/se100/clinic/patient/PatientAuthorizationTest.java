package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.shared.PageParams;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Checks the role gates on patient endpoints with the real method-security proxy. */
@SpringJUnitConfig(PatientAuthorizationTest.TestConfig.class)
class PatientAuthorizationTest {

  private static final UUID ID = UUID.randomUUID();

  @Autowired private PatientController patients;
  @Autowired private PatientMedicalHistoryController histories;
  @Autowired private PatientService patientService;
  @Autowired private PatientMedicalHistoryService historyService;
  @Autowired private PatientActorResolver actors;

  @BeforeEach
  void resetMocks() {
    reset(patientService, historyService, actors);
    when(actors.resolve(any())).thenReturn(new PatientActor(1L, UserRole.PATIENT));
  }

  @Test
  @WithMockUser(roles = "COMPANY")
  void companyCannotSearchPatients() {
    assertThatThrownBy(
            () -> patients.search(null, "0901234567", null, new PageParams(null, null, null)))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = "COMPANY")
  void companyCannotOpenProfileOrCard() {
    assertThatThrownBy(() -> patients.getByPublicId(null, ID))
        .isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> patients.card(null, ID)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "DOCTOR", "COMPANY"})
  void onlyPatientAndStaffCanCreateProfiles_adminDoctorCompanyCannot() {
    assertThatThrownBy(() -> patients.create(null, null)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> patients.update(null, ID, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = "DOCTOR")
  void doctorCannotReadCards() {
    assertThatThrownBy(() -> patients.card(null, ID)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> patients.cardValidity(null, ID))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = {"PATIENT", "MEDICAL_STAFF", "DOCTOR"})
  void onlyAdminCanChangeClass() {
    assertThatThrownBy(() -> patients.changeClass(null, ID, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = {"MEDICAL_STAFF", "DOCTOR", "ADMIN", "COMPANY"})
  void onlyPatientCanOpenMe() {
    // ADMIN/DOCTOR/STAFF/COMPANY alone are rejected; the mock user holds no PATIENT role.
    assertThatThrownBy(() -> patients.me(null)).isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(patientService);
  }

  @Test
  @WithMockUser(roles = "PATIENT")
  void patientCanOpenMe() {
    patients.me(null);
  }

  @Test
  @WithMockUser(roles = {"ADMIN", "COMPANY"})
  void adminAndCompanyCannotTouchMedicalHistory() {
    assertThatThrownBy(() -> histories.summary(null, ID)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> histories.update(null, 1L, null))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(historyService);
  }

  @Test
  @WithMockUser(roles = "DOCTOR")
  void doctorCanReadMedicalHistorySummary() {
    histories.summary(null, ID);
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    PatientService patientService() {
      return mock(PatientService.class);
    }

    @Bean
    PatientMedicalHistoryService historyService() {
      return mock(PatientMedicalHistoryService.class);
    }

    @Bean
    PatientActorResolver actorResolver() {
      return mock(PatientActorResolver.class);
    }

    @Bean
    PatientController patientController(PatientService service, PatientActorResolver resolver) {
      return new PatientController(service, resolver);
    }

    @Bean
    PatientMedicalHistoryController historyController(
        PatientMedicalHistoryService service, PatientActorResolver resolver) {
      return new PatientMedicalHistoryController(service, resolver);
    }
  }
}
