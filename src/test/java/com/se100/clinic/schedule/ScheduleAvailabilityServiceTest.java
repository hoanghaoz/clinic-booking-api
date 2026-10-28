package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.appointment.AppointmentSchedulingService.ReservationSnapshot;
import com.se100.clinic.schedule.enums.SlotStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ScheduleAvailabilityServiceTest {
  private static DoctorSchedule schedule(int general, int specialist) {
    DoctorSchedule schedule = new DoctorSchedule();
    schedule.setDoctorId(1L);
    schedule.setSpecialtyId(2L);
    schedule.setRoomId(3L);
    schedule.setGeneralCapacity(general);
    schedule.setSpecialistCapacity(specialist);
    return schedule;
  }

  private static List<DoctorScheduleSlot> slots(int count) {
    Instant start = Instant.parse("2026-11-03T01:00:00Z");
    List<DoctorScheduleSlot> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      DoctorScheduleSlot slot = new DoctorScheduleSlot();
      slot.setId((long) i + 1);
      slot.setStartAt(start.plusSeconds(i * 600L));
      slot.setEndAt(start.plusSeconds((i + 1) * 600L));
      result.add(slot);
    }
    return result;
  }

  @Test
  void onlyConsecutiveUnblockedUnreservedSlotsFormWindows() {
    List<DoctorScheduleSlot> slots = slots(6);
    slots.get(2).setStatus(SlotStatus.BLOCKED);
    var reserved = new ReservationSnapshot(Set.of(6L), 0, 0);

    var windows = ScheduleAvailabilityService.windows(schedule(12, 8), 4L, slots, reserved, 20);

    assertThat(windows).hasSize(2);
    assertThat(windows.get(0).startAt()).isEqualTo(slots.get(0).getStartAt());
    assertThat(windows.get(1).startAt()).isEqualTo(slots.get(3).getStartAt());
  }

  @Test
  void specialistWindowNeedsThreeConsecutiveSlots() {
    List<DoctorScheduleSlot> slots = slots(5);
    slots.get(2).setStatus(SlotStatus.BLOCKED);

    assertThat(
            ScheduleAvailabilityService.windows(
                schedule(12, 8), 4L, slots, new ReservationSnapshot(Set.of(), 0, 0), 30))
        .isEmpty();
  }

  @Test
  void configuredPerTypeLimitCanMakeShiftFullBeforeAllSlotsAreUsed() {
    assertThat(
            ScheduleAvailabilityService.windows(
                schedule(1, 8), 4L, slots(24), new ReservationSnapshot(Set.of(), 1, 0), 20))
        .isEmpty();
  }
}
