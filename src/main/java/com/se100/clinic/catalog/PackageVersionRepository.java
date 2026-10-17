package com.se100.clinic.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

interface PackageVersionRepository extends JpaRepository<PackageVersion, Long>, JpaSpecificationExecutor<PackageVersion> {
  Optional<PackageVersion> findFirstByMedicalPackageOrderByEffectiveFromDesc(
      MedicalPackage medicalPackage);
}
