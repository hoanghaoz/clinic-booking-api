package com.se100.clinic.shared;

/**
 * Thrown when a request conflicts with current state, e.g. creating a specialty whose code already
 * exists. Like NestJS's {@code ConflictException}; mapped to HTTP 409 by {@link
 * GlobalExceptionHandler}.
 *
 * <p>Prefer the {@code (ErrorCode, message)} constructor with a module code (e.g. {@code
 * SPECIALTY_CODE_EXISTS}) so the frontend can react to the specific conflict.
 */
public class ConflictException extends BusinessException {

  public ConflictException(String message) {
    super(CommonErrorCode.CONFLICT, message);
  }

  public ConflictException(ErrorCode errorCode, String message) {
    super(errorCode, message);
  }
}
