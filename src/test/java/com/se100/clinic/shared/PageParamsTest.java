package com.se100.clinic.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageParamsTest {

  private static final Set<String> SORTABLE = Set.of("name", "code", "createdAt");
  private static final Sort DEFAULT_SORT = Sort.by("name");

  private static Pageable resolve(Integer page, Integer size, List<String> sort) {
    return new PageParams(page, size, sort).toPageable(SORTABLE, DEFAULT_SORT);
  }

  private static BusinessException rejected(Integer page, Integer size, List<String> sort) {
    var thrown =
        assertThatThrownBy(() -> resolve(page, size, sort)).isInstanceOf(BusinessException.class);
    return (BusinessException) thrown.actual();
  }

  @Test
  void absentParams_shouldUseDefaults_andFirstPageIsIndexZero() {
    Pageable pageable = resolve(null, null, null);

    assertThat(pageable.getPageNumber()).isZero();
    assertThat(pageable.getPageSize()).isEqualTo(20);
    assertThat(pageable.getSort().toList())
        .extracting(o -> o.getProperty() + ":" + o.getDirection())
        .containsExactly("name:ASC", "id:ASC");
  }

  @Test
  void page_isOneBased_soPage3IsIndex2() {
    assertThat(resolve(3, 10, null).getPageNumber()).isEqualTo(2);
  }

  @Test
  void size_acceptsMax() {
    assertThat(resolve(1, 100, null).getPageSize()).isEqualTo(100);
  }

  @Test
  void page0_isRejected_notSilentlyCorrected() {
    BusinessException ex = rejected(0, null, null);

    assertThat(ex.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    assertThat(ex.getViolations()).extracting(FieldViolation::field).containsExactly("page");
  }

  @Test
  void sizeOutOfRange_isRejectedNotClamped() {
    assertThat(rejected(1, 101, null).getViolations())
        .extracting(FieldViolation::field)
        .containsExactly("size");
    assertThat(rejected(1, 0, null).getViolations())
        .extracting(FieldViolation::field)
        .containsExactly("size");
  }

  @Test
  void sort_prefixMinusMeansDescending_andIdTieBreakerIsAppended() {
    Pageable pageable = resolve(1, 20, List.of("-createdAt", "name"));

    assertThat(pageable.getSort().toList())
        .extracting(o -> o.getProperty() + ":" + o.getDirection())
        .containsExactly("createdAt:DESC", "name:ASC", "id:ASC");
  }

  @Test
  void sort_explicitIdIsNotDuplicated() {
    // "id" is not whitelisted here, so allow it for this one case.
    Pageable pageable =
        new PageParams(1, 20, List.of("-id")).toPageable(Set.of("id", "name"), DEFAULT_SORT);

    assertThat(pageable.getSort().toList())
        .extracting(o -> o.getProperty() + ":" + o.getDirection())
        .containsExactly("id:DESC");
  }

  @Test
  void sort_unknownField_isRejected_andMessageListsAllowedFields() {
    BusinessException ex = rejected(1, 20, List.of("password"));

    assertThat(ex.getViolations()).hasSize(1);
    assertThat(ex.getViolations().get(0).field()).isEqualTo("sort");
    assertThat(ex.getViolations().get(0).message())
        .contains("password")
        .contains("code, createdAt, name");
  }

  @Test
  void sort_duplicateField_isRejected() {
    assertThat(rejected(1, 20, List.of("name", "-name")).getViolations())
        .extracting(FieldViolation::field)
        .containsExactly("sort");
  }

  @Test
  void multipleProblems_areAllReportedAtOnce() {
    assertThat(rejected(0, 500, List.of("nope")).getViolations())
        .extracting(FieldViolation::field)
        .containsExactly("page", "size", "sort");
  }
}
