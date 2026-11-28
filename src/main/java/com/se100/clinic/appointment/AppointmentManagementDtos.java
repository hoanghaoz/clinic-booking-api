package com.se100.clinic.appointment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

final class AppointmentManagementDtos {
  private AppointmentManagementDtos() {}

  enum Group {
    UPCOMING,
    COMPLETED,
    CANCELLED,
    OTHER
  }

  enum Action {
    CANCEL,
    RESCHEDULE,
    CONFIRM
  }

  record ChangeRequest(
      @NotNull Action action,
      UUID schedulePublicId,
      Instant startAt,
      @Size(max = 500) String reason) {}

  record DeskBookingRequest(
      @NotNull UUID patientPublicId, @NotNull @Valid BookingDtos.CreateRequest booking) {}

  record ClinicOption(Long id, String name) {}

  record Summary(
      UUID publicId,
      String code,
      String status,
      String displayStatus,
      Instant startAt,
      Instant endAt,
      Long clinicId,
      String clinicName,
      String doctorName,
      String patientName,
      BigDecimal depositRequired,
      BigDecimal depositPaid,
      Long freeCancellationHours) {}

  record Detail(
      Summary appointment,
      String qrPngBase64,
      BigDecimal price,
      String preparationInstruction,
      Integer refundEligiblePercent,
      String cancellationReason) {}
}
