package com.se100.clinic.appointment;

import com.se100.clinic.appointment.enums.AppointmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booking facts used by schedule availability. A released slot never reduces availability. */
@Service
@RequiredArgsConstructor
public class AppointmentSchedulingService {
  private final EntityManager entityManager;
  private final JdbcTemplate jdbcTemplate;

  @Transactional(readOnly = true)
  public boolean hasAppointments(Long scheduleId) {
    Long count =
        entityManager
            .createQuery(
                "select count(a) from Appointment a where a.scheduleId = :scheduleId", Long.class)
            .setParameter("scheduleId", scheduleId)
            .getSingleResult();
    return count > 0;
  }

  @Transactional(readOnly = true)
  public ReservationSnapshot reservations(Long scheduleId) {
    List<AppointmentSlot> links =
        entityManager
            .createQuery(
                "select s from AppointmentSlot s join fetch s.appointment a "
                    + "where a.scheduleId = :scheduleId and s.releasedAt is null",
                AppointmentSlot.class)
            .setParameter("scheduleId", scheduleId)
            .getResultList();
    Set<Long> slotIds = new HashSet<>();
    Set<Long> generalAppointments = new HashSet<>();
    Set<Long> specialistAppointments = new HashSet<>();
    for (AppointmentSlot link : links) {
      slotIds.add(link.getId().getSlotId());
      Appointment appointment = link.getAppointment();
      long minutes = Duration.between(appointment.getStartAt(), appointment.getEndAt()).toMinutes();
      if (minutes == 20) {
        generalAppointments.add(appointment.getId());
      } else if (minutes == 30) {
        specialistAppointments.add(appointment.getId());
      }
    }
    return new ReservationSnapshot(
        Set.copyOf(slotIds), generalAppointments.size(), specialistAppointments.size());
  }

  public record ReservationSnapshot(
      Set<Long> occupiedSlotIds, int generalVisits, int specialistVisits) {}

  /** Patient list for a single assigned shift, ordered by actual reception time. */
  @Transactional(readOnly = true)
  public List<DoctorQueueItem> doctorQueue(Long scheduleId) {
    return jdbcTemplate.query(
        """
        select a.public_id as appointment_public_id, a.code as appointment_code,
               p.public_id as patient_public_id, p.full_name as patient_name,
               a.package_version_id, pv.name as package_name,
               a.service_id, ms.name as service_name,
               a.status, a.start_at, v.received_at
        from appointments a
        join patients p on p.id = a.patient_id
        left join package_versions pv on pv.id = a.package_version_id
        left join medical_services ms on ms.id = a.service_id
        left join medical_visits v on v.appointment_id = a.id
        where a.schedule_id = ?
          and a.status in ('CONFIRMED', 'CHECKED_IN', 'COMPLETED', 'NO_SHOW')
        order by case when v.received_at is null then 1 else 0 end,
                 v.received_at, a.start_at, a.id
        """,
        (rs, rowNum) -> {
          var receivedAt = rs.getTimestamp("received_at");
          return new DoctorQueueItem(
              rs.getObject("appointment_public_id", UUID.class),
              rs.getString("appointment_code"),
              rs.getObject("patient_public_id", UUID.class),
              rs.getString("patient_name"),
              rs.getObject("package_version_id", Long.class),
              rs.getString("package_name"),
              rs.getObject("service_id", Long.class),
              rs.getString("service_name"),
              AppointmentStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("start_at").toInstant(),
              receivedAt == null ? null : receivedAt.toInstant());
        },
        scheduleId);
  }

  @Schema(
      requiredProperties = {
        "appointmentPublicId",
        "appointmentCode",
        "patientPublicId",
        "patientName",
        "status",
        "startAt"
      })
  public record DoctorQueueItem(
      UUID appointmentPublicId,
      String appointmentCode,
      UUID patientPublicId,
      String patientName,
      Long packageVersionId,
      String packageName,
      Long serviceId,
      String serviceName,
      AppointmentStatus status,
      Instant startAt,
      Instant receivedAt) {}
}
