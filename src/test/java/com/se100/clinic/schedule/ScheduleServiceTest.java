package com.se100.clinic.schedule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.appointment.AppointmentSchedulingService;
import com.se100.clinic.doctor.DoctorSchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService;
import com.se100.clinic.facility.FacilitySchedulingService.RoomAssignment;
import com.se100.clinic.schedule.ScheduleDtos.SaveScheduleRequest;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ConflictException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
  @Mock private ShiftRepository shifts;
  @Mock private DoctorScheduleRepository schedules;
  @Mock private DoctorScheduleSlotRepository slots;
  @Mock private DoctorSchedulingService doctors;
  @Mock private FacilitySchedulingService facilities;
  @Mock private AppointmentSchedulingService appointments;

  private ScheduleService service;

  @BeforeEach
  void setUp() {
    service =
        new ScheduleService(
            shifts, schedules, slots, doctors, facilities, appointments, "Asia/Ho_Chi_Minh");
  }

  private static SaveScheduleRequest request(int generalCapacity, int specialistCapacity) {
    return new SaveScheduleRequest(
        11L, 12L, 13L, 14L, 15L, LocalDate.of(2026, 11, 3), generalCapacity, specialistCapacity);
  }

  private static Shift morning() {
    Shift shift = new Shift();
    shift.setId(15L);
    shift.setCode("MORNING");
    shift.setName("Ca sáng");
    shift.setStartTime(LocalTime.of(8, 0));
    shift.setEndTime(LocalTime.of(12, 0));
    return shift;
  }

  private void validReferences() {
    when(shifts.findById(15L)).thenReturn(Optional.of(morning()));
    when(doctors.exists(11L)).thenReturn(true);
    when(doctors.isWorking(11L)).thenReturn(true);
    when(doctors.hasSpecialty(11L, 12L)).thenReturn(true);
    when(facilities.roomAssignment(14L))
        .thenReturn(Optional.of(new RoomAssignment(13L, true, true)));
  }

  @Test
  void createGenerates24TenMinuteSlotsAndStoresConfiguredCapacities() {
    validReferences();
    when(schedules.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    var response = service.create(request(10, 7));

    assertThat(response.generalCapacity()).isEqualTo(10);
    assertThat(response.specialistCapacity()).isEqualTo(7);
    assertThat(response.startAt()).isEqualTo(Instant.parse("2026-11-03T01:00:00Z"));
    assertThat(response.endAt()).isEqualTo(Instant.parse("2026-11-03T05:00:00Z"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<DoctorScheduleSlot>> captured = ArgumentCaptor.forClass(List.class);
    verify(slots).saveAllAndFlush(captured.capture());
    assertThat(captured.getValue()).hasSize(24);
    assertThat(captured.getValue().getFirst().getStartAt()).isEqualTo(response.startAt());
    assertThat(captured.getValue().getLast().getEndAt()).isEqualTo(response.endAt());
    assertThat(captured.getValue())
        .allSatisfy(
            slot -> assertThat(slot.getEndAt()).isEqualTo(slot.getStartAt().plusSeconds(600)));
  }

  @Test
  void rejectsCapacityAbovePhysicalShiftDuration() {
    when(shifts.findById(15L)).thenReturn(Optional.of(morning()));

    assertThatThrownBy(() -> service.create(request(13, 8)))
        .isInstanceOf(BusinessException.class)
        .extracting(error -> ((BusinessException) error).getErrorCode())
        .isEqualTo(ScheduleErrorCode.INVALID_CAPACITY);
    verify(schedules, never()).saveAndFlush(any());
  }

  @Test
  void rejectsOverlappingDoctorEvenWhenRoomIsFree() {
    validReferences();
    when(schedules.overlapsDoctor(eq(11L), any(), any(), eq(ScheduleStatus.CANCELLED), eq(null)))
        .thenReturn(true);

    assertThatThrownBy(() -> service.create(request(12, 8)))
        .isInstanceOf(ConflictException.class)
        .extracting(error -> ((ConflictException) error).getErrorCode())
        .isEqualTo(ScheduleErrorCode.SCHEDULE_OVERLAP);
    verify(schedules, never()).saveAndFlush(any());
  }

  @Test
  void updatePreservesAssignmentWhenAnyAppointmentReferencesIt() {
    DoctorSchedule existing = new DoctorSchedule();
    existing.setId(99L);
    existing.setPublicId(UUID.randomUUID());
    when(schedules.findByPublicId(existing.getPublicId())).thenReturn(Optional.of(existing));
    when(appointments.hasAppointments(99L)).thenReturn(true);

    assertThatThrownBy(() -> service.update(existing.getPublicId(), request(12, 8)))
        .isInstanceOf(ConflictException.class)
        .extracting(error -> ((ConflictException) error).getErrorCode())
        .isEqualTo(ScheduleErrorCode.SCHEDULE_HAS_APPOINTMENTS);
    verify(schedules, never()).flush();
  }
}
