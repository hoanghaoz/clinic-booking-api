package com.se100.clinic.shared;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central exception handling that returns {@link ProblemDetail} (RFC 9457) bodies — the equivalent
 * of a global NestJS {@code ExceptionFilter} or ASP.NET Core's ProblemDetails middleware.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC's own errors keep their correct
 * status (unknown URL 404, malformed JSON 400, wrong method 405...) instead of being swallowed by
 * the catch-all handler below as 500.
 *
 * <p>Medical data: never put a raw system exception message in the response — it may contain SQL or
 * sensitive values (national ID, medical history). Unexpected errors get a generic message; the
 * detail is logged server-side only, and services must not put sensitive fields in exception
 * messages.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleNotFound(NotFoundException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException ex) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
  }

  /** Bean Validation failures (e.g. {@code @NotBlank}) on a {@code @Valid @RequestBody}. */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ");
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    problem.setProperty("errors", fieldErrors);
    return ResponseEntity.badRequest().body(problem);
  }

  /** Fallback for anything not handled above: never leak internal details to the client. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau");
  }
}
