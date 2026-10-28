package com.se100.clinic.appointment;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ReservationErrorCode implements ErrorCode {
  APPOINTMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  APPOINTMENT_NOT_BOOKABLE(HttpStatus.CONFLICT),
  APPOINTMENT_NOT_RELEASABLE(HttpStatus.CONFLICT),
  APPOINTMENT_CHANGED(HttpStatus.CONFLICT),
  APPOINTMENT_ALREADY_RESERVED(HttpStatus.CONFLICT),
  SLOT_OCCUPIED(HttpStatus.CONFLICT);

  private final HttpStatus status;

  ReservationErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
