package com.se100.clinic.shared;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception handling that returns {@link ProblemDetail} (RFC 9457) bodies — the equivalent
 * of a global NestJS {@code ExceptionFilter} or ASP.NET Core's ProblemDetails middleware.
 *
 * <p>EVERY error body carries a machine-readable {@code code} property (see {@link ErrorCode}); the
 * frontend branches on {@code code}, not on {@code detail}. Errors about specific input fields
 * ({@code VALIDATION_ERROR}) also carry {@code errors: [{field, message}]}. Full contract:
 * docs/conventions/api-conventions.md.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own errors keep their correct
 * status (unknown URL 404, malformed JSON 400, wrong method 405...) instead of being swallowed by
 * the catch-all handler below as 500; {@link #handleExceptionInternal} stamps a default {@code
 * code} onto those.
 *
 * <p>Medical data: never put a raw system exception message in the response — it may contain SQL or
 * sensitive values (national ID, medical history). Unexpected errors get a generic message; the
 * detail is logged server-side only, and services must not put sensitive fields in exception
 * messages. For the same reason, DB constraint violations are logged by SQLSTATE + constraint name
 * only (the driver message contains the offending values).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  /** Name of the extra ProblemDetail property holding the {@link ErrorCode}. */
  static final String CODE = "code";

  /** Name of the extra ProblemDetail property holding the {@link FieldViolation} list. */
  static final String ERRORS = "errors";

  @ExceptionHandler(BusinessException.class)
  public ProblemDetail handleBusiness(BusinessException ex) {
    return problem(ex.getErrorCode(), ex.getMessage(), ex.getViolations());
  }

  /**
   * A write was rejected by a DB constraint that the service did not translate into a business
   * code. Map by SQLSTATE so a race (two requests passing the same {@code existsBy...} check) or a
   * forgotten pre-check yields a 4xx, never a generic 500.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
    String sqlState = DbErrors.sqlState(ex);
    log.warn(
        "DB constraint violated: sqlState={}, constraint={}",
        sqlState,
        DbErrors.constraintName(ex));
    if (sqlState == null) {
      return problem(
          CommonErrorCode.DATA_INTEGRITY_VIOLATION,
          "Dữ liệu vi phạm ràng buộc toàn vẹn",
          List.of());
    }
    return switch (sqlState) {
      case DbErrors.UNIQUE_VIOLATION ->
          problem(CommonErrorCode.DUPLICATE_RESOURCE, "Dữ liệu đã tồn tại", List.of());
      case DbErrors.FOREIGN_KEY_VIOLATION ->
          problem(
              CommonErrorCode.REFERENCE_VIOLATION,
              "Dữ liệu đang được tham chiếu hoặc tham chiếu tới bản ghi không tồn tại",
              List.of());
      case DbErrors.EXCLUSION_VIOLATION ->
          problem(CommonErrorCode.CONFLICT, "Dữ liệu xung đột với bản ghi hiện có", List.of());
      case DbErrors.NOT_NULL_VIOLATION ->
          problem(CommonErrorCode.INVALID_REQUEST, "Thiếu dữ liệu bắt buộc", List.of());
      case DbErrors.CHECK_VIOLATION ->
          problem(CommonErrorCode.INVALID_REQUEST, "Dữ liệu vi phạm ràng buộc kiểm tra", List.of());
      default ->
          // SQLSTATE class 22 = data exception (value too long, bad number format...).
          sqlState.startsWith("22")
              ? problem(CommonErrorCode.INVALID_REQUEST, "Giá trị dữ liệu không hợp lệ", List.of())
              : problem(
                  CommonErrorCode.DATA_INTEGRITY_VIOLATION,
                  "Dữ liệu vi phạm ràng buộc toàn vẹn",
                  List.of());
    };
  }

  /** Another request modified the same row first ({@code @Version} mismatch). */
  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
    log.warn("Optimistic lock failure: {}", ex.getClass().getSimpleName());
    return problem(
        CommonErrorCode.CONFLICT,
        "Dữ liệu vừa được thay đổi bởi người khác, vui lòng tải lại và thử lại",
        List.of());
  }

  /** Bean Validation failures (e.g. {@code @NotBlank}) on a {@code @Valid @RequestBody}. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldViolation> violations =
        ex.getBindingResult().getAllErrors().stream().map(this::toViolation).toList();
    return respond(
        problem(CommonErrorCode.VALIDATION_ERROR, "Dữ liệu gửi lên không hợp lệ", violations));
  }

  /** A query/path parameter could not be converted, e.g. {@code ?active=abc} or {@code ?page=x}. */
  @Override
  protected ResponseEntity<Object> handleTypeMismatch(
      TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    String field =
        ex instanceof MethodArgumentTypeMismatchException methodArgument
            ? methodArgument.getName()
            : ex.getPropertyName();
    // The offending value is deliberately not echoed back.
    List<FieldViolation> violations =
        List.of(new FieldViolation(field == null ? "" : field, "Giá trị không hợp lệ"));
    return respond(
        problem(CommonErrorCode.VALIDATION_ERROR, "Tham số truy vấn không hợp lệ", violations));
  }

  /** Body is not parseable JSON, or a value has the wrong JSON type. */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return respond(
        problem(
            CommonErrorCode.INVALID_REQUEST,
            "Nội dung request không đọc được (sai định dạng JSON hoặc sai kiểu dữ liệu)",
            List.of()));
  }

  /**
   * All other Spring MVC errors (404 unknown URL, 405, 415, missing parameter...) keep Spring's
   * status and get a default {@code code} derived from it.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
      problem.setProperty(CODE, defaultCodeFor(statusCode).code());
    }
    return response;
  }

  /** Fallback for anything not handled above: never leak internal details to the client. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        CommonErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau", List.of());
  }

  private static ProblemDetail problem(
      ErrorCode errorCode, String detail, List<FieldViolation> violations) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
    problem.setProperty(CODE, errorCode.code());
    if (!violations.isEmpty()) {
      problem.setProperty(ERRORS, violations);
    }
    return problem;
  }

  private static ResponseEntity<Object> respond(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus()).body(problem);
  }

  private FieldViolation toViolation(ObjectError error) {
    if (error instanceof FieldError fieldError) {
      // A binding failure (wrong type) has a long, technical default message — replace it.
      String message =
          fieldError.isBindingFailure() ? "Giá trị không hợp lệ" : fieldError.getDefaultMessage();
      return new FieldViolation(fieldError.getField(), message);
    }
    return new FieldViolation(error.getObjectName(), error.getDefaultMessage());
  }

  private static ErrorCode defaultCodeFor(HttpStatusCode status) {
    if (status.value() == HttpStatus.NOT_FOUND.value()) {
      return CommonErrorCode.NOT_FOUND;
    } else if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
      return CommonErrorCode.METHOD_NOT_ALLOWED;
    } else if (status.value() == HttpStatus.NOT_ACCEPTABLE.value()) {
      return CommonErrorCode.NOT_ACCEPTABLE;
    } else if (status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
      return CommonErrorCode.UNSUPPORTED_MEDIA_TYPE;
    } else if (status.is5xxServerError()) {
      return CommonErrorCode.INTERNAL_ERROR;
    }
    return CommonErrorCode.INVALID_REQUEST;
  }
}
