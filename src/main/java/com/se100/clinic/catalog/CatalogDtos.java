package com.se100.clinic.catalog;

import com.se100.clinic.catalog.enums.PackageClass;
import com.se100.clinic.catalog.enums.PackageStatus;
import com.se100.clinic.catalog.enums.TargetGender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public final class CatalogDtos {

  private CatalogDtos() {}

  @Schema(requiredProperties = {"code", "name", "description", "gender", "instruction", "status", "price", "packageClass", "consultationMinutes", "effectiveFrom"})
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
  ){}
}
