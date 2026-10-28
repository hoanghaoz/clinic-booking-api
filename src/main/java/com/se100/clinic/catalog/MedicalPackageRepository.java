package com.se100.clinic.catalog;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

interface MedicalPackageRepository
    extends JpaRepository<MedicalPackage, Long>, JpaSpecificationExecutor<MedicalPackage> {
  Optional<MedicalPackage> findByName(String name);

  Optional<MedicalPackage> findByPublicId(UUID publicId);

  /**
   * Locks the package row ({@code SELECT ... FOR UPDATE}) until the transaction ends. Writers that
   * roll a new version take this first, so concurrent writers of one package run one after the
   * other and each reads the version the previous one created. Waits at most 3 seconds.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select p from MedicalPackage p where p.publicId = :publicId")
  Optional<MedicalPackage> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

  Optional<MedicalPackage> findByCode(String code);
}
