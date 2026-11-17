package com.se100.clinic.examination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.examination.ExaminationDtos.AppointmentSummary;
import com.se100.clinic.examination.ExaminationDtos.PatientSummary;
import com.se100.clinic.examination.ExaminationDtos.ScheduleSummary;
import com.se100.clinic.examination.ExaminationDtos.UpdateExaminationRequest;
import com.se100.clinic.examination.ExaminationReadRepository.VisitContext;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.patient.MedicalHistoryDtos.MedicalHistorySummaryResponse;
import com.se100.clinic.patient.PatientActor;
import com.se100.clinic.patient.PatientMedicalHistoryService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ExaminationServiceTest {

  private static final long DOCTOR = 7L;
  private static final long OTHER_DOCTOR = 8L;
  private static final Instant NOW = Instant.parse("2026-11-16T03:00:00Z");

  @Mock private MedicalVisitRepository visits;
  @Mock private ExaminationReadRepository reads;
  @Mock private PatientMedicalHistoryService histories;

  private ExaminationService service;

  @BeforeEach
  void setUp() {
    service =
        new ExaminationService(
            visits, reads, histories, Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
  }

  private MedicalVisit visit(MedicalVisitStatus status, Long primaryDoctorId) {
    MedicalVisit visit = new MedicalVisit();
    visit.setId(1L);
    visit.setCode("PTN-001");
    visit.setPatientId(20L);
    visit.setAppointmentId(30L);
    visit.setPrimaryDoctorId(primaryDoctorId);
    visit.setStatus(status);
    visit.setReceivedAt(NOW);
    visit.setVersion(3L);
    when(visits.findByPublicId(visit.getPublicId())).thenReturn(Optional.of(visit));
    return visit;
  }

  private void stubContext() {
    when(reads.context(1L))
        .thenReturn(
            Optional.of(
                new VisitContext(
                    DOCTOR,
                    new PatientSummary(UUID.randomUUID(), "Nguyễn Văn B", null, "MALE", "0900"),
                    new AppointmentSummary(UUID.randomUUID(), "AP-1", NOW, null, null),
                    new ScheduleSummary(
                        UUID.randomUUID(), LocalDate.of(2026, 11, 16), "Ca sáng", "P1"))));
    when(reads.orders(1L)).thenReturn(List.of());
  }

  private static UpdateExaminationRequest request(
      Long version, Integer systolic, Integer diastolic) {
    return new UpdateExaminationRequest(
        version,
        "  Ho, sốt  ",
        "   ",
        "Viêm họng",
        "Uống nhiều nước",
        new BigDecimal("170"),
        new BigDecimal("65"),
        new BigDecimal("38.5"),
        90,
        systolic,
        diastolic);
  }

  private static void assertFailsWith(ErrorCode expected, ThrowingCallable call) {
    assertThatThrownBy(call)
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(expected);
  }

  // ---- queue --------------------------------------------------------------------------------

  @Test
  void myQueue_shouldDefaultToToday_whenNoScheduleAndNoDate() {
    service.myQueue(DOCTOR, null, null, null);

    verify(reads).queue(DOCTOR, null, LocalDate.of(2026, 11, 16), null);
  }

  @Test
  void myQueue_shouldNotForceToday_whenScheduleIsGiven() {
    UUID schedule = UUID.randomUUID();

    service.myQueue(DOCTOR, schedule, null, MedicalVisitStatus.WAITING);

    verify(reads).queue(DOCTOR, schedule, null, MedicalVisitStatus.WAITING);
  }

  @Test
  void myQueue_shouldReturnEmptyList_whenNobodyIsReceived() {
    when(reads.queue(eq(DOCTOR), any(), any(), any())).thenReturn(List.of());

    assertThat(service.myQueue(DOCTOR, null, null, null)).isEmpty();
  }

  // ---- ownership ----------------------------------------------------------------------------

  @Test
  void get_shouldReturnDetail_whenDoctorIsPrimary() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, DOCTOR);
    stubContext();

    var detail = service.get(visit.getPublicId(), DOCTOR);

    assertThat(detail.code()).isEqualTo("PTN-001");
    assertThat(detail.version()).isEqualTo(3L);
  }

  @Test
  void get_shouldRejectVisitOfAnotherDoctor() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, OTHER_DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_NOT_ASSIGNED, () -> service.get(visit.getPublicId(), DOCTOR));
  }

  @Test
  void get_shouldFallBackToScheduleDoctor_whenNoPrimaryDoctorYet() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, null);
    when(reads.scheduleDoctorId(30L)).thenReturn(Optional.of(DOCTOR));
    stubContext();

    assertThat(service.get(visit.getPublicId(), DOCTOR).publicId()).isEqualTo(visit.getPublicId());
  }

  @Test
  void get_shouldRejectDoctorOfAnotherShift_whenNoPrimaryDoctorYet() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, null);
    when(reads.scheduleDoctorId(30L)).thenReturn(Optional.of(OTHER_DOCTOR));

    assertFailsWith(
        ExaminationErrorCode.VISIT_NOT_ASSIGNED, () -> service.get(visit.getPublicId(), DOCTOR));
  }

  @Test
  void get_shouldFailNotFound_whenVisitDoesNotExist() {
    UUID unknown = UUID.randomUUID();
    when(visits.findByPublicId(unknown)).thenReturn(Optional.empty());

    assertFailsWith(ExaminationErrorCode.VISIT_NOT_FOUND, () -> service.get(unknown, DOCTOR));
  }

  // ---- start --------------------------------------------------------------------------------

  @Test
  void start_shouldMoveWaitingToInProgressAndSetPrimaryDoctor() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, null);
    when(reads.scheduleDoctorId(30L)).thenReturn(Optional.of(DOCTOR));
    when(visits.saveAndFlush(visit)).thenReturn(visit);
    stubContext();

    service.start(visit.getPublicId(), DOCTOR);

    assertThat(visit.getStatus()).isEqualTo(MedicalVisitStatus.IN_PROGRESS);
    assertThat(visit.getPrimaryDoctorId()).isEqualTo(DOCTOR);
  }

  @Test
  void start_shouldKeepStatus_whenAlreadyAwaitingResults() {
    MedicalVisit visit = visit(MedicalVisitStatus.AWAITING_RESULTS, DOCTOR);
    when(visits.saveAndFlush(visit)).thenReturn(visit);
    stubContext();

    service.start(visit.getPublicId(), DOCTOR);

    assertThat(visit.getStatus()).isEqualTo(MedicalVisitStatus.AWAITING_RESULTS);
  }

  @Test
  void start_shouldRejectClosedVisit() {
    MedicalVisit visit = visit(MedicalVisitStatus.COMPLETED, DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_CLOSED, () -> service.start(visit.getPublicId(), DOCTOR));
    verify(visits, never()).saveAndFlush(any());
  }

  // ---- update -------------------------------------------------------------------------------

  @Test
  void update_shouldSaveClinicalRecordAndStartVisit() {
    MedicalVisit visit = visit(MedicalVisitStatus.WAITING, DOCTOR);
    when(visits.saveAndFlush(visit)).thenReturn(visit);
    stubContext();

    service.update(visit.getPublicId(), DOCTOR, request(3L, 120, 80));

    assertThat(visit.getStatus()).isEqualTo(MedicalVisitStatus.IN_PROGRESS);
    assertThat(visit.getSymptoms()).isEqualTo("Ho, sốt");
    assertThat(visit.getClinicalNotes()).as("blank text is stored as null").isNull();
    assertThat(visit.getInitialDiagnosis()).isEqualTo("Viêm họng");
    assertThat(visit.getAdvice()).isEqualTo("Uống nhiều nước");
    assertThat(visit.getTemperatureC()).isEqualByComparingTo("38.5");
    assertThat(visit.getSystolicBp()).isEqualTo(120);
  }

  @Test
  void update_shouldRejectDoctorWhoIsNotTheTreatingDoctor() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, OTHER_DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_NOT_ASSIGNED,
        () -> service.update(visit.getPublicId(), DOCTOR, request(3L, 120, 80)));
    verify(visits, never()).saveAndFlush(any());
  }

  @Test
  void update_shouldRejectClosedVisit() {
    MedicalVisit visit = visit(MedicalVisitStatus.COMPLETED, DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_CLOSED,
        () -> service.update(visit.getPublicId(), DOCTOR, request(3L, 120, 80)));
  }

  @Test
  void update_shouldRejectCancelledVisit() {
    MedicalVisit visit = visit(MedicalVisitStatus.CANCELLED, DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_CLOSED,
        () -> service.update(visit.getPublicId(), DOCTOR, request(3L, 120, 80)));
  }

  @Test
  void update_shouldRejectStaleVersion() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_VERSION_CONFLICT,
        () -> service.update(visit.getPublicId(), DOCTOR, request(2L, 120, 80)));
    verify(visits, never()).saveAndFlush(any());
  }

  @Test
  void update_shouldReportConflict_whenAnotherTransactionSavedFirst() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, DOCTOR);
    when(visits.saveAndFlush(visit))
        .thenThrow(new ObjectOptimisticLockingFailureException(MedicalVisit.class, 1L));

    assertFailsWith(
        ExaminationErrorCode.VISIT_VERSION_CONFLICT,
        () -> service.update(visit.getPublicId(), DOCTOR, request(3L, 120, 80)));
  }

  @Test
  void update_shouldRejectDiastolicNotBelowSystolic() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.INVALID_VITAL_SIGNS,
        () -> service.update(visit.getPublicId(), DOCTOR, request(3L, 80, 80)));
  }

  @Test
  void update_shouldAcceptBloodPressureWithOnlyOneValue() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, DOCTOR);
    when(visits.saveAndFlush(visit)).thenReturn(visit);
    stubContext();

    service.update(visit.getPublicId(), DOCTOR, request(3L, 120, null));

    assertThat(visit.getDiastolicBp()).isNull();
  }

  // ---- patient history ----------------------------------------------------------------------

  @Test
  void patientHistory_shouldAskPatientModuleAsDoctorAndListOlderVisits() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, DOCTOR);
    var summary = new MedicalHistorySummaryResponse(List.of(), List.of(), List.of());
    when(histories.getSummaryByPatientId(20L, new PatientActor(99L, UserRole.DOCTOR)))
        .thenReturn(summary);
    when(reads.previousVisits(20L, 1L, NOW, ExaminationService.HISTORY_LIMIT))
        .thenReturn(List.of());

    var history = service.patientHistory(visit.getPublicId(), DOCTOR, 99L);

    assertThat(history.medicalHistory()).isSameAs(summary);
    assertThat(history.previousVisits()).isEmpty();
  }

  @Test
  void patientHistory_shouldRejectVisitOfAnotherDoctor() {
    MedicalVisit visit = visit(MedicalVisitStatus.IN_PROGRESS, OTHER_DOCTOR);

    assertFailsWith(
        ExaminationErrorCode.VISIT_NOT_ASSIGNED,
        () -> service.patientHistory(visit.getPublicId(), DOCTOR, 99L));
    verify(histories, never()).getSummaryByPatientId(any(), any());
  }
}
