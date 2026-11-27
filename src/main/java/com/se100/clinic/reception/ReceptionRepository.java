package com.se100.clinic.reception;

import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.reception.ReceptionDtos.ClinicRoomOption;
import com.se100.clinic.reception.ReceptionDtos.ReceptionListItem;
import com.se100.clinic.reception.ReceptionDtos.RoutedOrderResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * SQL queries and atomic writes for the reception module (REC-01..REC-04).
 *
 * <p>Uses {@link JdbcTemplate} so this module never imports package-private entities or
 * repositories from other modules, following the same cross-module pattern as {@code
 * ExaminationReadRepository}.
 */
@Repository
class ReceptionRepository {

  record StaffClinicRow(Long id, UUID publicId, String code, String name, String status) {}

  record RoomRow(Long id, UUID publicId, Long clinicId, String code, String name, String status) {}

  record AppointmentLookupRef(
      Long appointmentId,
      UUID appointmentPublicId,
      String code,
      Long patientId,
      UUID patientPublicId,
      Long clinicId) {}

  record AppointmentDetailRow(
      Long appointmentId,
      UUID appointmentPublicId,
      String code,
      AppointmentStatus status,
      Instant startAt,
      Instant endAt,
      Long packageVersionId,
      Long serviceId,
      BigDecimal priceSnapshot,
      BigDecimal depositRequired,
      BigDecimal paidAmount,
      Long scheduleId,
      LocalDate workDate,
      Instant shiftStartAt,
      Instant shiftEndAt,
      String shiftCode,
      String shiftName,
      Long clinicId,
      String clinicName,
      String clinicStatus,
      Long roomId,
      UUID roomPublicId,
      String roomCode,
      String roomName,
      String roomStatus,
      Long doctorId,
      String doctorName,
      String specialtyName,
      String packageName,
      String packageVersion,
      String serviceName,
      UUID existingVisitPublicId,
      String existingVisitCode,
      Instant existingVisitReceivedAt) {}

  record LockedAppointmentRow(
      Long appointmentId,
      UUID appointmentPublicId,
      String code,
      Long patientId,
      UUID patientPublicId,
      String patientName,
      String patientPhone,
      Long scheduleId,
      AppointmentStatus status,
      Instant startAt,
      Instant endAt,
      Long packageVersionId,
      Long serviceId,
      LocalDate workDate,
      Instant shiftStartAt,
      Instant shiftEndAt,
      String shiftName,
      Long clinicId,
      String clinicName,
      String clinicStatus,
      Long roomId,
      UUID roomPublicId,
      String roomCode,
      String roomName,
      String roomStatus,
      String doctorName,
      String packageName,
      String serviceName,
      Long existingVisitId) {}

  record CatalogItemRow(
      Long itemId,
      UUID itemPublicId,
      String itemName,
      MedicalItemType itemType,
      String instruction,
      BigDecimal unitPrice,
      int quantity) {}

  record VisitHeaderRow(
      Long visitId,
      UUID visitPublicId,
      String visitCode,
      MedicalVisitStatus visitStatus,
      Instant receivedAt,
      Long receivedByStaffId,
      String receivedByStaffName,
      Long clinicId,
      String clinicName,
      UUID appointmentPublicId,
      String appointmentCode,
      AppointmentStatus appointmentStatus,
      Instant appointmentStartAt,
      Instant appointmentEndAt,
      LocalDate workDate,
      UUID patientPublicId,
      String patientName,
      String patientPhone,
      String doctorName,
      String shiftName,
      String clinicalRoomName,
      String packageName,
      String serviceName) {}

  record LockedOrderRow(
      Long orderId,
      UUID orderPublicId,
      Long visitId,
      MedicalVisitStatus visitStatus,
      Long clinicId,
      MedicalOrderStatus orderStatus) {}

  private final JdbcTemplate jdbc;

  ReceptionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<StaffClinicRow> findStaffClinics(Long staffId) {
    return jdbc.query(
        """
        select c.id, c.public_id, c.code, c.name, c.status
        from clinic_staffs cs
        join clinics c on c.id = cs.clinic_id
        where cs.staff_id = ?
        order by c.name asc, c.id asc
        """,
        (rs, rowNum) ->
            new StaffClinicRow(
                rs.getLong("id"),
                rs.getObject("public_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status")),
        staffId);
  }

  Optional<String> findStaffName(Long staffId) {
    return jdbc
        .query(
            "select name from staffs where id = ?", (rs, rowNum) -> rs.getString("name"), staffId)
        .stream()
        .findFirst();
  }

  List<ClinicRoomOption> findOpenRoomsByClinic(Long clinicId) {
    return jdbc.query(
        """
        select id, public_id, code, name
        from rooms
        where clinic_id = ? and status = 'OPENING'
        order by code asc, id asc
        """,
        (rs, rowNum) ->
            new ClinicRoomOption(
                rs.getLong("id"),
                rs.getObject("public_id", UUID.class),
                rs.getString("code"),
                rs.getString("name")),
        clinicId);
  }

  Optional<RoomRow> findRoomByPublicId(UUID roomPublicId) {
    return jdbc
        .query(
            """
            select id, public_id, clinic_id, code, name, status
            from rooms
            where public_id = ?
            """,
            (rs, rowNum) ->
                new RoomRow(
                    rs.getLong("id"),
                    rs.getObject("public_id", UUID.class),
                    rs.getLong("clinic_id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("status")),
            roomPublicId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findPatientPublicIdById(Long patientId) {
    return jdbc
        .query(
            "select public_id from patients where id = ?",
            (rs, rowNum) -> rs.getObject("public_id", UUID.class),
            patientId)
        .stream()
        .findFirst();
  }

  Optional<AppointmentLookupRef> findAppointmentRefByCodeOrQrHash(String code, String qrTokenHash) {
    return jdbc
        .query(
            """
            select a.id as appointment_id, a.public_id as appointment_public_id, a.code,
                   a.patient_id, p.public_id as patient_public_id, r.clinic_id
            from appointments a
            join patients p on p.id = a.patient_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join rooms r on r.id = ds.room_id
            where upper(a.code) = upper(?) or a.qr_token_hash = ?
            limit 1
            """,
            (rs, rowNum) ->
                new AppointmentLookupRef(
                    rs.getLong("appointment_id"),
                    rs.getObject("appointment_public_id", UUID.class),
                    rs.getString("code"),
                    rs.getLong("patient_id"),
                    rs.getObject("patient_public_id", UUID.class),
                    rs.getLong("clinic_id")),
            code,
            qrTokenHash)
        .stream()
        .findFirst();
  }

  List<AppointmentDetailRow> findAppointmentsForPatient(UUID patientPublicId) {
    return jdbc.query(
        """
        select a.id as appointment_id, a.public_id as appointment_public_id, a.code as appointment_code,
               a.status as appointment_status, a.start_at, a.end_at,
               a.package_version_id, a.service_id,
               a.price_snapshot, a.deposit_required,
               coalesce((
                 select sum(pay.amount)
                 from invoices inv
                 join payments pay on pay.invoice_id = inv.id
                 where inv.appointment_id = a.id
                   and inv.status <> 'VOID'
                   and pay.status = 'SUCCEEDED'
               ), 0) as paid_amount,
               ds.id as schedule_id, ds.work_date,
               ds.start_at as shift_start_at, ds.end_at as shift_end_at,
               sh.code as shift_code, sh.name as shift_name,
               c.id as clinic_id, c.name as clinic_name, c.status as clinic_status,
               r.id as room_id, r.public_id as room_public_id, r.code as room_code,
               r.name as room_name, r.status as room_status,
               d.id as doctor_id, d.name as doctor_name,
               sp.name as specialty_name,
               pv.name as package_name, pv.version as package_version,
               ms.name as service_name,
               mv.public_id as visit_public_id, mv.code as visit_code,
               mv.received_at as visit_received_at
        from appointments a
        join patients p on p.id = a.patient_id
        join doctor_schedules ds on ds.id = a.schedule_id
        join shifts sh on sh.id = ds.shift_id
        join rooms r on r.id = ds.room_id
        join clinics c on c.id = r.clinic_id
        join doctors d on d.id = ds.doctor_id
        join specialties sp on sp.id = ds.specialty_id
        left join package_versions pv on pv.id = a.package_version_id
        left join medical_services ms on ms.id = a.service_id
        left join medical_visits mv on mv.appointment_id = a.id
        where p.public_id = ?
        order by a.start_at asc, a.id asc
        """,
        (rs, rowNum) ->
            new AppointmentDetailRow(
                rs.getLong("appointment_id"),
                rs.getObject("appointment_public_id", UUID.class),
                rs.getString("appointment_code"),
                AppointmentStatus.valueOf(rs.getString("appointment_status")),
                instant(rs.getTimestamp("start_at")),
                instant(rs.getTimestamp("end_at")),
                rs.getObject("package_version_id", Long.class),
                rs.getObject("service_id", Long.class),
                rs.getBigDecimal("price_snapshot"),
                rs.getBigDecimal("deposit_required"),
                rs.getBigDecimal("paid_amount"),
                rs.getLong("schedule_id"),
                rs.getObject("work_date", LocalDate.class),
                instant(rs.getTimestamp("shift_start_at")),
                instant(rs.getTimestamp("shift_end_at")),
                rs.getString("shift_code"),
                rs.getString("shift_name"),
                rs.getLong("clinic_id"),
                rs.getString("clinic_name"),
                rs.getString("clinic_status"),
                rs.getLong("room_id"),
                rs.getObject("room_public_id", UUID.class),
                rs.getString("room_code"),
                rs.getString("room_name"),
                rs.getString("room_status"),
                rs.getLong("doctor_id"),
                rs.getString("doctor_name"),
                rs.getString("specialty_name"),
                rs.getString("package_name"),
                rs.getString("package_version"),
                rs.getString("service_name"),
                rs.getObject("visit_public_id", UUID.class),
                rs.getString("visit_code"),
                instant(rs.getTimestamp("visit_received_at"))),
        patientPublicId);
  }

  Optional<LockedAppointmentRow> lockAppointmentForCheckIn(UUID appointmentPublicId) {
    return jdbc
        .query(
            """
            select a.id as appointment_id, a.public_id as appointment_public_id,
                   a.code as appointment_code, a.patient_id,
                   p.public_id as patient_public_id, p.full_name as patient_name,
                   p.phone as patient_phone, a.schedule_id,
                   a.status as appointment_status, a.start_at, a.end_at,
                   a.package_version_id, a.service_id,
                   ds.work_date, ds.start_at as shift_start_at, ds.end_at as shift_end_at,
                   sh.name as shift_name,
                   c.id as clinic_id, c.name as clinic_name, c.status as clinic_status,
                   r.id as room_id, r.public_id as room_public_id, r.code as room_code,
                   r.name as room_name, r.status as room_status,
                   d.name as doctor_name,
                   pv.name as package_name, ms.name as service_name,
                   mv.id as visit_id
            from appointments a
            join patients p on p.id = a.patient_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join shifts sh on sh.id = ds.shift_id
            join rooms r on r.id = ds.room_id
            join clinics c on c.id = r.clinic_id
            join doctors d on d.id = ds.doctor_id
            left join package_versions pv on pv.id = a.package_version_id
            left join medical_services ms on ms.id = a.service_id
            left join medical_visits mv on mv.appointment_id = a.id
            where a.public_id = ?
            for update of a
            """,
            (rs, rowNum) ->
                new LockedAppointmentRow(
                    rs.getLong("appointment_id"),
                    rs.getObject("appointment_public_id", UUID.class),
                    rs.getString("appointment_code"),
                    rs.getLong("patient_id"),
                    rs.getObject("patient_public_id", UUID.class),
                    rs.getString("patient_name"),
                    rs.getString("patient_phone"),
                    rs.getLong("schedule_id"),
                    AppointmentStatus.valueOf(rs.getString("appointment_status")),
                    instant(rs.getTimestamp("start_at")),
                    instant(rs.getTimestamp("end_at")),
                    rs.getObject("package_version_id", Long.class),
                    rs.getObject("service_id", Long.class),
                    rs.getObject("work_date", LocalDate.class),
                    instant(rs.getTimestamp("shift_start_at")),
                    instant(rs.getTimestamp("shift_end_at")),
                    rs.getString("shift_name"),
                    rs.getLong("clinic_id"),
                    rs.getString("clinic_name"),
                    rs.getString("clinic_status"),
                    rs.getLong("room_id"),
                    rs.getObject("room_public_id", UUID.class),
                    rs.getString("room_code"),
                    rs.getString("room_name"),
                    rs.getString("room_status"),
                    rs.getString("doctor_name"),
                    rs.getString("package_name"),
                    rs.getString("service_name"),
                    rs.getObject("visit_id", Long.class)),
            appointmentPublicId)
        .stream()
        .findFirst();
  }

  List<CatalogItemRow> findPackageVersionItems(Long packageVersionId) {
    return jdbc.query(
        """
        select pvi.item_id, mi.public_id as item_public_id,
               pvi.item_name_snapshot as item_name, mi.type as item_type,
               pvi.instruction_snapshot as instruction, mi.price as unit_price,
               pvi.quantity
        from package_version_items pvi
        join medical_items mi on mi.id = pvi.item_id
        where pvi.package_version_id = ?
        order by pvi.position asc, pvi.item_id asc
        """,
        (rs, rowNum) ->
            new CatalogItemRow(
                rs.getLong("item_id"),
                rs.getObject("item_public_id", UUID.class),
                rs.getString("item_name"),
                MedicalItemType.valueOf(rs.getString("item_type")),
                rs.getString("instruction"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity")),
        packageVersionId);
  }

  List<CatalogItemRow> findServiceItems(Long serviceId) {
    return jdbc.query(
        """
        select msi.item_id, mi.public_id as item_public_id,
               mi.name as item_name, mi.type as item_type,
               mi.instruction as instruction, mi.price as unit_price,
               msi.quantity
        from medical_service_items msi
        join medical_items mi on mi.id = msi.item_id
        where msi.service_id = ?
        order by msi.item_id asc
        """,
        (rs, rowNum) ->
            new CatalogItemRow(
                rs.getLong("item_id"),
                rs.getObject("item_public_id", UUID.class),
                rs.getString("item_name"),
                MedicalItemType.valueOf(rs.getString("item_type")),
                rs.getString("instruction"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity")),
        serviceId);
  }

  Map<Long, Integer> activeQueueCountsByRoom(Long clinicId, Instant dayStart, Instant dayEnd) {
    Map<Long, Integer> counts = new HashMap<>();
    jdbc.query(
        """
        select mo.room_id, count(*) as cnt
        from medical_orders mo
        join rooms r on r.id = mo.room_id
        where r.clinic_id = ?
          and mo.status in ('WAITING', 'IN_PROGRESS')
          and mo.queued_at >= ?
          and mo.queued_at < ?
        group by mo.room_id
        """,
        rs -> {
          counts.put(rs.getLong("room_id"), rs.getInt("cnt"));
        },
        clinicId,
        Timestamp.from(dayStart),
        Timestamp.from(dayEnd));
    return counts;
  }

  int allocateQueueNumberForRoom(Long roomId, Instant dayStart, Instant dayEnd) {
    jdbc.query("select id from rooms where id = ? for update", rs -> {}, roomId);
    Integer next =
        jdbc.queryForObject(
            """
            select coalesce(max(queue_number), 0) + 1
            from medical_orders
            where room_id = ?
              and queued_at >= ?
              and queued_at < ?
            """,
            Integer.class,
            roomId,
            Timestamp.from(dayStart),
            Timestamp.from(dayEnd));
    return next == null ? 1 : next;
  }

  void markAppointmentCheckedIn(
      Long appointmentId,
      Long staffId,
      Long scheduleId,
      Instant startAt,
      Instant endAt,
      Instant now) {
    Timestamp nowTs = Timestamp.from(now);
    jdbc.update(
        "update appointments set status = 'CHECKED_IN', updated_at = ? where id = ?",
        nowTs,
        appointmentId);
    jdbc.update(
        """
        insert into appointment_histories (
          appointment_id, action, actor, actor_staff_id,
          old_status, new_status, old_schedule_id, new_schedule_id,
          old_start_at, new_start_at, old_end_at, new_end_at, created_at
        )
        values (?, 'CHECKED_IN', 'STAFF', ?, 'CONFIRMED', 'CHECKED_IN', ?, ?, ?, ?, ?, ?, ?)
        """,
        appointmentId,
        staffId,
        scheduleId,
        scheduleId,
        Timestamp.from(startAt),
        Timestamp.from(startAt),
        Timestamp.from(endAt),
        Timestamp.from(endAt),
        nowTs);
  }

  Long insertVisit(
      UUID publicId,
      String code,
      Long patientId,
      Long appointmentId,
      Long receivedByStaffId,
      Instant receivedAt) {
    Timestamp ts = Timestamp.from(receivedAt);
    return jdbc.queryForObject(
        """
        insert into medical_visits (
          public_id, code, patient_id, appointment_id, received_by_staff_id,
          received_at, status, summary_status, prescription_status, version,
          created_at, updated_at
        )
        values (?, ?, ?, ?, ?, ?, 'WAITING', 'DRAFT', 'DRAFT', 0, ?, ?)
        returning id
        """,
        Long.class,
        publicId,
        code,
        patientId,
        appointmentId,
        receivedByStaffId,
        ts,
        ts,
        ts);
  }

  void insertOrder(
      UUID publicId,
      String code,
      Long visitId,
      Long itemId,
      MedicalOrderSource source,
      Long packageVersionId,
      Long serviceId,
      int occurrenceNo,
      Long roomId,
      Long performedVisitId,
      String itemNameSnapshot,
      String instructionSnapshot,
      BigDecimal unitPriceSnapshot,
      MedicalOrderStatus status,
      Integer queueNumber,
      Instant queuedAt,
      Instant orderedAt) {
    jdbc.update(
        """
        insert into medical_orders (
          public_id, code, visit_id, item_id, source, package_version_id, service_id,
          occurrence_no, room_id, performed_visit_id, item_name_snapshot, instruction_snapshot,
          unit_price_snapshot, status, queue_number, queued_at, ordered_at
        )
        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        publicId,
        code,
        visitId,
        itemId,
        source.name(),
        packageVersionId,
        serviceId,
        occurrenceNo,
        roomId,
        performedVisitId,
        itemNameSnapshot,
        instructionSnapshot,
        unitPriceSnapshot,
        status.name(),
        queueNumber,
        queuedAt == null ? null : Timestamp.from(queuedAt),
        Timestamp.from(orderedAt));
  }

  Optional<VisitHeaderRow> findVisitHeader(UUID visitPublicId) {
    return jdbc
        .query(
            """
            select mv.id as visit_id, mv.public_id as visit_public_id, mv.code as visit_code,
                   mv.status as visit_status, mv.received_at,
                   mv.received_by_staff_id, s.name as staff_name,
                   c.id as clinic_id, c.name as clinic_name,
                   a.public_id as appointment_public_id, a.code as appointment_code,
                   a.status as appointment_status, a.start_at, a.end_at, ds.work_date,
                   p.public_id as patient_public_id, p.full_name as patient_name, p.phone as patient_phone,
                   d.name as doctor_name, sh.name as shift_name, r.name as clinical_room_name,
                   pv.name as package_name, ms.name as service_name
            from medical_visits mv
            join staffs s on s.id = mv.received_by_staff_id
            join appointments a on a.id = mv.appointment_id
            join patients p on p.id = mv.patient_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join shifts sh on sh.id = ds.shift_id
            join rooms r on r.id = ds.room_id
            join clinics c on c.id = r.clinic_id
            join doctors d on d.id = ds.doctor_id
            left join package_versions pv on pv.id = a.package_version_id
            left join medical_services ms on ms.id = a.service_id
            where mv.public_id = ?
            """,
            (rs, rowNum) ->
                new VisitHeaderRow(
                    rs.getLong("visit_id"),
                    rs.getObject("visit_public_id", UUID.class),
                    rs.getString("visit_code"),
                    MedicalVisitStatus.valueOf(rs.getString("visit_status")),
                    instant(rs.getTimestamp("received_at")),
                    rs.getLong("received_by_staff_id"),
                    rs.getString("staff_name"),
                    rs.getLong("clinic_id"),
                    rs.getString("clinic_name"),
                    rs.getObject("appointment_public_id", UUID.class),
                    rs.getString("appointment_code"),
                    AppointmentStatus.valueOf(rs.getString("appointment_status")),
                    instant(rs.getTimestamp("start_at")),
                    instant(rs.getTimestamp("end_at")),
                    rs.getObject("work_date", LocalDate.class),
                    rs.getObject("patient_public_id", UUID.class),
                    rs.getString("patient_name"),
                    rs.getString("patient_phone"),
                    rs.getString("doctor_name"),
                    rs.getString("shift_name"),
                    rs.getString("clinical_room_name"),
                    rs.getString("package_name"),
                    rs.getString("service_name")),
            visitPublicId)
        .stream()
        .findFirst();
  }

  List<RoutedOrderResponse> findOrdersByVisitId(Long visitId) {
    return jdbc.query(
        """
        select mo.public_id as order_public_id, mo.code,
               mi.public_id as item_public_id, mo.item_name_snapshot, mi.type as item_type,
               mo.source, mo.occurrence_no,
               count(*) over (partition by mo.visit_id, mo.source, mo.item_id) as total_occurrences,
               (mo.performed_visit_id = mo.visit_id) as current_visit_routed,
               mo.status,
               r.public_id as room_public_id, r.code as room_code, r.name as room_name,
               mo.queue_number, mo.queued_at, mo.instruction_snapshot
        from medical_orders mo
        join medical_items mi on mi.id = mo.item_id
        left join rooms r on r.id = mo.room_id
        where mo.visit_id = ?
        order by mo.id asc
        """,
        (rs, rowNum) ->
            new RoutedOrderResponse(
                rs.getObject("order_public_id", UUID.class),
                rs.getString("code"),
                rs.getObject("item_public_id", UUID.class),
                rs.getString("item_name_snapshot"),
                MedicalItemType.valueOf(rs.getString("item_type")),
                MedicalOrderSource.valueOf(rs.getString("source")),
                rs.getInt("occurrence_no"),
                rs.getInt("total_occurrences"),
                rs.getBoolean("current_visit_routed"),
                MedicalOrderStatus.valueOf(rs.getString("status")),
                rs.getObject("room_public_id", UUID.class),
                rs.getString("room_code"),
                rs.getString("room_name"),
                rs.getObject("queue_number", Integer.class),
                instant(rs.getTimestamp("queued_at")),
                rs.getString("instruction_snapshot")),
        visitId);
  }

  Optional<RoutedOrderResponse> findOrderByPublicId(UUID orderPublicId) {
    return jdbc
        .query(
            """
            select mo.public_id as order_public_id, mo.code,
                   mi.public_id as item_public_id, mo.item_name_snapshot, mi.type as item_type,
                   mo.source, mo.occurrence_no,
                   (select count(*) from medical_orders mo2
                     where mo2.visit_id = mo.visit_id
                       and mo2.source = mo.source
                       and mo2.item_id = mo.item_id) as total_occurrences,
                   (mo.performed_visit_id = mo.visit_id) as current_visit_routed,
                   mo.status,
                   r.public_id as room_public_id, r.code as room_code, r.name as room_name,
                   mo.queue_number, mo.queued_at, mo.instruction_snapshot
            from medical_orders mo
            join medical_items mi on mi.id = mo.item_id
            left join rooms r on r.id = mo.room_id
            where mo.public_id = ?
            """,
            (rs, rowNum) ->
                new RoutedOrderResponse(
                    rs.getObject("order_public_id", UUID.class),
                    rs.getString("code"),
                    rs.getObject("item_public_id", UUID.class),
                    rs.getString("item_name_snapshot"),
                    MedicalItemType.valueOf(rs.getString("item_type")),
                    MedicalOrderSource.valueOf(rs.getString("source")),
                    rs.getInt("occurrence_no"),
                    rs.getInt("total_occurrences"),
                    rs.getBoolean("current_visit_routed"),
                    MedicalOrderStatus.valueOf(rs.getString("status")),
                    rs.getObject("room_public_id", UUID.class),
                    rs.getString("room_code"),
                    rs.getString("room_name"),
                    rs.getObject("queue_number", Integer.class),
                    instant(rs.getTimestamp("queued_at")),
                    rs.getString("instruction_snapshot")),
            orderPublicId)
        .stream()
        .findFirst();
  }

  Optional<LockedOrderRow> lockOrderForRouting(UUID visitPublicId, UUID orderPublicId) {
    return jdbc
        .query(
            """
            select mo.id as order_id, mo.public_id as order_public_id,
                   mv.id as visit_id, mv.status as visit_status,
                   r.clinic_id, mo.status as order_status
            from medical_orders mo
            join medical_visits mv on mv.id = mo.visit_id
            join appointments a on a.id = mv.appointment_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join rooms r on r.id = ds.room_id
            where mv.public_id = ? and mo.public_id = ?
            for update of mo
            """,
            (rs, rowNum) ->
                new LockedOrderRow(
                    rs.getLong("order_id"),
                    rs.getObject("order_public_id", UUID.class),
                    rs.getLong("visit_id"),
                    MedicalVisitStatus.valueOf(rs.getString("visit_status")),
                    rs.getLong("clinic_id"),
                    MedicalOrderStatus.valueOf(rs.getString("order_status"))),
            visitPublicId,
            orderPublicId)
        .stream()
        .findFirst();
  }

  void updateOrderRouting(
      Long orderId, Long visitId, Long roomId, Integer queueNumber, Instant queuedAt) {
    jdbc.update(
        """
        update medical_orders
        set room_id = ?, performed_visit_id = ?, status = 'WAITING',
            queue_number = ?, queued_at = ?
        where id = ?
        """,
        roomId,
        visitId,
        queueNumber,
        Timestamp.from(queuedAt),
        orderId);
  }

  Page<ReceptionListItem> listReceptions(
      Long clinicId, LocalDate date, String keyword, MedicalVisitStatus status, Pageable pageable) {
    StringBuilder where =
        new StringBuilder(
            """
            from medical_visits mv
            join appointments a on a.id = mv.appointment_id
            join patients p on p.id = mv.patient_id
            join doctor_schedules ds on ds.id = a.schedule_id
            join shifts sh on sh.id = ds.shift_id
            join rooms r on r.id = ds.room_id
            join doctors d on d.id = ds.doctor_id
            left join package_versions pv on pv.id = a.package_version_id
            left join medical_services ms on ms.id = a.service_id
            where r.clinic_id = ?
            """);
    List<Object> params = new ArrayList<>();
    params.add(clinicId);
    if (date != null) {
      where.append("  and ds.work_date = ?\n");
      params.add(date);
    }
    if (status != null) {
      where.append("  and mv.status = ?\n");
      params.add(status.name());
    }
    if (keyword != null && !keyword.isBlank()) {
      String escaped = escapeLike(keyword.trim().toLowerCase(Locale.ROOT));
      String pattern = "%" + escaped + "%";
      where.append(
          """
            and (
              lower(mv.code) like ? escape '\\'
              or lower(a.code) like ? escape '\\'
              or lower(p.full_name) like ? escape '\\'
              or lower(p.phone) like ? escape '\\'
            )
          """);
      params.add(pattern);
      params.add(pattern);
      params.add(pattern);
      params.add(pattern);
    }

    Long total = jdbc.queryForObject("select count(*) " + where, Long.class, params.toArray());
    long totalElements = total == null ? 0L : total;
    if (totalElements == 0L) {
      return new PageImpl<>(List.of(), pageable, 0L);
    }

    String orderBy = buildOrderBy(pageable.getSort());
    List<Object> pageParams = new ArrayList<>(params);
    pageParams.add(pageable.getPageSize());
    pageParams.add(pageable.getOffset());

    List<ReceptionListItem> content =
        jdbc.query(
            """
            select mv.public_id as visit_public_id, mv.code as visit_code,
                   mv.status as visit_status, mv.received_at,
                   a.public_id as appointment_public_id, a.code as appointment_code,
                   a.start_at as appointment_start_at,
                   p.public_id as patient_public_id, p.full_name as patient_name,
                   p.phone as patient_phone,
                   d.name as doctor_name, sh.name as shift_name,
                   r.name as clinical_room_name,
                   pv.name as package_name, ms.name as service_name,
                   (select count(*) from medical_orders mo where mo.visit_id = mv.id) as total_orders,
                   (select count(*) from medical_orders mo
                     where mo.visit_id = mv.id and mo.room_id is not null) as routed_orders
            """
                + where
                + " order by "
                + orderBy
                + " limit ? offset ?",
            (rs, rowNum) ->
                new ReceptionListItem(
                    rs.getObject("visit_public_id", UUID.class),
                    rs.getString("visit_code"),
                    MedicalVisitStatus.valueOf(rs.getString("visit_status")),
                    instant(rs.getTimestamp("received_at")),
                    rs.getObject("appointment_public_id", UUID.class),
                    rs.getString("appointment_code"),
                    instant(rs.getTimestamp("appointment_start_at")),
                    rs.getObject("patient_public_id", UUID.class),
                    rs.getString("patient_name"),
                    rs.getString("patient_phone"),
                    rs.getString("doctor_name"),
                    rs.getString("shift_name"),
                    rs.getString("clinical_room_name"),
                    rs.getString("package_name"),
                    rs.getString("service_name"),
                    rs.getInt("total_orders"),
                    rs.getInt("routed_orders")),
            pageParams.toArray());

    return new PageImpl<>(content, pageable, totalElements);
  }

  private static String buildOrderBy(Sort sort) {
    List<String> clauses = new ArrayList<>();
    for (Sort.Order order : sort) {
      String column =
          switch (order.getProperty()) {
            case "receivedAt" -> "mv.received_at";
            case "code" -> "mv.code";
            case "status" -> "mv.status";
            default -> null;
          };
      if (column != null) {
        clauses.add(column + (order.isAscending() ? " asc" : " desc"));
      }
    }
    if (clauses.isEmpty()) {
      clauses.add("mv.received_at desc");
    }
    clauses.add("mv.id asc");
    return String.join(", ", clauses);
  }

  private static String escapeLike(String literal) {
    return literal.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static Instant instant(Timestamp ts) {
    return ts == null ? null : ts.toInstant();
  }
}
