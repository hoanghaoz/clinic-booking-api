package com.se100.clinic.patient;

import static com.se100.clinic.patient.PatientAccess.requireOwnerIfPatient;
import static com.se100.clinic.patient.PatientAccess.requireRole;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.patient.PatientDtos.PatientCardResponse;
import com.se100.clinic.patient.PatientDtos.PatientCardValidityResponse;
import com.se100.clinic.patient.PatientDtos.PatientClassUpdateRequest;
import com.se100.clinic.patient.PatientDtos.PatientContact;
import com.se100.clinic.patient.PatientDtos.PatientCreateRequest;
import com.se100.clinic.patient.PatientDtos.PatientResponse;
import com.se100.clinic.patient.PatientDtos.PatientUpdateRequest;
import com.se100.clinic.patient.enums.IdentityType;
import com.se100.clinic.patient.enums.PatientCardStatus;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.ConflictException;
import com.se100.clinic.shared.error.DbErrors;
import com.se100.clinic.shared.error.FieldViolation;
import com.se100.clinic.shared.error.NotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>PUBLIC</b> — the only entry point other modules (and the future controller) use for patient
 * profiles and cards.
 *
 * <p>Every method takes a {@link PatientActor} and enforces access itself: a PATIENT may only touch
 * their own profile and card, MEDICAL_STAFF may touch any patient, ADMIN may only read cards, and
 * lookups by phone / identity number are open to every role except COMPANY.
 */
@Service
public class PatientService {

  /**
   * Fields the patient lookups may be sorted by. They are API field names AND entity property names
   * (keep them identical).
   */
  public static final Set<String> SORTABLE_FIELDS = Set.of("fullName", "dateOfBirth", "createdAt");

  /** Used when the client sends no {@code sort}. */
  public static final Sort DEFAULT_SORT = Sort.by("fullName");

  /** Card validity from activation, per docs/de-tai.md section C. */
  static final int CARD_VALID_YEARS = 1;

  /** A valid card expiring within this many days is reported as EXPIRING_SOON. */
  static final long EXPIRING_SOON_DAYS = 30;

  /** Oldest accepted birth date; anything earlier is a typo. */
  static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

  private static final long SECONDS_PER_DAY = 86_400;
  private static final String CARD_PREFIX = "BN";
  private static final String CARD_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int CARD_SUFFIX_LENGTH = 8;
  private static final int CARD_NUMBER_ATTEMPTS = 5;

  // Constraint names, see V20261016_1004__patient_create_patient_tables.sql.
  private static final String IDENTITY_CONSTRAINT = "uk_patients_identity";
  private static final String USER_ID_CONSTRAINT = "patients_user_id_key";
  private static final String CARD_NUMBER_CONSTRAINT = "patients_card_number_key";

  private static final Set<UserRole> WRITE_ROLES = Set.of(UserRole.PATIENT, UserRole.MEDICAL_STAFF);
  private static final Set<UserRole> CARD_READ_ROLES =
      Set.of(UserRole.PATIENT, UserRole.MEDICAL_STAFF, UserRole.ADMIN);
  private static final Set<UserRole> PATIENT_ONLY = Set.of(UserRole.PATIENT);
  private static final Set<UserRole> ADMIN_ONLY = Set.of(UserRole.ADMIN);
  // Everyone except COMPANY may look patients up (and open a single profile).
  private static final Set<UserRole> SEARCH_ROLES =
      Set.of(UserRole.PATIENT, UserRole.DOCTOR, UserRole.MEDICAL_STAFF, UserRole.ADMIN);

  private static final SecureRandom RANDOM = new SecureRandom();

  private final PatientRepository patientRepository;
  private final Clock clock;

  PatientService(PatientRepository patientRepository, Clock clock) {
    this.patientRepository = patientRepository;
    this.clock = clock;
  }

  /**
   * Creates a patient profile and issues its card (activated now, valid for one year). A PATIENT
   * actor creates their own profile, linked to their account; MEDICAL_STAFF create a walk-in
   * profile with no account. The class is always PERSONAL here.
   */
  @Transactional
  public PatientResponse create(PatientCreateRequest request, PatientActor actor) {
    requireRole(actor, WRITE_ROLES);
    requireValidBirthDate(request.dateOfBirth());
    // Normalize first so the duplicate check and the stored values agree (search trims too).
    IdentityType identityType = request.identityType();
    String identityNumber = normalizeIdentityNumber(request.identityNumber());
    requireValidIdentityNumber(identityType, identityNumber);
    String identityIssuer = clean(request.identityIssuer());
    Long ownerUserId = null;
    if (actor.role() == UserRole.PATIENT) {
      if (patientRepository.existsByUserId(actor.userId())) {
        throw profileExists();
      }
      ownerUserId = actor.userId();
    }
    if (patientRepository.existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
        identityType, identityIssuer, identityNumber)) {
      throw identityExists();
    }

