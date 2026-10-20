package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

  private CatalogDtos() {
  }

  /**
   * Fields client send to server , both create and update.
   */
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
    Instant effectiveFrom
    // TODO(specialties): add `List<Long> specialtyIds` (required, at least one)
  ) implements PackageVersionFields {
  }

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
    Integer consultationMinutes,
    Instant effectiveFrom)
    implements PackageVersionFields {
  }

  public record MedicalPackageSearchRequestDto(
    String keyword,
    PackageStatus status,
    TargetGender gender,
    PackageClass packageClass,
    Integer minAge,
    Integer maxAge,
    // TODO(specialties): add `List<Long> specialtyIds` (optional, match any)
    BigDecimal minPrice,
    BigDecimal maxPrice) {
  }

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

  public record MedicalItemCreateDto(
    String name,
    MedicalItemType medicalItemType,
    BigDecimal price,
    String description,
    String instruction) {}

  public record MedicalItemCreateBatchDto(
    List<@Valid MedicalItemCreateDto> items
  ){}

  public record MedicalItemResponseDto(
    UUID publicId,
    String name,
    BigDecimal price,
    MedicalItemType medicalItemType,
    String description,
    String instruction
  ) {
    static MedicalItemResponseDto from(MedicalItem medicalItem) {
      return new MedicalItemResponseDto(
        medicalItem.getPublicId(),
        medicalItem.getName(),
        medicalItem.getPrice(),
        medicalItem.getType(),
        medicalItem.getDescription(),
        medicalItem.getInstruction()
      );
    }
  }
}
