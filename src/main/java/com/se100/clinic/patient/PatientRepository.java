package com.se100.clinic.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  Optional<Patient> findByPublicId(UUID publicId);
}
