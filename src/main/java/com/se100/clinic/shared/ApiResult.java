package com.se100.clinic.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The single envelope for every SUCCESSFUL response body: the payload is always under {@code data}.
 * List endpoints also fill {@code meta} with pagination info; single-object endpoints omit it.
 *
 * <pre>{@code
 * { "data": { "id": 1, "code": "NHI" } }
 * { "data": [ {...}, {...} ], "meta": { "page": 1, "size": 20, "totalElements": 53, "totalPages": 3 } }
 * }</pre>
 *
 * Errors do NOT use this type — they are RFC 9457 {@code ProblemDetail} bodies. {@code DELETE}
 * returns 204 with no body. See docs/conventions/api-conventions.md.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResult<T>(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) T data,
    @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) PageMeta meta) {

  /** Wrap a single object (or any non-paged payload). */
  public static <T> ApiResult<T> of(T data) {
    return new ApiResult<>(data, null);
  }

  /**
   * Wrap one page of a list. {@code meta.page} is 1-based, unlike Spring's 0-based {@link Page}.
   */
  public static <T> ApiResult<List<T>> of(Page<T> page) {
    return new ApiResult<>(
        page.getContent(),
        new PageMeta(
            page.getNumber() + PageParams.FIRST_PAGE,
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()));
  }

  /** Pagination metadata of a list response. */
  @Schema(requiredProperties = {"page", "size", "totalElements", "totalPages"})
  public record PageMeta(int page, int size, long totalElements, int totalPages) {}
}
