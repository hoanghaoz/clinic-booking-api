package com.se100.clinic.patient;

import static com.se100.clinic.patient.PatientAccess.forbidden;
import static com.se100.clinic.patient.PatientAccess.requireOwnerIfPatient;
import static com.se100.clinic.patient.PatientAccess.requireRole;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.doctor.DoctorService;
import com.se100.clinic.employee.StaffService;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryCreateRequest;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryResponse;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistoryUpdateRequest;
import com.se100.clinic.patient.enums.MedicalHistorySource;
import com.se100.clinic.patient.enums.MedicalHistoryType;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.CommonErrorCode;
import com.se100.clinic.shared.error.FieldViolation;
import com.se100.clinic.shared.error.NotFoundException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>PUBLIC</b> — stores and looks up a patient's personal history, family history and drug
 * allergies. The reception (check-in) and examination flows read it through {@link
 * #getSummary(UUID, PatientActor)} / {@link #getSummaryByPatientId(Long, PatientActor)}.
 *
 * <p>Access: PATIENT (own profile only), DOCTOR and MEDICAL_STAFF; every other role gets 403. Never
 * log entry descriptions — they are medical data.
 */
@Service
public class PatientMedicalHistoryService {

  /** Fields the history list may be sorted by (API names = entity property names). */
  public static final Set<String> SORTABLE_FIELDS = Set.of("type", "recordedAt", "updatedAt");

  /** Newest entries first; used when the client sends no {@code sort}. */
  public static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("recordedAt"));

  private static final Set<UserRole> ACCESS_ROLES =
      Set.of(UserRole.PATIENT, UserRole.DOCTOR, UserRole.MEDICAL_STAFF);

  private final PatientMedicalHistoryRepository historyRepository;
  private final PatientRepository patientRepository;
  private final DoctorService doctorService;
  private final StaffService staffService;

  PatientMedicalHistoryService(
      PatientMedicalHistoryRepository historyRepository,
      PatientRepository patientRepository,
      DoctorService doctorService,
      StaffService staffService) {
    this.historyRepository = historyRepository;
    this.patientRepository = patientRepository;
    this.doctorService = doctorService;
    this.staffService = staffService;
  }

  /**
   * Records an entry. A DOCTOR / MEDICAL_STAFF actor is stored as the recorder; a PATIENT's entry
   * has no recorder, which marks it as self-declared.
   */
  @Transactional
  public MedicalHistoryResponse add(
      UUID patientPublicId, MedicalHistoryCreateRequest request, PatientActor actor) {
    requireRole(actor, ACCESS_ROLES);
    Patient patient = loadPatient(patientPublicId, actor);
    String relationship = normalizeRelationship(request.familyRelationship());
    validateRelationship(request.type(), relationship);

    PatientMedicalHistory history = new PatientMedicalHistory();
    history.setPatient(patient);
    history.setType(request.type());
    history.setDescription(request.description().trim());
    history.setFamilyRelationship(relationship);
    switch (actor.role()) {
      case DOCTOR ->
          history.setRecordedByDoctorId(
              doctorService
                  .findWorkingDoctorIdByUserId(actor.userId())
                  .orElseThrow(PatientAccess::forbidden));
      case MEDICAL_STAFF ->
          history.setRecordedByStaffId(
              staffService
                  .findWorkingStaffIdByUserId(actor.userId())
                  .orElseThrow(PatientAccess::forbidden));
      default -> {
        // PATIENT: self-declared, no recorder.
      }
    }
    return MedicalHistoryResponse.from(historyRepository.save(history));
  }

  /**
   * Changes the description and family relationship of an entry. A PATIENT may only edit entries
   * they declared themselves on their own profile; DOCTOR and MEDICAL_STAFF may edit any entry. The
   * type and the recorder never change.
   */
  @Transactional
  public MedicalHistoryResponse update(
      Long historyId, MedicalHistoryUpdateRequest request, PatientActor actor) {
    requireRole(actor, ACCESS_ROLES);
    PatientMedicalHistory history =
        historyRepository
            .findById(historyId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        PatientErrorCode.PATIENT_MEDICAL_HISTORY_NOT_FOUND,
                        "Không tìm thấy mục tiền sử"));
    if (actor.role() == UserRole.PATIENT) {
      requireOwnerIfPatient(actor, history.getPatient());
      if (MedicalHistoryResponse.sourceOf(history) != MedicalHistorySource.PATIENT) {
        throw forbidden();
      }
    }
    String relationship = normalizeRelationship(request.familyRelationship());
    validateRelationship(history.getType(), relationship);

    history.setDescription(request.description().trim());
    history.setFamilyRelationship(relationship);
    // saveAndFlush so the returned updatedAt is the new value, not the stale one.
    return MedicalHistoryResponse.from(historyRepository.saveAndFlush(history));
  }

  /**
   * One page of a patient's history, optionally filtered by type.
   *
   * @param type {@code null} = all types
   * @param pageable already validated by {@code PageParams#toPageable}
   */
  @Transactional(readOnly = true)
  public Page<MedicalHistoryResponse> list(
      UUID patientPublicId, MedicalHistoryType type, PatientActor actor, Pageable pageable) {
    requireRole(actor, ACCESS_ROLES);
    Patient patient = loadPatient(patientPublicId, actor);
    Page<PatientMedicalHistory> page =
        type == null
            ? historyRepository.findByPatientId(patient.getId(), pageable)
            : historyRepository.findByPatientIdAndType(patient.getId(), type, pageable);
    return page.map(MedicalHistoryResponse::from);
  }

  /** The whole history grouped by type, for reception and the examination screen. */
  @Transactional(readOnly = true)
  public MedicalHistorySummaryResponse getSummary(UUID patientPublicId, PatientActor actor) {
    requireRole(actor, ACCESS_ROLES);
    return summaryOf(loadPatient(patientPublicId, actor));
  }

  /**
   * Same as {@link #getSummary(UUID, PatientActor)} for modules that only hold the internal patient
   * id (appointment check-in, examination).
   */
  @Transactional(readOnly = true)
  public MedicalHistorySummaryResponse getSummaryByPatientId(Long patientId, PatientActor actor) {
    requireRole(actor, ACCESS_ROLES);
    Patient patient =
        patientRepository
            .findById(patientId)
            .orElseThrow(PatientMedicalHistoryService::patientNotFound);
    requireOwnerIfPatient(actor, patient);
    return summaryOf(patient);
  }

  private MedicalHistorySummaryResponse summaryOf(Patient patient) {
    List<PatientMedicalHistory> all =
        historyRepository.findByPatientIdOrderByRecordedAtDesc(patient.getId());
    return new MedicalHistorySummaryResponse(
        responsesOf(all, MedicalHistoryType.PERSONAL),
        responsesOf(all, MedicalHistoryType.FAMILY),
        responsesOf(all, MedicalHistoryType.ALLERGY));
  }

  private static List<MedicalHistoryResponse> responsesOf(
      List<PatientMedicalHistory> all, MedicalHistoryType type) {
    return all.stream()
        .filter(history -> history.getType() == type)
        .map(MedicalHistoryResponse::from)
        .toList();
  }

  private Patient loadPatient(UUID publicId, PatientActor actor) {
    Patient patient =
        patientRepository
            .findByPublicId(publicId)
            .orElseThrow(PatientMedicalHistoryService::patientNotFound);
    requireOwnerIfPatient(actor, patient);
    return patient;
  }

  private static NotFoundException patientNotFound() {
    return new NotFoundException(PatientErrorCode.PATIENT_NOT_FOUND, "Không tìm thấy bệnh nhân");
  }

  private static String normalizeRelationship(String relationship) {
    return relationship == null || relationship.isBlank() ? null : relationship.trim();
  }

  /** FAMILY entries need the relationship; the other types must not have one. */
  private static void validateRelationship(MedicalHistoryType type, String relationship) {
    String message = null;
    if (type == MedicalHistoryType.FAMILY && relationship == null) {
      message = "Quan hệ gia đình là bắt buộc với tiền sử gia đình";
    } else if (type != MedicalHistoryType.FAMILY && relationship != null) {
      message = "Chỉ tiền sử gia đình mới có quan hệ gia đình";
    }
    if (message != null) {
      throw new BusinessException(
          CommonErrorCode.VALIDATION_ERROR,
          "Dữ liệu không hợp lệ",
          List.of(new FieldViolation("familyRelationship", message)));
    }
  }
}
