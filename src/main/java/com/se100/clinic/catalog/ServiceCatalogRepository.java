package com.se100.clinic.catalog;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface ServiceCatalogRepository extends JpaRepository<MedicalService, Long> {
  Optional<MedicalService> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_READ)
  Optional<MedicalService> findForBookingByPublicId(UUID publicId);

  Page<MedicalService> findByDurationMinutesIn(Collection<Integer> durations, Pageable pageable);
}
