package com.se100.clinic.appointment;

import com.se100.clinic.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

enum BookingErrorCode implements ErrorCode {
  PATIENT_PROFILE_REQUIRED(HttpStatus.CONFLICT),
  ONLINE_BOOKING_RESTRICTED(HttpStatus.FORBIDDEN),
  INVALID_BOOKING_PRODUCT(HttpStatus.BAD_REQUEST),
  BOOKING_DATE_LIMIT(HttpStatus.BAD_REQUEST),
  BOOKING_START_IN_PAST(HttpStatus.BAD_REQUEST),
  BOOKING_PRODUCT_INELIGIBLE(HttpStatus.CONFLICT),
  BOOKING_SPECIALTY_MISMATCH(HttpStatus.CONFLICT),
  BOOKING_SCHEDULE_CHANGED(HttpStatus.CONFLICT),
  APPOINTMENT_OVERLAP(HttpStatus.CONFLICT),
  APPOINTMENT_OTHER_CLINIC_SAME_SHIFT(HttpStatus.CONFLICT);

  private final HttpStatus status;

  BookingErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
