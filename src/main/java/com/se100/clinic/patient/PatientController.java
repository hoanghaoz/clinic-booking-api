package com.se100.clinic.patient;

import com.se100.clinic.patient.PatientDtos.PatientCardResponse;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientClassUpdateRequest;
import com.se100.clinic.patient.PatientDtos.PatientCreateRequest;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.PatientDtos.PatientUpdateRequest;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.annotation.AdminOnly;
import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.FieldViolation;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * package-private — HTTP routing only. Role and ownership rules live in {@link PatientService}; the
 * {@code @PreAuthorize} here just rejects the roles that can never use an endpoint (401/403 before
 * any work is done).
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Bệnh nhân", description = "Hồ sơ bệnh nhân, thẻ bệnh nhân và tra cứu SĐT/CCCD")
class PatientController {

  // Roles that can create / edit a profile.
  private static final String WRITE = "hasAnyRole('PATIENT','MEDICAL_STAFF')";
  // Everyone except COMPANY.
  private static final String READ = "hasAnyRole('PATIENT','DOCTOR','MEDICAL_STAFF','ADMIN')";
  private static final String CARD = "hasAnyRole('PATIENT','MEDICAL_STAFF','ADMIN')";

  private final PatientService patientService;
  private final PatientActorResolver actors;

  PatientController(PatientService patientService, PatientActorResolver actors) {
    this.patientService = patientService;
    this.actors = actors;
  }

  @PostMapping
  @PreAuthorize(WRITE)
  @Operation(
      summary = "Tạo hồ sơ bệnh nhân và cấp thẻ (hiệu lực 1 năm)",
      description =
          "PATIENT tạo hồ sơ cho chính mình (gắn với tài khoản, mỗi tài khoản một hồ sơ)."
              + " MEDICAL_STAFF tạo hồ sơ cho khách đến quầy (chưa có tài khoản). Hạng luôn là"
              + " PERSONAL.")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới hồ sơ")
  @ApiResponse(
      responseCode = "409",
      description =
          "PATIENT_PROFILE_EXISTS (tài khoản đã có hồ sơ) hoặc PATIENT_IDENTITY_EXISTS (trùng giấy tờ)",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<PatientResponse>> create(
      Authentication authentication, @Valid @RequestBody PatientCreateRequest request) {
    PatientResponse created = patientService.create(request, actors.resolve(authentication));
    return ResponseEntity.created(URI.create("/api/v1/patients/" + created.publicId()))
        .body(ApiResult.of(created));
  }

  @GetMapping("/me")
  @PreAuthorize("hasRole('PATIENT')")
  @Operation(summary = "Bệnh nhân xem hồ sơ của chính mình")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND — tài khoản chưa có hồ sơ",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientResponse> me(Authentication authentication) {
    return ApiResult.of(patientService.getMyProfile(actors.resolve(authentication)));
  }

  @GetMapping("/search")
  @PreAuthorize(READ)
  @Operation(
      summary = "Tra cứu bệnh nhân theo SĐT hoặc số giấy tờ (CCCD/hộ chiếu)",
      description =
          "Truyền đúng một trong hai: `phone` hoặc `identityNumber`; khớp chính xác. PATIENT chỉ"
              + " thấy hồ sơ của mình (hồ sơ người khác không trả về). Các vai trò còn lại nhận"
              + " mọi hồ sơ khớp. Sắp xếp được theo: fullName, dateOfBirth, createdAt. Mặc định:"
              + " fullName tăng dần.")
  ApiResult<List<PatientResponse>> search(
      Authentication authentication,
      @Parameter(description = "Số điện thoại; bỏ qua dấu cách, chấm, gạch ngang")
          @RequestParam(required = false)
          String phone,
      @Parameter(description = "Số CCCD hoặc hộ chiếu") @RequestParam(required = false)
          String identityNumber,
      @ParameterObject PageParams pageParams) {
    boolean hasPhone = phone != null && !phone.isBlank();
    boolean hasIdentity = identityNumber != null && !identityNumber.isBlank();
    if (hasPhone == hasIdentity) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Tham số truy vấn không hợp lệ",
          List.of(
              new FieldViolation(
                  hasPhone ? "identityNumber" : "phone",
                  "Truyền đúng một trong hai tham số: phone hoặc identityNumber")));
    }
    Pageable pageable =
        pageParams.toPageable(PatientService.SORTABLE_FIELDS, PatientService.DEFAULT_SORT);
    PatientActor actor = actors.resolve(authentication);
    return ApiResult.of(
        hasPhone
            ? patientService.searchByPhone(phone, actor, pageable)
            : patientService.searchByIdentityNumber(identityNumber, actor, pageable));
  }

  @GetMapping("/{publicId}")
  @PreAuthorize(READ)
  @Operation(summary = "Chi tiết hồ sơ bệnh nhân")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientResponse> getByPublicId(
      Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(patientService.getProfile(publicId, actors.resolve(authentication)));
  }

  @PutMapping("/{publicId}")
  @PreAuthorize(WRITE)
  @Operation(
      summary = "Cập nhật hồ sơ bệnh nhân",
      description =
          "PATIENT chỉ sửa hồ sơ của mình. Giấy tờ định danh và hạng không sửa được ở đây.")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientResponse> update(
      Authentication authentication,
      @PathVariable UUID publicId,
      @Valid @RequestBody PatientUpdateRequest request) {
    return ApiResult.of(patientService.update(publicId, request, actors.resolve(authentication)));
  }

  @PatchMapping("/{publicId}/class")
  @AdminOnly
  @Operation(
      summary = "Admin đổi hạng bệnh nhân giữa PERSONAL và VIP",
      description = "COMPANY do hợp đồng doanh nghiệp quản lý nên không gán hoặc đổi thủ công.")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description = "PATIENT_CLASS_LOCKED — bệnh nhân đang thuộc hợp đồng doanh nghiệp",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientResponse> changeClass(
      Authentication authentication,
      @PathVariable UUID publicId,
      @Valid @RequestBody PatientClassUpdateRequest request) {
    return ApiResult.of(
        patientService.changeClass(publicId, request, actors.resolve(authentication)));
  }

  @GetMapping("/{publicId}/card")
  @PreAuthorize(CARD)
  @Operation(summary = "Xem thẻ bệnh nhân kèm trạng thái")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND hoặc PATIENT_CARD_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientCardResponse> card(Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(patientService.getCard(publicId, actors.resolve(authentication)));
  }

  @GetMapping("/{publicId}/card/validity")
  @PreAuthorize(CARD)
  @Operation(
      summary = "Kiểm tra thời hạn thẻ",
      description =
          "status: ACTIVE, EXPIRING_SOON (còn từ 30 ngày trở xuống), EXPIRED, REVOKED."
              + " `valid` là true với ACTIVE và EXPIRING_SOON.")
  @ApiResponse(
      responseCode = "404",
      description = "PATIENT_NOT_FOUND hoặc PATIENT_CARD_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientCardValidityResponse> cardValidity(
      Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(patientService.checkCardValidity(publicId, actors.resolve(authentication)));
  }
}
