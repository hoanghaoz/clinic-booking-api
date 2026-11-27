package com.se100.clinic.reception;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

enum ReceptionErrorCode implements ErrorCode {
  STAFF_PROFILE_NOT_FOUND(HttpStatus.FORBIDDEN),
  CLINIC_ACCESS_DENIED(HttpStatus.FORBIDDEN),
  STAFF_CLINIC_REQUIRED(HttpStatus.BAD_REQUEST),
  APPOINTMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
  RECEPTION_VISIT_NOT_FOUND(HttpStatus.NOT_FOUND),
  RECEPTION_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
  RECEPTION_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
  APPOINTMENT_ALREADY_CHECKED_IN(HttpStatus.CONFLICT),
  APPOINTMENT_NOT_RECEIVABLE(HttpStatus.CONFLICT),
  APPOINTMENT_NOT_TODAY(HttpStatus.CONFLICT),
  SHIFT_ALREADY_ENDED(HttpStatus.CONFLICT),
  PATIENT_CARD_REVOKED(HttpStatus.CONFLICT),
  CLINIC_NOT_OPEN(HttpStatus.CONFLICT),
  ROOM_NOT_IN_CLINIC(HttpStatus.CONFLICT),
  ROOM_NOT_OPEN(HttpStatus.CONFLICT),
  RECEPTION_VISIT_CLOSED(HttpStatus.CONFLICT),
  ORDER_NOT_ROUTABLE(HttpStatus.CONFLICT);

  private final HttpStatus status;

  ReceptionErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
