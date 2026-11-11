package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.MedicalHistorySource;
import com.se100.clinic.patient.enums.MedicalHistoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/**
 * DTOs of the patient medical history (personal, family, allergy). Public because the appointment
 * (check-in) and examination modules read {@link MedicalHistorySummaryResponse}.
 */
public final class MedicalHistoryDtos {

  private MedicalHistoryDtos() {}

  public record MedicalHistoryCreateRequest(
      @NotNull(message = "Loại tiền sử không được để trống") MedicalHistoryType type,
      @NotBlank(message = "Mô tả không được để trống")
          @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
          String description,
      @Schema(
              description =
                  "Quan hệ trong gia đình; bắt buộc khi type = FAMILY, bỏ trống với loại khác")
          @Size(max = 50, message = "Quan hệ gia đình tối đa 50 ký tự")
          String familyRelationship) {}

  // The type of an entry never changes after it is recorded.
  public record MedicalHistoryUpdateRequest(
      @NotBlank(message = "Mô tả không được để trống")
          @Size(max = 2000, message = "Mô tả tối đa 2000 ký tự")
          String description,
      @Size(max = 50, message = "Quan hệ gia đình tối đa 50 ký tự") String familyRelationship) {}

  @Schema(requiredProperties = {"id", "type", "description", "source", "recordedAt", "updatedAt"})
  public record MedicalHistoryResponse(
      Long id,
      MedicalHistoryType type,
      String description,
      String familyRelationship,
      MedicalHistorySource source,
      Instant recordedAt,
      Instant updatedAt) {

    static MedicalHistoryResponse from(PatientMedicalHistory history) {
      return new MedicalHistoryResponse(
          history.getId(),
          history.getType(),
          history.getDescription(),
          history.getFamilyRelationship(),
          sourceOf(history),
          history.getRecordedAt(),
          history.getUpdatedAt());
    }

    // Internal doctor / staff ids are deliberately not exposed.
    static MedicalHistorySource sourceOf(PatientMedicalHistory history) {
      if (history.getRecordedByDoctorId() != null) {
        return MedicalHistorySource.DOCTOR;
      }
      if (history.getRecordedByStaffId() != null) {
        return MedicalHistorySource.STAFF;
      }
      return MedicalHistorySource.PATIENT;
    }
  }

  /** Full history grouped by type, newest first; used by reception and the examination flow. */
  @Schema(requiredProperties = {"personal", "family", "allergies"})
  public record MedicalHistorySummaryResponse(
      List<MedicalHistoryResponse> personal,
      List<MedicalHistoryResponse> family,
      List<MedicalHistoryResponse> allergies) {}
}
