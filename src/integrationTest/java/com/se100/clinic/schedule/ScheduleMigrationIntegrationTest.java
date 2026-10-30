package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import com.se100.clinic.shared.error.DbErrors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

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
    var data = ScheduleTestData.create(jdbc);
    data.schedule(
        data.doctorId(),
        data.roomId(),
        "MORNING",
        data.start(),
        data.start().plusSeconds(14400),
        "OPEN");
    assertThatThrownBy(
            () ->
                data.schedule(
                    data.doctorId(),
                    data.secondRoomId(),
                    "AFTERNOON",
                    data.start().plusSeconds(10800),
                    data.start().plusSeconds(25200),
                    "OPEN"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ex_doctor_schedules_doctor_overlap")
        .satisfies(
            error -> assertThat(DbErrors.sqlState(error)).isEqualTo(DbErrors.EXCLUSION_VIOLATION));
  }

  @Test
  void overlappingRoomAssignmentsAreRejectedForDifferentDoctors() {
    var data = ScheduleTestData.create(jdbc);
    data.schedule(
        data.doctorId(),
        data.roomId(),
        "MORNING",
        data.start(),
        data.start().plusSeconds(14400),
        "OPEN");
    assertThatThrownBy(
            () ->
                data.schedule(
                    data.secondDoctorId(),
                    data.roomId(),
                    "MORNING",
                    data.start(),
                    data.start().plusSeconds(14400),
                    "OPEN"))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("ex_doctor_schedules_room_overlap")
        .satisfies(
            error -> assertThat(DbErrors.sqlState(error)).isEqualTo(DbErrors.EXCLUSION_VIOLATION));
  }

  @Test
  void adjacentAndCancelledAssignmentsDoNotConflict() {
    var data = ScheduleTestData.create(jdbc);
    data.schedule(
        data.doctorId(),
        data.roomId(),
        "MORNING",
        data.start(),
        data.start().plusSeconds(14400),
        "OPEN");
    assertThat(
            data.schedule(
                data.doctorId(),
                data.roomId(),
                "AFTERNOON",
                data.start().plusSeconds(14400),
                data.start().plusSeconds(28800),
                "OPEN"))
        .isPositive();
    assertThat(
            data.schedule(
                data.doctorId(),
                data.roomId(),
                "MORNING",
                data.start(),
                data.start().plusSeconds(14400),
                "CANCELLED"))
        .isPositive();
  }
}
