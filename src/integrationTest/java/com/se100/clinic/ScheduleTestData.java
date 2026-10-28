package com.se100.clinic;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Complete FK-valid fixtures, isolated by generated identifiers for the shared test database. */
public record ScheduleTestData(
    JdbcTemplate jdbc,
    long clinicId,
    long roomId,
    long secondRoomId,
    long doctorId,
    long secondDoctorId,
    long specialtyId,
    long patientId,
    String doctorUsername,
    String password) {
  public static ScheduleTestData create(JdbcTemplate jdbc) {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String password = "schedule-test-password";
    String username = "doctor_" + suffix;
    long user = user(jdbc, username, password, "DOCTOR");
    long otherUser = user(jdbc, "other_" + suffix, password, "DOCTOR");
    long specialty =
        jdbc.queryForObject(
            "insert into specialties (code, name) values (?, ?) returning id",
            Long.class,
            "SP_" + suffix,
            "Specialty " + suffix);
    long clinic =
        jdbc.queryForObject(
            """
        insert into clinics (public_id, code, name, address, phone_number, status)
        values (?, ?, 'Test clinic', 'Test address', '0900000000', 'OPENING') returning id
        """,
            Long.class,
            UUID.randomUUID(),
            "CL_" + suffix);
    long room = room(jdbc, clinic, "R1");
    long otherRoom = room(jdbc, clinic, "R2");
    long doctor = doctor(jdbc, user, specialty);
    long otherDoctor = doctor(jdbc, otherUser, specialty);
    long patient =
        jdbc.queryForObject(
            """
        insert into patients (public_id, full_name, date_of_birth, gender, identity_type,
          identity_number, identity_issuer, phone, address)
        values (?, 'Test patient', '2000-01-01', 'MALE', 'CCCD', ?, 'VN', '0900000000', 'Test address') returning id
        """,
            Long.class,
            UUID.randomUUID(),
            suffix);
    return new ScheduleTestData(
        jdbc, clinic, room, otherRoom, doctor, otherDoctor, specialty, patient, username, password);
  }

  public static long user(JdbcTemplate jdbc, String username, String password, String role) {
    return jdbc.queryForObject(
        """
        insert into users (public_id, username, email, password_hashed, role, status)
        values (?, ?, ?, ?, ?, 'ACTIVE') returning id
        """,
        Long.class,
        UUID.randomUUID(),
        username,
        username + "@test.invalid",
        new BCryptPasswordEncoder().encode(password),
        role);
  }

  private static long room(JdbcTemplate jdbc, long clinic, String code) {
    return jdbc.queryForObject(
        """
        insert into rooms (public_id, clinic_id, code, name, status)
        values (?, ?, ?, 'Test room', 'OPENING') returning id
        """,
        Long.class,
        UUID.randomUUID(),
        clinic,
        code);
  }

  private static long doctor(JdbcTemplate jdbc, long user, long specialty) {
    long doctor =
        jdbc.queryForObject(
            """
        insert into doctors (public_id, user_id, name, degree, status)
        values (?, ?, 'Test doctor', 'MD', 'WORKING') returning id
        """,
            Long.class,
            UUID.randomUUID(),
            user);
    jdbc.update(
        "insert into doctor_specialities (doctor_id, specialty_id) values (?, ?)",
        doctor,
        specialty);
    return doctor;
  }

  public LocalDate workDate() {
    return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(7);
  }

  public Instant start() {
    return workDate().atTime(8, 0).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
  }

  public long schedule(
      long doctor, long room, String shift, Instant start, Instant end, String status) {
    return jdbc.queryForObject(
        """
        insert into doctor_schedules (public_id, doctor_id, specialty_id, room_id, shift_id,
          work_date, start_at, end_at, status, general_capacity, specialist_capacity)
        values (?, ?, ?, ?, (select id from shifts where code = ?), ?, ?, ?, ?, 12, 8) returning id
        """,
        Long.class,
        UUID.randomUUID(),
        doctor,
        specialtyId,
        room,
        shift,
        workDate(),
        Timestamp.from(start),
        Timestamp.from(end),
        status);
  }

  public long appointment(long scheduleId, Instant start, int durationMinutes, String status) {
    return jdbc.queryForObject(
        """
        insert into appointments (public_id, code, patient_id, schedule_id, start_at, end_at,
          status, price_snapshot, cancellation_policy_snapshot, hold_expires_at)
        values (?, ?, ?, ?, ?, ?, ?, 100000, '{}', ?) returning id
        """,
        Long.class,
        UUID.randomUUID(),
        "AP_" + UUID.randomUUID().toString().substring(0, 20),
        patientId,
        scheduleId,
        Timestamp.from(start),
        Timestamp.from(start.plusSeconds(durationMinutes * 60L)),
        status,
        status.equals("HELD") ? Timestamp.from(Instant.now().plusSeconds(900)) : null);
  }
}
