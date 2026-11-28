package com.se100.clinic.appointment;

import com.se100.clinic.appointment.AppointmentManagementDtos.ClinicOption;
import com.se100.clinic.appointment.AppointmentManagementDtos.Detail;
import com.se100.clinic.appointment.AppointmentManagementDtos.Group;
import com.se100.clinic.appointment.AppointmentManagementDtos.Summary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AppointmentManagementQueries {
  private static final String FROM =
      """
      from appointments a
      join patients p on p.id = a.patient_id
      join doctor_schedules s on s.id = a.schedule_id
      join doctors d on d.id = s.doctor_id
      join rooms r on r.id = s.room_id
      join clinics c on c.id = r.clinic_id
      """;

  private static final String STAFF_SCOPE =
      """
      exists (select 1 from clinic_staffs cs where cs.staff_id = :staff and cs.clinic_id = c.id)
      """;
  private static final String UPCOMING =
      """
      a.start_at >= :now and (a.status = 'CONFIRMED'
        or (a.status = 'HELD' and a.hold_expires_at > :now))
      """;
  private static final String POLICY_HOURS =
      """
      case when a.cancellation_policy_snapshot ->> 'freeCancellationHours' ~ '^[0-9]{1,6}$'
        then (a.cancellation_policy_snapshot ->> 'freeCancellationHours')::bigint else 24 end
      """;
  private static final String POLICY_REFUND =
      """
      case when a.cancellation_policy_snapshot ->> 'eligibleDepositRefundPercent' ~ '^[0-9]{1,3}$'
        then least((a.cancellation_policy_snapshot ->> 'eligibleDepositRefundPercent')::integer, 100)
        else 100 end
      """;
  private static final String FIELDS =
      """
      select a.public_id, a.code, a.status, a.start_at, a.end_at, a.hold_expires_at,
        c.id clinic_id, c.name clinic_name, d.name doctor_name, p.full_name,
        a.deposit_required, (%s) free_hours,
        coalesce((select sum(pay.amount) from payments pay
          join invoices i on i.id = pay.invoice_id
          where i.appointment_id = a.id and pay.kind = 'DEPOSIT' and pay.status = 'SUCCEEDED'), 0)
          deposit_paid
      """
          .formatted(POLICY_HOURS);

  private final JdbcClient db;

  Page<Summary> forPatient(Long patient, Group group, Instant now, Pageable page) {
    String filter =
        switch (group) {
          case UPCOMING -> UPCOMING;
          case COMPLETED -> "a.status = 'COMPLETED'";
          case CANCELLED -> "a.status = 'CANCELLED'";
          case OTHER ->
              "a.status not in ('COMPLETED', 'CANCELLED') and not coalesce(("
                  + UPCOMING
                  + "), false)";
        };
    String where = " where a.patient_id = :patient and (" + filter + ")";
    String sort =
        group == Group.UPCOMING
            ? " order by a.start_at, a.id"
            : " order by a.start_at desc, a.id desc";
    var rows =
        db.sql(FIELDS + FROM + where + sort + " limit :size offset :offset")
            .param("patient", patient)
            .param("now", utc(now))
            .param("size", page.getPageSize())
            .param("offset", page.getOffset())
            .query((rs, index) -> summary(rs, now, false))
            .list();
    long count =
        db.sql("select count(*) " + FROM + where)
            .param("patient", patient)
            .param("now", utc(now))
            .query(Long.class)
            .single();
    return new PageImpl<>(rows, page, count);
  }

  Page<Summary> forStaff(
      Long staff,
      Long clinic,
      String term,
      Instant dayStart,
      Instant dayEnd,
      boolean daily,
      Instant now,
      Pageable page) {
    String where =
        " where "
            + STAFF_SCOPE
            + """
        and c.id = :clinic
        and (:term = '' or upper(a.code) = upper(:term) or p.phone = :term or p.identity_number = :term)
        and (cast(:dayStart as timestamptz) is null or (a.start_at >= :dayStart and a.start_at < :dayEnd))
        and (not :daily or a.status in ('CONFIRMED','CHECKED_IN','COMPLETED','NO_SHOW'))
        """;
    var rows =
        staffQuery(
                FIELDS + FROM + where + " order by a.start_at, a.id limit :size offset :offset",
                staff,
                clinic,
                term,
                dayStart,
                dayEnd,
                daily)
            .param("size", page.getPageSize())
            .param("offset", page.getOffset())
            .query((rs, index) -> summary(rs, now, true))
            .list();
    long count =
        staffQuery("select count(*) " + FROM + where, staff, clinic, term, dayStart, dayEnd, daily)
            .query(Long.class)
            .single();
    return new PageImpl<>(rows, page, count);
  }

  private JdbcClient.StatementSpec staffQuery(
      String sql,
      Long staff,
      Long clinic,
      String term,
      Instant dayStart,
      Instant dayEnd,
      boolean daily) {
    return db.sql(sql)
        .param("staff", staff)
        .param("clinic", clinic)
        .param("term", term)
        .param(
            "dayStart",
            dayStart == null ? null : utc(dayStart),
            java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param(
            "dayEnd", dayEnd == null ? null : utc(dayEnd), java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        .param("daily", daily);
  }

  Optional<Detail> detail(UUID publicId, Instant now, Long employee) {
    return db.sql(
            FIELDS
                + """
        , a.qr_token_hash is not null has_qr, a.price_snapshot, a.preparation_snapshot,
        (%s) refund_percent, h.action, h.reason
        """
                    .formatted(POLICY_REFUND)
                + FROM
                + """
        left join lateral (
          select action, reason from appointment_histories h
          where h.appointment_id = a.id and h.action in ('CANCELLED', 'LATE_CANCELLED')
          order by h.id desc limit 1
        ) h on true where a.public_id = :id
        """
                + (employee == null ? "" : " and " + STAFF_SCOPE))
        .param("id", publicId)
        .param("staff", employee)
        .query(
            (rs, row) -> {
              Summary summary = summary(rs, now, employee != null);
              String action = rs.getString("action");
              Integer refund =
                  action == null
                      ? null
                      : "LATE_CANCELLED".equals(action) ? 0 : rs.getInt("refund_percent");
              return new Detail(
                  summary,
                  rs.getBoolean("has_qr") ? BookingQr.pngBase64(summary.code()) : null,
                  rs.getBigDecimal("price_snapshot"),
                  rs.getString("preparation_snapshot"),
                  refund,
                  rs.getString("reason"));
            })
        .optional();
  }

  List<ClinicOption> clinics(Long staff) {
    return db.sql(
            "select c.id, c.name from clinics c where " + STAFF_SCOPE + " order by c.name, c.id")
        .param("staff", staff)
        .query(ClinicOption.class)
        .list();
  }

  boolean assigned(Long staff, Long clinic) {
    return db.sql(
            "select exists(select 1 from clinic_staffs where staff_id = :staff and clinic_id = :clinic)")
        .param("staff", staff)
        .param("clinic", clinic)
        .query(Boolean.class)
        .single();
  }

  Optional<Long> patientId(UUID publicId) {
    return db.sql("select id from patients where public_id = :id")
        .param("id", publicId)
        .query(Long.class)
        .optional();
  }

  Long clinicOfSchedule(Long scheduleId) {
    return db.sql(
            "select r.clinic_id from doctor_schedules s join rooms r on r.id = s.room_id where s.id = :id")
        .param("id", scheduleId)
        .query(Long.class)
        .single();
  }

  Optional<Long> scheduleId(UUID publicId) {
    return db.sql("select id from doctor_schedules where public_id = :id")
        .param("id", publicId)
        .query(Long.class)
        .optional();
  }

  boolean hasReservations(Long id) {
    return db.sql(
            "select exists(select 1 from appointment_slots where appointment_id = :id and released_at is null)")
        .param("id", id)
        .query(Boolean.class)
        .single();
  }

  long cancellationHours(Long id) {
    return db.sql("select " + POLICY_HOURS + " from appointments a where a.id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private static Summary summary(ResultSet rs, Instant now, boolean staff) throws SQLException {
    String status = rs.getString("status");
    Instant start = rs.getObject("start_at", OffsetDateTime.class).toInstant();
    OffsetDateTime hold = rs.getObject("hold_expires_at", OffsetDateTime.class);
    String display =
        switch (status) {
          case "HELD" -> hold == null || !hold.toInstant().isAfter(now) ? "EXPIRED" : "HELD";
          case "CONFIRMED" -> start.isBefore(now) ? "OVERDUE" : "UPCOMING";
          default -> status;
        };
    return new Summary(
        rs.getObject("public_id", UUID.class),
        rs.getString("code"),
        status,
        display,
        start,
        rs.getObject("end_at", OffsetDateTime.class).toInstant(),
        rs.getLong("clinic_id"),
        rs.getString("clinic_name"),
        rs.getString("doctor_name"),
        staff ? rs.getString("full_name") : null,
        rs.getBigDecimal("deposit_required"),
        rs.getBigDecimal("deposit_paid"),
        rs.getLong("free_hours"));
  }

  private static OffsetDateTime utc(Instant value) {
    return value.atOffset(ZoneOffset.UTC);
  }
}
