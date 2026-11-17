package com.se100.clinic.examination;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MedicalVisitRepository extends JpaRepository<MedicalVisit, Long> {

  Optional<MedicalVisit> findByPublicId(UUID publicId);
}
