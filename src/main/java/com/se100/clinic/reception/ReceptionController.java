package com.se100.clinic.reception;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.employee.StaffService;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.reception.ReceptionDtos.CreateReceptionRequest;
import com.se100.clinic.reception.ReceptionDtos.ReceptionListItem;
import com.se100.clinic.reception.ReceptionDtos.ReceptionLookupResponse;
import com.se100.clinic.reception.ReceptionDtos.ReceptionTicketResponse;
import com.se100.clinic.reception.ReceptionDtos.RoutedOrderResponse;
import com.se100.clinic.reception.ReceptionDtos.StaffClinicResponse;
import com.se100.clinic.reception.ReceptionDtos.UpdateOrderRoutingRequest;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.annotation.MedicalStaffOnly;
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
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
 * Counter reception and patient room-routing endpoints (REC-01..REC-04). Thin controller: resolves
 * the logged-in working staff member and delegates all logic to {@link ReceptionService}.
 */
@RestController
@RequestMapping("/api/v1/receptions")
@MedicalStaffOnly
@Tag(
    name = "Tiếp nhận bệnh nhân",
    description =
        "Nhân viên y tế: tra cứu lịch hẹn tại quầy, tạo phiếu tiếp nhận và phân luồng phòng")
@SecurityRequirement(name = "bearerAuth")
class ReceptionController {

  private final ReceptionService receptionService;
  private final AuthenticatedUserService users;
  private final StaffService staffService;

  ReceptionController(
      ReceptionService receptionService,
      AuthenticatedUserService users,
      StaffService staffService) {
    this.receptionService = receptionService;
    this.users = users;
    this.staffService = staffService;
  }

  private record StaffPrincipal(Long userId, Long staffId) {}

  @GetMapping("/clinics")
  @Operation(
      summary = "Danh sách cơ sở nhân viên được phân công tiếp nhận kèm các phòng đang mở",
      description =
          "Dùng để chọn quầy/cơ sở làm việc và lấy danh sách phòng chức năng cho phân luồng.")
  ApiResult<List<StaffClinicResponse>> clinics(Authentication authentication) {
    return ApiResult.of(receptionService.staffClinics(currentStaff(authentication).staffId()));
  }

  @GetMapping("/lookup")
  @Operation(
      summary = "Tra cứu hồ sơ bệnh nhân và lịch hẹn tại quầy (REC-01, REC-02)",
      description =
          "Truyền đúng một trong bốn tham số: `qr`, `appointmentCode`, `phone` hoặc"
              + " `identityNumber`. Trả về hồ sơ, tiền sử bệnh lý (PAT-03), trạng thái thẻ"
              + " (PAT-05), danh sách lịch hẹn sắp xếp gần nhất trước cùng đánh dấu đúng giờ/quá"
              + " giờ và trạng thái thanh toán.")
  @ApiResponse(
      responseCode = "403",
      description =
          "CLINIC_ACCESS_DENIED — lịch hẹn thuộc cơ sở khác hoặc nhân viên không thuộc cơ sở",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "404",
      description =
          "APPOINTMENT_NOT_FOUND / NOT_FOUND — mã QR/đặt lịch hoặc SĐT/CCCD không tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<ReceptionLookupResponse> lookup(
      Authentication authentication,
      @Parameter(description = "ID cơ sở đang tiếp nhận (bắt buộc nếu nhân viên thuộc nhiều cơ sở)")
          @RequestParam(required = false)
          Long clinicId,
      @Parameter(description = "Nội dung quét từ mã QR trên phiếu đặt lịch")
          @RequestParam(required = false)
          String qr,
      @Parameter(description = "Mã đặt lịch nhập tay (vd. AP-...)") @RequestParam(required = false)
          String appointmentCode,
      @Parameter(description = "Số điện thoại bệnh nhân") @RequestParam(required = false)
          String phone,
      @Parameter(description = "Số CCCD/CMND/hộ chiếu của bệnh nhân")
          @RequestParam(required = false)
          String identityNumber) {
    StaffPrincipal staff = currentStaff(authentication);
    return ApiResult.of(
        receptionService.lookup(
            staff.userId(), staff.staffId(), clinicId, qr, appointmentCode, phone, identityNumber));
  }

