package com.se100.clinic.catalog;

import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateBatchDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemCreateDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemResponseDto;
import com.se100.clinic.catalog.CatalogDtos.MedicalItemUpdateDto;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/medical-items")
@RequiredArgsConstructor
@Tag(
    name = "Hạng mục y tế",
    description = "Danh mục hạng mục dùng để cấu thành gói khám và dịch vụ")
class MedicalItemController {
  private final MedicalItemService medicalItemService;

  @PostMapping
  @Operation(summary = "Tạo một hạng mục y tế")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới tài nguyên mới")
  @ApiResponse(
      responseCode = "409",
      description = "MEDICAL_ITEM_EXISTS — tên hạng mục đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<MedicalItemResponseDto>> createMedicalItem(
      @Valid @RequestBody MedicalItemCreateDto request) {
    MedicalItemResponseDto created = medicalItemService.createMedicalItem(request);
    return ResponseEntity.created(URI.create("/api/v1/medical-items/" + created.publicId()))
        .body(ApiResult.of(created));
  }

  @PostMapping("/batch")
  @Operation(
      summary = "Tạo nhiều hạng mục y tế một lần",
      description =
          "Tối đa 100 hạng mục. Tất cả hoặc không có hạng mục nào được tạo: một phần tử sai hoặc"
              + " trùng tên thì cả lô bị từ chối. Kết quả giữ đúng thứ tự gửi lên.")
  @ApiResponse(responseCode = "201", description = "Đã tạo toàn bộ hạng mục trong lô")
  @ApiResponse(
      responseCode = "409",
      description = "MEDICAL_ITEM_EXISTS — có tên hạng mục đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<List<MedicalItemResponseDto>>> createBatchMedicalItems(
      @Valid @RequestBody MedicalItemCreateBatchDto request) {
    List<MedicalItemResponseDto> created = medicalItemService.createBatchMedicalItems(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(ApiResult.of(created));
  }

  @PutMapping("/{publicId}")
  @Operation(
      summary = "Cập nhật hạng mục y tế",
      description =
          "Thay toàn bộ các trường (mô tả và hướng dẫn gửi null sẽ bị xóa trắng). Các phiên bản"
              + " gói khám đã tạo giữ nguyên tên và hướng dẫn cũ của hạng mục.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description = "MEDICAL_ITEM_EXISTS — tên hạng mục đã tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalItemResponseDto> updateMedicalItem(
      @PathVariable UUID publicId, @Valid @RequestBody MedicalItemUpdateDto request) {
    return ApiResult.of(medicalItemService.updateMedicalItem(publicId, request));
  }

  @DeleteMapping("/{publicId}")
  @Operation(
      summary = "Ngưng hoạt động hạng mục y tế (xóa mềm)",
      description =
          "Hạng mục không bị xóa khỏi dữ liệu và vẫn nằm trong các phiên bản gói khám cũ, nhưng"
              + " không thêm được vào gói khám mới. Gọi lại trên hạng mục đã ngưng vẫn trả 204.")
  @ApiResponse(responseCode = "204", description = "Thành công, KHÔNG có body")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<Void> deactivateMedicalItem(@PathVariable UUID publicId) {
    medicalItemService.deactivateMedicalItem(publicId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{publicId}/activate")
  @Operation(
      summary = "Kích hoạt lại hạng mục y tế đã ngưng",
      description =
          "Hoàn tác thao tác ngưng hoạt động: hạng mục lại thêm được vào gói khám. Gọi lại trên"
              + " hạng mục đang hoạt động vẫn trả 200.")
  @ApiResponse(
      responseCode = "404",
      description = "MEDICAL_ITEM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalItemResponseDto> activateMedicalItem(@PathVariable UUID publicId) {
    return ApiResult.of(medicalItemService.activateMedicalItem(publicId));
  }

  @GetMapping
  @Operation(
      summary = "Tìm kiếm / danh sách hạng mục y tế (phân trang)",
      description =
          "Sắp xếp được theo: name, type, price, active, createdAt. Mặc định: name tăng dần."
              + " Không gửi `active` thì trả cả hạng mục đã ngưng; khi chọn hạng mục cho gói khám"
              + " nên gửi `active=true`.")
  @ApiResponse(responseCode = "200", description = "Kết quả tìm kiếm")
  ApiResult<List<MedicalItemResponseDto>> searchMedicalItems(
      @ParameterObject @Valid CatalogDtos.MedicalItemSearchRequestDto request,
      @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(MedicalItemService.SORTABLE_FIELDS, MedicalItemService.DEFAULT_SORT);
    return ApiResult.of(medicalItemService.searchMedicalItems(request, pageable));
  }
}
