package com.se100.clinic.patient;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientMedicalHistoryRepository extends JpaRepository<PatientMedicalHistory, Long> {
}
