package com.se100.clinic.examination;

import com.se100.clinic.examination.enums.MedicalOrderSource;
import com.se100.clinic.examination.enums.MedicalOrderStatus;
import com.se100.clinic.examination.enums.MedicalResultStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs of the doctor's examination flow. Every record is an immutable JSON shape. */
public final class ExaminationDtos {

  private ExaminationDtos() {}

  /** One received patient in the doctor's list; {@code queueNumber} is the order within a shift. */
  @Schema(
      requiredProperties = {
        "queueNumber",
        "visitPublicId",
        "visitCode",
        "status",
        "receivedAt",
        "patientPublicId",
        "patientName",
        "appointmentCode",
        "schedulePublicId",
        "workDate"
      })
  public record QueueItem(
      int queueNumber,
      UUID visitPublicId,
      String visitCode,
      MedicalVisitStatus status,
      Instant receivedAt,
      UUID patientPublicId,
      String patientName,
      UUID appointmentPublicId,
      String appointmentCode,
      UUID schedulePublicId,
      LocalDate workDate,
      String shiftName,
      String roomName) {}

  public record PatientSummary(
      UUID publicId, String fullName, LocalDate dateOfBirth, String gender, String phone) {}

  public record AppointmentSummary(
      UUID publicId, String code, Instant startAt, String packageName, String serviceName) {}

  public record ScheduleSummary(
      UUID publicId, LocalDate workDate, String shiftName, String roomName) {}

  /** The part of the visit the doctor writes during the examination (EXAM-03). */
  public record ClinicalRecord(
      String symptoms,
      String clinicalNotes,
      String initialDiagnosis,
      String advice,
      BigDecimal heightCm,
      BigDecimal weightKg,
      BigDecimal temperatureC,
      Integer heartRate,
      Integer systolicBp,
      Integer diastolicBp) {}

  public record OrderSummary(
      UUID publicId,
      String code,
      String itemName,
      MedicalOrderSource source,
      MedicalOrderStatus status) {}

  @Schema(
      requiredProperties = {
        "publicId",
        "code",
        "status",
        "receivedAt",
        "version",
        "patient",
        "appointment",
        "schedule",
        "clinical",
        "orders"
      })
  public record VisitDetail(
      UUID publicId,
      String code,
      MedicalVisitStatus status,
      Instant receivedAt,
      @Schema(description = "Gửi lại nguyên giá trị này khi PUT để phát hiện ghi đè đồng thời")
          long version,
      PatientSummary patient,
      AppointmentSummary appointment,
      ScheduleSummary schedule,
      ClinicalRecord clinical,
      List<OrderSummary> orders) {}

  /**
   * Replaces the whole clinical record: a field left out is cleared. {@code version} must be the
   * one returned by the last GET.
   */
  public record UpdateExaminationRequest(
      @NotNull(message = "Phiên bản lượt khám không được để trống") Long version,
      @Size(max = 5000, message = "Triệu chứng tối đa 5000 ký tự") String symptoms,
      @Size(max = 5000, message = "Ghi chú lâm sàng tối đa 5000 ký tự") String clinicalNotes,
      @Size(max = 5000, message = "Chẩn đoán ban đầu tối đa 5000 ký tự") String initialDiagnosis,
      @Size(max = 5000, message = "Tư vấn tối đa 5000 ký tự") String advice,
      @DecimalMin(value = "30", message = "Chiều cao phải từ 30 cm")
          @DecimalMax(value = "250", message = "Chiều cao tối đa 250 cm")
          BigDecimal heightCm,
      @DecimalMin(value = "1", message = "Cân nặng phải từ 1 kg")
          @DecimalMax(value = "500", message = "Cân nặng tối đa 500 kg")
          BigDecimal weightKg,
      @DecimalMin(value = "30", message = "Thân nhiệt phải từ 30°C")
          @DecimalMax(value = "45", message = "Thân nhiệt tối đa 45°C")
          BigDecimal temperatureC,
      @Min(value = 20, message = "Nhịp tim phải từ 20 lần/phút")
          @Max(value = 250, message = "Nhịp tim tối đa 250 lần/phút")
          Integer heartRate,
      @Min(value = 40, message = "Huyết áp tâm thu phải từ 40 mmHg")
          @Max(value = 300, message = "Huyết áp tâm thu tối đa 300 mmHg")
          Integer systolicBp,
      @Min(value = 20, message = "Huyết áp tâm trương phải từ 20 mmHg")
          @Max(value = 200, message = "Huyết áp tâm trương tối đa 200 mmHg")
          Integer diastolicBp) {}

  /** A released lab/imaging result of an older visit (EXAM-02). */
  public record PreviousResult(
      UUID orderPublicId,
      String itemName,
      MedicalResultStatus resultStatus,
      String resultText,
      String resultConclusion,
      Instant performedAt) {}

  public record PreviousVisit(
      UUID publicId,
      String code,
      MedicalVisitStatus status,
      Instant receivedAt,
      String symptoms,
      String initialDiagnosis,
      String finalDiagnosis,
      String conclusion,
      List<PreviousResult> results) {}

  @Schema(requiredProperties = {"medicalHistory", "previousVisits"})
  public record PatientHistoryResponse(
      MedicalHistorySummaryResponse medicalHistory, List<PreviousVisit> previousVisits) {}
}
