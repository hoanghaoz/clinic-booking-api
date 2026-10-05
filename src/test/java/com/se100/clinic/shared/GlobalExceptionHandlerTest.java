package com.se100.clinic.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Pins the error contract documented in docs/conventions/api-conventions.md: HTTP status + {@code
 * code} (+ {@code errors[]}) for every kind of failure. Standalone MockMvc — no Spring context, no
 * Docker — so it runs with {@code ./gradlew test}.
 */
class GlobalExceptionHandlerTest {

  enum TestErrorCode implements ErrorCode {
    SLOT_FULL(HttpStatus.CONFLICT),
    APPOINTMENT_OVERLAP(HttpStatus.CONFLICT),
    PATIENT_BLOCKED(HttpStatus.FORBIDDEN);

    private final HttpStatus status;

    TestErrorCode(HttpStatus status) {
      this.status = status;
    }

    @Override
    public HttpStatus status() {
      return status;
    }
  }

  record Body(@NotBlank(message = "Tên không được để trống") String name) {}

  @RestController
  static class ThrowingController {

    @GetMapping("/business")
    String business() {
      throw new BusinessException(TestErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ");
    }

    @GetMapping("/business-forbidden")
    String businessForbidden() {
      throw new BusinessException(TestErrorCode.PATIENT_BLOCKED, "Bệnh nhân bị khoá đặt lịch");
    }

    @GetMapping("/not-found")
    String notFound() {
      throw new NotFoundException("Không có");
    }

    @GetMapping("/conflict")
    String conflict() {
      throw new ConflictException("Trùng");
    }

    @GetMapping("/violations")
    String violations() {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "sai",
          List.of(new FieldViolation("page", "page >= 1")));
    }

    @PostMapping("/body")
    String body(@Valid @RequestBody Body body) {
      return body.name();
    }

    @GetMapping("/typed")
    String typed(@RequestParam(required = false) Boolean active) {
      return String.valueOf(active);
    }

    @GetMapping("/required")
    String required(@RequestParam String must) {
      return must;
    }

    @GetMapping("/db/{sqlState}")
    String db(@PathVariable String sqlState) {
      throw new DataIntegrityViolationException(
          "could not execute statement; Key (national_id)=(079123456789) already exists",
          new SQLException("raw driver message with 079123456789", sqlState));
    }

    @GetMapping("/db-no-state")
    String dbNoState() {
      throw new DataIntegrityViolationException("no sqlstate available");
    }

    @GetMapping("/optimistic")
    String optimistic() {
      throw new OptimisticLockingFailureException("row was updated by another transaction");
    }

    @GetMapping("/boom")
    String boom() {
      throw new IllegalStateException("NullPointer at patient 079123456789 SELECT * FROM patients");
    }
  }

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void businessException_usesItsCodeAndStatus_asProblemJson() throws Exception {
    mockMvc
        .perform(get("/business"))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("SLOT_FULL"))
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.detail").value("Khung giờ đã hết chỗ"))
        .andExpect(jsonPath("$.instance").value("/business"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void businessException_statusComesFromErrorCode_notAlwaysConflict() throws Exception {
    mockMvc
        .perform(get("/business-forbidden"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PATIENT_BLOCKED"));
  }

  @Test
  void notFoundAndConflictExceptions_fallBackToCommonCodes() throws Exception {
    mockMvc
        .perform(get("/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mockMvc
        .perform(get("/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void businessExceptionWithViolations_exposesErrorsArray() throws Exception {
    mockMvc
        .perform(get("/violations"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("page"))
        .andExpect(jsonPath("$.errors[0].message").value("page >= 1"));
  }

  @Test
  void bodyValidationFailure_isValidationErrorWithFieldList() throws Exception {
    mockMvc
        .perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors.length()").value(1))
        .andExpect(jsonPath("$.errors[0].field").value("name"))
        .andExpect(jsonPath("$.errors[0].message").value("Tên không được để trống"));
  }

  @Test
  void malformedJson_isInvalidRequest_withoutErrorsArray() throws Exception {
    mockMvc
        .perform(post("/body").contentType(MediaType.APPLICATION_JSON).content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  @Test
  void queryParamOfWrongType_isValidationErrorNamingTheParam_withoutEchoingValue()
      throws Exception {
    mockMvc
        .perform(get("/typed").param("active", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors[0].field").value("active"))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("abc"))));
  }

  @Test
  void missingRequiredParam_isInvalidRequest() throws Exception {
    mockMvc
        .perform(get("/required"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void wrongHttpMethod_keepsSpring405_withCode() throws Exception {
    mockMvc
        .perform(post("/business"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
  }

  @Test
  void unsupportedMediaType_keepsSpring415_withCode() throws Exception {
    mockMvc
        .perform(post("/body").contentType(MediaType.TEXT_PLAIN).content("x"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void dbUniqueViolation_is409DuplicateResource_notGeneric500() throws Exception {
    mockMvc
        .perform(get("/db/23505"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
  }

  @Test
  void dbForeignKeyViolation_is409ReferenceViolation() throws Exception {
    mockMvc
        .perform(get("/db/23503"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REFERENCE_VIOLATION"));
  }

  @Test
  void dbExclusionViolation_is409Conflict() throws Exception {
    // e.g. two overlapping appointments rejected by an EXCLUDE constraint.
    mockMvc
        .perform(get("/db/23P01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void dbNotNullAndCheckViolations_are400InvalidRequest() throws Exception {
    mockMvc
        .perform(get("/db/23502"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(get("/db/23514"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void dbDataException_class22_is400InvalidRequest() throws Exception {
    // 22001 = string_data_right_truncation (value too long for the column).
    mockMvc
        .perform(get("/db/22001"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void dbOtherIntegrityViolation_orUnknownState_is409DataIntegrityViolation() throws Exception {
    mockMvc
        .perform(get("/db/23001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
    mockMvc
        .perform(get("/db-no-state"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"));
  }

  @Test
  void dbViolation_neverLeaksDriverMessageOrValues() throws Exception {
    String body = mockMvc.perform(get("/db/23505")).andReturn().getResponse().getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("079123456789")
        .doesNotContain("national_id")
        .doesNotContain("driver message");
  }

  @Test
  void optimisticLockFailure_is409Conflict() throws Exception {
    mockMvc
        .perform(get("/optimistic"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void unexpectedException_is500InternalError_withGenericMessageOnly() throws Exception {
    String body =
        mockMvc
            .perform(get("/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    org.assertj.core.api.Assertions.assertThat(body)
        .doesNotContain("079123456789")
        .doesNotContain("SELECT")
        .doesNotContain("IllegalState");
  }
}
