package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

  private CatalogDtos() {}

  /** Fields client send to server , both create and update. */
  interface PackageVersionFields {
    String name();

    String description();

    TargetGender gender();

    Integer minAge();

    Integer maxAge();

    String instruction();

    PackageStatus status();

    BigDecimal price();

    PackageClass packageClass();

    Integer consultationMinutes();

    Instant effectiveFrom();
  }

  @Schema(
      requiredProperties = {
        "code",
        "name",
        "description",
        "gender",
        "instruction",
        "status",
        "price",
        "packageClass",
        "consultationMinutes",
        "items",
        "effectiveFrom"
        // TODO(specialties): add "specialtyIds"
      })
  public record MedicalPackageCreateDto(
      String code,
      String name,
      String description,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      String instruction,
      PackageStatus status,
      BigDecimal price,
      PackageClass packageClass,
      Integer consultationMinutes,
      MedicalPackageItemAddListDto medicalItems,
      Instant effectiveFrom
      // TODO(specialties): add `List<Long> specialtyIds` (required, at least one)
      ) implements PackageVersionFields {}

  public record MedicalPackageUpdateDto(
      String name,
      String description,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      String instruction,
      PackageStatus status,
      BigDecimal price,
      PackageClass packageClass,
      MedicalPackageItemAddListDto medicalItems,
      Integer consultationMinutes,
      Instant effectiveFrom)
      implements PackageVersionFields {}

  public record MedicalPackageSearchRequestDto(
      String keyword,
      PackageStatus status,
      TargetGender gender,
      PackageClass packageClass,
      Integer minAge,
      Integer maxAge,
      // TODO(specialties): add `List<Long> specialtyIds` (optional, match any)
      BigDecimal minPrice,
      BigDecimal maxPrice) {}

  public record MedicalPackageResponseDto(
      UUID publicId,
      String code,
      String name,
      String description,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      String instruction,
      PackageStatus status,
      BigDecimal price,
      PackageClass packageClass,
      Integer consultationMinutes,
      Instant effectiveFrom
      // TODO(specialties): add `List<Long> specialtyIds`
      ) {
    static MedicalPackageResponseDto from(
        MedicalPackage medicalPackage, PackageVersion packageVersion) {
      return new MedicalPackageResponseDto(
          medicalPackage.getPublicId(),
          medicalPackage.getCode(),
          medicalPackage.getName(),
          packageVersion.getDescription(),
          packageVersion.getTarget(),
          packageVersion.getMinAge(),
          packageVersion.getMaxAge(),
          packageVersion.getInstruction(),
          packageVersion.getStatus(),
          packageVersion.getUnitPrice(),
          packageVersion.getPackageClass(),
          packageVersion.getConsultationMinutes(),
          packageVersion.getEffectiveFrom());
    }
  }

  @Schema(requiredProperties = {"name", "type", "price"})
  public record MedicalItemCreateDto(
      @NotBlank(message = "Tên hạng mục không được để trống")
          @Size(max = 200, message = "Tên hạng mục tối đa 200 ký tự")
          String name,
      @NotNull(message = "Loại hạng mục không được để trống") MedicalItemType type,
      @NotNull(message = "Giá hạng mục không được để trống")
          @PositiveOrZero(message = "Giá hạng mục không được âm")
          BigDecimal price,
      String description,
      String instruction) {}

  public record MedicalItemCreateBatchDto(
      @NotEmpty(message = "Danh sách hạng mục không được để trống")
          @Size(max = 100, message = "Mỗi lần chỉ tạo tối đa 100 hạng mục")
          List<@NotNull(message = "Hạng mục không được null") @Valid MedicalItemCreateDto> items) {}

  public record MedicalItemResponseDto(
      UUID publicId,
      String name,
      BigDecimal price,
      MedicalItemType type,
      String description,
      String instruction) {
    static MedicalItemResponseDto from(MedicalItem medicalItem) {
      return new MedicalItemResponseDto(
          medicalItem.getPublicId(),
          medicalItem.getName(),
          medicalItem.getPrice(),
          medicalItem.getType(),
          medicalItem.getDescription(),
          medicalItem.getInstruction());
    }
  }

  public record MedicalItemSearchRequestDto(
      @Size(max = 200, message = "keyword không được vượt quá 200 ký tự") String keyword,
      MedicalItemType type,
      BigDecimal minPrice,
      BigDecimal maxPrice) {}

  public record MedicalPackageItemAddDto(
      @NotNull(message = "medicalItemPublicId không được để trống") UUID medicalItemPublicId,
      @NotNull(message = "quantity không được để trống")
      @Positive(message = "quantity phải là số nguyên dương") Integer quantity,

      @NotNull(message = "position không được để trống")
      @Positive(message = "Vị trí phải là số dương") Integer position
  ){}

  public record MedicalPackageItemAddListDto(
      @NotEmpty(message = "Danh sách hạng mục không được để trống")
      @Size(max = 100, message = "Mỗi lần chỉ thêm tối đa 100 hạng mục")
      List<@NotNull(message = "Hạng mục không được null") @Valid MedicalPackageItemAddDto> items) {}
}
