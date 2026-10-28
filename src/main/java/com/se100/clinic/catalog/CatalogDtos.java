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

  private static final int MAX_AGE = 150;

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

  // Required = what the service cannot default. gender falls back to BOTH, effectiveFrom to now,
  // description/instruction/ages are nullable columns.
  @Schema(
      requiredProperties = {
        "code",
        "name",
        "status",
        "price",
        "packageClass",
        "consultationMinutes",
        "medicalItems"
        // TODO(specialties): add "specialtyIds"
      })
  public record MedicalPackageCreateDto(
      @NotBlank(message = "Mã gói khám không được để trống")
          @Size(max = 40, message = "Mã gói khám tối đa 40 ký tự")
          String code,
      @NotBlank(message = "Tên gói khám không được để trống")
          @Size(max = 200, message = "Tên gói khám tối đa 200 ký tự")
          String name,
      String description,
      TargetGender gender,
      @Min(value = 0, message = "Tuổi tối thiểu không được âm")
          @Max(value = MAX_AGE, message = "Tuổi tối thiểu không được vượt quá 150")
          Integer minAge,
      @Min(value = 0, message = "Tuổi tối đa không được âm")
          @Max(value = MAX_AGE, message = "Tuổi tối đa không được vượt quá 150")
          Integer maxAge,
      String instruction,
      @NotNull(message = "Trạng thái gói khám không được để trống") PackageStatus status,
      @NotNull(message = "Giá gói khám không được để trống")
          @PositiveOrZero(message = "Giá gói khám không được âm")
          @Digits(integer = 13, fraction = 2, message = "Giá gói khám tối đa 13 chữ số, 2 số lẻ")
          BigDecimal price,
      @NotNull(message = "Hạng gói khám không được để trống") PackageClass packageClass,
      @NotNull(message = "Thời lượng tư vấn không được để trống")
          @Positive(message = "Thời lượng tư vấn phải là số dương")
          Integer consultationMinutes,
      @NotNull(message = "Danh sách hạng mục không được để trống") @Valid
          MedicalPackageItemAddListDto medicalItems,
      Instant effectiveFrom
      // TODO(specialties): add `List<Long> specialtyIds` (required, at least one)
      ) implements PackageVersionFields {}

  /** Same limits as create; every field is optional and a missing one keeps the current value. */
  public record MedicalPackageUpdateDto(
      @Size(max = 200, message = "Tên gói khám tối đa 200 ký tự") String name,
      String description,
      TargetGender gender,
      @Min(value = 0, message = "Tuổi tối thiểu không được âm")
          @Max(value = MAX_AGE, message = "Tuổi tối thiểu không được vượt quá 150")
          Integer minAge,
      @Min(value = 0, message = "Tuổi tối đa không được âm")
          @Max(value = MAX_AGE, message = "Tuổi tối đa không được vượt quá 150")
          Integer maxAge,
      String instruction,
      PackageStatus status,
      @PositiveOrZero(message = "Giá gói khám không được âm")
          @Digits(integer = 13, fraction = 2, message = "Giá gói khám tối đa 13 chữ số, 2 số lẻ")
          BigDecimal price,
      PackageClass packageClass,
      @Positive(message = "Thời lượng tư vấn phải là số dương") Integer consultationMinutes,
      @Valid MedicalPackageItemAddListDto medicalItems,
      Instant effectiveFrom)
      implements PackageVersionFields {}

  public record MedicalPackageSearchRequestDto(
      @Size(max = 200, message = "keyword không được vượt quá 200 ký tự") String keyword,
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

  /** Full replacement of an item: description and instruction sent as null are cleared. */
  @Schema(requiredProperties = {"name", "type", "price"})
  public record MedicalItemUpdateDto(
      @NotBlank(message = "Tên hạng mục không được để trống")
          @Size(max = 200, message = "Tên hạng mục tối đa 200 ký tự")
          String name,
      @NotNull(message = "Loại hạng mục không được để trống") MedicalItemType type,
      @NotNull(message = "Giá hạng mục không được để trống")
          @PositiveOrZero(message = "Giá hạng mục không được âm")
          BigDecimal price,
      String description,
      String instruction) {}

  @Schema(requiredProperties = {"publicId", "name", "price", "type", "active"})
  public record MedicalItemResponseDto(
      UUID publicId,
      String name,
      BigDecimal price,
      MedicalItemType type,
      String description,
      String instruction,
      boolean active) {
    static MedicalItemResponseDto from(MedicalItem medicalItem) {
      return new MedicalItemResponseDto(
          medicalItem.getPublicId(),
          medicalItem.getName(),
          medicalItem.getPrice(),
          medicalItem.getType(),
          medicalItem.getDescription(),
          medicalItem.getInstruction(),
          medicalItem.isActive());
    }
  }

  public record MedicalItemSearchRequestDto(
      @Size(max = 200, message = "keyword không được vượt quá 200 ký tự") String keyword,
      MedicalItemType type,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      Boolean active) {}

  public record MedicalPackageItemAddDto(
      @NotNull(message = "medicalItemPublicId không được để trống") UUID medicalItemPublicId,
      @NotNull(message = "quantity không được để trống")
          @Positive(message = "quantity phải là số nguyên dương")
          Integer quantity,
      @NotNull(message = "position không được để trống")
          @Positive(message = "Vị trí phải là số dương")
          Integer position) {}

  /** New quantity and position of one item already in the package. */
  @Schema(requiredProperties = {"quantity", "position"})
  public record MedicalPackageItemUpdateDto(
      @NotNull(message = "quantity không được để trống")
          @Positive(message = "quantity phải là số nguyên dương")
          Integer quantity,
      @NotNull(message = "position không được để trống")
          @Positive(message = "Vị trí phải là số dương")
          Integer position) {}

  public record MedicalPackageItemAddListDto(
      @NotEmpty(message = "Danh sách hạng mục không được để trống")
          @Size(max = 100, message = "Mỗi lần chỉ thêm tối đa 100 hạng mục")
          List<@NotNull(message = "Hạng mục không được null") @Valid MedicalPackageItemAddDto>
              items) {}

  /** An item of one package version, as snapshotted when that version was created. */
  public record MedicalPackageItemResponseDto(
      UUID medicalItemPublicId,
      String name,
      String instruction,
      Integer quantity,
      Integer position) {
    static MedicalPackageItemResponseDto from(PackageVersionItem versionItem) {
      return new MedicalPackageItemResponseDto(
          versionItem.getItem().getPublicId(),
          versionItem.getItemNameSnapshot(),
          versionItem.getInstructionSnapshot(),
          versionItem.getQuantity(),
          versionItem.getPosition());
    }
  }

  /** One version of a package; {@code current} is the version that is in effect now. */
  @Schema(
      requiredProperties = {
        "version",
        "name",
        "gender",
        "status",
        "price",
        "packageClass",
        "consultationMinutes",
        "effectiveFrom",
        "current"
      })
  public record PackageVersionResponseDto(
      String version,
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
      Instant effectiveFrom,
      Instant effectiveTo,
      boolean current) {
    static PackageVersionResponseDto from(PackageVersion packageVersion) {
      return new PackageVersionResponseDto(
          packageVersion.getVersion(),
          packageVersion.getName(),
          packageVersion.getDescription(),
          packageVersion.getTarget(),
          packageVersion.getMinAge(),
          packageVersion.getMaxAge(),
          packageVersion.getInstruction(),
          packageVersion.getStatus(),
          packageVersion.getUnitPrice(),
          packageVersion.getPackageClass(),
          packageVersion.getConsultationMinutes(),
          packageVersion.getEffectiveFrom(),
          packageVersion.getEffectiveTo(),
          packageVersion.getEffectiveTo() == null);
    }
  }

  /** A version together with its items, in display order. */
  @Schema(
      requiredProperties = {
        "version",
        "name",
        "gender",
        "status",
        "price",
        "packageClass",
        "consultationMinutes",
        "effectiveFrom",
        "current",
        "medicalItems"
      })
  public record PackageVersionDetailResponseDto(
      String version,
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
      Instant effectiveFrom,
      Instant effectiveTo,
      boolean current,
      List<MedicalPackageItemResponseDto> medicalItems) {
    static PackageVersionDetailResponseDto from(
        PackageVersion packageVersion, List<PackageVersionItem> versionItems) {
      PackageVersionResponseDto base = PackageVersionResponseDto.from(packageVersion);
      return new PackageVersionDetailResponseDto(
          base.version(),
          base.name(),
          base.description(),
          base.gender(),
          base.minAge(),
          base.maxAge(),
          base.instruction(),
          base.status(),
          base.price(),
          base.packageClass(),
          base.consultationMinutes(),
          base.effectiveFrom(),
          base.effectiveTo(),
          base.current(),
          versionItems.stream().map(MedicalPackageItemResponseDto::from).toList());
    }
  }

  /** A package as it is now: its current version and that version's items. */
  @Schema(
      requiredProperties = {
        "publicId",
        "code",
        "name",
        "version",
        "gender",
        "status",
        "price",
        "packageClass",
        "consultationMinutes",
        "effectiveFrom",
        "medicalItems"
      })
  public record MedicalPackageDetailResponseDto(
      UUID publicId,
      String code,
      String name,
      String version,
      String description,
      TargetGender gender,
      Integer minAge,
      Integer maxAge,
      String instruction,
      PackageStatus status,
      BigDecimal price,
      PackageClass packageClass,
      Integer consultationMinutes,
      Instant effectiveFrom,
      List<MedicalPackageItemResponseDto> medicalItems) {
    static MedicalPackageDetailResponseDto from(
        MedicalPackage medicalPackage,
        PackageVersion packageVersion,
        List<PackageVersionItem> versionItems) {
      return new MedicalPackageDetailResponseDto(
          medicalPackage.getPublicId(),
          medicalPackage.getCode(),
          medicalPackage.getName(),
          packageVersion.getVersion(),
          packageVersion.getDescription(),
          packageVersion.getTarget(),
          packageVersion.getMinAge(),
          packageVersion.getMaxAge(),
          packageVersion.getInstruction(),
          packageVersion.getStatus(),
          packageVersion.getUnitPrice(),
          packageVersion.getPackageClass(),
          packageVersion.getConsultationMinutes(),
          packageVersion.getEffectiveFrom(),
          versionItems.stream().map(MedicalPackageItemResponseDto::from).toList());
    }
  }
}
