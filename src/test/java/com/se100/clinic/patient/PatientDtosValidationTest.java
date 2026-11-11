package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.se100.clinic.patient.PatientDtos.PatientCreateRequest;
import com.se100.clinic.patient.PatientDtos.PatientUpdateRequest;
import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.IdentityType;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Bean Validation edge cases of the patient request DTOs, checked with the real validator. Rules
 * that need the application clock (birth date range) live in {@code PatientServiceTest}.
 */
class PatientDtosValidationTest {

  private static final Validator VALIDATOR =
      Validation.buildDefaultValidatorFactory().getValidator();

  private static PatientCreateRequest create(
      String fullName, String phone, String email, String address, String identityNumber) {
    return new PatientCreateRequest(
        fullName,
        LocalDate.of(1990, 5, 20),
        Gender.MALE,
        phone,
        email,
        address,
        IdentityType.CCCD,
        identityNumber,
        "Cục CSQLHC");
  }

  private static Set<String> invalidFields(PatientCreateRequest request) {
    return VALIDATOR.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }

  @Test
  void create_shouldAcceptTypicalRequest() {
    assertThat(
            invalidFields(
                create("Nguyễn Văn A", "0901234567", "a@example.com", "Quận 3", "079123456789")))
        .isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"090 123-4567", "+84 901 234 567", "090.123.4567", "0901234"})
  void create_shouldAcceptPhoneFormatsWithSeparators(String phone) {
    assertThat(invalidFields(create("A", phone, null, "Quận 3", "1"))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"abc", "123456", "090@1234567", "++84901234567", "0901234567x", "090 (1)"})
  void create_shouldRejectMalformedPhone(String phone) {
    assertThat(invalidFields(create("A", phone, null, "Quận 3", "1"))).containsExactly("phone");
  }

  @Test
  void create_shouldRejectPhoneLongerThan25Chars() {
    assertThat(invalidFields(create("A", "0".repeat(26), null, "Quận 3", "1"))).contains("phone");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void create_shouldRejectBlankRequiredText(String blank) {
    assertThat(invalidFields(create(blank, "0901234567", null, blank, blank)))
        .containsExactlyInAnyOrder("fullName", "address", "identityNumber");
  }

  @Test
  void create_shouldRejectOverlongText() {
    assertThat(invalidFields(create("A".repeat(151), "0901234567", null, "Quận 3", "1")))
        .containsExactly("fullName");
    assertThat(invalidFields(create("A", "0901234567", null, "Q".repeat(501), "1")))
        .containsExactly("address");
    assertThat(invalidFields(create("A", "0901234567", null, "Quận 3", "9".repeat(41))))
        .containsExactly("identityNumber");
    assertThat(invalidFields(create("A", "0901234567", "a".repeat(250) + "@x.vn", "Quận 3", "1")))
        .containsExactly("contactEmail");
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-an-email", "a@", "@x.vn", "a b@x.vn"})
  void create_shouldRejectMalformedEmail(String email) {
    assertThat(invalidFields(create("A", "0901234567", email, "Quận 3", "1")))
        .containsExactly("contactEmail");
  }

  @Test
  void create_shouldRequireBirthDateGenderAndIdentityType() {
    var request =
        new PatientCreateRequest(
            "A", null, null, "0901234567", null, "Quận 3", null, "1", "Cục CSQLHC");

    assertThat(invalidFields(request))
        .containsExactlyInAnyOrder("dateOfBirth", "gender", "identityType");
  }

  @Test
  void update_shouldApplyTheSameRules() {
    var request = new PatientUpdateRequest("", null, null, "abc", "bad", "Q".repeat(501));

    assertThat(
            VALIDATOR.validate(request).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrder(
            "fullName", "dateOfBirth", "gender", "phone", "contactEmail", "address");
  }
}
