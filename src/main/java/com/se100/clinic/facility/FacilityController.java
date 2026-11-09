package com.se100.clinic.facility;

import com.se100.clinic.facility.FacilityDtos.ChangeStatusRequest;
import com.se100.clinic.facility.FacilityDtos.ClinicResponse;
import com.se100.clinic.facility.FacilityDtos.RoomResponse;
import com.se100.clinic.facility.FacilityDtos.SaveClinicRequest;
import com.se100.clinic.facility.FacilityDtos.SaveRoomRequest;
import com.se100.clinic.facility.enums.FacilityStatus;
import com.se100.clinic.shared.PageParams;
import com.se100.clinic.shared.annotation.AdminOnly;
import com.se100.clinic.shared.api.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
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
@RequestMapping(FacilityController.BASE)
@RequiredArgsConstructor
@Tag(name = "Cơ sở và phòng", description = "Quản lý cơ sở và phòng chức năng")
@SecurityRequirement(name = "bearerAuth")
class FacilityController {
  static final String BASE = "/api/v1/clinics";
  private static final Set<String> SORTABLE = Set.of("code", "name", "status", "createdAt");

  private final FacilityManagementService service;

  @GetMapping
  @AdminOnly
  @Operation(summary = "Danh sách cơ sở theo mã, tên hoặc trạng thái")
  ApiResult<List<ClinicResponse>> listClinics(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) FacilityStatus status,
      @ParameterObject PageParams paging) {
    return ApiResult.of(service.listClinics(keyword, status, page(paging)));
  }

  @PostMapping
  @AdminOnly
  @Operation(summary = "Tạo cơ sở")
  ResponseEntity<ApiResult<ClinicResponse>> createClinic(
      @Valid @RequestBody SaveClinicRequest request) {
    ClinicResponse result = service.createClinic(request);
    return ResponseEntity.created(URI.create(BASE + "/" + result.publicId()))
        .body(ApiResult.of(result));
  }

  @GetMapping("/{clinicPublicId}")
  @AdminOnly
  @Operation(summary = "Chi tiết cơ sở")
  ApiResult<ClinicResponse> getClinic(@PathVariable UUID clinicPublicId) {
    return ApiResult.of(service.getClinic(clinicPublicId));
  }

  @PutMapping("/{clinicPublicId}")
  @AdminOnly
  @Operation(summary = "Cập nhật thông tin cơ sở")
  ApiResult<ClinicResponse> updateClinic(
      @PathVariable UUID clinicPublicId, @Valid @RequestBody SaveClinicRequest request) {
    return ApiResult.of(service.updateClinic(clinicPublicId, request));
  }

  @PatchMapping("/{clinicPublicId}/status")
  @AdminOnly
  @Operation(summary = "Mở hoặc ngừng hoạt động cơ sở")
  ApiResult<ClinicResponse> changeClinicStatus(
      @PathVariable UUID clinicPublicId, @Valid @RequestBody ChangeStatusRequest request) {
    return ApiResult.of(service.changeClinicStatus(clinicPublicId, request.status()));
  }

  @GetMapping("/{clinicPublicId}/rooms")
  @AdminOnly
  @Operation(summary = "Danh sách phòng thuộc cơ sở")
  ApiResult<List<RoomResponse>> listRooms(
      @PathVariable UUID clinicPublicId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) FacilityStatus status,
      @ParameterObject PageParams paging) {
    return ApiResult.of(service.listRooms(clinicPublicId, keyword, status, page(paging)));
  }

  @PostMapping("/{clinicPublicId}/rooms")
  @AdminOnly
  @Operation(summary = "Tạo phòng thuộc cơ sở")
  ResponseEntity<ApiResult<RoomResponse>> createRoom(
      @PathVariable UUID clinicPublicId, @Valid @RequestBody SaveRoomRequest request) {
    RoomResponse result = service.createRoom(clinicPublicId, request);
    return ResponseEntity.created(
            URI.create(BASE + "/" + clinicPublicId + "/rooms/" + result.publicId()))
        .body(ApiResult.of(result));
  }

  @GetMapping("/{clinicPublicId}/rooms/{roomPublicId}")
  @AdminOnly
  @Operation(summary = "Chi tiết phòng trong cơ sở")
  ApiResult<RoomResponse> getRoom(
      @PathVariable UUID clinicPublicId, @PathVariable UUID roomPublicId) {
    return ApiResult.of(service.getRoom(clinicPublicId, roomPublicId));
  }

  @PutMapping("/{clinicPublicId}/rooms/{roomPublicId}")
  @AdminOnly
  @Operation(summary = "Cập nhật mã và tên phòng")
  ApiResult<RoomResponse> updateRoom(
      @PathVariable UUID clinicPublicId,
      @PathVariable UUID roomPublicId,
      @Valid @RequestBody SaveRoomRequest request) {
    return ApiResult.of(service.updateRoom(clinicPublicId, roomPublicId, request));
  }

  @PatchMapping("/{clinicPublicId}/rooms/{roomPublicId}/status")
  @AdminOnly
  @Operation(summary = "Mở hoặc ngừng hoạt động phòng")
  ApiResult<RoomResponse> changeRoomStatus(
      @PathVariable UUID clinicPublicId,
      @PathVariable UUID roomPublicId,
      @Valid @RequestBody ChangeStatusRequest request) {
    return ApiResult.of(service.changeRoomStatus(clinicPublicId, roomPublicId, request.status()));
  }

  private static Pageable page(PageParams params) {
    return params.toPageable(SORTABLE, Sort.by("name"));
  }
}
