package com.se100.clinic.shared;

/**
 * Thrown when a request conflicts with current state, e.g. creating a specialty whose code already
 * exists. Like NestJS's {@code ConflictException}; mapped to HTTP 409 by {@link
 * GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
