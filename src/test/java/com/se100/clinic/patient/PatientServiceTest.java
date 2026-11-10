package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.patient.PatientDtos.PatientCardResponse;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientCreateRequest;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.PatientDtos.PatientUpdateRequest;
import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** Pure unit test: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

  // 2026-11-10 10:00 in Asia/Ho_Chi_Minh.
  private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
  private static final Instant NEXT_YEAR = Instant.parse("2027-11-10T03:00:00Z");
  private static final PatientActor PATIENT = new PatientActor(7L, UserRole.PATIENT);
  private static final PatientActor OTHER_PATIENT = new PatientActor(8L, UserRole.PATIENT);
  private static final PatientActor STAFF = new PatientActor(20L, UserRole.MEDICAL_STAFF);
  private static final PatientActor ADMIN = new PatientActor(1L, UserRole.ADMIN);

  @Mock private PatientRepository patientRepository;

  private PatientService patientService;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
    patientService = new PatientService(patientRepository, clock);
  }

  // ---- create ----

  @Test
  void create_shouldLinkAccountAndIssueOneYearCard_whenPatientCreatesOwnProfile() {
    stubCreateHappyPath();
    when(patientRepository.existsByUserId(7L)).thenReturn(false);

    PatientResponse response = patientService.create(createRequest(), PATIENT);

    Patient saved = captureSaved();
    assertThat(saved.getUserId()).isEqualTo(7L);
    assertThat(saved.getPatientClass()).isEqualTo(PatientClass.PERSONAL);
    assertThat(saved.getCardNumber()).matches("BN2026-[A-Z0-9]{8}");
    assertThat(saved.getCardActivatedAt()).isEqualTo(NOW);
    assertThat(saved.getCardExpiresAt()).isEqualTo(NEXT_YEAR);
    assertThat(response.cardNumber()).isEqualTo(saved.getCardNumber());
    assertThat(response.fullName()).isEqualTo("Nguyễn Văn A");
  }

  @Test
  void create_shouldLeaveAccountEmptyAndIssueCard_whenStaffCreatesWalkInProfile() {
    stubCreateHappyPath();

    patientService.create(createRequest(), STAFF);

    Patient saved = captureSaved();
    assertThat(saved.getUserId()).isNull();
    assertThat(saved.getCardExpiresAt()).isEqualTo(NEXT_YEAR);
    verify(patientRepository, never()).existsByUserId(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"ADMIN", "DOCTOR", "COMPANY"})
  void create_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    var actor = new PatientActor(99L, role);

    assertThatThrownBy(() -> patientService.create(createRequest(), actor))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    verify(patientRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldThrowConflict_whenAccountAlreadyHasProfile() {
    when(patientRepository.existsByUserId(7L)).thenReturn(true);

    assertThatThrownBy(() -> patientService.create(createRequest(), PATIENT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_PROFILE_EXISTS));
    verify(patientRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldThrowConflict_whenIdentityAlreadyExists() {
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            "CCCD", "Cục CSQLHC", "079123456789"))
        .thenReturn(true);

    assertThatThrownBy(() -> patientService.create(createRequest(), STAFF))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_IDENTITY_EXISTS));
    verify(patientRepository, never()).saveAndFlush(any());
  }

  @Test
  void create_shouldTranslateUniqueViolation_whenRaceOnIdentity() {
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            any(), any(), any()))
        .thenReturn(false);
    when(patientRepository.existsByCardNumber(any())).thenReturn(false);
    when(patientRepository.saveAndFlush(any(Patient.class)))
        .thenThrow(uniqueViolation("uk_patients_identity"));

    assertThatThrownBy(() -> patientService.create(createRequest(), STAFF))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_IDENTITY_EXISTS));
  }

  @Test
  void create_shouldTranslateUniqueViolation_whenRaceOnAccount() {
    when(patientRepository.existsByUserId(7L)).thenReturn(false);
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            any(), any(), any()))
        .thenReturn(false);
    when(patientRepository.existsByCardNumber(any())).thenReturn(false);
    when(patientRepository.saveAndFlush(any(Patient.class)))
        .thenThrow(uniqueViolation("patients_user_id_key"));

    assertThatThrownBy(() -> patientService.create(createRequest(), PATIENT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_PROFILE_EXISTS));
  }

  @Test
  void create_shouldRetryCardNumber_whenFirstCandidateTaken() {
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            any(), any(), any()))
        .thenReturn(false);
    when(patientRepository.existsByCardNumber(any())).thenReturn(true, false);
    when(patientRepository.saveAndFlush(any(Patient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    patientService.create(createRequest(), STAFF);

    verify(patientRepository, org.mockito.Mockito.times(2)).existsByCardNumber(any());
  }

  @Test
  void create_shouldThrowConflict_whenEveryCardCandidateTaken() {
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            any(), any(), any()))
        .thenReturn(false);
    when(patientRepository.existsByCardNumber(any())).thenReturn(true);

    assertThatThrownBy(() -> patientService.create(createRequest(), STAFF))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.CONFLICT));
    verify(patientRepository, never()).saveAndFlush(any());
  }

  // ---- update ----

  @Test
  void update_shouldChangeEditableFields_whenPatientOwnsProfile() {
    UUID publicId = UUID.randomUUID();
    Patient patient = patient(7L);
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    PatientResponse response = patientService.update(publicId, updateRequest(), PATIENT);

    assertThat(patient.getFullName()).isEqualTo("Nguyễn Văn B");
    assertThat(patient.getPhone()).isEqualTo("0987654321");
    assertThat(patient.getContactEmail()).isEqualTo("b@example.com");
    assertThat(patient.getAddress()).isEqualTo("Quận 1");
    // Identity fields and class are not editable.
    assertThat(patient.getIdentityNumber()).isEqualTo("079123456789");
    assertThat(patient.getPatientClass()).isEqualTo(PatientClass.PERSONAL);
    assertThat(response.fullName()).isEqualTo("Nguyễn Văn B");
  }

  @Test
  void update_shouldAllowAnyPatient_whenActorIsStaff() {
    UUID publicId = UUID.randomUUID();
    Patient patient = patient(7L);
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    patientService.update(publicId, updateRequest(), STAFF);

    assertThat(patient.getFullName()).isEqualTo("Nguyễn Văn B");
  }

  @Test
  void update_shouldThrowForbidden_whenPatientUpdatesSomeoneElse() {
    UUID publicId = UUID.randomUUID();
    Patient patient = patient(7L);
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    assertThatThrownBy(() -> patientService.update(publicId, updateRequest(), OTHER_PATIENT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    assertThat(patient.getFullName()).isEqualTo("Nguyễn Văn A");
  }

  @Test
  void update_shouldThrowForbidden_whenAdmin() {
    UUID publicId = UUID.randomUUID();

    assertThatThrownBy(() -> patientService.update(publicId, updateRequest(), ADMIN))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
    verify(patientRepository, never()).findByPublicId(any());
  }

  @Test
  void update_shouldThrowNotFound_whenPatientMissing() {
    UUID publicId = UUID.randomUUID();
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> patientService.update(publicId, updateRequest(), STAFF))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_NOT_FOUND));
  }

  // ---- card ----

  @Test
  void getCard_shouldReturnCardWithStatus_whenPatientReadsOwnCard() {
    UUID publicId = UUID.randomUUID();
    Patient patient =
        patientWithCard(7L, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(355)));
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    PatientCardResponse card = patientService.getCard(publicId, PATIENT);

    assertThat(card.cardNumber()).isEqualTo("BN2026-ABCDEFGH");
    assertThat(card.status()).isEqualTo(PatientCardStatus.ACTIVE);
    assertThat(card.expiresAt()).isEqualTo(patient.getCardExpiresAt());
  }

  @Test
  void getCard_shouldAllowAdmin() {
    UUID publicId = UUID.randomUUID();
    Patient patient =
        patientWithCard(7L, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(355)));
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    assertThat(patientService.getCard(publicId, ADMIN).status())
        .isEqualTo(PatientCardStatus.ACTIVE);
  }

  @Test
  void getCard_shouldThrowForbidden_whenPatientReadsSomeoneElsesCard() {
    UUID publicId = UUID.randomUUID();
    Patient patient =
        patientWithCard(7L, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(355)));
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    assertThatThrownBy(() -> patientService.getCard(publicId, OTHER_PATIENT))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"DOCTOR", "COMPANY"})
  void getCard_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    assertThatThrownBy(() -> patientService.getCard(UUID.randomUUID(), new PatientActor(5L, role)))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
  }

  @Test
  void getCard_shouldThrowNotFound_whenPatientHasNoCard() {
    UUID publicId = UUID.randomUUID();
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient(7L)));

    assertThatThrownBy(() -> patientService.getCard(publicId, STAFF))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_CARD_NOT_FOUND));
  }

  @ParameterizedTest
  @CsvSource({
    "200, ACTIVE, true, 200",
    "31, ACTIVE, true, 31",
    "30, EXPIRING_SOON, true, 30",
    "10, EXPIRING_SOON, true, 10",
    "0, EXPIRED, false, 0",
    "-5, EXPIRED, false, 0"
  })
  void checkCardValidity_shouldComputeStatus_fromExpiryDate(
      long daysUntilExpiry, PatientCardStatus status, boolean valid, long daysRemaining) {
    UUID publicId = UUID.randomUUID();
    Patient patient =
        patientWithCard(
            7L, NOW.minus(Duration.ofDays(100)), NOW.plus(Duration.ofDays(daysUntilExpiry)));
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    PatientCardValidityResponse result = patientService.checkCardValidity(publicId, STAFF);

    assertThat(result.status()).isEqualTo(status);
    assertThat(result.valid()).isEqualTo(valid);
    assertThat(result.daysRemaining()).isEqualTo(daysRemaining);
  }

  @Test
  void checkCardValidity_shouldReportRevoked_evenWhenNotExpired() {
    UUID publicId = UUID.randomUUID();
    Patient patient =
        patientWithCard(7L, NOW.minus(Duration.ofDays(10)), NOW.plus(Duration.ofDays(355)));
    patient.setCardRevokedAt(NOW.minus(Duration.ofDays(1)));
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));

    PatientCardValidityResponse result = patientService.checkCardValidity(publicId, PATIENT);

    assertThat(result.status()).isEqualTo(PatientCardStatus.REVOKED);
    assertThat(result.valid()).isFalse();
    assertThat(result.daysRemaining()).isZero();
  }

  // ---- helpers ----

  private void stubCreateHappyPath() {
    when(patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
            "CCCD", "Cục CSQLHC", "079123456789"))
        .thenReturn(false);
    when(patientRepository.existsByCardNumber(any())).thenReturn(false);
    when(patientRepository.saveAndFlush(any(Patient.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  private Patient captureSaved() {
    ArgumentCaptor<Patient> captor = ArgumentCaptor.forClass(Patient.class);
    verify(patientRepository).saveAndFlush(captor.capture());
    return captor.getValue();
  }

  private static DataIntegrityViolationException uniqueViolation(String constraint) {
    var sql = new SQLException("duplicate key", "23505");
    return new DataIntegrityViolationException(
        "duplicate", new ConstraintViolationException("duplicate key", sql, constraint));
  }

  private static PatientCreateRequest createRequest() {
    return new PatientCreateRequest(
        "Nguyễn Văn A",
        LocalDate.of(1990, 5, 20),
        Gender.MALE,
        "0901234567",
        "a@example.com",
        "Quận 3",
        "CCCD",
        "079123456789",
        "Cục CSQLHC");
  }

  private static PatientUpdateRequest updateRequest() {
    return new PatientUpdateRequest(
        "Nguyễn Văn B",
        LocalDate.of(1991, 6, 21),
        Gender.FEMALE,
        "0987654321",
        "b@example.com",
        "Quận 1");
  }

  private static Patient patient(Long userId) {
    Patient patient = new Patient();
    patient.setUserId(userId);
    patient.setFullName("Nguyễn Văn A");
    patient.setDateOfBirth(LocalDate.of(1990, 5, 20));
    patient.setGender(Gender.MALE);
    patient.setPhone("0901234567");
    patient.setAddress("Quận 3");
    patient.setIdentityType("CCCD");
    patient.setIdentityNumber("079123456789");
    patient.setIdentityIssuer("Cục CSQLHC");
    return patient;
  }

  private static Patient patientWithCard(Long userId, Instant activatedAt, Instant expiresAt) {
    Patient patient = patient(userId);
    patient.setCardNumber("BN2026-ABCDEFGH");
    patient.setCardActivatedAt(activatedAt);
    patient.setCardExpiresAt(expiresAt);
    return patient;
  }
}
