package com.se100.clinic.catalog;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicalPackageRepository extends JpaRepository<MedicalPackage, Long> {
  Optional<MedicalPackage> findByName(String name);

  Optional<MedicalPackage> findByPublicId(UUID publicId);
}
