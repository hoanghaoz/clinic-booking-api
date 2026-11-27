package com.se100.clinic.reception;

import com.se100.clinic.appointment.enums.AppointmentStatus;
import com.se100.clinic.catalog.enums.MedicalItemType;
import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.reception.enums.CardAlertLevel;
import com.se100.clinic.reception.enums.PaymentSummaryStatus;
import com.se100.clinic.reception.enums.PunctualityStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Immutable DTOs for the patient reception and room-routing flow (REC-01..REC-04). */
public final class ReceptionDtos {

  private ReceptionDtos() {}

  @Schema(requiredProperties = {"roomId", "roomPublicId", "code", "name"})
  public record ClinicRoomOption(Long roomId, UUID roomPublicId, String code, String name) {}

  @Schema(
      requiredProperties = {
        "clinicId",
        "clinicPublicId",
        "clinicCode",
        "clinicName",
        "clinicStatus",
        "openRooms"
      })
  public record StaffClinicResponse(
      Long clinicId,
      UUID clinicPublicId,
      String clinicCode,
      String clinicName,
      String clinicStatus,
      List<ClinicRoomOption> openRooms) {}

  @Schema(requiredProperties = {"itemId", "itemPublicId", "itemName", "itemType", "quantity"})
  public record PlannedOrderItem(
      Long itemId,
      UUID itemPublicId,
      String itemName,
      MedicalItemType itemType,
      int quantity,
      UUID suggestedRoomPublicId,
      String suggestedRoomCode,
      String suggestedRoomName) {}

  @Schema(
      requiredProperties = {
        "appointmentPublicId",
        "code",
        "status",
        "startAt",
        "endAt",
        "workDate",
        "shiftCode",
        "shiftName",
        "shiftStartAt",
        "shiftEndAt",
        "clinicId",
        "clinicName",
        "roomId",
        "roomPublicId",
        "roomCode",
        "roomName",
        "doctorId",
        "doctorName",
        "specialtyName",
        "priceSnapshot",
        "depositRequired",
        "paidAmount",
        "paymentStatus",
        "punctuality",
        "minutesDelta",
        "receivable",
        "plannedItems"
      })
  public record ReceptionAppointmentItem(
      UUID appointmentPublicId,
      String code,
      AppointmentStatus status,
      Instant startAt,
      Instant endAt,
      LocalDate workDate,
      String shiftCode,
      String shiftName,
      Instant shiftStartAt,
      Instant shiftEndAt,
      Long clinicId,
      String clinicName,
      Long roomId,
      UUID roomPublicId,
      String roomCode,
      String roomName,
      Long doctorId,
      String doctorName,
      String specialtyName,
      String packageName,
      String packageVersion,
      String serviceName,
      BigDecimal priceSnapshot,
      BigDecimal depositRequired,
      BigDecimal paidAmount,
      PaymentSummaryStatus paymentStatus,
      PunctualityStatus punctuality,
      @Schema(description = "Số phút lệch so với giờ hẹn: âm = đến sớm, dương = quá giờ")
          long minutesDelta,
      boolean receivable,
      String ineligibleCode,
      String ineligibleReason,
      UUID existingVisitPublicId,
      String existingVisitCode,
      Instant receivedAt,
      List<PlannedOrderItem> plannedItems) {}

  @Schema(requiredProperties = {"profile", "medicalHistory", "cardAlert", "appointments"})
  public record ReceptionPatientCard(
      PatientResponse profile,
      MedicalHistorySummaryResponse medicalHistory,
      PatientCardValidityResponse cardValidity,
      CardAlertLevel cardAlert,
      String cardWarningMessage,
      List<ReceptionAppointmentItem> appointments) {}

  @Schema(
      requiredProperties = {
        "clinicId",
        "clinicPublicId",
        "clinicCode",
        "clinicName",
        "openRooms",
        "patients"
      })
  public record ReceptionLookupResponse(
      Long clinicId,
      UUID clinicPublicId,
      String clinicCode,
      String clinicName,
      List<ClinicRoomOption> openRooms,
      List<ReceptionPatientCard> patients) {}

