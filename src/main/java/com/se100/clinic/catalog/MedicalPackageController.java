package com.se100.clinic.catalog;

import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/medical-packages")
@RequiredArgsConstructor
@Tag(name = "Gói khám", description = "Quản lý gói khám và các phiên bản của gói")
class MedicalPackageController {

  private final MedicalPackageService service;

  @PostMapping
  @Operation(summary = "Tạo gói khám (phiên bản đầu tiên v1.0)")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới tài nguyên mới")
  @ApiResponse(
      responseCode = "409",
      description = "Mã gói khám đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<MedicalPackageResponseDto>> createMedicalPackage(
      @Valid @RequestBody MedicalPackageCreateDto request) {
    MedicalPackageResponseDto created = service.createMedicalPackage(request);
    return ResponseEntity.created(URI.create("/api/v1/medical-packages/" + created.publicId()))
        .body(ApiResult.of(created));
  }

  @PutMapping("/{packagePublicId}")
  @Operation(
      summary = "Cập nhật gói khám",
      description =
          "Không sửa phiên bản cũ: tạo phiên bản mới, trường bỏ trống giữ nguyên giá trị hiện tại.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalPackageResponseDto> updateMedicalPackage(
      @PathVariable UUID packagePublicId, @Valid @RequestBody MedicalPackageUpdateDto request) {
    return ApiResult.of(service.updateMedicalPackage(request, packagePublicId));
  }

  @GetMapping
  @Operation(
      summary = "Tìm kiếm / danh sách gói khám (phân trang)",
      description =
          "Chỉ trả phiên bản hiện hành của mỗi gói. Sắp xếp được theo: code, name, status,"
              + " unitPrice, createdAt. Mặc định: name tăng dần. `meta.page` bắt đầu từ 1.")
  ApiResult<List<MedicalPackageResponseDto>> searchMedicalPackages(
      @ParameterObject MedicalPackageSearchRequestDto search,
      @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(
            MedicalPackageService.SORTABLE_FIELDS, MedicalPackageService.DEFAULT_SORT);
    return ApiResult.of(service.list(search, pageable));
  }
}
