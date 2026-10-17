package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Real Postgres: checks that creating a package and rolling it over to a new version persist. */
class MedicalPackageServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired private MedicalPackageService medicalPackageService;

  // TODO(specialties): also @Autowired SpecialtyService, create two specialties, pass their ids
  // in the create dto, and assert response.specialtyIds() after create and after update (the
  // composite-key PackageSpeciality rows must persist and survive the version rollover).

  @Test
  void createThenUpdate_shouldPersistNextVersion() {
    String tag = UUID.randomUUID().toString().substring(0, 8);

    MedicalPackageResponseDto created =
        medicalPackageService.createMedicalPackage(
            new MedicalPackageCreateDto(
                "PKG" + tag,
                "Package " + tag,
                null,
                null,
                null,
                null,
                null,
                PackageStatus.OPENING,
                new BigDecimal("1000000"),
                PackageClass.STANDARD,
                30,
                Instant.parse("2026-01-01T00:00:00Z")));

    MedicalPackageResponseDto updated =
        medicalPackageService.updateMedicalPackage(
            new MedicalPackageUpdateDto(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("2000000"),
                null,
                null,
                null),
            created.publicId());

    assertThat(updated.publicId()).isEqualTo(created.publicId());
    assertThat(updated.code()).isEqualTo("PKG" + tag);
    assertThat(updated.price()).isEqualByComparingTo("2000000");
  }
}
