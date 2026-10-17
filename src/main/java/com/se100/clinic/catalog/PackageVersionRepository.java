package com.se100.clinic.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PackageVersionRepository extends JpaRepository<PackageVersion, Long> {
  Optional<PackageVersion> findFirstByMedicalPackageOrderByEffectiveFromDesc(
      MedicalPackage medicalPackage);
}
