package com.se100.clinic.doctor;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** package-private — only {@link DoctorService} uses it (same rule as SpecialtyRepository). */
interface DoctorRepository extends JpaRepository<Doctor, Long>, JpaSpecificationExecutor<Doctor> {

  boolean existsByCode(String code);

  boolean existsByUserId(Long userId);
}
