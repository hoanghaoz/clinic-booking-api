package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

/** Checks what a visitor without a token may do on medical packages, with JWT security enabled. */
@TestPropertySource(
    properties = {
      "app.security.jwt.enabled=true",
      "app.security.jwt.secret=test-only-random-key-0123456789-abcdef"
    })
class MedicalPackageGuestAccessIntegrationTest extends AbstractIntegrationTest {

  private static final String BASE = "/api/v1/medical-packages";

  @Autowired private MedicalPackageService medicalPackageService;
  @Autowired private MedicalItemService medicalItemService;

  @Test
  void list_shouldBeReadableByGuests() {
    newPackage();

    assertThat(status(HttpMethod.GET, BASE)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void detail_shouldBeReadableByGuests() {
    MedicalPackageResponseDto created = newPackage();

    assertThat(status(HttpMethod.GET, BASE + "/" + created.publicId())).isEqualTo(HttpStatus.OK);
  }

  @Test
  void versionsAndSpecialties_shouldStillRequireLogin() {
    String detail = BASE + "/" + newPackage().publicId();

    assertThat(status(HttpMethod.GET, detail + "/versions")).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(status(HttpMethod.GET, detail + "/versions/v1.0"))
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(status(HttpMethod.GET, detail + "/specialties")).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void writes_shouldStillRequireLogin() {
    String detail = BASE + "/" + newPackage().publicId();

    assertThat(status(HttpMethod.POST, BASE)).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(status(HttpMethod.PUT, detail)).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  private HttpStatus status(HttpMethod method, String url) {
    return HttpStatus.valueOf(
        restTemplate.exchange(url, method, null, String.class).getStatusCode().value());
  }

  private MedicalPackageResponseDto newPackage() {
    MedicalItemResponseDto item =
        medicalItemService.createMedicalItem(
            new MedicalItemCreateDto(
                "Item " + UUID.randomUUID(), MedicalItemType.LAB, BigDecimal.TEN, null, null));
    String tag = UUID.randomUUID().toString().substring(0, 8);
    return medicalPackageService.createMedicalPackage(
        new MedicalPackageCreateDto(
            "PUB" + tag,
            "Package " + tag,
            null,
            null,
            null,
            null,
            null,
            PackageStatus.OPENING,
            new BigDecimal("500000"),
            PackageClass.STANDARD,
            20,
            new MedicalPackageItemAddListDto(
                List.of(new MedicalPackageItemAddDto(item.publicId(), 1, 1))),
            Instant.parse("2026-01-01T00:00:00Z")));
  }
}
