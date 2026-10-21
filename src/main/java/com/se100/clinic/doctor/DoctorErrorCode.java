package com.se100.clinic.doctor;

import com.se100.clinic.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/** Business error codes of the Doctor part of the {@code doctor} module. */
enum DoctorErrorCode implements ErrorCode {
  DOCTOR_NOT_FOUND(HttpStatus.NOT_FOUND),
  DOCTOR_CODE_EXISTS(HttpStatus.CONFLICT),
  /** {@code userId} in the request does not match any account. */
  DOCTOR_USER_NOT_FOUND(HttpStatus.NOT_FOUND),
  /** The account already has a doctor profile (one account = at most one doctor). */
  DOCTOR_USER_ALREADY_LINKED(HttpStatus.CONFLICT),
  /** A specialty cannot be removed while work schedules still use it for this doctor. */
  DOCTOR_SPECIALTY_IN_USE(HttpStatus.CONFLICT),
  /** The doctor is NOT_WORKING, so cannot be given new assignments. */
  DOCTOR_NOT_WORKING(HttpStatus.CONFLICT),
  /** The doctor does not have the requested specialty. */
  DOCTOR_SPECIALTY_NOT_ASSIGNED(HttpStatus.CONFLICT);

  private final HttpStatus status;

  DoctorErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
