package com.se100.clinic.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ApiResultTest {

  @Test
  void of_singleObject_hasNoMeta() {
    ApiResult<String> result = ApiResult.of("x");

    assertThat(result.data()).isEqualTo("x");
    assertThat(result.meta()).isNull();
  }

  @Test
  void of_page_convertsToOneBasedMeta() {
    // Spring page index 1 (second page), size 2, 5 elements total -> 3 pages.
    var page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

    ApiResult<List<String>> result = ApiResult.of(page);

    assertThat(result.data()).containsExactly("c", "d");
    assertThat(result.meta()).isEqualTo(new ApiResult.PageMeta(2, 2, 5, 3));
  }

  @Test
  void of_emptyPage_hasZeroTotals() {
    var page = new PageImpl<String>(List.of(), PageRequest.of(0, 20), 0);

    assertThat(ApiResult.of(page).meta()).isEqualTo(new ApiResult.PageMeta(1, 20, 0, 0));
  }
}