  @PostMapping
  @Operation(
      summary = "Xác nhận tiếp nhận, tạo phiếu tiếp nhận và tự phân luồng phòng (REC-03, REC-04)",
      description =
          "Chuyển trạng thái lịch hẹn sang CHECKED_IN, tạo medical_visits và sinh danh sách"
              + " medical_orders theo gói/dịch vụ (hạng mục nhiều lần như HPV 3 mũi sinh đủ số lần"
              + " và chỉ xếp hàng lần hiện tại). Chống tiếp nhận trùng đồng thời.")
  @ApiResponse(
      responseCode = "201",
      description = "Đã tạo phiếu tiếp nhận; header Location trỏ tới phiếu")
  @ApiResponse(
      responseCode = "403",
      description = "CLINIC_ACCESS_DENIED — nhân viên cơ sở khác không được tiếp nhận",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "404",
      description = "APPOINTMENT_NOT_FOUND / RECEPTION_ROOM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "APPOINTMENT_ALREADY_CHECKED_IN / APPOINTMENT_NOT_RECEIVABLE / APPOINTMENT_NOT_TODAY /"
              + " SHIFT_ALREADY_ENDED / PATIENT_CARD_REVOKED / CLINIC_NOT_OPEN / ROOM_NOT_IN_CLINIC"
              + " / ROOM_NOT_OPEN",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<ReceptionTicketResponse>> checkIn(
      Authentication authentication, @Valid @RequestBody CreateReceptionRequest request) {
    StaffPrincipal staff = currentStaff(authentication);
    ReceptionTicketResponse created =
        receptionService.checkIn(staff.userId(), staff.staffId(), request);
    return ResponseEntity.created(URI.create("/api/v1/receptions/" + created.visitPublicId()))
        .body(ApiResult.of(created));
  }

  @GetMapping
  @Operation(
      summary = "Danh sách phiếu tiếp nhận tại cơ sở",
      description =
          "Mặc định lọc theo ngày hôm nay (giờ Việt Nam). Hỗ trợ tìm theo mã phiếu, mã lịch hẹn,"
              + " tên hoặc SĐT bệnh nhân. Sắp xếp được theo: receivedAt, code, status.")
  ApiResult<List<ReceptionListItem>> list(
      Authentication authentication,
      @Parameter(description = "ID cơ sở") @RequestParam(required = false) Long clinicId,
      @Parameter(description = "Ngày tiếp nhận (yyyy-MM-dd), mặc định hôm nay")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date,
      @Parameter(description = "Từ khóa: mã phiếu, mã lịch hẹn, họ tên hoặc SĐT bệnh nhân")
          @RequestParam(required = false)
          String keyword,
      @Parameter(description = "Trạng thái lượt khám") @RequestParam(required = false)
          MedicalVisitStatus status,
      @ParameterObject PageParams pageParams) {
    Pageable pageable =
        pageParams.toPageable(ReceptionService.SORTABLE_FIELDS, ReceptionService.DEFAULT_SORT);
    return ApiResult.of(
        receptionService.list(
            currentStaff(authentication).staffId(), clinicId, date, keyword, status, pageable));
  }

  @GetMapping("/{visitPublicId}")
  @Operation(summary = "Xem chi tiết phiếu tiếp nhận và phân luồng hạng mục")
  @ApiResponse(
      responseCode = "403",
      description = "CLINIC_ACCESS_DENIED",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "404",
      description = "RECEPTION_VISIT_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<ReceptionTicketResponse> getTicket(
      Authentication authentication, @PathVariable UUID visitPublicId) {
    StaffPrincipal staff = currentStaff(authentication);
    return ApiResult.of(receptionService.getTicket(staff.userId(), staff.staffId(), visitPublicId));
  }

  @PutMapping("/{visitPublicId}/orders/{orderPublicId}/routing")
  @Operation(
      summary = "Điều phối hoặc đổi phòng chức năng cho một hạng mục (REC-04)",
      description =
          "Gán phòng đang mở trong cùng cơ sở, chuyển hạng mục sang WAITING và cấp số thứ tự hàng"
              + " đợi trong ngày tại phòng đó.")
  @ApiResponse(
      responseCode = "403",
      description = "CLINIC_ACCESS_DENIED",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "404",
      description = "RECEPTION_ORDER_NOT_FOUND / RECEPTION_ROOM_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "RECEPTION_VISIT_CLOSED / ORDER_NOT_ROUTABLE / ROOM_NOT_IN_CLINIC / ROOM_NOT_OPEN",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<RoutedOrderResponse> routeOrder(
      Authentication authentication,
      @PathVariable UUID visitPublicId,
      @PathVariable UUID orderPublicId,
      @Valid @RequestBody UpdateOrderRoutingRequest request) {
    return ApiResult.of(
        receptionService.routeOrder(
            currentStaff(authentication).staffId(), visitPublicId, orderPublicId, request));
  }

  private StaffPrincipal currentStaff(Authentication authentication) {
    if (authentication == null) {
      throw new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập");
    }
    Long userId =
        users
            .userIdForPrincipal(authentication.getName())
            .orElseThrow(
                () -> new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập"));
    Long staffId =
        staffService
            .findWorkingStaffIdByUserId(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ReceptionErrorCode.STAFF_PROFILE_NOT_FOUND,
                        "Tài khoản chưa liên kết với hồ sơ nhân viên y tế đang làm việc"));
    return new StaffPrincipal(userId, staffId);
  }
}
