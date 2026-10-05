package com.se100.clinic.shared;

import org.springframework.http.HttpStatus;

/**
 * A machine-readable error code plus the HTTP status it is returned with. The frontend branches on
 * {@link #code()} (the {@code code} field of the error body), never on the human-readable {@code
 * detail} text.
 *
 * <p>Implemented by enums: {@link CommonErrorCode} for cross-cutting errors, and one enum per
 * module for its own business errors (e.g. {@code BookingErrorCode.SLOT_FULL}). See
 * docs/conventions/api-conventions.md.
 */
public interface ErrorCode {

  /** Enum constant name, e.g. {@code SLOT_FULL}. Enums satisfy this automatically. */
  String name();

  HttpStatus status();

  /** The value sent to the frontend. Defaults to the enum constant name. */
  default String code() {
    return name();
  }
}
