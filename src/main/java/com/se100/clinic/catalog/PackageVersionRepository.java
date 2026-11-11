package com.se100.clinic.catalog;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface PackageVersionRepository
    extends JpaRepository<PackageVersion, Long>, JpaSpecificationExecutor<PackageVersion> {
  Optional<PackageVersion> findFirstByMedicalPackageOrderByEffectiveFromDesc(
      MedicalPackage medicalPackage);

  @Query(
      """
      select v from PackageVersion v where v.medicalPackage = :medicalPackage
      and v.effectiveFrom <= :now and (v.effectiveTo is null or v.effectiveTo > :now)
      order by v.effectiveFrom desc, v.id desc limit 1
      """)
  Optional<PackageVersion> findEffectiveVersion(MedicalPackage medicalPackage, Instant now);

  Page<PackageVersion> findAllByMedicalPackage(MedicalPackage medicalPackage, Pageable pageable);

  Optional<PackageVersion> findByMedicalPackageAndVersion(
      MedicalPackage medicalPackage, String version);

  @Override
  @EntityGraph(attributePaths = {"medicalPackage"})
  Page<PackageVersion> findAll(Specification<PackageVersion> spec, Pageable pageable);
}
