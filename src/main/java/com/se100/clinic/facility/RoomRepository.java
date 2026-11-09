package com.se100.clinic.facility;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
  Optional<Room> findByClinicIdAndPublicId(Long clinicId, UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select r from Room r where r.clinic.id = :clinicId and r.publicId = :publicId")
  Optional<Room> findForUpdate(@Param("clinicId") Long clinicId, @Param("publicId") UUID publicId);

  @Query(
      value =
          """
      select exists (
        select 1 from doctor_schedules s
        where s.room_id = :roomId and s.end_at > :now and s.status <> 'CANCELLED'
      )
      """,
      nativeQuery = true)
  boolean hasUpcomingSchedules(@Param("roomId") Long roomId, @Param("now") Instant now);
}