    Patient patient = new Patient();
    patient.setUserId(ownerUserId);
    patient.setFullName(clean(request.fullName()));
    patient.setDateOfBirth(request.dateOfBirth());
    patient.setGender(request.gender());
    patient.setPhone(normalizePhone(request.phone()));
    patient.setContactEmail(cleanOrNull(request.contactEmail()));
    patient.setAddress(clean(request.address()));
    patient.setIdentityType(identityType);
    patient.setIdentityNumber(identityNumber);
    patient.setIdentityIssuer(identityIssuer);
    issueCard(patient);
    try {
      // saveAndFlush: surface a unique-constraint violation inside this try block, not at commit.
      return PatientResponse.from(patientRepository.saveAndFlush(patient));
    } catch (DataIntegrityViolationException e) {
      throw translateUniqueViolation(e);
    }
  }

  /** One profile by public id. A PATIENT only sees their own; COMPANY gets 403. */
  @Transactional(readOnly = true)
  public PatientResponse getProfile(UUID publicId, PatientActor actor) {
    return PatientResponse.from(loadForActor(publicId, actor, SEARCH_ROLES));
  }

  /** The profile linked to the calling PATIENT account. */
  @Transactional(readOnly = true)
  public PatientResponse getMyProfile(PatientActor actor) {
    requireRole(actor, PATIENT_ONLY);
    return patientRepository
        .findByUserId(actor.userId())
        .map(PatientResponse::from)
        .orElseThrow(
            () ->
                new NotFoundException(
                    PatientErrorCode.PATIENT_NOT_FOUND, "Tài khoản chưa có hồ sơ bệnh nhân"));
  }

  /**
   * ADMIN-only: moves a patient between PERSONAL and VIP. COMPANY is owned by the company module,
   * so it can neither be assigned here nor be changed away from manually.
   */
  @Transactional
  public PatientResponse changeClass(
      UUID publicId, PatientClassUpdateRequest request, PatientActor actor) {
    requireRole(actor, ADMIN_ONLY);
    if (request.patientClass() == PatientClass.COMPANY) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Dữ liệu không hợp lệ",
          List.of(
              new FieldViolation(
                  "patientClass",
                  "Hạng COMPANY do hợp đồng doanh nghiệp quản lý, không gán thủ công")));
    }
    Patient patient =
        patientRepository
            .findByPublicId(publicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        PatientErrorCode.PATIENT_NOT_FOUND, "Không tìm thấy bệnh nhân"));
    if (patient.getPatientClass() == PatientClass.COMPANY) {
      throw new ConflictException(
          PatientErrorCode.PATIENT_CLASS_LOCKED,
          "Bệnh nhân thuộc hợp đồng doanh nghiệp, không đổi hạng thủ công");
    }
    patient.setPatientClass(request.patientClass());
    // saveAndFlush so the returned updatedAt is the new value, not the stale one.
    return PatientResponse.from(patientRepository.saveAndFlush(patient));
  }

  /**
   * Updates the editable profile fields. Identity fields and the class are never changed here.
   * Entity is managed, so dirty checking writes the UPDATE at commit.
   */
  @Transactional
  public PatientResponse update(UUID publicId, PatientUpdateRequest request, PatientActor actor) {
    Patient patient = loadForActor(publicId, actor, WRITE_ROLES);
    requireValidBirthDate(request.dateOfBirth());
    patient.setFullName(clean(request.fullName()));
    patient.setDateOfBirth(request.dateOfBirth());
    patient.setGender(request.gender());
    patient.setPhone(normalizePhone(request.phone()));
    patient.setContactEmail(cleanOrNull(request.contactEmail()));
    patient.setAddress(clean(request.address()));
    return PatientResponse.from(patient);
  }

  /**
   * Exact-match lookup by phone number (separators in the input are ignored), one page at a time. A
   * PATIENT only ever sees their own profile, so others' records are neither returned nor revealed;
   * every other allowed role gets all profiles sharing the number, since a phone is not unique.
   *
   * @param pageable already validated by {@code PageParams#toPageable}
   */
  @Transactional(readOnly = true)
  public Page<PatientResponse> searchByPhone(String phone, PatientActor actor, Pageable pageable) {
    requireRole(actor, SEARCH_ROLES);
    String normalized = normalizePhone(requireNotBlank(phone, "Số điện thoại không được để trống"));
    Page<Patient> matches =
        actor.role() == UserRole.PATIENT
            ? patientRepository.findByPhoneAndUserId(normalized, actor.userId(), pageable)
            : patientRepository.findByPhone(normalized, pageable);
    return matches.map(PatientResponse::from);
  }

  /**
   * Exact-match lookup by identity document number (CCCD / passport), same visibility rules and
   * paging as {@link #searchByPhone}. The number alone is not unique (the key also has type and
   * issuer).
   */
  @Transactional(readOnly = true)
  public Page<PatientResponse> searchByIdentityNumber(
      String identityNumber, PatientActor actor, Pageable pageable) {
    requireRole(actor, SEARCH_ROLES);
    String normalized =
        normalizeIdentityNumber(requireNotBlank(identityNumber, "Số giấy tờ không được để trống"));
    Page<Patient> matches =
        actor.role() == UserRole.PATIENT
            ? patientRepository.findByIdentityNumberAndUserId(normalized, actor.userId(), pageable)
            : patientRepository.findByIdentityNumber(normalized, pageable);
    return matches.map(PatientResponse::from);
  }

  /**
   * Contact data of one patient for system-to-system use (for example the notification module).
   * There is no actor check, so never call it on behalf of an API request.
   */
  @Transactional(readOnly = true)
  public Optional<PatientContact> findContact(Long patientId) {
    return patientRepository
        .findById(patientId)
        .map(
            patient ->
                new PatientContact(
                    patient.getId(),
                    patient.getUserId(),
                    patient.getContactEmail(),
                    patient.getFullName()));
  }

  @Transactional(readOnly = true)
  public PatientCardResponse getCard(UUID publicId, PatientActor actor) {
    Patient patient = loadForActor(publicId, actor, CARD_READ_ROLES);
    requireCard(patient);
    return new PatientCardResponse(
        patient.getCardNumber(),
        patient.getCardActivatedAt(),
        patient.getCardExpiresAt(),
        patient.getCardRevokedAt(),
        cardStatus(patient, clock.instant()));
  }

  @Transactional(readOnly = true)
  public PatientCardValidityResponse checkCardValidity(UUID publicId, PatientActor actor) {
    Patient patient = loadForActor(publicId, actor, CARD_READ_ROLES);
    requireCard(patient);
    Instant now = clock.instant();
    PatientCardStatus status = cardStatus(patient, now);
    boolean valid = status == PatientCardStatus.ACTIVE || status == PatientCardStatus.EXPIRING_SOON;
    long daysRemaining = valid ? daysLeft(now, patient.getCardExpiresAt()) : 0;
    return new PatientCardValidityResponse(
        status, valid, daysRemaining, patient.getCardExpiresAt());
  }

  /** Whole days left, rounded up: a card activated just now has 365 days, not 364. */
  private static long daysLeft(Instant now, Instant expiresAt) {
    long seconds = Duration.between(now, expiresAt).getSeconds();
    return (seconds + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY;
  }

  /** Revoked wins over expired; "expiring soon" is a valid card inside the reminder window. */
  private static PatientCardStatus cardStatus(Patient patient, Instant now) {
    if (patient.getCardRevokedAt() != null) {
      return PatientCardStatus.REVOKED;
    }
    Instant expiresAt = patient.getCardExpiresAt();
    if (!now.isBefore(expiresAt)) {
      return PatientCardStatus.EXPIRED;
    }
    if (Duration.between(now, expiresAt).compareTo(Duration.ofDays(EXPIRING_SOON_DAYS)) <= 0) {
      return PatientCardStatus.EXPIRING_SOON;
    }
    return PatientCardStatus.ACTIVE;
  }

  private void issueCard(Patient patient) {
    ZonedDateTime now = ZonedDateTime.now(clock);
    patient.setCardNumber(generateCardNumber(now.getYear()));
    patient.setCardActivatedAt(now.toInstant());
    // Add the year in the application zone, so the expiry lands on the same local date next year.
    patient.setCardExpiresAt(now.plusYears(CARD_VALID_YEARS).toInstant());
  }

  /** {@code BN<year>-<8 random chars>}; random so numbers cannot be guessed or enumerated. */
  private String generateCardNumber(int year) {
    for (int attempt = 0; attempt < CARD_NUMBER_ATTEMPTS; attempt++) {
      StringBuilder suffix = new StringBuilder(CARD_SUFFIX_LENGTH);
      for (int i = 0; i < CARD_SUFFIX_LENGTH; i++) {
        suffix.append(CARD_ALPHABET.charAt(RANDOM.nextInt(CARD_ALPHABET.length())));
      }
      String candidate = CARD_PREFIX + year + "-" + suffix;
      if (!patientRepository.existsByCardNumber(candidate)) {
        return candidate;
      }
    }
    throw new ConflictException("Không thể sinh mã thẻ, vui lòng thử lại");
  }

  /** Loads the patient and enforces the role and (for PATIENT) ownership rules. */
  private Patient loadForActor(UUID publicId, PatientActor actor, Set<UserRole> allowedRoles) {
    requireRole(actor, allowedRoles);
    Patient patient =
        patientRepository
            .findByPublicId(publicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        PatientErrorCode.PATIENT_NOT_FOUND, "Không tìm thấy bệnh nhân"));
    requireOwnerIfPatient(actor, patient);
    return patient;
  }

  /**
   * The birth date must not be in the future (today in the application time zone is fine, e.g. a
   * newborn) and not before {@link #EARLIEST_BIRTH_DATE}. Done here, not with {@code @Past},
   * because Bean Validation would compare against the JVM time zone instead of {@code
   * app.timezone}.
   */
  private void requireValidBirthDate(LocalDate dateOfBirth) {
    if (dateOfBirth.isAfter(LocalDate.now(clock)) || dateOfBirth.isBefore(EARLIEST_BIRTH_DATE)) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Dữ liệu không hợp lệ",
          List.of(
              new FieldViolation(
                  "dateOfBirth",
                  "Ngày sinh phải từ 01/01/1900 đến hôm nay, không được ở tương lai")));
    }
  }

  /**
   * Identity numbers are stored without whitespace and in upper case, so a passport typed as "b12
   * 34567" matches "B1234567". Applied on create and on search.
   */
  private static String normalizeIdentityNumber(String raw) {
    return raw.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
  }

  /** Checks the number's shape for its document type (already normalized). */
  private static void requireValidIdentityNumber(IdentityType type, String number) {
    String problem =
        switch (type) {
          case CCCD -> number.matches("[0-9]{12}") ? null : "Số CCCD phải gồm đúng 12 chữ số";
          // Old cards have 9 digits; some provinces issued 12-digit CMND numbers.
          case CMND ->
              number.matches("[0-9]{9}|[0-9]{12}") ? null : "Số CMND phải gồm 9 hoặc 12 chữ số";
          case PASSPORT ->
              number.matches("[A-Z0-9]{6,9}")
                  ? null
                  : "Số hộ chiếu phải gồm 6 đến 9 ký tự, chỉ chữ cái (A-Z) và chữ số";
        };
    if (problem != null) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Dữ liệu không hợp lệ",
          List.of(new FieldViolation("identityNumber", problem)));
    }
  }

  private static String clean(String value) {
    return value.trim();
  }

  /** Blank optional text is stored as {@code null}, never as an empty string. */
  private static String cleanOrNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String requireNotBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(CommonErrorCode.INVALID_REQUEST, message);
    }
    return value.trim();
  }

  /** Stored and searched phones drop spaces, dots and dashes so exact matching is reliable. */
  private static String normalizePhone(String phone) {
    return phone.replaceAll("[\\s.\\-]", "");
  }

  private static void requireCard(Patient patient) {
    if (patient.getCardNumber() == null
        || patient.getCardActivatedAt() == null
        || patient.getCardExpiresAt() == null) {
      throw new NotFoundException(PatientErrorCode.PATIENT_CARD_NOT_FOUND, "Bệnh nhân chưa có thẻ");
    }
  }

  /** Maps a unique violation to the matching module code; anything else is rethrown as is. */
  private static RuntimeException translateUniqueViolation(DataIntegrityViolationException e) {
    if (DbErrors.isUniqueViolation(e)) {
      String constraint = DbErrors.constraintName(e);
      if (IDENTITY_CONSTRAINT.equals(constraint)) {
        return identityExists();
      }
      if (USER_ID_CONSTRAINT.equals(constraint)) {
        return profileExists();
      }
      if (CARD_NUMBER_CONSTRAINT.equals(constraint)) {
        return new ConflictException("Mã thẻ bị trùng, vui lòng thử lại");
      }
    }
    return e;
  }

  private static ConflictException profileExists() {
    return new ConflictException(
        PatientErrorCode.PATIENT_PROFILE_EXISTS, "Tài khoản này đã có hồ sơ bệnh nhân");
  }

  private static ConflictException identityExists() {
    return new ConflictException(
        PatientErrorCode.PATIENT_IDENTITY_EXISTS, "Giấy tờ định danh này đã được đăng ký");
  }
}
