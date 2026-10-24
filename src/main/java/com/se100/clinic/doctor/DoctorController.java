package com.se100.clinic.doctor;

import com.se100.clinic.doctor.DoctorDtos.CreateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.DoctorResponse;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorRequest;
import com.se100.clinic.doctor.DoctorDtos.UpdateDoctorStatusRequest;
import com.se100.clinic.employee.enums.EmployeeStatus;
import com.se100.clinic.shared.ApiDocs;
import com.se100.clinic.shared.ApiResult;
import com.se100.clinic.shared.PageParams;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
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

/**
 * Doctor management (RES-04/RES-05, UC-11). Thin controller like {@link SpecialtyController}; all
 * rules live in {@link DoctorService}.
 *
 * <p>No DELETE: doctors are referenced by schedules/visits, so they are deactivated via {@code
 * PATCH /{id}/status} instead.
 */
@RestController
@RequestMapping("/api/v1/doctors")
@Tag(name = "Bác sĩ", description = "Quản lý bác sĩ: hồ sơ, chuyên khoa, trạng thái hoạt động")
class DoctorController {

  private final DoctorService doctorService;

  DoctorController(DoctorService doctorService) {
    this.doctorService = doctorService;
  }

  @PostMapping
  @Operation(
      summary = "Tạo hồ sơ bác sĩ",
      description =
          "Gắn với tài khoản `userId` Admin đã cấp. Trạng thái ban đầu: WORKING."
              + " Phải có ít nhất 1 chuyên khoa đang hoạt động.")
  @ApiResponse(responseCode = "201", description = "Đã tạo; header Location trỏ tới tài nguyên mới")
  @ApiResponse(
      responseCode = "404",
      description =
          "DOCTOR_USER_NOT_FOUND — không có tài khoản `userId`;"
              + " SPECIALTY_NOT_FOUND — có id chuyên khoa không tồn tại",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "DOCTOR_CODE_EXISTS — mã bác sĩ đã tồn tại;"
              + " DOCTOR_USER_ALREADY_LINKED — tài khoản đã gắn với bác sĩ khác;"
              + " SPECIALTY_INACTIVE — chuyên khoa đã ngưng hoạt động",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ResponseEntity<ApiResult<DoctorResponse>> create(
      @Valid @RequestBody CreateDoctorRequest request) {
    DoctorResponse created = doctorService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/doctors/" + created.id()))
        .body(ApiResult.of(created));
  }

  @GetMapping
  @Operation(
      summary = "Danh sách bác sĩ (phân trang)",
      description =
          "Sắp xếp được theo: code, name, status, createdAt. Mặc định: name tăng dần."
              + " `meta.page` bắt đầu từ 1.")
  ApiResult<List<DoctorResponse>> list(
      @Parameter(description = "Tìm theo họ tên hoặc mã bác sĩ (chứa, không phân biệt hoa thường)")
          @RequestParam(required = false)
          String keyword,
      @Parameter(description = "Chỉ lấy bác sĩ thuộc chuyên khoa này")
          @RequestParam(required = false)
          Long specialtyId,
      @Parameter(description = "Lọc theo trạng thái; bỏ trống = tất cả")
          @RequestParam(required = false)
          EmployeeStatus status,
      @ParameterObject PageParams pageParams) {
    var pageable = pageParams.toPageable(DoctorService.SORTABLE_FIELDS, DoctorService.DEFAULT_SORT);
    return ApiResult.of(doctorService.list(keyword, specialtyId, status, pageable));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Chi tiết bác sĩ")
  @ApiResponse(
      responseCode = "404",
      description = "DOCTOR_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<DoctorResponse> getById(@PathVariable Long id) {
    return ApiResult.of(doctorService.getById(id));
  }

  @PutMapping("/{id}")
  @Operation(
      summary = "Cập nhật họ tên, học vị và danh sách chuyên khoa",
      description =
          "`specialtyIds` THAY THẾ toàn bộ danh sách cũ. Mã bác sĩ và tài khoản không đổi được.")
  @ApiResponse(
      responseCode = "404",
      description = "DOCTOR_NOT_FOUND; SPECIALTY_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  @ApiResponse(
      responseCode = "409",
      description =
          "SPECIALTY_INACTIVE — thêm chuyên khoa đã ngưng hoạt động;"
              + " DOCTOR_SPECIALTY_IN_USE — bỏ chuyên khoa đang có lịch làm việc",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<DoctorResponse> update(
      @PathVariable Long id, @Valid @RequestBody UpdateDoctorRequest request) {
    return ApiResult.of(doctorService.update(id, request));
  }

  @PatchMapping("/{id}/status")
  @Operation(
      summary = "Đổi trạng thái hoạt động (WORKING / NOT_WORKING)",
      description = "Bác sĩ NOT_WORKING không được phân công ca làm việc mới.")
  @ApiResponse(
      responseCode = "404",
      description = "DOCTOR_NOT_FOUND",
      content =
          @Content(mediaType = ApiDocs.PROBLEM_JSON, schema = @Schema(ref = ApiDocs.ERROR_SCHEMA)))
  ApiResult<DoctorResponse> changeStatus(
      @PathVariable Long id, @Valid @RequestBody UpdateDoctorStatusRequest request) {
    return ApiResult.of(doctorService.changeStatus(id, request));
  }
}
