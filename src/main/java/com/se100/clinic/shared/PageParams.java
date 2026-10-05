package com.se100.clinic.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * The standard {@code page}/{@code size}/{@code sort} query parameters of every list endpoint.
 * Declare it (annotated with springdoc's {@code @ParameterObject}) as a controller parameter, then
 * call {@link #toPageable} with the fields that endpoint allows sorting by.
 *
 * <ul>
 *   <li>{@code page}: 1-based, default 1.
 *   <li>{@code size}: default {@value #DEFAULT_SIZE}, max {@value #MAX_SIZE}. Larger values are
 *       REJECTED (400), not silently clamped, so the frontend never misreads {@code meta.size}.
 *   <li>{@code sort}: comma-separated fields, {@code -} prefix = descending, e.g. {@code
 *       -createdAt,name}. A field outside the endpoint's whitelist is rejected (400). Sorting is
 *       always made stable by appending {@code id ASC}.
 * </ul>
 *
 * Any violation throws a {@link BusinessException} with {@link CommonErrorCode#VALIDATION_ERROR}
 * and one {@code errors[]} entry per bad parameter.
 */
public record PageParams(
    @Schema(description = "Trang, bắt đầu từ 1", defaultValue = "1", minimum = "1") Integer page,
    @Schema(
            description = "Số phần tử mỗi trang",
            defaultValue = "20",
            minimum = "1",
            maximum = "100")
        Integer size,
    @Schema(
            description =
                "Sắp xếp: các field phân tách bằng dấu phẩy, tiền tố '-' = giảm dần."
                    + " Chỉ các field mà endpoint cho phép.",
            example = "-createdAt,name")
        List<String> sort) {

  public static final int FIRST_PAGE = 1;
  public static final int DEFAULT_SIZE = 20;
  public static final int MAX_SIZE = 100;

  private static final String TIE_BREAKER = "id";

  /**
   * @param sortableFields API field names (they must equal the entity property names) this endpoint
   *     may be sorted by
   * @param defaultSort used when {@code sort} is absent
   */
  public Pageable toPageable(Set<String> sortableFields, Sort defaultSort) {
    List<FieldViolation> violations = new ArrayList<>();

    int pageNumber = page == null ? FIRST_PAGE : page;
    if (pageNumber < FIRST_PAGE) {
      violations.add(new FieldViolation("page", "page phải >= " + FIRST_PAGE));
    }
    int pageSize = size == null ? DEFAULT_SIZE : size;
    if (pageSize < 1 || pageSize > MAX_SIZE) {
      violations.add(new FieldViolation("size", "size phải trong khoảng 1.." + MAX_SIZE));
    }
    Sort resolvedSort = parseSort(sortableFields, defaultSort, violations);

    if (!violations.isEmpty()) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR, "Tham số truy vấn không hợp lệ", violations);
    }
    return PageRequest.of(pageNumber - FIRST_PAGE, pageSize, withTieBreaker(resolvedSort));
  }

  private Sort parseSort(
      Set<String> sortableFields, Sort defaultSort, List<FieldViolation> violations) {
    if (sort == null || sort.isEmpty()) {
      return defaultSort;
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String token : sort) {
      String trimmed = token == null ? "" : token.trim();
      boolean descending = trimmed.startsWith("-");
      String field = descending ? trimmed.substring(1).trim() : trimmed;
      if (!sortableFields.contains(field)) {
        violations.add(
            new FieldViolation(
                "sort",
                "Không hỗ trợ sắp xếp theo '"
                    + field
                    + "'. Các field cho phép: "
                    + String.join(", ", sortableFields.stream().sorted().toList())));
      } else if (orders.stream().anyMatch(order -> order.getProperty().equals(field))) {
        violations.add(new FieldViolation("sort", "Field '" + field + "' bị lặp trong sort"));
      } else {
        orders.add(descending ? Sort.Order.desc(field) : Sort.Order.asc(field));
      }
    }
    return Sort.by(orders);
  }

  private static Sort withTieBreaker(Sort sort) {
    return sort.getOrderFor(TIE_BREAKER) == null ? sort.and(Sort.by(TIE_BREAKER)) : sort;
  }
}
