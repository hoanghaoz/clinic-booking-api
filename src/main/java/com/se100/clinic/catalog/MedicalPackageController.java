package com.se100.clinic.catalog;

import com.se100.clinic.catalog.CatalogDtos.MedicalPackageCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemAddDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageItemUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageSearchRequestDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalPackageUpdateDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionDetailResponseDto;
import com.se100.clinic.catalog.CatalogDtos.PackageVersionResponseDto;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
              + " price, createdAt. Mặc định: name tăng dần. `meta.page` bắt đầu từ 1.")
  ApiResult<List<MedicalPackageResponseDto>> searchMedicalPackages(
      @ParameterObject @Valid MedicalPackageSearchRequestDto search,
      @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(
            MedicalPackageService.SORTABLE_FIELDS, MedicalPackageService.DEFAULT_SORT);
    return ApiResult.of(service.list(search, pageable));
  }

  @GetMapping("/{packagePublicId}")
  @Operation(
      summary = "Chi tiết gói khám",
      description = "Phiên bản hiện hành của gói, kèm danh sách hạng mục theo thứ tự hiển thị.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalPackageDetailResponseDto> getMedicalPackage(@PathVariable UUID packagePublicId) {
    return ApiResult.of(service.getMedicalPackage(packagePublicId));
  }

  @GetMapping("/{packagePublicId}/versions")
  @Operation(
      summary = "Danh sách phiên bản của gói khám (phân trang)",
      description =
          "Mọi phiên bản của gói, không kèm hạng mục (xem chi tiết một phiên bản để lấy hạng"
              + " mục). Sắp xếp được theo: effectiveFrom, createdAt. Mặc định: effectiveFrom giảm"
              + " dần (mới nhất trước). `current` = true là phiên bản đang hiệu lực.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<List<PackageVersionResponseDto>> listVersions(
      @PathVariable UUID packagePublicId, @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(
            MedicalPackageService.VERSION_SORTABLE_FIELDS,
            MedicalPackageService.VERSION_DEFAULT_SORT);
    return ApiResult.of(service.listVersions(packagePublicId, pageable));
  }

  @GetMapping("/{packagePublicId}/versions/{version}")
  @Operation(
      summary = "Chi tiết một phiên bản của gói khám",
      description =
          "`version` có dạng như `v1.0`. Trả kèm danh sách hạng mục theo thứ tự hiển thị, đúng"
              + " như lúc phiên bản được tạo (tên và hướng dẫn là bản chụp).")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND hoặc MEDICAL_PACKAGE_VERSION_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PackageVersionDetailResponseDto> getVersion(
      @PathVariable UUID packagePublicId, @PathVariable String version) {
    return ApiResult.of(service.getVersion(packagePublicId, version));
  }

  @PostMapping("/{packagePublicId}/items")
  @Operation(
      summary = "Thêm một hạng mục vào gói khám",
      description =
          "Không sửa phiên bản cũ: tạo phiên bản mới gồm các hạng mục hiện có cộng hạng mục này."
              + " Trả phiên bản mới; header Location trỏ tới phiên bản đó.")
  @ApiResponse(responseCode = "201", description = "Đã tạo phiên bản mới của gói")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND hoặc MEDICAL_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "MEDICAL_PACKAGE_ITEM_EXISTS (hạng mục đã có trong gói),"
              + " MEDICAL_PACKAGE_ITEM_POSITION_TAKEN (vị trí đã bị chiếm) hoặc"
              + " MEDICAL_ITEM_INACTIVE (hạng mục đã ngưng hoạt động)",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<PackageVersionDetailResponseDto>> addPackageItem(
      @PathVariable UUID packagePublicId, @Valid @RequestBody MedicalPackageItemAddDto request) {
    PackageVersionDetailResponseDto created = service.addPackageItem(packagePublicId, request);
    return ResponseEntity.created(
            URI.create(
                "/api/v1/medical-packages/" + packagePublicId + "/versions/" + created.version()))
        .body(ApiResult.of(created));
  }

  @PutMapping("/{packagePublicId}/items/{medicalItemPublicId}")
  @Operation(
      summary = "Đổi số lượng / vị trí của một hạng mục trong gói khám",
      description =
          "Tạo phiên bản mới của gói và trả phiên bản đó. Nếu số lượng và vị trí không đổi thì"
              + " không tạo phiên bản mới, trả lại phiên bản hiện hành.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND hoặc MEDICAL_PACKAGE_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description = "MEDICAL_PACKAGE_ITEM_POSITION_TAKEN — vị trí đã có hạng mục khác",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PackageVersionDetailResponseDto> updatePackageItem(
      @PathVariable UUID packagePublicId,
      @PathVariable UUID medicalItemPublicId,
      @Valid @RequestBody MedicalPackageItemUpdateDto request) {
    return ApiResult.of(service.updatePackageItem(packagePublicId, medicalItemPublicId, request));
  }

  @DeleteMapping("/{packagePublicId}/items/{medicalItemPublicId}")
  @Operation(
      summary = "Bớt một hạng mục khỏi gói khám",
      description =
          "Tạo phiên bản mới không có hạng mục này và trả phiên bản đó (200, có body) để màn hình"
              + " cập nhật ngay. Phiên bản cũ giữ nguyên hạng mục.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_PACKAGE_NOT_FOUND hoặc MEDICAL_PACKAGE_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description = "MEDICAL_PACKAGE_ITEMS_REQUIRED — không bớt được hạng mục cuối cùng",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PackageVersionDetailResponseDto> removePackageItem(
      @PathVariable UUID packagePublicId, @PathVariable UUID medicalItemPublicId) {
    return ApiResult.of(service.removePackageItem(packagePublicId, medicalItemPublicId));
  }
}
