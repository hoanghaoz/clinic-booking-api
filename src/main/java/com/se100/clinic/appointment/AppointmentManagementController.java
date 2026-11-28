package com.se100.clinic.appointment;

import com.se100.clinic.appointment.AppointmentManagementDtos.ChangeRequest;
import com.se100.clinic.appointment.AppointmentManagementDtos.ClinicOption;
import com.se100.clinic.appointment.AppointmentManagementDtos.DeskBookingRequest;
import com.se100.clinic.appointment.AppointmentManagementDtos.Detail;
import com.se100.clinic.appointment.AppointmentManagementDtos.Group;
import com.se100.clinic.appointment.AppointmentManagementDtos.Summary;
import com.se100.clinic.auth.AuthDtos.AccountIdentity;
import com.se100.clinic.auth.AuthenticatedUserService;
import com.se100.clinic.catalog.CatalogBookingService;
import com.se100.clinic.catalog.CatalogBookingService.StandaloneService;
import com.se100.clinic.schedule.ScheduleDtos.AvailableSchedule;
import com.se100.clinic.schedule.ScheduleService;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.api.ApiResult;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Quản lý lịch hẹn")
@SecurityRequirement(name = "bearerAuth")
class AppointmentManagementController {
  private final AppointmentManagementService service;
  private final AuthenticatedUserService users;
  private final CatalogBookingService catalog;

  @GetMapping("/me")
  @PreAuthorize("hasRole('PATIENT')")
  ApiResult<List<Summary>> mine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "UPCOMING") Group group,
      @ParameterObject PageParams page) {
    return ApiResult.of(service.mine(userId(jwt), group, listing(page)));
  }

  @GetMapping("/me/{id}")
  @PreAuthorize("hasRole('PATIENT')")
  ApiResult<Detail> myDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResult.of(service.detail(userId(jwt), false, id));
  }

  @PatchMapping("/me/{id}")
  @PreAuthorize("hasRole('PATIENT')")
  ApiResult<Detail> changeMine(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRequest request) {
    return ApiResult.of(service.change(userId(jwt), false, id, request));
  }

  @GetMapping("/me/{id}/availability")
  @PreAuthorize("hasRole('PATIENT')")
  ApiResult<List<AvailableSchedule>> myTimes(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam @Positive Long clinicId,
      @RequestParam LocalDate date,
      @ParameterObject PageParams page) {
    return ApiResult.of(
        service.times(userId(jwt), false, id, clinicId, date, null, schedules(page)));
  }

  @GetMapping("/manage/clinics")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<List<ClinicOption>> clinics(@AuthenticationPrincipal Jwt jwt) {
    return ApiResult.of(service.clinics(userId(jwt)));
  }

  @GetMapping("/manage/services")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<List<StandaloneService>> services(@ParameterObject PageParams page) {
    return ApiResult.of(catalog.listServices(page.toPageable(Set.of("name"), Sort.by("name"))));
  }

  @GetMapping("/manage")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<List<Summary>> desk(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @Positive Long clinicId,
      @RequestParam(defaultValue = "") @Size(max = 80) String term,
      @RequestParam(required = false) LocalDate date,
      @RequestParam(defaultValue = "false") boolean daily,
      @ParameterObject PageParams page) {
    return ApiResult.of(service.desk(userId(jwt), clinicId, term, date, daily, listing(page)));
  }

  @GetMapping("/manage/{id}")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<Detail> deskDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ApiResult.of(service.detail(userId(jwt), true, id));
  }

  @PatchMapping("/manage/{id}")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<Detail> changeAtDesk(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ChangeRequest request) {
    return ApiResult.of(service.change(userId(jwt), true, id, request));
  }

  @GetMapping("/manage/availability")
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<List<AvailableSchedule>> deskTimes(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam @Positive Long clinicId,
      @RequestParam LocalDate date,
      @RequestParam(required = false) UUID appointmentId,
      @RequestParam(required = false) Integer durationMinutes,
      @ParameterObject PageParams page) {
    return ApiResult.of(
        service.times(
            userId(jwt), true, appointmentId, clinicId, date, durationMinutes, schedules(page)));
  }

  @PostMapping("/manage")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('MEDICAL_STAFF')")
  ApiResult<BookingDtos.Ticket> bookAtDesk(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeskBookingRequest request) {
    return ApiResult.of(service.bookAtDesk(userId(jwt), request));
  }

  private Long userId(Jwt jwt) {
    if (jwt == null) throw new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập");
    return users
        .identityForSubject(jwt.getSubject())
        .map(AccountIdentity::userId)
        .orElseThrow(() -> new BusinessException(CommonErrorCode.AUTH_REQUIRED, "Cần đăng nhập"));
  }

  private static Pageable listing(PageParams page) {
    return page.toPageable(Set.of(), Sort.unsorted());
  }

  private static Pageable schedules(PageParams page) {
    return page.toPageable(ScheduleService.SORTABLE_FIELDS, ScheduleService.DEFAULT_SORT);
  }
}
