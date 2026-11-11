package com.se100.clinic.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/** Pure unit test: no Spring context, no database. */
@ExtendWith(MockitoExtension.class)
class PatientMedicalHistoryServiceTest {

  private static final PatientActor PATIENT = new PatientActor(7L, UserRole.PATIENT);
  private static final PatientActor OTHER_PATIENT = new PatientActor(8L, UserRole.PATIENT);
  private static final PatientActor DOCTOR = new PatientActor(11L, UserRole.DOCTOR);
  private static final PatientActor STAFF = new PatientActor(21L, UserRole.MEDICAL_STAFF);
  private static final Pageable PAGE =
      PageRequest.of(0, 20, PatientMedicalHistoryService.DEFAULT_SORT);

  @Mock private PatientMedicalHistoryRepository historyRepository;
  @Mock private PatientRepository patientRepository;
  @Mock private DoctorService doctorService;
  @Mock private StaffService staffService;

  private PatientMedicalHistoryService service;
  private UUID publicId;
  private Patient patient;

  @BeforeEach
  void setUp() {
    service =
        new PatientMedicalHistoryService(
            historyRepository, patientRepository, doctorService, staffService);
    publicId = UUID.randomUUID();
    patient = new Patient();
    patient.setUserId(7L);
    ReflectionTestUtils.setField(patient, "id", 100L);
  }

  // ---- add ----

  @Test
  void add_shouldStoreDoctorAsRecorder_whenDoctorAddsAllergy() {
    stubPatient();
    when(doctorService.findWorkingDoctorIdByUserId(11L)).thenReturn(Optional.of(3L));
    stubSave();

    MedicalHistoryResponse response =
        service.add(
            publicId, create(MedicalHistoryType.ALLERGY, "Dị ứng penicillin", null), DOCTOR);

    PatientMedicalHistory saved = captureSaved();
    assertThat(saved.getRecordedByDoctorId()).isEqualTo(3L);
    assertThat(saved.getRecordedByStaffId()).isNull();
    assertThat(saved.getPatient()).isSameAs(patient);
    assertThat(response.source()).isEqualTo(MedicalHistorySource.DOCTOR);
  }

  @Test
  void add_shouldStoreStaffAsRecorder_whenStaffAddsPersonalHistory() {
    stubPatient();
    when(staffService.findWorkingStaffIdByUserId(21L)).thenReturn(Optional.of(4L));
    stubSave();

    MedicalHistoryResponse response =
        service.add(
            publicId, create(MedicalHistoryType.PERSONAL, "Tiểu đường type 2", null), STAFF);

    assertThat(captureSaved().getRecordedByStaffId()).isEqualTo(4L);
    assertThat(response.source()).isEqualTo(MedicalHistorySource.STAFF);
  }

  @Test
  void add_shouldLeaveRecorderEmpty_whenPatientDeclaresOwnHistory() {
    stubPatient();
    stubSave();

    MedicalHistoryResponse response =
        service.add(
            publicId, create(MedicalHistoryType.PERSONAL, "  Cao huyết áp  ", null), PATIENT);

    PatientMedicalHistory saved = captureSaved();
    assertThat(saved.getRecordedByDoctorId()).isNull();
    assertThat(saved.getRecordedByStaffId()).isNull();
    assertThat(saved.getDescription()).isEqualTo("Cao huyết áp");
    assertThat(response.source()).isEqualTo(MedicalHistorySource.PATIENT);
  }

