package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.se100.clinic.AbstractIntegrationTest;
import com.se100.clinic.ScheduleTestData;
import com.se100.clinic.appointment.AppointmentReservationService;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.DbErrors;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ScheduleBehaviorIntegrationTest extends AbstractIntegrationTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ScheduleService schedules;
  @Autowired private ScheduleAvailabilityService availability;
  @Autowired private AppointmentReservationService reservations;
  @Autowired private DoctorScheduleRepository repository;
  @Autowired private PlatformTransactionManager transactions;

  private UUID create(
      ScheduleTestData data,
      long doctorId,
      long roomId,
      String shift,
      int general,
      int specialist) {
    Long shiftId = jdbc.queryForObject("select id from shifts where code = ?", Long.class, shift);
    return schedules
        .create(
            new SaveScheduleRequest(
                doctorId,
                data.specialtyId(),
                data.clinicId(),
                roomId,
                shiftId,
                data.workDate(),
                general,
                specialist))
        .publicId();
  }

  private long id(UUID publicId) {
    return repository.findByPublicId(publicId).orElseThrow().getId();
  }

  @Test
  void unavailableResourcesAreRemovedBeforePaginationButAdminKeepsHistory() {
    var data = ScheduleTestData.create(jdbc);
    create(data, data.doctorId(), data.roomId(), "MORNING", 12, 8);
    UUID valid = create(data, data.secondDoctorId(), data.secondRoomId(), "MORNING", 12, 8);
    jdbc.update("update doctors set status = 'NOT_WORKING' where id = ?", data.doctorId());
    var page =
        availability.find(
            null,
            data.clinicId(),
            null,
            data.workDate(),
            data.workDate(),
            20,
            null,
            PageRequest.of(0, 1));
    assertThat(page.getTotalElements()).isEqualTo(1);
    assertThat(page.getContent().getFirst().schedule().publicId()).isEqualTo(valid);
    assertThat(
            schedules
                .list(null, data.clinicId(), null, null, null, null, null, PageRequest.of(0, 10))
                .getTotalElements())
        .isEqualTo(2);
    jdbc.update("update rooms set status = 'CLOSING' where id = ?", data.secondRoomId());
    assertNoAvailability(data);
    jdbc.update("update rooms set status = 'OPENING' where id = ?", data.secondRoomId());
    jdbc.update("update clinics set status = 'CLOSING' where id = ?", data.clinicId());
    assertNoAvailability(data);
  }

  private void assertNoAvailability(ScheduleTestData data) {
    assertThat(
            availability
                .find(
                    null,
                    data.clinicId(),
                    null,
                    data.workDate(),
                    data.workDate(),
                    20,
                    null,
                    PageRequest.of(0, 10))
                .getTotalElements())
        .isZero();
  }

  @Test
  void reopeningRevalidatesDoctorAndFacility() {
    var data = ScheduleTestData.create(jdbc);
    UUID schedule = create(data, data.doctorId(), data.roomId(), "MORNING", 12, 8);
    schedules.changeStatus(schedule, ScheduleStatus.CLOSED);
    jdbc.update("update doctors set status = 'NOT_WORKING' where id = ?", data.doctorId());
    assertThatThrownBy(() -> schedules.changeStatus(schedule, ScheduleStatus.OPEN))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ScheduleErrorCode.DOCTOR_NOT_WORKING));
    jdbc.update("update doctors set status = 'WORKING' where id = ?", data.doctorId());
    jdbc.update("update rooms set status = 'CLOSING' where id = ?", data.roomId());
    assertThatThrownBy(() -> schedules.changeStatus(schedule, ScheduleStatus.OPEN))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ScheduleErrorCode.FACILITY_NOT_OPEN));
  }

  @Test
  void eveningCreates18SlotsAndRejectsExcessCapacity() {
    var data = ScheduleTestData.create(jdbc);
    UUID schedule = create(data, data.doctorId(), data.roomId(), "EVENING", 9, 6);
    assertThat(schedules.slots(schedule)).hasSize(18);
    assertThatThrownBy(
            () -> schedules.changeCapacity(schedule, new ScheduleDtos.ChangeCapacityRequest(10, 6)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ScheduleErrorCode.INVALID_CAPACITY));
  }

  @Test
  void concurrentBookingsCannotOverbookSameWindow() throws Exception {
    race(false);
  }

  @Test
  void concurrentDifferentWindowsCannotExceedConfiguredCapacity() throws Exception {
    race(true);
  }

  private void race(boolean differentWindows) throws Exception {
    var data = ScheduleTestData.create(jdbc);
    long schedule =
        id(create(data, data.doctorId(), data.roomId(), "MORNING", differentWindows ? 1 : 12, 8));
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> book(data, schedule, 0, ready, start));
      var second =
          executor.submit(() -> book(data, schedule, differentWindows ? 1200 : 0, ready, start));
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      assertThat(
              (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                  + (second.get(20, TimeUnit.SECONDS) ? 1 : 0))
          .isEqualTo(1);
    } finally {
      start.countDown();
    }
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointments where schedule_id = ?", Integer.class, schedule))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment_slots s join appointments a on a.id = s.appointment_id where a.schedule_id = ? and s.released_at is null",
                Integer.class,
                schedule))
        .isEqualTo(2);
  }

  private boolean book(
      ScheduleTestData data, long schedule, int offset, CountDownLatch ready, CountDownLatch start)
      throws Exception {
    ready.countDown();
    if (!start.await(10, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Timed out starting booking race");
    }
    try {
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                schedules.lockForBookingChange(schedule);
                long appointment =
                    data.appointment(schedule, data.start().plusSeconds(offset), 20, "CONFIRMED");
                reservations.reserve(appointment);
              });
      return true;
    } catch (BusinessException expected) {
      assertThat(expected.getErrorCode()).isEqualTo(ScheduleErrorCode.SLOT_UNAVAILABLE);
      return false;
    }
  }

  @Test
  void failedMoveKeepsOldTimeAndSlotsWhileSuccessfulMoveReleasesThem() {
    var data = ScheduleTestData.create(jdbc);
    UUID publicId = create(data, data.doctorId(), data.roomId(), "MORNING", 12, 8);
    long schedule = id(publicId);
    TransactionTemplate tx = new TransactionTemplate(transactions);
    long appointment =
        tx.execute(
            status -> {
              schedules.lockForBookingChange(schedule);
              long created = data.appointment(schedule, data.start(), 20, "CONFIRMED");
              reservations.reserve(created);
              reservations.reserve(
                  data.appointment(schedule, data.start().plusSeconds(1200), 20, "CONFIRMED"));
              return created;
            });
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status ->
                        reservations.move(
                            appointment,
                            schedule,
                            data.start().plusSeconds(1200),
                            data.start().plusSeconds(2400))))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo(ScheduleErrorCode.SLOT_UNAVAILABLE));
    assertThat(
            jdbc.queryForObject(
                    "select start_at from appointments where id = ?",
                    java.sql.Timestamp.class,
                    appointment)
                .toInstant())
        .isEqualTo(data.start());
    assertThat(schedules.slots(publicId).getFirst().occupied()).isTrue();
    tx.executeWithoutResult(
        status ->
            reservations.move(
                appointment,
                schedule,
                data.start().plusSeconds(2400),
                data.start().plusSeconds(4200)));
    assertThat(schedules.slots(publicId).getFirst().occupied()).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment_slots where appointment_id = ? and released_at is null",
                Integer.class,
                appointment))
        .isEqualTo(3);
  }

  @Test
  void databaseRejectsSecondActiveReservationAndAllowsReuseAfterRelease() {
    var data = ScheduleTestData.create(jdbc);
    UUID publicId = create(data, data.doctorId(), data.roomId(), "MORNING", 12, 8);
    long schedule = id(publicId);
    long first = data.appointment(schedule, data.start(), 20, "CONFIRMED");
    long second = data.appointment(schedule, data.start(), 20, "CONFIRMED");
    long slot = schedules.slots(publicId).getFirst().id();
    jdbc.update(
        "insert into appointment_slots (appointment_id, slot_id, reserved_at) values (?, ?, now())",
        first,
        slot);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "insert into appointment_slots (appointment_id, slot_id, reserved_at) values (?, ?, now())",
                    second,
                    slot))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uk_appointment_slots_active_slot")
        .satisfies(
            error -> assertThat(DbErrors.sqlState(error)).isEqualTo(DbErrors.UNIQUE_VIOLATION));
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              schedules.lockForBookingChange(schedule);
              jdbc.update("update appointments set status = 'CANCELLED' where id = ?", first);
              reservations.release(first);
              reservations.reserve(second);
            });
    assertThat(
            schedules.slots(publicId).stream().filter(ScheduleDtos.SlotResponse::occupied).count())
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from appointment_slots where appointment_id = ? and released_at is not null",
                Integer.class,
                first))
        .isEqualTo(1);
  }

  @Test
  void mixedVisitsShareTimeAndOccupiedSlotsCannotBeBlocked() {
    var data = ScheduleTestData.create(jdbc);
    UUID publicId = create(data, data.doctorId(), data.roomId(), "MORNING", 12, 8);
    long schedule = id(publicId);
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            status -> {
              schedules.lockForBookingChange(schedule);
              reservations.reserve(data.appointment(schedule, data.start(), 20, "CONFIRMED"));
              reservations.reserve(
                  data.appointment(schedule, data.start().plusSeconds(1200), 30, "HELD"));
            });
    assertThat(
            schedules.slots(publicId).stream().filter(ScheduleDtos.SlotResponse::occupied).count())
        .isEqualTo(5);
    var result =
        availability.find(
            data.doctorId(),
            data.clinicId(),
            null,
            data.workDate(),
            data.workDate(),
            30,
            null,
            PageRequest.of(0, 10));
    assertThat(result.getContent().getFirst().windows().getFirst().startAt())
        .isEqualTo(data.start().plusSeconds(3000));
    long occupiedSlot = schedules.slots(publicId).getFirst().id();
    assertThatThrownBy(
            () ->
                schedules.changeSlotStatus(
                    publicId, occupiedSlot, com.se100.clinic.schedule.enums.SlotStatus.BLOCKED))
        .isInstanceOfSatisfying(
            BusinessException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ScheduleErrorCode.SLOT_OCCUPIED));
  }
}