  public record ItemRoomAssignment(
      @NotNull(message = "Mã hạng mục không được để trống") UUID itemPublicId,
      @NotNull(message = "Mã phòng không được để trống") UUID roomPublicId) {}

  @Schema(requiredProperties = {"appointmentPublicId"})
  public record CreateReceptionRequest(
      @NotNull(message = "Mã lịch hẹn không được để trống") UUID appointmentPublicId,
      @Schema(description = "Cơ sở tiếp nhận (tùy chọn nếu nhân viên chỉ thuộc 1 cơ sở)")
          Long clinicId,
      @Schema(
              description =
                  "Gán phòng chức năng cho từng hạng mục (tùy chọn; hạng mục bỏ trống được tự phân"
                      + " luồng)")
          @Valid
          List<ItemRoomAssignment> roomAssignments) {}

  @Schema(requiredProperties = {"roomPublicId"})
  public record UpdateOrderRoutingRequest(
      @NotNull(message = "Mã phòng chức năng không được để trống") UUID roomPublicId) {}

  @Schema(
      requiredProperties = {
        "orderPublicId",
        "code",
        "itemPublicId",
        "itemName",
        "itemType",
        "source",
        "occurrenceNo",
        "totalOccurrences",
        "currentVisitRouted",
        "status"
      })
  public record RoutedOrderResponse(
      UUID orderPublicId,
      String code,
      UUID itemPublicId,
      String itemName,
      MedicalItemType itemType,
      MedicalOrderSource source,
      int occurrenceNo,
      int totalOccurrences,
      boolean currentVisitRouted,
      MedicalOrderStatus status,
      UUID roomPublicId,
      String roomCode,
      String roomName,
      Integer queueNumber,
      Instant queuedAt,
      String instruction) {}

  @Schema(
      requiredProperties = {
        "visitPublicId",
        "visitCode",
        "status",
        "receivedAt",
        "receivedByStaffId",
        "receivedByStaffName",
        "clinicId",
        "clinicName",
        "appointmentPublicId",
        "appointmentCode",
        "appointmentStatus",
        "appointmentStartAt",
        "appointmentEndAt",
        "punctuality",
        "minutesDelta",
        "patientPublicId",
        "patientName",
        "patientPhone",
        "cardAlert",
        "doctorName",
        "shiftName",
        "clinicalRoomName",
        "orders"
      })
  public record ReceptionTicketResponse(
      UUID visitPublicId,
      String visitCode,
      MedicalVisitStatus status,
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
      PunctualityStatus punctuality,
      long minutesDelta,
      UUID patientPublicId,
      String patientName,
      String patientPhone,
      CardAlertLevel cardAlert,
      String cardWarningMessage,
      String doctorName,
      String shiftName,
      String clinicalRoomName,
      String packageName,
      String serviceName,
      List<RoutedOrderResponse> orders) {}

  @Schema(
      requiredProperties = {
        "visitPublicId",
        "visitCode",
        "status",
        "receivedAt",
        "appointmentPublicId",
        "appointmentCode",
        "appointmentStartAt",
        "patientPublicId",
        "patientName",
        "patientPhone",
        "doctorName",
        "shiftName",
        "clinicalRoomName",
        "totalOrders",
        "routedOrders"
      })
  public record ReceptionListItem(
      UUID visitPublicId,
      String visitCode,
      MedicalVisitStatus status,
      Instant receivedAt,
      UUID appointmentPublicId,
      String appointmentCode,
      Instant appointmentStartAt,
      UUID patientPublicId,
      String patientName,
      String patientPhone,
      String doctorName,
      String shiftName,
      String clinicalRoomName,
      String packageName,
      String serviceName,
      int totalOrders,
      int routedOrders) {}
}
