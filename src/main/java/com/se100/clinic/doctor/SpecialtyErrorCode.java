package com.se100.clinic.doctor;

import com.se100.clinic.shared.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * Business error codes of the {@code doctor} module — the pattern every module follows: a
 * package-private enum implementing {@link ErrorCode}, one constant per rule the frontend needs to
 * recognise. The constant name IS the {@code code} sent to the frontend, so rename with care (it is
 * part of the API contract).
 *
 * <p>Naming: {@code <ENTITY>_<WHAT_HAPPENED>}, UPPER_SNAKE_CASE, globally unique across modules
 * (e.g. {@code SLOT_FULL}, {@code APPOINTMENT_OVERLAP} in the future {@code booking} module).
 */
enum SpecialtyErrorCode implements ErrorCode {
  SPECIALTY_NOT_FOUND(HttpStatus.NOT_FOUND),
  SPECIALTY_CODE_EXISTS(HttpStatus.CONFLICT);

  private final HttpStatus status;

  SpecialtyErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
