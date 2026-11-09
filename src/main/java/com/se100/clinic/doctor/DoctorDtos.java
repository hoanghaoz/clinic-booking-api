package com.se100.clinic.doctor;

import com.se100.clinic.employee.enums.EmployeeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** DTOs of the Doctor API ({@code /api/v1/doctors}). Same conventions as {@link SpecialtyDtos}. */
public final class DoctorDtos {

  private DoctorDtos() {}

  public record CreateDoctorRequest(
      @Schema(description = "id tài khoản (module auth) đã được Admin cấp cho bác sĩ")
          @NotNull(message = "Tài khoản của bác sĩ không được để trống")
          Long userId,
      @NotBlank(message = "Mã bác sĩ không được để trống") @Size(max = 50) String code,
      @NotBlank(message = "Họ tên bác sĩ không được để trống") @Size(max = 150) String name,
      @NotBlank(message = "Học vị không được để trống") @Size(max = 100) String degree,
      @NotEmpty(message = "Bác sĩ phải có ít nhất một chuyên khoa")
          List<@NotNull(message = "id chuyên khoa không được để trống") Long> specialtyIds) {}

  /** Replaces name, degree and the WHOLE specialty list. Code and account cannot be changed. */
  public record UpdateDoctorRequest(
      @NotBlank(message = "Họ tên bác sĩ không được để trống") @Size(max = 150) String name,
      @NotBlank(message = "Học vị không được để trống") @Size(max = 100) String degree,
      @NotEmpty(message = "Bác sĩ phải có ít nhất một chuyên khoa")
          List<@NotNull(message = "id chuyên khoa không được để trống") Long> specialtyIds) {}

  public record UpdateDoctorStatusRequest(
      @NotNull(message = "Trạng thái không được để trống") EmployeeStatus status) {}

  /** Short specialty info embedded in a doctor. */
  @Schema(requiredProperties = {"id", "code", "name", "active"})
  public record DoctorSpecialtyResponse(Long id, String code, String name, boolean active) {

    static DoctorSpecialtyResponse from(Specialty specialty) {
      return new DoctorSpecialtyResponse(
          specialty.getId(), specialty.getCode(), specialty.getName(), specialty.isActive());
    }
  }

  @Schema(
      requiredProperties = {
        "id",
        "userId",
        "code",
        "name",
        "degree",
        "status",
        "specialties",
        "createdAt",
        "updatedAt"
      })
  public record DoctorResponse(
      Long id,
      Long userId,
      String code,
      String name,
      String degree,
      EmployeeStatus status,
      @Schema(description = "Chuyên khoa của bác sĩ, sắp xếp theo tên")
          List<DoctorSpecialtyResponse> specialties,
      Instant createdAt,
      Instant updatedAt) {

    static DoctorResponse from(Doctor doctor) {
      return new DoctorResponse(
          doctor.getId(),
          doctor.getUserId(),
          doctor.getCode(),
          doctor.getName(),
          doctor.getDegree(),
          doctor.getStatus(),
          doctor.getSpecialities().stream()
              .map(link -> DoctorSpecialtyResponse.from(link.getSpecialty()))
              .sorted(Comparator.comparing(DoctorSpecialtyResponse::name))
              .toList(),
          doctor.getCreatedAt(),
          doctor.getUpdatedAt());
    }
  }
}
