package com.se100.clinic.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateBatchDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemUpdateDto;
import com.se100.clinic.catalog.enums.MedicalItemType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MedicalItemDtoValidationTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  private static MedicalItemCreateDto valid(String name) {
    return new MedicalItemCreateDto(name, MedicalItemType.LAB, BigDecimal.TEN, null, null);
  }

  private static List<String> paths(Set<ConstraintViolation<MedicalItemCreateBatchDto>> found) {
    return found.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
  }

  @Test
  void batch_shouldReportIndexOfInvalidElement() {
    var batch =
        new MedicalItemCreateBatchDto(
            List.of(
                valid("A"),
                new MedicalItemCreateDto(
                    "B", MedicalItemType.LAB, new BigDecimal("-1"), null, null),
                new MedicalItemCreateDto(" ", null, BigDecimal.ONE, null, null)));

    assertThat(paths(validator.validate(batch)))
        .containsExactly("items[1].price", "items[2].name", "items[2].type");
  }

  @Test
  void batch_shouldRejectEmptyAndOversizedLists() {
    assertThat(validator.validate(new MedicalItemCreateBatchDto(List.of()))).hasSize(1);
    assertThat(validator.validate(new MedicalItemCreateBatchDto(null))).hasSize(1);

    var tooMany =
        new MedicalItemCreateBatchDto(
            IntStream.range(0, 101).mapToObj(i -> valid("Item " + i)).toList());
    assertThat(validator.validate(tooMany)).hasSize(1);
  }

  @Test
  void batch_shouldRejectNullElement() {
    var batch = new MedicalItemCreateBatchDto(Collections.singletonList(null));

    assertThat(paths(validator.validate(batch))).containsExactly("items[0].<list element>");
  }

  @Test
  void search_shouldRejectKeywordLongerThan200Characters() {
    var tooLong = new MedicalItemSearchRequestDto("a".repeat(201), null, null, null, null);
    var ok = new MedicalItemSearchRequestDto("a".repeat(200), null, null, null, null);

    assertThat(validator.validate(tooLong))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("keyword");
    assertThat(validator.validate(ok)).isEmpty();
  }

  @Test
  void update_shouldAcceptValidAndReportEachInvalidField() {
    var ok = new MedicalItemUpdateDto("Name", MedicalItemType.LAB, BigDecimal.ZERO, null, null);
    var bad = new MedicalItemUpdateDto(" ", null, new BigDecimal("-1"), null, null);
    var tooLong =
        new MedicalItemUpdateDto("a".repeat(201), MedicalItemType.LAB, BigDecimal.ONE, null, null);

    assertThat(validator.validate(ok)).isEmpty();
    assertThat(validator.validate(bad))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactlyInAnyOrder("name", "type", "price");
    assertThat(validator.validate(tooLong))
        .extracting(v -> v.getPropertyPath().toString())
        .containsExactly("name");
  }
}
