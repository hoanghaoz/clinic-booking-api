package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
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
        "effectiveFrom"
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
      Instant effectiveFrom)
      implements PackageVersionFields {}

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
      implements PackageVersionFields {}

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
      Instant effectiveFrom) {
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
}
