package com.se100.clinic.schedule;

import com.se100.clinic.appointment.AppointmentSchedulingService.DoctorQueueItem;
import com.se100.clinic.schedule.enums.ScheduleStatus;
import com.se100.clinic.schedule.enums.SlotStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {
  private ScheduleDtos() {}

  @Schema(
      requiredProperties = {
        "doctorId",
        "specialtyId",
        "clinicId",
        "roomId",
        "shiftId",
        "workDate",
        "generalCapacity",
        "specialistCapacity"
      })
  public record SaveScheduleRequest(
      @NotNull @Positive Long doctorId,
      @NotNull @Positive Long specialtyId,
      @NotNull @Positive Long clinicId,
      @NotNull @Positive Long roomId,
      @NotNull @Positive Long shiftId,
      @NotNull LocalDate workDate,
      @NotNull @Positive Integer generalCapacity,
      @NotNull @Positive Integer specialistCapacity) {}

  public record ChangeScheduleStatusRequest(@NotNull ScheduleStatus status) {}

  public record ChangeSlotStatusRequest(@NotNull SlotStatus status) {}

  @Schema(requiredProperties = {"id", "code", "name", "startTime", "endTime"})
  public record ShiftResponse(
      Long id, String code, String name, LocalTime startTime, LocalTime endTime) {
    static ShiftResponse from(Shift shift) {
      return new ShiftResponse(
          shift.getId(),
          shift.getCode(),
          shift.getName(),
          shift.getStartTime(),
          shift.getEndTime());
    }
  }

  @Schema(
      requiredProperties = {
        "publicId",
        "doctorId",
        "specialtyId",
        "clinicId",
        "roomId",
        "shiftId",
        "shiftCode",
        "workDate",
        "startAt",
        "endAt",
        "generalCapacity",
        "specialistCapacity",
        "status"
      })
  public record ScheduleResponse(
      UUID publicId,
      Long doctorId,
      Long specialtyId,
      Long clinicId,
      Long roomId,
      Long shiftId,
      String shiftCode,
      LocalDate workDate,
      Instant startAt,
      Instant endAt,
      Integer generalCapacity,
      Integer specialistCapacity,
      ScheduleStatus status) {
    static ScheduleResponse from(DoctorSchedule schedule, Long clinicId) {
      return new ScheduleResponse(
          schedule.getPublicId(),
          schedule.getDoctorId(),
          schedule.getSpecialtyId(),
          clinicId,
          schedule.getRoomId(),
          schedule.getShift().getId(),
          schedule.getShift().getCode(),
          schedule.getWorkDate(),
          schedule.getStartAt(),
          schedule.getEndAt(),
          schedule.getGeneralCapacity(),
          schedule.getSpecialistCapacity(),
          schedule.getStatus());
    }
  }

  @Schema(requiredProperties = {"id", "startAt", "endAt", "status", "occupied"})
  public record SlotResponse(
      Long id, Instant startAt, Instant endAt, SlotStatus status, boolean occupied) {
    static SlotResponse from(DoctorScheduleSlot slot, boolean occupied) {
      return new SlotResponse(
          slot.getId(), slot.getStartAt(), slot.getEndAt(), slot.getStatus(), occupied);
    }
  }

  @Schema(
      requiredProperties = {
        "schedulePublicId",
        "doctorId",
        "specialtyId",
        "clinicId",
        "roomId",
        "startAt",
        "endAt",
        "durationMinutes"
      })
  public record AvailableWindow(
      UUID schedulePublicId,
      Long doctorId,
      Long specialtyId,
      Long clinicId,
      Long roomId,
      Instant startAt,
      Instant endAt,
      int durationMinutes) {}

  @Schema(requiredProperties = {"schedule", "windows", "full"})
  public record AvailableSchedule(
      ScheduleResponse schedule, List<AvailableWindow> windows, boolean full) {}

  @Schema(requiredProperties = {"schedule", "patients"})
  public record DoctorQueueResponse(ScheduleResponse schedule, List<DoctorQueueItem> patients) {}
}
