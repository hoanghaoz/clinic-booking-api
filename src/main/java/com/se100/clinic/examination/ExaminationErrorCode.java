package com.se100.clinic.examination;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Business error codes of the doctor's examination flow (REC-05, EXAM-01..03). */
enum ExaminationErrorCode implements ErrorCode {
  VISIT_NOT_FOUND(HttpStatus.NOT_FOUND),
  /** The visit exists but belongs to another doctor. */
  VISIT_NOT_ASSIGNED(HttpStatus.FORBIDDEN),
  /** The logged-in user has no doctor profile. */
  VISIT_DOCTOR_PROFILE_NOT_FOUND(HttpStatus.FORBIDDEN),
  /** The visit is COMPLETED or CANCELLED, so its clinical record is read-only. */
  VISIT_CLOSED(HttpStatus.CONFLICT),
  /** Someone else saved the visit after the client loaded it. */
  VISIT_VERSION_CONFLICT(HttpStatus.CONFLICT),
  INVALID_VITAL_SIGNS(HttpStatus.BAD_REQUEST);

  private final HttpStatus status;

  ExaminationErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
