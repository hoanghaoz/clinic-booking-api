package com.se100.clinic.facility;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

enum FacilityErrorCode implements ErrorCode {
  CLINIC_NOT_FOUND(HttpStatus.NOT_FOUND),
  FACILITY_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
  CLINIC_CODE_EXISTS(HttpStatus.CONFLICT),
  ROOM_CODE_EXISTS(HttpStatus.CONFLICT),
  CLINIC_CLOSED(HttpStatus.CONFLICT),
  FACILITY_HAS_SCHEDULES(HttpStatus.CONFLICT);

  private final HttpStatus status;

  FacilityErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
