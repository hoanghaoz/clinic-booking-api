package com.se100.clinic.examination;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.examination.ExaminationDtos.PatientHistoryResponse;
import com.se100.clinic.examination.ExaminationDtos.QueueItem;
import com.se100.clinic.examination.ExaminationDtos.UpdateExaminationRequest;
import com.se100.clinic.examination.ExaminationDtos.VisitDetail;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.shared.annotation.DoctorOnly;
import com.se100.clinic.shared.api.ApiDocs;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
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
 * The doctor's examination flow (REC-05, EXAM-01..03). Thin controller: it resolves who the doctor
 * is, everything else is in {@link ExaminationService}.
 */
@RestController
@RequestMapping("/api/v1/medical-visits")
@DoctorOnly
@Tag(name = "Khám bệnh", description = "Bác sĩ: danh sách đã tiếp nhận, hồ sơ lượt khám, tiền sử")
@SecurityRequirement(name = "bearerAuth")
class ExaminationController {

  private static final String FORBIDDEN_AND_CONFLICT =
      "VISIT_NOT_ASSIGNED (403) — lượt khám của bác sĩ khác;"
          + " VISIT_CLOSED (409) — lượt khám đã đóng hồ sơ";

  private final ExaminationService examinations;
  private final AuthenticatedUserService users;
  private final DoctorSchedulingService doctors;

  ExaminationController(
      ExaminationService examinations,
      AuthenticatedUserService users,
      DoctorSchedulingService doctors) {
    this.examinations = examinations;
    this.users = users;
    this.doctors = doctors;
  }

  /** The logged-in doctor: the user id (for the patient module) and the doctor id. */
  private record Doctor(Long userId, Long doctorId) {}

  @GetMapping("/my-queue")
  @Operation(
      summary = "Danh sách bệnh nhân đã tiếp nhận của bác sĩ, theo thứ tự tiếp nhận",
      description =
          "Chỉ gồm lượt khám đã có phiếu tiếp nhận và chưa hủy. Không truyền `schedulePublicId`"
              + " và `date` thì lấy các ca của hôm nay. `queueNumber` là số thứ tự trong ca.")
  ApiResult<List<QueueItem>> myQueue(
      Authentication authentication,
      @Parameter(description = "Chỉ lấy một ca được phân công") @RequestParam(required = false)
          UUID schedulePublicId,
      @Parameter(description = "Ngày làm việc (yyyy-MM-dd)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @Parameter(description = "Lọc theo trạng thái lượt khám") @RequestParam(required = false)
          MedicalVisitStatus status) {
    return ApiResult.of(
        examinations.myQueue(
            currentDoctor(authentication).doctorId(), schedulePublicId, date, status));
  }

  @GetMapping("/{publicId}")
  @Operation(summary = "Mở hồ sơ lượt khám")
  @ApiResponse(
      responseCode = "403",
      description = "VISIT_NOT_ASSIGNED — lượt khám của bác sĩ khác",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "404",
      description = "VISIT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<VisitDetail> get(Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(examinations.get(publicId, currentDoctor(authentication).doctorId()));
  }

  @PostMapping("/{publicId}/start")
  @Operation(
      summary = "Bắt đầu khám",
      description =
          "WAITING → IN_PROGRESS và đặt bác sĩ này làm bác sĩ khám chính. Gọi lại không đổi.")
  @ApiResponse(
      responseCode = "403",
      description = FORBIDDEN_AND_CONFLICT,
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<VisitDetail> start(Authentication authentication, @PathVariable UUID publicId) {
    return ApiResult.of(examinations.start(publicId, currentDoctor(authentication).doctorId()));
  }

  @PutMapping("/{publicId}/examination")
  @Operation(
      summary = "Ghi triệu chứng, chẩn đoán ban đầu và tư vấn",
      description =
          "Thay toàn bộ phần ghi của bác sĩ: trường bỏ trống sẽ bị xóa. `version` lấy từ lần GET"
              + " gần nhất; nếu lượt khám đã được lưu ở nơi khác thì trả 409"
              + " VISIT_VERSION_CONFLICT. Lần lưu đầu tiên tự chuyển lượt khám sang IN_PROGRESS.")
  @ApiResponse(
      responseCode = "403",
      description = FORBIDDEN_AND_CONFLICT,
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "VISIT_CLOSED — lượt khám đã đóng hồ sơ; VISIT_VERSION_CONFLICT — lưu đè phiên bản cũ",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<VisitDetail> update(
      Authentication authentication,
      @PathVariable UUID publicId,
      @Valid @RequestBody UpdateExaminationRequest request) {
    return ApiResult.of(
        examinations.update(publicId, currentDoctor(authentication).doctorId(), request));
  }

  @GetMapping("/{publicId}/patient-history")
  @Operation(
      summary = "Tiền sử bệnh lý, lịch sử khám và kết quả cận lâm sàng cũ của bệnh nhân",
      description =
          "Chỉ bác sĩ phụ trách lượt khám này mới xem được. Bệnh nhân chưa có dữ liệu thì trả"
              + " danh sách rỗng. Kết quả cận lâm sàng chỉ gồm kết quả đã duyệt (VERIFIED/RELEASED).")
  @ApiResponse(
      responseCode = "403",
      description = "VISIT_NOT_ASSIGNED — lượt khám của bác sĩ khác",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<PatientHistoryResponse> patientHistory(
      Authentication authentication, @PathVariable UUID publicId) {
    Doctor doctor = currentDoctor(authentication);
    return ApiResult.of(examinations.patientHistory(publicId, doctor.doctorId(), doctor.userId()));
  }

  private Doctor currentDoctor(Authentication authentication) {
    Long userId =
        users
            .userIdForPrincipal(authentication.getName())
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập"));
    Long doctorId =
        doctors
            .doctorIdForUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ExaminationErrorCode.VISIT_DOCTOR_PROFILE_NOT_FOUND,
                        "Tài khoản chưa liên kết với hồ sơ bác sĩ"));
    return new Doctor(userId, doctorId);
  }
}
