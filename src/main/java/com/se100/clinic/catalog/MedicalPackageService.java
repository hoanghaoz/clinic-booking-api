package com.se100.clinic.catalog;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.se100.clinic.catalog.CatalogDtos.*;

@Service
@RequiredArgsConstructor
public class MedicalPackageService {
  private final MedicalPackageRepository medicalPackageRepository;
  private final PackageVersionRepository packageVersionRepository;

  private static @NonNull PackageVersion createNewPackageVersion(MedicalPackageUpdateDto dto, PackageVersion packageVersion, MedicalPackage medicalPackage) {
    PackageVersion newPackageVersion = new PackageVersion();
    newPackageVersion.setCode(packageVersion.getCode());
    newPackageVersion.setName(dto.name() != null ? dto.name() : packageVersion.getName());
    newPackageVersion.setDescription(dto.description() != null ? dto.description() : packageVersion.getDescription());
    newPackageVersion.setTarget(dto.gender() != null ? dto.gender() : packageVersion.getTarget());
    newPackageVersion.setMinAge(dto.minAge() != null ? dto.minAge() : packageVersion.getMinAge());
    newPackageVersion.setMaxAge(dto.maxAge() != null ? dto.maxAge() : packageVersion.getMaxAge());
    newPackageVersion.setInstruction(dto.instruction() != null ? dto.instruction() : packageVersion.getInstruction());
    newPackageVersion.setStatus(dto.status() != null ? dto.status() : packageVersion.getStatus());
    newPackageVersion.setUnitPrice(dto.price() != null ? dto.price() : packageVersion.getUnitPrice());
    newPackageVersion.setPackageClass(dto.packageClass() != null ? dto.packageClass() : packageVersion.getPackageClass());
    newPackageVersion.setMedicalPackage(medicalPackage);
    newPackageVersion.setConsultationMinutes(dto.consultationMinutes() != null ? dto.consultationMinutes() : packageVersion.getConsultationMinutes());
    newPackageVersion.setEffectiveFrom(dto.effectiveFrom() != null ? dto.effectiveFrom() : packageVersion.getEffectiveFrom());
    return newPackageVersion;
  }

  @Transactional
  public MedicalPackageResponseDto createMedicalPackage(MedicalPackageCreateDto dto) {
    if(medicalPackageRepository.findByName(dto.name()).isPresent()){
      throw new ConflictException("Medical package already exists");
    }
    MedicalPackage medicalPackage = new MedicalPackage();
    PackageVersion packageVersion = new PackageVersion();
    medicalPackage.setCode(dto.code());
    medicalPackage.setName(dto.name());
    medicalPackageRepository.save(medicalPackage);

    packageVersion.setCode(dto.code());
    packageVersion.setDescription(dto.description());
    packageVersion.setTarget(dto.gender());
    packageVersion.setMinAge(dto.minAge());
    packageVersion.setMaxAge(dto.maxAge());
    packageVersion.setInstruction(dto.instruction());
    packageVersion.setStatus(dto.status());
    packageVersion.setUnitPrice(dto.price());
    packageVersion.setPackageClass(dto.packageClass());
    packageVersion.setMedicalPackage(medicalPackage);
    packageVersion.setConsultationMinutes(dto.consultationMinutes());
    packageVersion.setEffectiveFrom(dto.effectiveFrom());
    packageVersionRepository.save(packageVersion);
    return MedicalPackageResponseDto.from(medicalPackage, packageVersion);
  }

  @Transactional
  public MedicalPackageResponseDto updateMedicalPackage(MedicalPackageUpdateDto dto, UUID packagePublicId) {
    MedicalPackage medicalPackage = medicalPackageRepository.findByPublicId(packagePublicId).orElseThrow(() -> new NotFoundException("Package not found"));
    PackageVersion packageVersion = packageVersionRepository.findByCode(medicalPackage.getCode()).orElseThrow(() -> new NotFoundException("Package version not found"));

    // set new name for the medical package if the name is updated
    if(dto.name() != null && !dto.name().equals(medicalPackage.getName())) {
      medicalPackage.setName(dto.name());
      medicalPackageRepository.save(medicalPackage);
    }
    // create a new package version with updated fields, keeping the old values if the new ones are null
    PackageVersion newPackageVersion = createNewPackageVersion(dto, packageVersion, medicalPackage);

    // set the effectiveTo of the old package version to now
    packageVersion.setEffectiveTo(java.time.Instant.now());
    packageVersionRepository.save(packageVersion);
    packageVersionRepository.save(newPackageVersion);

    return MedicalPackageResponseDto.from(medicalPackage, newPackageVersion);
  }
}
