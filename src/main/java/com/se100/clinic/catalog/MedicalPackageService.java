package com.se100.clinic.catalog;

import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static com.se100.clinic.catalog.CatalogDtos.*;

@Service
@RequiredArgsConstructor
public class MedicalPackageService {
  private static final String FIRST_VERSION = "v1.0";
  private static final int MAX_MINOR_VERSION = 10;

  private final MedicalPackageRepository medicalPackageRepository;
  private final PackageVersionRepository packageVersionRepository;

  private static PackageVersion createNewPackageVersion(
      PackageVersionFields dto, PackageVersion base, MedicalPackage medicalPackage) {
    PackageVersion version = new PackageVersion();
    version.setMedicalPackage(medicalPackage);
    version.setCode(medicalPackage.getCode());
    version.setVersion(nextVersion(base.getVersion()));
    version.setName(coalesce(dto.name(), base.getName()));
    version.setDescription(coalesce(dto.description(), base.getDescription()));
    version.setTarget(coalesce(dto.gender(), base.getTarget()));
    version.setMinAge(coalesce(dto.minAge(), base.getMinAge()));
    version.setMaxAge(coalesce(dto.maxAge(), base.getMaxAge()));
    version.setInstruction(coalesce(dto.instruction(), base.getInstruction()));
    version.setStatus(coalesce(dto.status(), base.getStatus()));
    version.setUnitPrice(coalesce(dto.price(), base.getUnitPrice()));
    version.setPackageClass(coalesce(dto.packageClass(), base.getPackageClass()));
    version.setConsultationMinutes(
        coalesce(dto.consultationMinutes(), base.getConsultationMinutes()));
    version.setEffectiveFrom(coalesce(dto.effectiveFrom(), Instant.now()));
    return version;
  }

  private static <T> T coalesce(T value, T fallback) {
    return value != null ? value : fallback;
  }

  /** {@code null} -> v1.0; v1.0 -> v1.1; ... v1.10 -> v2.0. */
  static String nextVersion(String lastVersion) {
    if (lastVersion == null) {
      return FIRST_VERSION;
    }
    int dot = lastVersion.lastIndexOf('.');
    int major = Integer.parseInt(lastVersion.substring(1, dot));
    int minor = Integer.parseInt(lastVersion.substring(dot + 1)) + 1;
    if (minor > MAX_MINOR_VERSION) {
      return "v" + (major + 1) + ".0";
    }
    return "v" + major + "." + minor;
  }

  @Transactional
  public MedicalPackageResponseDto createMedicalPackage(MedicalPackageCreateDto dto) {
    if (medicalPackageRepository.findByName(dto.name()).isPresent()) {
      throw new ConflictException("Medical package already exists");
    }
    MedicalPackage medicalPackage = new MedicalPackage();
    medicalPackage.setCode(dto.code());
    medicalPackage.setName(dto.name());
    medicalPackageRepository.save(medicalPackage);

    PackageVersion packageVersion =
        createNewPackageVersion(dto, new PackageVersion(), medicalPackage);
    packageVersionRepository.save(packageVersion);
    return MedicalPackageResponseDto.from(medicalPackage, packageVersion);
  }

  @Transactional
  public MedicalPackageResponseDto updateMedicalPackage(
      MedicalPackageUpdateDto dto, UUID packagePublicId) {
    MedicalPackage medicalPackage =
        medicalPackageRepository
            .findByPublicId(packagePublicId)
            .orElseThrow(() -> new NotFoundException(CatalogErrorCode.MEDICAL_PACKAGE_NOT_FOUND, "Package not found"));
    PackageVersion currentVersion =
        packageVersionRepository
            .findFirstByMedicalPackageOrderByEffectiveFromDesc(medicalPackage)
            .orElseThrow(() -> new NotFoundException(CatalogErrorCode.MEDICAL_PACKAGE_VERSION_NOT_FOUND, "Package version not found"));

    if (dto.name() != null) {
      medicalPackage.setName(dto.name());
    }
    PackageVersion newVersion = createNewPackageVersion(dto, currentVersion, medicalPackage);

    // old version ends exactly when the new one starts: no gap, no overlap
    currentVersion.setEffectiveTo(newVersion.getEffectiveFrom());
    packageVersionRepository.save(newVersion);

    return MedicalPackageResponseDto.from(medicalPackage, newVersion);
  }
}
