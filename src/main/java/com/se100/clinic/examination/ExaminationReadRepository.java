package com.se100.clinic.examination;

import com.se100.clinic.examination.ExaminationDtos.AppointmentSummary;
import com.se100.clinic.examination.ExaminationDtos.OrderSummary;
import com.se100.clinic.examination.ExaminationDtos.PatientSummary;
import com.se100.clinic.examination.ExaminationDtos.PreviousResult;
import com.se100.clinic.examination.ExaminationDtos.PreviousVisit;
import com.se100.clinic.examination.ExaminationDtos.QueueItem;
import com.se100.clinic.examination.ExaminationDtos.ScheduleSummary;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalResultStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only queries that join the examination tables with appointments, schedules and patients.
 * Plain SQL (like {@code AppointmentSchedulingService}) because the modules do not share JPA
 * relations; the visit itself is written only through {@link MedicalVisitRepository}.
 */
@Repository
class ExaminationReadRepository {

  /** Facts about a visit that live outside the examination module. */
  record VisitContext(
      Long scheduleDoctorId,
      PatientSummary patient,
      AppointmentSummary appointment,
      ScheduleSummary schedule) {}

  private final JdbcTemplate jdbc;

  ExaminationReadRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Received patients of one doctor. Reception order = {@code received_at}, ties broken by visit id
   * so the order never changes between reloads. The queue number is computed before the status
   * filter, so filtering does not renumber the patients.
   */
  List<QueueItem> queue(
      Long doctorId, UUID schedulePublicId, LocalDate workDate, MedicalVisitStatus status) {
    StringBuilder sql =
        new StringBuilder(
            """
            select * from (
              select row_number() over (partition by ds.id order by v.received_at, v.id) as queue_number,
                     v.id as visit_id, v.public_id as visit_public_id, v.code as visit_code,
                     v.status as visit_status, v.received_at,
                     p.public_id as patient_public_id, p.full_name as patient_name,
                     a.public_id as appointment_public_id, a.code as appointment_code,
                     ds.public_id as schedule_public_id, ds.work_date, ds.start_at as shift_start,
                     sh.name as shift_name, r.name as room_name
              from medical_visits v
              join appointments a on a.id = v.appointment_id
              join doctor_schedules ds on ds.id = a.schedule_id
              join patients p on p.id = v.patient_id
              join shifts sh on sh.id = ds.shift_id
              join rooms r on r.id = ds.room_id
              where coalesce(v.primary_doctor_id, ds.doctor_id) = ?
                and v.status <> 'CANCELLED'
                and a.status <> 'CANCELLED'
            """);
    List<Object> params = new ArrayList<>();
    params.add(doctorId);
    if (schedulePublicId != null) {
      sql.append("    and ds.public_id = ?\n");
      params.add(schedulePublicId);
    }
    if (workDate != null) {
      sql.append("    and ds.work_date = ?\n");
      params.add(workDate);
    }
    sql.append(") q\n");
    if (status != null) {
      sql.append("where q.visit_status = ?\n");
      params.add(status.name());
    }
    sql.append("order by q.shift_start, q.received_at, q.visit_id");
    return jdbc.query(
        sql.toString(),
        (rs, rowNum) ->
            new QueueItem(
                rs.getInt("queue_number"),
                rs.getObject("visit_public_id", UUID.class),
                rs.getString("visit_code"),
                MedicalVisitStatus.valueOf(rs.getString("visit_status")),
                instant(rs.getTimestamp("received_at")),
                rs.getObject("patient_public_id", UUID.class),
                rs.getString("patient_name"),
                rs.getObject("appointment_public_id", UUID.class),
                rs.getString("appointment_code"),
                rs.getObject("schedule_public_id", UUID.class),
                rs.getObject("work_date", LocalDate.class),
                rs.getString("shift_name"),
                rs.getString("room_name")),
        params.toArray());
  }

  Optional<Long> scheduleDoctorId(Long appointmentId) {
    return jdbc
        .query(
            """
            select ds.doctor_id
            from appointments a join doctor_schedules ds on ds.id = a.schedule_id
            where a.id = ?
            """,
            (rs, rowNum) -> rs.getLong("doctor_id"),
            appointmentId)
        .stream()
        .findFirst();
  }

