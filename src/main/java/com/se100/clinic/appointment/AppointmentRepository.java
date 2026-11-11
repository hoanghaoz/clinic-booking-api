package com.se100.clinic.appointment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AppointmentRepository extends JpaRepository<Appointment, Long> {
  String ACTIVE_FOR_BOOKING =
      """
      a.patientId = :patientId and
      (a.status in ('CONFIRMED', 'CHECKED_IN') or
       (a.status = 'HELD' and a.holdExpiresAt > :now))
      """;

  @Query(
      "select (count(a) > 0) from Appointment a where "
          + ACTIVE_FOR_BOOKING
          + " and a.startAt < :endAt and a.endAt > :startAt")
  boolean overlaps(Long patientId, Instant startAt, Instant endAt, Instant now);

  @Query(
      "select distinct a.scheduleId from Appointment a where "
          + ACTIVE_FOR_BOOKING
          + " and a.startAt >= :dayStart and a.startAt < :dayEnd")
  List<Long> activeScheduleIds(Long patientId, Instant dayStart, Instant dayEnd, Instant now);

  Optional<Appointment> findByPublicIdAndPatientId(UUID publicId, Long patientId);

  Optional<Appointment> findByQrTokenHash(String qrTokenHash);
}
