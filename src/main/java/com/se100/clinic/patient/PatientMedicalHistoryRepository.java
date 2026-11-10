package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.MedicalHistoryType;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientMedicalHistoryRepository
    extends JpaRepository<PatientMedicalHistory, Long> {

  Page<PatientMedicalHistory> findByPatientId(Long patientId, Pageable pageable);

  Page<PatientMedicalHistory> findByPatientIdAndType(
      Long patientId, MedicalHistoryType type, Pageable pageable);

  List<PatientMedicalHistory> findByPatientIdOrderByRecordedAtDesc(Long patientId);
}
