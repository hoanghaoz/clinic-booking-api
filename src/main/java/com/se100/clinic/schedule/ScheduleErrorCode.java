package com.se100.clinic.schedule;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ScheduleErrorCode implements ErrorCode {
  SHIFT_NOT_FOUND(HttpStatus.NOT_FOUND),
  SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND),
  SCHEDULE_NOT_ASSIGNED(HttpStatus.NOT_FOUND),
  SLOT_NOT_FOUND(HttpStatus.NOT_FOUND),
  DOCTOR_NOT_FOUND(HttpStatus.NOT_FOUND),
  DOCTOR_PROFILE_NOT_FOUND(HttpStatus.FORBIDDEN),
  AUTH_REQUIRED(HttpStatus.UNAUTHORIZED),
  ROOM_NOT_FOUND(HttpStatus.NOT_FOUND),
  INVALID_SHIFT(HttpStatus.BAD_REQUEST),
  INVALID_CAPACITY(HttpStatus.BAD_REQUEST),
  CAPACITY_BELOW_BOOKED(HttpStatus.CONFLICT),
  INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
  DOCTOR_NOT_WORKING(HttpStatus.CONFLICT),
  DOCTOR_SPECIALTY_MISMATCH(HttpStatus.CONFLICT),
  FACILITY_NOT_OPEN(HttpStatus.CONFLICT),
  ROOM_CLINIC_MISMATCH(HttpStatus.CONFLICT),
  SCHEDULE_OVERLAP(HttpStatus.CONFLICT),
  SCHEDULE_HAS_APPOINTMENTS(HttpStatus.CONFLICT),
  SLOT_OCCUPIED(HttpStatus.CONFLICT);

  private final HttpStatus status;

  ScheduleErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
