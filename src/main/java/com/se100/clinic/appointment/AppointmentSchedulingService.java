package com.se100.clinic.appointment;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Booking facts used by schedule availability. A released slot never reduces availability. */
@Service
@RequiredArgsConstructor
public class AppointmentSchedulingService {
  private final EntityManager entityManager;

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
}
