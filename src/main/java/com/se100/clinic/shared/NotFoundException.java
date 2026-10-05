package com.se100.clinic.shared;

/**
 * Thrown when a record cannot be found by id/code.
 *
 * <p>Unchecked (extends {@link RuntimeException} via {@link BusinessException}), like NestJS's
 * {@code NotFoundException} — no {@code throws} clause needed. {@link GlobalExceptionHandler} maps
 * it to the HTTP status of its {@link ErrorCode} (404) as a {@code ProblemDetail}, so controllers
 * and services never set HTTP statuses themselves.
 *
 * <p>Prefer the {@code (ErrorCode, message)} constructor with a module code (e.g. {@code
 * SPECIALTY_NOT_FOUND}) so the frontend can tell which resource was missing.
 */
public class NotFoundException extends BusinessException {

  public NotFoundException(String message) {
    super(CommonErrorCode.NOT_FOUND, message);
  }

  public NotFoundException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
