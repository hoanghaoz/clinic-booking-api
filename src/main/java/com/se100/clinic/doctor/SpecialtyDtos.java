package com.se100.clinic.doctor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Gom tất cả DTO của module vào 1 file dùng {@code record} — mỗi record là 1 "khuôn dữ liệu" bất
 * biến (immutable), Java tự sinh constructor/getter/{@code equals}/{@code hashCode}/{@code
 * toString}, không cần Lombok.
 *
 * <p>So sánh: 1 {@code record} ≈ 1 class DTO của NestJS có {@code class-validator} decorator (vd.
 * {@code @IsNotEmpty() name: string}), hoặc 1 {@code record}/{@code class} dùng {@code
 * DataAnnotations} của C# (vd. {@code [Required] public string Name}). Bean Validation ({@code
 * jakarta.validation.constraints.*}) đóng vai trò giống {@code class-validator}/{@code
 * DataAnnotations} — controller khai báo {@code @Valid} trên tham số, Spring tự validate trước khi
 * vào method.
 *
 * <p>Public vì {@code SpecialtyResponse} có thể cần dùng lại ở module khác (vd. module {@code
 * doctor} phần Bác sĩ sau này trả kèm thông tin chuyên khoa).
 */
public final class SpecialtyDtos {

  private SpecialtyDtos() {}

  public record CreateSpecialtyRequest(
      @NotBlank(message = "Mã chuyên khoa không được để trống") @Size(max = 50) String code,
      @NotBlank(message = "Tên chuyên khoa không được để trống") @Size(max = 200) String name,
      String description) {}

  public record UpdateSpecialtyRequest(
      @NotBlank(message = "Tên chuyên khoa không được để trống") @Size(max = 200) String name,
      String description) {}

  public record SpecialtyResponse(
      Long id,
      String code,
      String name,
      String description,
      boolean active,
      Instant createdAt,
      Instant updatedAt) {

    static SpecialtyResponse from(Specialty specialty) {
      return new SpecialtyResponse(
          specialty.getId(),
          specialty.getCode(),
          specialty.getName(),
          specialty.getDescription(),
          specialty.isActive(),
          specialty.getCreatedAt(),
          specialty.getUpdatedAt());
    }
  }
}
