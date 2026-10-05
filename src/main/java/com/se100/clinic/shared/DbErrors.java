package com.se100.clinic.shared;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Helpers to read WHY the database rejected a write, from the {@code
 * DataIntegrityViolationException} Spring throws. Used by {@link GlobalExceptionHandler} for the
 * generic mapping, and by services that want a module-specific code instead:
 *
 * <pre>{@code
 * try {
 *   bookingRepository.saveAndFlush(booking);
 * } catch (DataIntegrityViolationException e) {
 *   if (DbErrors.isUniqueViolation(e)) {
 *     throw new ConflictException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ");
 *   }
 *   throw e;
 * }
 * }</pre>
 *
 * Use {@code saveAndFlush} (not {@code save}) so the violation surfaces inside the try block rather
 * than at commit time.
 */
public final class DbErrors {

  /** PostgreSQL SQLSTATE codes (class 23 = integrity constraint violation). */
  public static final String UNIQUE_VIOLATION = "23505";

  public static final String FOREIGN_KEY_VIOLATION = "23503";
  public static final String NOT_NULL_VIOLATION = "23502";
  public static final String CHECK_VIOLATION = "23514";
  public static final String EXCLUSION_VIOLATION = "23P01";

  private DbErrors() {}

  /** SQLSTATE of the first {@link SQLException} in the cause chain, or {@code null}. */
  public static String sqlState(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof SQLException sql && sql.getSQLState() != null) {
        return sql.getSQLState();
      }
    }
    return null;
  }

  /** Name of the violated constraint when Hibernate reports it, else {@code null}. */
  public static String constraintName(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
        return cve.getConstraintName();
      }
    }
    return null;
  }

  public static boolean isUniqueViolation(Throwable error) {
    return UNIQUE_VIOLATION.equals(sqlState(error));
  }

  /** True for {@code EXCLUDE USING gist} violations — e.g. two overlapping appointments. */
  public static boolean isExclusionViolation(Throwable error) {
    return EXCLUSION_VIOLATION.equals(sqlState(error));
  }
}
