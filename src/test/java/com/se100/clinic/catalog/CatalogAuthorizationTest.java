package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
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

/** Checks role enforcement on catalog endpoints with the real method-security proxy. */
@SpringJUnitConfig(CatalogAuthorizationTest.TestConfig.class)
class CatalogAuthorizationTest {
  @Autowired private MedicalItemController items;
  @Autowired private MedicalPackageController packages;
  @Autowired private MedicalItemService itemService;
  @Autowired private MedicalPackageService packageService;

  @BeforeEach
  void resetMocks() {
    reset(itemService, packageService);
  }

  @Test
  @WithMockUser(roles = "MEDICAL_STAFF")
  void medicalStaffCannotActivateMedicalItem() {
    assertThatThrownBy(() -> items.activateMedicalItem(UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(itemService);
  }

  @Test
  @WithMockUser(roles = "PATIENT")
  void patientCannotRemovePackageItem() {
    assertThatThrownBy(() -> packages.removePackageItem(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(packageService);
  }

  @Test
  @WithAnonymousUser
  void anonymousUserCannotReadPackage() {
    assertThatThrownBy(() -> packages.getMedicalPackage(UUID.randomUUID()))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(packageService);
  }

  @Test
  @WithMockUser(roles = "PATIENT")
  void patientCanReadPackage() {
    packages.getMedicalPackage(UUID.randomUUID());
  }

  @Configuration
  @EnableMethodSecurity
  static class TestConfig {
    @Bean
    MedicalItemService itemService() {
      return mock(MedicalItemService.class);
    }

    @Bean
    MedicalPackageService packageService() {
      return mock(MedicalPackageService.class);
    }

    @Bean
    MedicalItemController itemController(MedicalItemService service) {
      return new MedicalItemController(service);
    }

    @Bean
    MedicalPackageController packageController(MedicalPackageService service) {
      return new MedicalPackageController(service);
    }
  }
}
