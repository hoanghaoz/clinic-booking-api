package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.IdentityType;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.patient.enums.PatientClass;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * All DTOs of the patient module as immutable records.
 *
 * <p>Public because other modules (appointment, examination) may embed {@link PatientResponse}.
 * Request limits mirror the column definitions in {@code Patient}.
 */
public final class PatientDtos {

  private PatientDtos() {}

  // Loose international-style phone format: optional leading +, then digits, spaces, dots, dashes.
  private static final String PHONE_REGEX = "^\\+?[0-9][0-9 .\\-]{6,23}$";

  // userId and patientClass are intentionally absent: the server derives userId from the
  // authenticated principal on self-registration, and patientClass defaults to PERSONAL.
  public record PatientCreateRequest(
      @NotBlank(message = "Họ tên không được để trống")
          @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
          String fullName,
      // Range (not in the future, not before 1900) is checked in PatientService with the app clock.
      @NotNull(message = "Ngày sinh không được để trống") LocalDate dateOfBirth,
      @NotNull(message = "Giới tính không được để trống") Gender gender,
      @NotBlank(message = "Số điện thoại không được để trống")
          @Size(max = 25, message = "Số điện thoại tối đa 25 ký tự")
          @Pattern(regexp = PHONE_REGEX, message = "Số điện thoại không hợp lệ")
          String phone,
      @Email(message = "Email không hợp lệ") @Size(max = 254, message = "Email tối đa 254 ký tự")
          String contactEmail,
      @NotBlank(message = "Địa chỉ không được để trống")
          @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
          String address,
      @NotNull(message = "Loại giấy tờ không được để trống") IdentityType identityType,
      @NotBlank(message = "Số giấy tờ không được để trống")
          @Size(max = 40, message = "Số giấy tờ tối đa 40 ký tự")
          String identityNumber,
      @NotBlank(message = "Nơi cấp giấy tờ không được để trống")
          @Size(max = 80, message = "Nơi cấp giấy tờ tối đa 80 ký tự")
          String identityIssuer) {}

  // Identity fields are not updatable: they form the unique key uk_patients_identity.
  public record PatientUpdateRequest(
      @NotBlank(message = "Họ tên không được để trống")
          @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
          String fullName,
      // Range (not in the future, not before 1900) is checked in PatientService with the app clock.
      @NotNull(message = "Ngày sinh không được để trống") LocalDate dateOfBirth,
      @NotNull(message = "Giới tính không được để trống") Gender gender,
      @NotBlank(message = "Số điện thoại không được để trống")
          @Size(max = 25, message = "Số điện thoại tối đa 25 ký tự")
          @Pattern(regexp = PHONE_REGEX, message = "Số điện thoại không hợp lệ")
          String phone,
      @Email(message = "Email không hợp lệ") @Size(max = 254, message = "Email tối đa 254 ký tự")
          String contactEmail,
      @NotBlank(message = "Địa chỉ không được để trống")
          @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
          String address) {}

  // ADMIN-only. Only PERSONAL and VIP are assignable here; COMPANY is owned by the company module
  // and the future service must reject it.
  public record PatientClassUpdateRequest(
      @NotNull(message = "Hạng bệnh nhân không được để trống") PatientClass patientClass) {}

  @Schema(requiredProperties = {"cardNumber", "activatedAt", "expiresAt", "status"})
  public record PatientCardResponse(
      String cardNumber,
      Instant activatedAt,
      Instant expiresAt,
      Instant revokedAt,
      PatientCardStatus status) {}

  @Schema(requiredProperties = {"status", "valid", "daysRemaining", "expiresAt"})
  public record PatientCardValidityResponse(
      PatientCardStatus status,
      @Schema(description = "true khi thẻ còn dùng được (ACTIVE hoặc EXPIRING_SOON)") boolean valid,
      @Schema(
              description =
                  "Số ngày còn lại đến hạn (làm tròn lên); 0 khi thẻ đã hết hạn hoặc bị thu hồi")
          long daysRemaining,
      Instant expiresAt) {}

  // requiredProperties: always-present fields, so the generated TypeScript type is not optional.
  @Schema(
      requiredProperties = {
        "publicId",
        "fullName",
        "dateOfBirth",
        "gender",
        "phone",
        "address",
        "identityType",
        "identityNumber",
        "identityIssuer",
        "patientClass",
        "createdAt",
        "updatedAt"
      })
  public record PatientResponse(
      UUID publicId,
      String fullName,
      LocalDate dateOfBirth,
      Gender gender,
      String phone,
      String contactEmail,
      String address,
      IdentityType identityType,
      String identityNumber,
      String identityIssuer,
      PatientClass patientClass,
      String cardNumber,
      Instant cardActivatedAt,
      Instant cardExpiresAt,
      Instant cardRevokedAt,
      Instant createdAt,
      Instant updatedAt) {

    // Internal Long id and userId are deliberately not exposed.
    static PatientResponse from(Patient patient) {
      return new PatientResponse(
          patient.getPublicId(),
          patient.getFullName(),
          patient.getDateOfBirth(),
          patient.getGender(),
          patient.getPhone(),
          patient.getContactEmail(),
          patient.getAddress(),
          patient.getIdentityType(),
          patient.getIdentityNumber(),
          patient.getIdentityIssuer(),
          patient.getPatientClass(),
          patient.getCardNumber(),
          patient.getCardActivatedAt(),
          patient.getCardExpiresAt(),
          patient.getCardRevokedAt(),
          patient.getCreatedAt(),
          patient.getUpdatedAt());
    }
  }
}
