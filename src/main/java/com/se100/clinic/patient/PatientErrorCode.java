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
  PATIENT_MEDICAL_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND),
  /** A COMPANY patient belongs to a corporate contract, so its class is not changed manually. */
  PATIENT_CLASS_LOCKED(HttpStatus.CONFLICT),
  /** The card is revoked, so it cannot be renewed or revoked again; reinstate it first. */
  PATIENT_CARD_REVOKED(HttpStatus.CONFLICT),
  /** Reinstating needs a revoked card. */
  PATIENT_CARD_NOT_REVOKED(HttpStatus.CONFLICT),
  /**
   * No walk-in profile matches the identity and phone sent when linking an account. One code for
   * every miss (unknown, wrong phone, already linked) so it cannot be used to probe for profiles.
   */
  PATIENT_LINK_NOT_MATCHED(HttpStatus.NOT_FOUND);

  private final HttpStatus status;

  PatientErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
