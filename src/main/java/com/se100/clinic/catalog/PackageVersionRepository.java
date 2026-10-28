package com.se100.clinic.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface PackageVersionRepository
    extends JpaRepository<PackageVersion, Long>, JpaSpecificationExecutor<PackageVersion> {
  Optional<PackageVersion> findFirstByMedicalPackageOrderByEffectiveFromDesc(
      MedicalPackage medicalPackage);

  Page<PackageVersion> findAllByMedicalPackage(MedicalPackage medicalPackage, Pageable pageable);

  Optional<PackageVersion> findByMedicalPackageAndVersion(
      MedicalPackage medicalPackage, String version);

  @Override
  @EntityGraph(attributePaths = {"medicalPackage"})
  Page<PackageVersion> findAll(Specification<PackageVersion> spec, Pageable pageable);
}
