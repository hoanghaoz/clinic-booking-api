package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifies Flyway seed data and database-level overlap protection on PostgreSQL. */
class ScheduleMigrationIntegrationTest extends AbstractIntegrationTest {
  @Autowired private JdbcTemplate jdbc;

  @Test
  void fixedShiftsAreSeededWithAuditTimestamps() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from shifts where code in ('MORNING', 'AFTERNOON', 'EVENING') "
                    + "and created_at is not null and updated_at is not null",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  void overlappingDoctorAssignmentsAreRejectedEvenAcrossDifferentShiftCodes() {
    long doctorId = ThreadLocalRandom.current().nextLong(1_000_000_000L, 1_000_000_000_000L);
    long roomId = doctorId + 1;
    insert(doctorId, roomId, "MORNING", "2026-11-03T01:00:00Z", "2026-11-03T05:00:00Z");

    assertThatThrownBy(
            () ->
                insert(
                    doctorId,
                    roomId + 1,
                    "AFTERNOON",
                    "2026-11-03T04:00:00Z",
                    "2026-11-03T08:00:00Z"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insert(long doctorId, long roomId, String shiftCode, String start, String end) {
    jdbc.update(
        """
        insert into doctor_schedules
          (work_date, created_at, updated_at, doctor_id, room_id, shift_id,
           specialty_id, start_at, end_at, public_id, status,
           general_capacity, specialist_capacity)
        values (?, now(), now(), ?, ?, (select id from shifts where code = ?),
                ?, ?, ?, ?, 'OPEN', 12, 8)
        """,
        LocalDate.of(2026, 11, 3),
        doctorId,
        roomId,
        shiftCode,
        1L,
        Timestamp.from(Instant.parse(start)),
        Timestamp.from(Instant.parse(end)),
        UUID.randomUUID());
  }
}
