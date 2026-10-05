package com.se100.clinic.shared;

import java.util.List;

/**
 * Base class for expected, business-level failures. Carries an {@link ErrorCode}; {@link
 * GlobalExceptionHandler} turns it into a {@code ProblemDetail} with the code's HTTP status and
 * {@code code} field, so services never touch HTTP.
 *
 * <p>Throw it directly for module-specific rules:
 *
 * <pre>{@code
 * throw new BusinessException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ");
 * }</pre>
 *
 * <p>Never put sensitive data (national ID, medical history...) in the message — it is returned to
 * the client.
 */
public class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;
  private final transient List<FieldViolation> violations;

  public BusinessException(ErrorCode errorCode, String message) {
    this(errorCode, message, List.of());
  }

  public BusinessException(ErrorCode errorCode, String message, List<FieldViolation> violations) {
    super(message);
    this.errorCode = errorCode;
    this.violations = List.copyOf(violations);
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  /** Field-level details; empty unless the failure is about specific input fields. */
  public List<FieldViolation> getViolations() {
    return violations;
  }
}
