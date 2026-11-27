package com.se100.clinic.appointment;

import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.schedule.ScheduleDtos.AvailableWindow;
import com.se100.clinic.schedule.ScheduleDtos.ScheduleResponse;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BookingDtos {
  private BookingDtos() {}

  public record CreateRequest(
      @NotNull UUID schedulePublicId,
      @NotNull @Positive Long clinicId,
      @NotNull @Positive Long doctorId,
      @NotNull Instant startAt,
      UUID packagePublicId,
      @Size(max = 30) String packageVersion,
      UUID servicePublicId) {}

  public record Availability(
      ScheduleResponse schedule,
      List<AvailableWindow> windows,
      boolean full,
      String packageVersion,
      BigDecimal price) {}

  public record Ticket(
      UUID publicId,
      String code,
      String qrPngBase64,
      AppointmentStatus status,
      Instant startAt,
      Instant endAt,
      Long clinicId,
      String clinicName,
      Long doctorId,
      String doctorName,
      BigDecimal price,
      BigDecimal depositRequired,
      String preparationInstruction) {}

  public record ReceptionReference(
      UUID appointmentPublicId,
      String code,
      Long patientId,
      AppointmentStatus status,
      Instant startAt,
      Instant endAt) {}
}
