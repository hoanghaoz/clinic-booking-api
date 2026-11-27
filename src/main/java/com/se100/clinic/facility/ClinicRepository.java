package com.se100.clinic.facility;

import com.se100.clinic.facility.enums.FacilityStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ClinicRepository extends JpaRepository<Clinic, Long>, JpaSpecificationExecutor<Clinic> {
  Optional<Clinic> findByPublicId(UUID publicId);

  Page<Clinic> findByStatus(FacilityStatus status, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select c from Clinic c where c.publicId = :publicId")
  Optional<Clinic> findForUpdate(@Param("publicId") UUID publicId);

  @Query(
      value =
          """
      select exists (
        select 1 from doctor_schedules s join rooms r on r.id = s.room_id
        where r.clinic_id = :clinicId and s.end_at > :now and s.status <> 'CANCELLED'
      )
      """,
      nativeQuery = true)
  boolean hasUpcomingSchedules(@Param("clinicId") Long clinicId, @Param("now") Instant now);
}
