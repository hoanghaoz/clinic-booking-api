package com.se100.clinic.schedule;

import com.se100.clinic.schedule.enums.ScheduleStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DoctorScheduleRepository
    extends JpaRepository<DoctorSchedule, Long>, JpaSpecificationExecutor<DoctorSchedule> {
  Optional<DoctorSchedule> findByPublicId(UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DoctorSchedule s where s.publicId = :publicId")
  Optional<DoctorSchedule> findForUpdateByPublicId(@Param("publicId") UUID publicId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from DoctorSchedule s where s.id = :id")
  Optional<DoctorSchedule> findForUpdateById(@Param("id") Long id);

  @Query(
      "select (count(s) > 0) from DoctorSchedule s where s.doctorId = :doctorId "
          + "and s.status <> :cancelled and s.startAt < :endAt and s.endAt > :startAt "
          + "and (:excludeId is null or s.id <> :excludeId)")
  boolean overlapsDoctor(
      @Param("doctorId") Long doctorId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt,
      @Param("cancelled") ScheduleStatus cancelled,
      @Param("excludeId") Long excludeId);

  @Query(
      "select (count(s) > 0) from DoctorSchedule s where s.roomId = :roomId "
          + "and s.status <> :cancelled and s.startAt < :endAt and s.endAt > :startAt "
          + "and (:excludeId is null or s.id <> :excludeId)")
  boolean overlapsRoom(
      @Param("roomId") Long roomId,
      @Param("startAt") Instant startAt,
      @Param("endAt") Instant endAt,
      @Param("cancelled") ScheduleStatus cancelled,
      @Param("excludeId") Long excludeId);
}