  @Test
  void add_shouldStoreRelationship_whenFamilyHistory() {
    stubPatient();
    stubSave();

    service.add(publicId, create(MedicalHistoryType.FAMILY, "Ung thư gan", " Cha "), PATIENT);

    PatientMedicalHistory saved = captureSaved();
    assertThat(saved.getType()).isEqualTo(MedicalHistoryType.FAMILY);
    assertThat(saved.getFamilyRelationship()).isEqualTo("Cha");
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"ADMIN", "COMPANY"})
  void add_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    var actor = new PatientActor(1L, role);

    assertForbidden(
        () -> service.add(publicId, create(MedicalHistoryType.PERSONAL, "x", null), actor));
    verify(historyRepository, never()).save(any());
  }

  @Test
  void add_shouldThrowForbidden_whenPatientWritesToSomeoneElsesProfile() {
    stubPatient();

    assertForbidden(
        () -> service.add(publicId, create(MedicalHistoryType.PERSONAL, "x", null), OTHER_PATIENT));
    verify(historyRepository, never()).save(any());
  }

  @Test
  void add_shouldThrowForbidden_whenDoctorHasNoWorkingDoctorRecord() {
    stubPatient();
    when(doctorService.findWorkingDoctorIdByUserId(11L)).thenReturn(Optional.empty());

    assertForbidden(
        () -> service.add(publicId, create(MedicalHistoryType.PERSONAL, "x", null), DOCTOR));
    verify(historyRepository, never()).save(any());
  }

  @Test
  void add_shouldThrowForbidden_whenStaffHasNoWorkingStaffRecord() {
    stubPatient();
    when(staffService.findWorkingStaffIdByUserId(21L)).thenReturn(Optional.empty());

    assertForbidden(
        () -> service.add(publicId, create(MedicalHistoryType.PERSONAL, "x", null), STAFF));
  }

  @Test
  void add_shouldThrowNotFound_whenPatientMissing() {
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.add(publicId, create(MedicalHistoryType.PERSONAL, "x", null), DOCTOR))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_NOT_FOUND));
  }

  @Test
  void add_shouldFailValidation_whenFamilyHistoryHasNoRelationship() {
    stubPatient();

    assertRelationshipViolation(
        () -> service.add(publicId, create(MedicalHistoryType.FAMILY, "Tim mạch", "  "), PATIENT));
    verify(historyRepository, never()).save(any());
  }

  @ParameterizedTest
  @EnumSource(
      value = MedicalHistoryType.class,
      names = {"PERSONAL", "ALLERGY"})
  void add_shouldFailValidation_whenNonFamilyHistoryHasRelationship(MedicalHistoryType type) {
    stubPatient();

    assertRelationshipViolation(() -> service.add(publicId, create(type, "Mô tả", "Cha"), PATIENT));
  }

  // ---- update ----

  @Test
  void update_shouldChangeDescription_whenPatientEditsOwnSelfDeclaredEntry() {
    PatientMedicalHistory entry = entry(1L, MedicalHistoryType.PERSONAL, null, null);
    when(historyRepository.findById(1L)).thenReturn(Optional.of(entry));
    when(historyRepository.saveAndFlush(entry)).thenReturn(entry);

    MedicalHistoryResponse response =
        service.update(1L, new MedicalHistoryUpdateRequest(" Cao huyết áp độ 2 ", null), PATIENT);

    assertThat(entry.getDescription()).isEqualTo("Cao huyết áp độ 2");
    assertThat(response.description()).isEqualTo("Cao huyết áp độ 2");
    assertThat(entry.getType()).isEqualTo(MedicalHistoryType.PERSONAL);
  }

  @Test
  void update_shouldThrowForbidden_whenPatientEditsEntryRecordedByDoctor() {
    PatientMedicalHistory entry = entry(1L, MedicalHistoryType.PERSONAL, 3L, null);
    when(historyRepository.findById(1L)).thenReturn(Optional.of(entry));

    assertForbidden(
        () -> service.update(1L, new MedicalHistoryUpdateRequest("Sửa", null), PATIENT));
    verify(historyRepository, never()).saveAndFlush(any());
  }

  @Test
  void update_shouldThrowForbidden_whenPatientEditsAnotherPatientsEntry() {
    PatientMedicalHistory entry = entry(1L, MedicalHistoryType.PERSONAL, null, null);
    when(historyRepository.findById(1L)).thenReturn(Optional.of(entry));

    assertForbidden(
        () -> service.update(1L, new MedicalHistoryUpdateRequest("Sửa", null), OTHER_PATIENT));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"DOCTOR", "MEDICAL_STAFF"})
  void update_shouldAllowClinicalStaffToEditAnyEntry(UserRole role) {
    PatientMedicalHistory entry = entry(1L, MedicalHistoryType.ALLERGY, 3L, null);
    when(historyRepository.findById(1L)).thenReturn(Optional.of(entry));
    when(historyRepository.saveAndFlush(entry)).thenReturn(entry);

    service.update(
        1L, new MedicalHistoryUpdateRequest("Dị ứng aspirin", null), new PatientActor(5L, role));

    assertThat(entry.getDescription()).isEqualTo("Dị ứng aspirin");
    // The original recorder is kept.
    assertThat(entry.getRecordedByDoctorId()).isEqualTo(3L);
  }

  @Test
  void update_shouldFailValidation_whenFamilyEntryLosesRelationship() {
    PatientMedicalHistory entry = entry(1L, MedicalHistoryType.FAMILY, null, 4L);
    entry.setFamilyRelationship("Mẹ");
    when(historyRepository.findById(1L)).thenReturn(Optional.of(entry));

    assertRelationshipViolation(
        () -> service.update(1L, new MedicalHistoryUpdateRequest("Tiểu đường", null), STAFF));
    assertThat(entry.getDescription()).isEqualTo("mô tả cũ");
  }

  @Test
  void update_shouldThrowNotFound_whenEntryMissing() {
    when(historyRepository.findById(9L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.update(9L, new MedicalHistoryUpdateRequest("x", null), DOCTOR))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e ->
                assertThat(e.getErrorCode())
                    .isEqualTo(PatientErrorCode.PATIENT_MEDICAL_HISTORY_NOT_FOUND));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"ADMIN", "COMPANY"})
  void update_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    assertForbidden(
        () ->
            service.update(
                1L, new MedicalHistoryUpdateRequest("x", null), new PatientActor(1L, role)));
    verify(historyRepository, never()).findById(any());
  }

  // ---- list / summary ----

  @Test
  void list_shouldQueryAllTypesWithPageable_whenNoTypeFilter() {
    stubPatient();
    when(historyRepository.findByPatientId(100L, PAGE))
        .thenReturn(
            new PageImpl<>(List.of(entry(1L, MedicalHistoryType.PERSONAL, null, null)), PAGE, 1));

    Page<MedicalHistoryResponse> result = service.list(publicId, null, DOCTOR, PAGE);

    assertThat(result.getContent()).hasSize(1);
    verify(historyRepository, never()).findByPatientIdAndType(any(), any(), any());
  }

  @Test
  void list_shouldFilterByType_whenTypeGiven() {
    stubPatient();
    when(historyRepository.findByPatientIdAndType(100L, MedicalHistoryType.ALLERGY, PAGE))
        .thenReturn(
            new PageImpl<>(List.of(entry(2L, MedicalHistoryType.ALLERGY, 3L, null)), PAGE, 1));

    Page<MedicalHistoryResponse> result =
        service.list(publicId, MedicalHistoryType.ALLERGY, STAFF, PAGE);

    assertThat(result.getContent())
        .extracting(MedicalHistoryResponse::type)
        .containsExactly(MedicalHistoryType.ALLERGY);
    verify(historyRepository, never()).findByPatientId(any(), any());
  }

  @Test
  void list_shouldThrowForbidden_whenPatientReadsSomeoneElsesHistory() {
    stubPatient();

    assertForbidden(() -> service.list(publicId, null, OTHER_PATIENT, PAGE));
    verify(historyRepository, never()).findByPatientId(any(), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"ADMIN", "COMPANY"})
  void list_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    assertForbidden(() -> service.list(publicId, null, new PatientActor(1L, role), PAGE));
  }

  @Test
  void getSummary_shouldGroupByType() {
    stubPatient();
    stubAllEntries();

    MedicalHistorySummaryResponse summary = service.getSummary(publicId, DOCTOR);

    assertThat(summary.personal()).extracting(MedicalHistoryResponse::id).containsExactly(1L);
    assertThat(summary.family()).extracting(MedicalHistoryResponse::id).containsExactly(2L);
    assertThat(summary.allergies()).extracting(MedicalHistoryResponse::id).containsExactly(3L);
  }

  @Test
  void getSummaryByPatientId_shouldGroupByType_forReceptionStaff() {
    when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));
    stubAllEntries();

    MedicalHistorySummaryResponse summary = service.getSummaryByPatientId(100L, STAFF);

    assertThat(summary.personal()).hasSize(1);
    assertThat(summary.family()).hasSize(1);
    assertThat(summary.allergies()).hasSize(1);
  }

  @Test
  void getSummaryByPatientId_shouldThrowForbidden_whenPatientAsksForSomeoneElse() {
    when(patientRepository.findById(100L)).thenReturn(Optional.of(patient));

    assertForbidden(() -> service.getSummaryByPatientId(100L, OTHER_PATIENT));
  }

  @Test
  void getSummaryByPatientId_shouldThrowNotFound_whenPatientMissing() {
    when(patientRepository.findById(404L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getSummaryByPatientId(404L, DOCTOR))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(PatientErrorCode.PATIENT_NOT_FOUND));
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"ADMIN", "COMPANY"})
  void getSummaryByPatientId_shouldThrowForbidden_whenRoleNotAllowed(UserRole role) {
    assertForbidden(() -> service.getSummaryByPatientId(100L, new PatientActor(1L, role)));
    verify(patientRepository, never()).findById(any());
  }

  // ---- helpers ----

  private void stubPatient() {
    when(patientRepository.findByPublicId(publicId)).thenReturn(Optional.of(patient));
  }

  private void stubSave() {
    when(historyRepository.save(any(PatientMedicalHistory.class)))
        .thenAnswer(
            invocation -> {
              PatientMedicalHistory history = invocation.getArgument(0);
              history.setRecordedAt(Instant.parse("2026-11-10T03:00:00Z"));
              history.setUpdatedAt(Instant.parse("2026-11-10T03:00:00Z"));
              return history;
            });
  }

  private void stubAllEntries() {
    when(historyRepository.findByPatientIdOrderByRecordedAtDesc(100L))
        .thenReturn(
            List.of(
                entry(1L, MedicalHistoryType.PERSONAL, null, null),
                entry(2L, MedicalHistoryType.FAMILY, null, 4L),
                entry(3L, MedicalHistoryType.ALLERGY, 3L, null)));
  }

  private PatientMedicalHistory captureSaved() {
    ArgumentCaptor<PatientMedicalHistory> captor =
        ArgumentCaptor.forClass(PatientMedicalHistory.class);
    verify(historyRepository).save(captor.capture());
    return captor.getValue();
  }

  private PatientMedicalHistory entry(
      long id, MedicalHistoryType type, Long doctorId, Long staffId) {
    PatientMedicalHistory history = new PatientMedicalHistory();
    history.setId(id);
    history.setPatient(patient);
    history.setType(type);
    history.setDescription("mô tả cũ");
    history.setRecordedByDoctorId(doctorId);
    history.setRecordedByStaffId(staffId);
    history.setRecordedAt(Instant.parse("2026-11-01T03:00:00Z"));
    history.setUpdatedAt(Instant.parse("2026-11-01T03:00:00Z"));
    return history;
  }

  private static MedicalHistoryCreateRequest create(
      MedicalHistoryType type, String description, String relationship) {
    return new MedicalHistoryCreateRequest(type, description, relationship);
  }

  private static void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
  }

  private static void assertRelationshipViolation(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> {
              assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR);
              assertThat(e.getViolations())
                  .extracting(violation -> violation.field())
                  .containsExactly("familyRelationship");
            });
  }
}
