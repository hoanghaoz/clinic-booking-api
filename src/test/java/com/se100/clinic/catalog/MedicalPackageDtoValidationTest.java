package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddListDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MedicalPackageDtoValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static MedicalPackageItemAddListDto items() {
    return new MedicalPackageItemAddListDto(
        List.of(new MedicalPackageItemAddDto(UUID.randomUUID(), 1, 1)));
  }

  private static MedicalPackageCreateDto valid() {
    return create("HPV3", "HPV package", 9, 26, new BigDecimal("100.50"), 30);
  }

  private static MedicalPackageCreateDto create(
      String code, String name, Integer minAge, Integer maxAge, BigDecimal price, Integer minutes) {
    return new MedicalPackageCreateDto(
        code,
        name,
        null,
        null,
        minAge,
        maxAge,
        null,
        PackageStatus.OPENING,
        price,
        PackageClass.STANDARD,
        minutes,
        items(),
        null);
  }

  private static List<String> paths(Set<? extends ConstraintViolation<?>> found) {
    return found.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
  }

  @Test
  void create_shouldAccept_whenOnlyRequiredFieldsAreSet() {
    assertThat(validator.validate(valid())).isEmpty();
    assertThat(validator.validate(create("A", "B", null, null, BigDecimal.ZERO, 1))).isEmpty();
  }

  @Test
  void create_shouldReportEveryMissingRequiredField() {
    var dto =
        new MedicalPackageCreateDto(
            " ", null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(paths(validator.validate(dto)))
        .containsExactly(
            "code",
            "consultationMinutes",
            "medicalItems",
            "name",
            "packageClass",
            "price",
            "status");
  }

  @Test
  void create_shouldRejectOutOfRangeValues() {
    var dto = create("C".repeat(41), "N".repeat(201), -1, 151, new BigDecimal("-1"), 0);

    assertThat(paths(validator.validate(dto)))
        .containsExactly("code", "consultationMinutes", "maxAge", "minAge", "name", "price");
  }

  @Test
  void create_shouldRejectPriceBeyondColumnPrecision() {
    assertThat(paths(validator.validate(create("A", "B", null, null, new BigDecimal("1.234"), 30))))
        .containsExactly("price");
    assertThat(
            paths(
                validator.validate(
                    create("A", "B", null, null, new BigDecimal("12345678901234"), 30))))
        .containsExactly("price");
  }

  @Test
  void create_shouldValidateItemsInsideMedicalItems() {
    var dto =
        new MedicalPackageCreateDto(
            "A",
            "B",
            null,
            null,
            null,
            null,
            null,
            PackageStatus.OPENING,
            BigDecimal.ONE,
            PackageClass.STANDARD,
            30,
            new MedicalPackageItemAddListDto(
                List.of(new MedicalPackageItemAddDto(UUID.randomUUID(), 0, 1))),
            null);

    assertThat(paths(validator.validate(dto))).containsExactly("medicalItems.items[0].quantity");
  }

  @Test
  void update_shouldAccept_whenEveryFieldIsMissing() {
    var dto =
        new MedicalPackageUpdateDto(
            null, null, null, null, null, null, null, null, null, null, null, null);

    assertThat(validator.validate(dto)).isEmpty();
  }

  @Test
  void update_shouldRejectInvalidValuesThatAreSet() {
    var dto =
        new MedicalPackageUpdateDto(
            "N".repeat(201),
            null,
            null,
            -1,
            151,
            null,
            null,
            new BigDecimal("-1"),
            null,
            0,
            null,
            null);

    assertThat(paths(validator.validate(dto)))
        .containsExactly("consultationMinutes", "maxAge", "minAge", "name", "price");
  }

  @Test
  void search_shouldRejectOverlongKeyword() {
    var request =
        new MedicalPackageSearchRequestDto(
            "k".repeat(201), null, null, null, null, null, null, null);

    assertThat(paths(validator.validate(request))).containsExactly("keyword");
  }
}
