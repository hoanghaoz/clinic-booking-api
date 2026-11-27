package com.se100.clinic.appointment;

import com.se100.clinic.appointment.BookingDtos.Availability;
import com.se100.clinic.appointment.BookingDtos.CreateRequest;
import com.se100.clinic.appointment.BookingDtos.Ticket;
import com.se100.clinic.auth.AuthDtos.AccountIdentity;
import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.catalog.CatalogBookingService;
import com.se100.clinic.catalog.CatalogBookingService.StandaloneService;
import com.se100.clinic.facility.FacilitySchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService.BookingClinic;
import com.se100.clinic.schedule.ScheduleService;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Đặt lịch khám")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
class AppointmentBookingController {
  private final AppointmentBookingService booking;
  private final AuthenticatedUserService users;
  private final CatalogBookingService catalog;
  private final FacilitySchedulingService facilities;

  @GetMapping("/clinics")
  @Operation(summary = "Cơ sở đang hoạt động để bệnh nhân chọn khi đặt lịch")
  ApiResult<List<BookingClinic>> clinics(@ParameterObject PageParams pageParams) {
    return ApiResult.of(
        facilities.openClinics(pageParams.toPageable(Set.of("name"), Sort.by("name", "id"))));
  }

  @GetMapping("/availability")
  @Operation(summary = "Tìm giờ khám trống theo cơ sở, bác sĩ, chuyên khoa hoặc gói khám")
  ApiResult<List<Availability>> availability(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @Positive Long clinicId,
      @RequestParam(required = false) @Positive Long doctorId,
      @RequestParam(required = false) @Positive Long specialtyId,
      @RequestParam(required = false) UUID packagePublicId,
      @RequestParam(required = false) UUID servicePublicId,
      @RequestParam(required = false) Integer durationMinutes,
      @RequestParam LocalDate fromDate,
      @RequestParam LocalDate toDate,
      @ParameterObject PageParams pageParams) {
    return ApiResult.of(
        booking.available(
            userId(jwt),
            clinicId,
            doctorId,
            specialtyId,
            packagePublicId,
            servicePublicId,
            durationMinutes,
            fromDate,
            toDate,
            pageParams.toPageable(ScheduleService.SORTABLE_FIELDS, ScheduleService.DEFAULT_SORT)));
  }

  @GetMapping("/services")
  @Operation(summary = "Danh sách dịch vụ khám lẻ có thể chọn khi đặt lịch")
  ApiResult<List<StandaloneService>> services(@ParameterObject PageParams pageParams) {
    return ApiResult.of(
        catalog.listServices(
            pageParams.toPageable(
                Set.of("name", "price", "durationMinutes"), Sort.by("name", "id"))));
  }

  @PostMapping
  @Operation(summary = "Xác nhận đặt lịch và nhận phiếu có mã QR")
  ResponseEntity<ApiResult<Ticket>> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRequest request) {
    Ticket created = booking.bookOnline(userId(jwt), request);
    return ResponseEntity.created(
            URI.create("/api/v1/appointments/" + created.publicId() + "/ticket"))
        .cacheControl(CacheControl.noStore())
        .body(ApiResult.of(created));
  }

  @GetMapping("/{publicId}/ticket")
  @Operation(summary = "Xem phiếu đặt lịch của chính bệnh nhân")
  ResponseEntity<ApiResult<Ticket>> ticket(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID publicId) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResult.of(booking.ticketForUser(userId(jwt), publicId)));
  }

  private Long userId(Jwt jwt) {
    if (jwt == null) {
      throw new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập");
    }
    return users
        .identityForSubject(jwt.getSubject())
        .map(AccountIdentity::userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập"));
  }
}