  Optional<VisitContext> context(Long visitId) {
    return jdbc
        .query(
            """
            select ds.doctor_id as schedule_doctor_id,
                   p.public_id as patient_public_id, p.full_name, p.date_of_birth, p.gender, p.phone,
                   a.public_id as appointment_public_id, a.code as appointment_code, a.start_at,
                   pv.name as package_name, ms.name as service_name,
                   ds.public_id as schedule_public_id, ds.work_date,
                   sh.name as shift_name, r.name as room_name
            from medical_visits v
            join appointments a on a.id = v.appointment_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join patients p on p.id = v.patient_id
            join shifts sh on sh.id = ds.shift_id
            join rooms r on r.id = ds.room_id
            left join package_versions pv on pv.id = a.package_version_id
            left join medical_services ms on ms.id = a.service_id
            where v.id = ?
            """,
            (rs, rowNum) ->
                new VisitContext(
                    rs.getLong("schedule_doctor_id"),
                    new PatientSummary(
                        rs.getObject("patient_public_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getObject("date_of_birth", LocalDate.class),
                        rs.getString("gender"),
                        rs.getString("phone")),
                    new AppointmentSummary(
                        rs.getObject("appointment_public_id", UUID.class),
                        rs.getString("appointment_code"),
                        instant(rs.getTimestamp("start_at")),
                        rs.getString("package_name"),
                        rs.getString("service_name")),
                    new ScheduleSummary(
                        rs.getObject("schedule_public_id", UUID.class),
                        rs.getObject("work_date", LocalDate.class),
                        rs.getString("shift_name"),
                        rs.getString("room_name"))),
            visitId)
        .stream()
        .findFirst();
  }

  List<OrderSummary> orders(Long visitId) {
    return jdbc.query(
        """
        select public_id, code, item_name_snapshot, source, status
        from medical_orders where visit_id = ? order by id
        """,
        (rs, rowNum) ->
            new OrderSummary(
                rs.getObject("public_id", UUID.class),
                rs.getString("code"),
                rs.getString("item_name_snapshot"),
                MedicalOrderSource.valueOf(rs.getString("source")),
                MedicalOrderStatus.valueOf(rs.getString("status"))),
        visitId);
  }

  /** Older, non-cancelled visits of the patient, newest first, each with its verified results. */
  List<PreviousVisit> previousVisits(
      Long patientId, Long excludeVisitId, Instant before, int limit) {
    record Row(Long id, PreviousVisit visit) {}
    List<Row> rows =
        jdbc.query(
            """
            select id, public_id, code, status, received_at, symptoms, initial_diagnosis,
                   final_diagnosis, conclusion
            from medical_visits
            where patient_id = ? and id <> ? and status <> 'CANCELLED' and received_at < ?
            order by received_at desc, id desc
            limit ?
            """,
            (rs, rowNum) ->
                new Row(
                    rs.getLong("id"),
                    new PreviousVisit(
                        rs.getObject("public_id", UUID.class),
                        rs.getString("code"),
                        MedicalVisitStatus.valueOf(rs.getString("status")),
                        instant(rs.getTimestamp("received_at")),
                        rs.getString("symptoms"),
                        rs.getString("initial_diagnosis"),
                        rs.getString("final_diagnosis"),
                        rs.getString("conclusion"),
                        List.of())),
            patientId,
            excludeVisitId,
            Timestamp.from(before),
            limit);
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<Long, List<PreviousResult>> resultsByVisit = results(rows.stream().map(Row::id).toList());
    return rows.stream()
        .map(
            row -> {
              PreviousVisit v = row.visit();
              return new PreviousVisit(
                  v.publicId(),
                  v.code(),
                  v.status(),
                  v.receivedAt(),
                  v.symptoms(),
                  v.initialDiagnosis(),
                  v.finalDiagnosis(),
                  v.conclusion(),
                  resultsByVisit.getOrDefault(row.id(), List.of()));
            })
        .toList();
  }

  /**
   * Only VERIFIED / RELEASED results: a DRAFT result is not final and must not guide a diagnosis.
   */
  private Map<Long, List<PreviousResult>> results(List<Long> visitIds) {
    String placeholders = String.join(",", visitIds.stream().map(id -> "?").toList());
    Map<Long, List<PreviousResult>> byVisit = new HashMap<>();
    jdbc.query(
        """
        select visit_id, public_id, item_name_snapshot, result_status, result_text,
               result_conclusion, performed_at
        from medical_orders
        where visit_id in (%s) and result_status in ('VERIFIED', 'RELEASED')
        order by performed_at nulls last, id
        """
            .formatted(placeholders),
        rs -> {
          byVisit
              .computeIfAbsent(rs.getLong("visit_id"), id -> new ArrayList<>())
              .add(
                  new PreviousResult(
                      rs.getObject("public_id", UUID.class),
                      rs.getString("item_name_snapshot"),
                      MedicalResultStatus.valueOf(rs.getString("result_status")),
                      rs.getString("result_text"),
                      rs.getString("result_conclusion"),
                      instant(rs.getTimestamp("performed_at"))));
        },
        visitIds.toArray());
    return byVisit;
  }

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
