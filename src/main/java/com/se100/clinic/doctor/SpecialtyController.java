package com.se100.clinic.doctor;

import com.se100.clinic.doctor.SpecialtyDtos.CreateSpecialtyRequest;
import com.se100.clinic.doctor.SpecialtyDtos.SpecialtyResponse;
import com.se100.clinic.doctor.SpecialtyDtos.UpdateSpecialtyRequest;
import com.se100.clinic.shared.ApiDocs;
import com.se100.clinic.shared.ApiResult;
import com.se100.clinic.shared.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * package-private — chỉ định tuyến HTTP và convert request/response, KHÔNG chứa logic nghiệp vụ
 * (logic nằm ở {@link SpecialtyService}). Đây là điểm khác so với nhiều dự án NestJS nhỏ hay nhét
 * logic thẳng vào Controller — ở Spring, quy ước "Controller mỏng, chỉ gọi Service" nên được giữ
 * nghiêm để dễ test Service độc lập bằng unit test (không cần khởi HTTP server).
 *
 * <p>{@code @RestController} = {@code @Controller} + {@code @ResponseBody} — tương đương
 * {@code @Controller()} của NestJS (NestJS mặc định trả JSON, không cần decorator riêng), hoặc
 * {@code [ApiController]} của ASP.NET Core.
 *
 * <p><b>Quy ước response (xem docs/conventions/api-conventions.md):</b> mọi response thành công bọc
 * trong {@link ApiResult} ({@code {"data": ...}}, danh sách thêm {@code meta}); DELETE trả 204
 * không body; lỗi do {@code GlobalExceptionHandler} trả (ProblemDetail + {@code code}), controller
 * không tự dựng body lỗi. Swagger: chỉ cần ghi {@code @ApiResponse} cho các lỗi riêng của endpoint
 * (404/409...) — 400 và 500 được thêm tự động.
 */
@RestController
@RequestMapping("/api/v1/specialties")
@Tag(name = "Chuyên khoa", description = "Module mẫu `doctor` — CRUD chuyên khoa")
class SpecialtyController {

  private final SpecialtyService specialtyService;

  SpecialtyController(SpecialtyService specialtyService) {
    this.specialtyService = specialtyService;
  }

  @PostMapping
  @Operation(summary = "Tạo chuyên khoa")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới tài nguyên mới")
  @ApiResponse(
      responseCode = "409",
      description = "SPECIALTY_CODE_EXISTS — mã chuyên khoa đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<SpecialtyResponse>> create(
      @Valid @RequestBody CreateSpecialtyRequest request) {
    SpecialtyResponse created = specialtyService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/specialties/" + created.id()))
        .body(ApiResult.of(created));
  }

  @GetMapping
  @Operation(
      summary = "Danh sách chuyên khoa (phân trang)",
      description =
          "Sắp xếp được theo: code, name, active, createdAt. Mặc định: name tăng dần."
              + " `meta.page` bắt đầu từ 1.")
  ApiResult<List<SpecialtyResponse>> list(
      @Parameter(
              description = "Tìm theo tên hoặc mã chuyên khoa (chứa, không phân biệt hoa thường)")
          @RequestParam(required = false)
          String keyword,
      @Parameter(description = "Lọc theo trạng thái hoạt động; bỏ trống = tất cả")
          @RequestParam(required = false)
          Boolean active,
      @ParameterObject PageParams pageParams) {
    var pageable =
        pageParams.toPageable(SpecialtyService.SORTABLE_FIELDS, SpecialtyService.DEFAULT_SORT);
    return ApiResult.of(specialtyService.list(keyword, active, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Chi tiết chuyên khoa")
  @ApiResponse(
      responseCode = "404",
      description = "SPECIALTY_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<SpecialtyResponse> getById(@PathVariable Long id) {
    return ApiResult.of(specialtyService.getById(id));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Cập nhật tên/mô tả chuyên khoa (mã không đổi được)")
  @ApiResponse(
      responseCode = "404",
      description = "SPECIALTY_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<SpecialtyResponse> update(
      @PathVariable Long id, @Valid @RequestBody UpdateSpecialtyRequest request) {
    return ApiResult.of(specialtyService.update(id, request));
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Ngưng hoạt động chuyên khoa (xoá mềm)")
  @ApiResponse(responseCode = "204", description = "Thành công, KHÔNG có body")
  @ApiResponse(
      responseCode = "404",
      description = "SPECIALTY_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<Void> deactivate(@PathVariable Long id) {
    specialtyService.deactivate(id);
    return ResponseEntity.noContent().build();
  }
}
