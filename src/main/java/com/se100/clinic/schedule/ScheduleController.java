package com.se100.clinic.schedule;

import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.schedule.ScheduleAvailabilityService.AvailableSchedule;
import com.se100.clinic.schedule.ScheduleDtos.ChangeScheduleStatusRequest;
import com.se100.clinic.schedule.ScheduleDtos.ChangeSlotStatusRequest;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import com.se100.clinic.schedule.ScheduleDtos.ShiftResponse;
import com.se100.clinic.schedule.ScheduleDtos.SlotResponse;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.annotation.AdminOnly;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/api/v1/doctor-schedules")
@RequiredArgsConstructor
@Tag(name = "Lịch làm việc", description = "Ca, phân công bác sĩ và lịch khả dụng")
class ScheduleController {
  private final ScheduleService service;
  private final ScheduleAvailabilityService availability;
  private final AuthenticatedUserService users;
  private final DoctorSchedulingService doctors;

  @GetMapping("/shifts")
  @Operation(summary = "Ba mẫu ca làm việc")
  ApiResult<List<ShiftResponse>> shifts() {
    return ApiResult.of(service.shifts());
  }

  @PostMapping
  @AdminOnly
  @Operation(summary = "Admin phân công bác sĩ theo cơ sở, phòng, ngày và ca")
  ResponseEntity<ApiResult<ScheduleResponse>> create(
      @Valid @RequestBody SaveScheduleRequest request) {
    ScheduleResponse created = service.create(request);
    return ResponseEntity.created(URI.create("/api/v1/doctor-schedules/" + created.publicId()))
        .body(ApiResult.of(created));
  }

  @GetMapping
  @AdminOnly
  @Operation(summary = "Admin tra cứu lịch phân công")
  ApiResult<List<ScheduleResponse>> list(
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long clinicId,
      @RequestParam(required = false) Long specialtyId,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @RequestParam(required = false) ScheduleStatus status,
      @ParameterObject PageParams pageParams) {
    Pageable pageable = servicePage(pageParams);
    return ApiResult.of(
        service.list(doctorId, clinicId, specialtyId, fromDate, toDate, status, pageable));
  }

  @GetMapping("/my")
  @PreAuthorize("hasRole('DOCTOR')")
  @Operation(summary = "Bác sĩ xem ca và phòng được phân công của mình")
  ApiResult<List<ScheduleResponse>> mySchedules(
      Authentication authentication,
      @RequestParam(required = false) Long clinicId,
      @RequestParam(required = false) LocalDate fromDate,
      @RequestParam(required = false) LocalDate toDate,
      @ParameterObject PageParams pageParams) {
    Long userId =
        users
            .userIdForPrincipal(authentication.getName())
            .orElseThrow(
                () -> new BusinessException(ScheduleErrorCode.AUTH_REQUIRED, "Cần đăng nhập"));
    Long doctorId =
        doctors
            .doctorIdForUser(userId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        ScheduleErrorCode.DOCTOR_PROFILE_NOT_FOUND,
                        "Tài khoản chưa liên kết với hồ sơ bác sĩ"));
    return ApiResult.of(
        service.list(doctorId, clinicId, null, fromDate, toDate, null, servicePage(pageParams)));
  }

  @GetMapping("/availability")
  @PreAuthorize("isAuthenticated()")
  @Operation(summary = "Các khoảng 20 hoặc 30 phút còn có thể đặt")
  ApiResult<List<AvailableSchedule>> available(
      @RequestParam(required = false) Long doctorId,
      @RequestParam(required = false) Long clinicId,
      @RequestParam(required = false) Long specialtyId,
      @RequestParam LocalDate fromDate,
      @RequestParam LocalDate toDate,
      @RequestParam(defaultValue = "20") int durationMinutes,
      @ParameterObject PageParams pageParams) {
    return ApiResult.of(
        availability.find(
            doctorId,
            clinicId,
            specialtyId,
            fromDate,
            toDate,
            durationMinutes,
            servicePage(pageParams)));
  }

  @GetMapping("/{publicId}")
  @AdminOnly
  @Operation(summary = "Chi tiết lịch phân công")
  ApiResult<ScheduleResponse> get(@PathVariable UUID publicId) {
    return ApiResult.of(service.get(publicId));
  }

  @PutMapping("/{publicId}")
  @AdminOnly
  @Operation(summary = "Sửa phân công chưa có lịch hẹn")
  ApiResult<ScheduleResponse> update(
      @PathVariable UUID publicId, @Valid @RequestBody SaveScheduleRequest request) {
    return ApiResult.of(service.update(publicId, request));
  }

  @PatchMapping("/{publicId}/status")
  @AdminOnly
  @Operation(summary = "Đổi trạng thái ca OPEN, CLOSED hoặc CANCELLED")
  ApiResult<ScheduleResponse> changeStatus(
      @PathVariable UUID publicId, @Valid @RequestBody ChangeScheduleStatusRequest request) {
    return ApiResult.of(service.changeStatus(publicId, request.status()));
  }

  @GetMapping("/{publicId}/slots")
  @AdminOnly
  @Operation(summary = "Danh sách ô 10 phút của ca và trạng thái giữ chỗ")
  ApiResult<List<SlotResponse>> slots(@PathVariable UUID publicId) {
    return ApiResult.of(service.slots(publicId));
  }

  @PatchMapping("/{publicId}/slots/{slotId}/status")
  @AdminOnly
  @Operation(summary = "Chặn hoặc mở một ô giờ chưa có lịch hẹn")
  ApiResult<SlotResponse> changeSlotStatus(
      @PathVariable UUID publicId,
      @PathVariable Long slotId,
      @Valid @RequestBody ChangeSlotStatusRequest request) {
    return ApiResult.of(service.changeSlotStatus(publicId, slotId, request.status()));
  }

  private static Pageable servicePage(PageParams params) {
    return params.toPageable(ScheduleService.SORTABLE_FIELDS, ScheduleService.DEFAULT_SORT);
  }
}
