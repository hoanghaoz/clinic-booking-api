package com.se100.clinic.patient;

import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryCreateRequest;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryResponse;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryUpdateRequest;
import com.se100.clinic.patient.enums.MedicalHistoryType;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * package-private — HTTP routing only; see {@link PatientMedicalHistoryService} for the rules.
 * Medical data: never log request or response bodies here.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('PATIENT','DOCTOR','MEDICAL_STAFF')")
@Tag(name = "Tiền sử bệnh", description = "Tiền sử bản thân, gia đình và dị ứng thuốc")
class PatientMedicalHistoryController {

  private final PatientMedicalHistoryService historyService;
  private final PatientActorResolver actors;

  PatientMedicalHistoryController(
      PatientMedicalHistoryService historyService, PatientActorResolver actors) {
    this.historyService = historyService;
    this.actors = actors;
  }

  @PostMapping("/patients/{publicId}/medical-histories")
  @Operation(
      summary = "Ghi một mục tiền sử",
      description =
          "Bác sĩ hoặc nhân viên ghi thì được lưu là người ghi; bệnh nhân tự khai trên hồ sơ của"
              + " mình thì `source` = PATIENT. `familyRelationship` bắt buộc khi type = FAMILY và"
              + " phải bỏ trống với loại khác.")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới mục tiền sử")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<MedicalHistoryResponse>> add(
      Authentication authentication,
      @PathVariable UUID publicId,
      @Valid @RequestBody MedicalHistoryCreateRequest request) {
    MedicalHistoryResponse created =
        historyService.add(publicId, request, actors.resolve(authentication));
    return ResponseEntity.created(URI.create("/api/v1/medical-histories/" + created.id()))
        .body(ApiResult.of(created));
  }

  @GetMapping("/patients/{publicId}/medical-histories")
  @Operation(
      summary = "Danh sách tiền sử của bệnh nhân (phân trang)",
      description =
          "Sắp xếp được theo: type, recordedAt, updatedAt. Mặc định: recordedAt giảm dần (mới"
              + " nhất trước).")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<List<MedicalHistoryResponse>> list(
      Authentication authentication,
      @PathVariable UUID publicId,
      @Parameter(description = "Lọc theo loại: PERSONAL, FAMILY, ALLERGY; bỏ trống = tất cả")
          @RequestParam(required = false)
          MedicalHistoryType type,
      @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(
            PatientMedicalHistoryService.SORTABLE_FIELDS,
            PatientMedicalHistoryService.DEFAULT_SORT);
    return ApiResult.of(
        historyService.list(publicId, type, actors.resolve(authentication), pageable));
  }

  @GetMapping("/patients/{publicId}/medical-histories/summary")
  @Operation(
      summary = "Tóm tắt toàn bộ tiền sử theo nhóm (cho tiếp nhận và khám)",
      description =
          "Không phân trang: trả đủ ba nhóm personal, family, allergies, mỗi nhóm mới nhất trước.")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalHistorySummaryResponse> summary(
      Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(historyService.getSummary(publicId, actors.resolve(authentication)));
  }

  @PutMapping("/medical-histories/{id}")
  @Operation(
      summary = "Sửa mô tả một mục tiền sử",
      description =
          "Loại và người ghi không đổi. PATIENT chỉ sửa mục do chính mình tự khai; DOCTOR và"
              + " MEDICAL_STAFF sửa được mọi mục.")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_MEDICAL_HISTORY_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<MedicalHistoryResponse> update(
      Authentication authentication,
      @PathVariable Long id,
      @Valid @RequestBody MedicalHistoryUpdateRequest request) {
    return ApiResult.of(historyService.update(id, request, actors.resolve(authentication)));
  }
}
