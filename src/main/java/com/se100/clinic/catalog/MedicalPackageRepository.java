package com.se100.clinic.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface MedicalPackageRepository
    extends JpaRepository<MedicalPackage, Long>, JpaSpecificationExecutor<MedicalPackage> {
  Optional<MedicalPackage> findByName(String name);

  Optional<MedicalPackage> findByPublicId(UUID publicId);
}
