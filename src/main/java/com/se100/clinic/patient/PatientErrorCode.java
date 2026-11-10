package com.se100.clinic.patient;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Business error codes of the {@code patient} module. */
enum PatientErrorCode implements ErrorCode {
  PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  /** The authenticated account already owns a patient profile. */
  PATIENT_PROFILE_EXISTS(HttpStatus.CONFLICT),
  /** Same identity type, issuer and number is already registered. */
  PATIENT_IDENTITY_EXISTS(HttpStatus.CONFLICT),
  /** The patient has no card (legacy rows only; new profiles always get one). */
  PATIENT_CARD_NOT_FOUND(HttpStatus.NOT_FOUND),
  PATIENT_MEDICAL_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND);

  private final HttpStatus status;

  PatientErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
