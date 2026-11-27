package com.se100.clinic.examination;

import com.se100.clinic.auth.enums.UserRole;
import com.se100.clinic.examination.ExaminationDtos.ClinicalRecord;
import com.se100.clinic.examination.ExaminationDtos.PatientHistoryResponse;
import com.se100.clinic.examination.ExaminationDtos.QueueItem;
import com.se100.clinic.examination.ExaminationDtos.UpdateExaminationRequest;
import com.se100.clinic.examination.ExaminationDtos.VisitDetail;
import com.se100.clinic.examination.ExaminationReadRepository.VisitContext;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
import com.se100.clinic.patient.PatientActor;
import com.se100.clinic.patient.PatientMedicalHistoryService;
import com.se100.clinic.shared.error.BusinessException;
import com.se100.clinic.shared.error.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The doctor's side of a medical visit (REC-05, EXAM-01..03): the list of received patients, the
 * visit record, the patient's history and the clinical notes.
 *
 * <p>The receptionist creates the visit (it has a received_by_staff_id); this service never creates
 * one. Every method takes the id of the logged-in doctor and refuses visits of another doctor with
 * {@code VISIT_NOT_ASSIGNED}.
 */
@Service
@RequiredArgsConstructor
public class ExaminationService {

  /** How many older visits the history screen shows. */
  static final int HISTORY_LIMIT = 20;

  private final MedicalVisitRepository visits;
  private final ExaminationReadRepository reads;
  private final PatientMedicalHistoryService histories;
  private final Clock clock;

  /**
   * Received patients of the doctor in reception order. With neither {@code schedulePublicId} nor
   * {@code date}, only today's shifts (application time zone) are listed.
   */
  @Transactional(readOnly = true)
  public List<QueueItem> myQueue(
      Long doctorId, UUID schedulePublicId, LocalDate date, MedicalVisitStatus status) {
    LocalDate workDate = schedulePublicId == null && date == null ? LocalDate.now(clock) : date;
    return reads.queue(doctorId, schedulePublicId, workDate, status);
  }

  @Transactional(readOnly = true)
  public VisitDetail get(UUID visitPublicId, Long doctorId) {
    return detailOf(requireOwned(visitPublicId, doctorId));
  }

  /** WAITING → IN_PROGRESS. Calling it again on a started visit changes nothing. */
  @Transactional
  public VisitDetail start(UUID visitPublicId, Long doctorId) {
    MedicalVisit visit = requireOwned(visitPublicId, doctorId);
    requireOpen(visit);
    visit.start(doctorId);
    return saveAndDescribe(visit);
  }

  /** Saves symptoms, initial diagnosis, advice and vital signs; the first save starts the visit. */
  @Transactional
  public VisitDetail update(UUID visitPublicId, Long doctorId, UpdateExaminationRequest request) {
    MedicalVisit visit = requireOwned(visitPublicId, doctorId);
    requireOpen(visit);
    if (!request.version().equals(visit.getVersion())) {
      throw versionConflict();
    }
    validateBloodPressure(request.systolicBp(), request.diastolicBp());
    visit.start(doctorId);
    visit.recordExamination(
        blankToNull(request.symptoms()),
        blankToNull(request.clinicalNotes()),
        blankToNull(request.initialDiagnosis()),
        blankToNull(request.advice()),
        request.heightCm(),
        request.weightKg(),
        request.temperatureC(),
        request.heartRate(),
        request.systolicBp(),
        request.diastolicBp());
    return saveAndDescribe(visit);
  }

  /**
   * Medical history (PAT-03), older visits and their verified results for the visit's patient. Only
   * the doctor who owns an open or past visit of that patient can read it.
   */
  @Transactional(readOnly = true)
  public PatientHistoryResponse patientHistory(UUID visitPublicId, Long doctorId, Long userId) {
    MedicalVisit visit = requireOwned(visitPublicId, doctorId);
    return new PatientHistoryResponse(
        histories.getSummaryByPatientId(
            visit.getPatientId(), new PatientActor(userId, UserRole.DOCTOR)),
        reads.previousVisits(
            visit.getPatientId(), visit.getId(), visit.getReceivedAt(), HISTORY_LIMIT));
  }

  /**
   * The doctor in charge is the primary doctor, or — until one is set — the doctor of the shift the
   * appointment was booked into.
   */
  private MedicalVisit requireOwned(UUID visitPublicId, Long doctorId) {
    MedicalVisit visit =
        visits
            .findByPublicId(visitPublicId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ExaminationErrorCode.VISIT_NOT_FOUND, "Không tìm thấy lượt khám"));
    Long inCharge =
        visit.getPrimaryDoctorId() != null
            ? visit.getPrimaryDoctorId()
            : reads.scheduleDoctorId(visit.getAppointmentId()).orElse(null);
    if (!doctorId.equals(inCharge)) {
      throw new BusinessException(
          ExaminationErrorCode.VISIT_NOT_ASSIGNED, "Lượt khám không thuộc bác sĩ này");
    }
    return visit;
  }

  private static void requireOpen(MedicalVisit visit) {
    if (visit.isClosed()) {
      throw new BusinessException(
          ExaminationErrorCode.VISIT_CLOSED, "Lượt khám đã đóng hồ sơ, không thể chỉnh sửa");
    }
  }

  private static void validateBloodPressure(Integer systolic, Integer diastolic) {
    if (systolic != null && diastolic != null && diastolic >= systolic) {
      throw new BusinessException(
          ExaminationErrorCode.INVALID_VITAL_SIGNS,
          "Huyết áp tâm trương phải nhỏ hơn huyết áp tâm thu");
    }
  }

  private VisitDetail saveAndDescribe(MedicalVisit visit) {
    try {
      return detailOf(visits.saveAndFlush(visit));
    } catch (OptimisticLockingFailureException concurrentSave) {
      throw versionConflict();
    }
  }

  private static BusinessException versionConflict() {
    return new BusinessException(
        ExaminationErrorCode.VISIT_VERSION_CONFLICT,
        "Lượt khám vừa được cập nhật ở nơi khác, hãy tải lại trước khi lưu");
  }

  private VisitDetail detailOf(MedicalVisit visit) {
    VisitContext context =
        reads
            .context(visit.getId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        ExaminationErrorCode.VISIT_NOT_FOUND, "Không tìm thấy lượt khám"));
    return new VisitDetail(
        visit.getPublicId(),
        visit.getCode(),
        visit.getStatus(),
        visit.getReceivedAt(),
        visit.getVersion(),
        context.patient(),
        context.appointment(),
        context.schedule(),
        new ClinicalRecord(
            visit.getSymptoms(),
            visit.getClinicalNotes(),
            visit.getInitialDiagnosis(),
            visit.getAdvice(),
            visit.getHeightCm(),
            visit.getWeightKg(),
            visit.getTemperatureC(),
            visit.getHeartRate(),
            visit.getSystolicBp(),
            visit.getDiastolicBp()),
        reads.orders(visit.getId()));
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.trim();
  }
}
