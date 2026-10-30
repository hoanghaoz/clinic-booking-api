package com.se100.clinic.appointment;

import com.se100.clinic.schedule.ScheduleService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.NotFoundException;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal booking API. Caller owns appointment validation, status/history, and the transaction.
 */
@Service
@RequiredArgsConstructor
public class AppointmentReservationService {
  private final ScheduleService schedules;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;

  @Transactional(propagation = Propagation.MANDATORY)
  public void reserve(Long appointmentId) {
    entityManager.flush();
    ReservationAppointment candidate = appointment(appointmentId, false);
    List<Long> slotIds =
        schedules.lockSlotsForBooking(candidate.scheduleId(), candidate.start(), candidate.end());
    ReservationAppointment current = appointment(appointmentId, true);
    requireUnchanged(candidate, current);
    requireBookable(current);
    if (jdbc.queryForObject(
            "select count(*) from appointment_slots where appointment_id = ? and released_at is null",
            Integer.class,
            appointmentId)
        > 0) {
      throw new ConflictException(
          ReservationErrorCode.APPOINTMENT_ALREADY_RESERVED,
          "Lịch hẹn đã giữ chỗ; hãy dùng thao tác đổi lịch");
    }
    writeReservations(appointmentId, slotIds);
  }

  private static void requireBookable(ReservationAppointment current) {
    if (!(current.status().equals("CONFIRMED")
        || current.status().equals("HELD")
            && current.holdExpiresAt() != null
            && current.holdExpiresAt().isAfter(Instant.now()))) {
      throw new ConflictException(
          ReservationErrorCode.APPOINTMENT_NOT_BOOKABLE,
          "Lịch hẹn không ở trạng thái được giữ chỗ");
    }
  }

  /** Both schedules are locked in id order; failure rolls back the old time and reservations. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void move(Long appointmentId, Long scheduleId, Instant start, Instant end) {
    if (scheduleId == null || start == null || end == null) {
      throw new BusinessException(
          com.se100.clinic.schedule.ScheduleErrorCode.INVALID_WINDOW,
          "Thiếu ca hoặc thời gian khám mới");
    }
    entityManager.flush();
    ReservationAppointment candidate = appointment(appointmentId, false);
    for (Long id :
        java.util.stream.Stream.of(candidate.scheduleId(), scheduleId)
            .distinct()
            .sorted()
            .toList()) {
      schedules.lockForBookingChange(id);
    }
    ReservationAppointment current = appointment(appointmentId, true);
    requireUnchanged(candidate, current);
    requireBookable(current);
    jdbc.update(
        "update appointment_slots set released_at = now() where appointment_id = ? and released_at is null",
        appointmentId);
    Appointment appointment = entityManager.find(Appointment.class, appointmentId);
    entityManager.refresh(appointment);
    appointment.setScheduleId(scheduleId);
    appointment.setStartAt(start);
    appointment.setEndAt(end);
    // Target validation, the time change and reservation writes share the outer transaction.
    reserve(appointmentId);
  }

  private void writeReservations(Long appointmentId, List<Long> slotIds) {
    try {
      for (Long slotId : slotIds) {
        jdbc.update(
            """
            insert into appointment_slots (appointment_id, slot_id, reserved_at, released_at)
            values (?, ?, now(), null)
            on conflict (appointment_id, slot_id) do update
            set reserved_at = excluded.reserved_at, released_at = null
            where appointment_slots.released_at is not null
            """,
            appointmentId,
            slotId);
      }
    } catch (DataIntegrityViolationException error) {
      if (DbErrors.isUniqueViolation(error)) {
        throw new ConflictException(
            ReservationErrorCode.SLOT_OCCUPIED, "Khung giờ đã có người giữ chỗ");
      }
      throw error;
    }
  }

  /** Set CANCELLED/EXPIRED and write history in the same outer transaction before releasing. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void release(Long appointmentId) {
    entityManager.flush();
    ReservationAppointment candidate = appointment(appointmentId, false);
    schedules.lockForBookingChange(candidate.scheduleId());
    ReservationAppointment current = appointment(appointmentId, true);
    requireUnchanged(candidate, current);
    if (!(current.status().equals("CANCELLED") || current.status().equals("EXPIRED"))) {
      throw new ConflictException(
          ReservationErrorCode.APPOINTMENT_NOT_RELEASABLE,
          "Chỉ nhả chỗ sau khi lịch hẹn đã hủy hoặc hết hạn");
    }
    jdbc.update(
        "update appointment_slots set released_at = now() where appointment_id = ? and released_at is null",
        appointmentId);
  }

  private ReservationAppointment appointment(Long id, boolean forUpdate) {
    List<ReservationAppointment> rows =
        jdbc.query(
            """
        select schedule_id, start_at, end_at, status, hold_expires_at
        from appointments where id = ?
        """
                + (forUpdate ? " for update" : ""),
            (rs, row) -> {
              Timestamp expires = rs.getTimestamp("hold_expires_at");
              return new ReservationAppointment(
                  rs.getLong("schedule_id"),
                  rs.getTimestamp("start_at").toInstant(),
                  rs.getTimestamp("end_at").toInstant(),
                  rs.getString("status"),
                  expires == null ? null : expires.toInstant());
            },
            id);
    if (rows.isEmpty()) {
      throw new NotFoundException(
          ReservationErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn");
    }
    return rows.getFirst();
  }

  private static void requireUnchanged(
      ReservationAppointment before, ReservationAppointment after) {
    if (!before.equals(after)) {
      throw new BusinessException(
          ReservationErrorCode.APPOINTMENT_CHANGED, "Lịch hẹn vừa thay đổi, vui lòng thử lại");
    }
  }

  private record ReservationAppointment(
      Long scheduleId, Instant start, Instant end, String status, Instant holdExpiresAt) {}
}
