package com.se100.clinic.shared;

/**
 * Thrown when a record cannot be found by id/code.
 *
 * <p>Unchecked (extends {@link RuntimeException}), like NestJS's {@code NotFoundException} — no
 * {@code throws} clause needed. {@link GlobalExceptionHandler} maps it to HTTP 404 as a {@code
 * ProblemDetail}, so controllers and services never set HTTP statuses themselves.
 */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
