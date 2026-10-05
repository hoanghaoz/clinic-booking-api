package com.se100.clinic.shared;

import org.springframework.http.HttpStatus;

/**
 * Error codes shared by every module. Module-specific business errors do NOT go here — define an
 * enum implementing {@link ErrorCode} inside the module (see {@code SpecialtyErrorCode}).
 */
public enum CommonErrorCode implements ErrorCode {
  /** 400 with field-level {@code errors[]}: bad body field, bad query param, bad page/sort. */
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
  /** 400 without field info: unreadable JSON, missing required param/header. */
  INVALID_REQUEST(HttpStatus.BAD_REQUEST),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
  NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
  UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
  /** 409: generic state conflict (also optimistic-lock failures, exclusion constraints). */
  CONFLICT(HttpStatus.CONFLICT),
  /** 409: a DB unique constraint was hit and no module-specific code translated it. */
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
  /** 409: a DB foreign-key constraint was hit. */
  REFERENCE_VIOLATION(HttpStatus.CONFLICT),
  /** 409: some other DB integrity constraint was hit. */
  DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  CommonErrorCode(HttpStatus status) {
    this.status = status;
  }

  @Override
  public HttpStatus status() {
    return status;
  }
}
