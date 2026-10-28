package com.se100.clinic.doctor;

import com.se100.clinic.employee.enums.EmployeeStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only doctor facts exposed to the schedule module without sharing entities or repositories. */
@Service
@RequiredArgsConstructor
public class DoctorSchedulingService {
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public boolean exists(Long doctorId) {
    return entityManager.find(Doctor.class, doctorId) != null;
  }

  @Transactional(readOnly = true)
  public boolean isWorking(Long doctorId) {
    Doctor doctor = entityManager.find(Doctor.class, doctorId);
    return doctor != null && doctor.getStatus() == EmployeeStatus.WORKING;
  }

  @Transactional(readOnly = true)
  public boolean hasSpecialty(Long doctorId, Long specialtyId) {
    return entityManager.find(DoctorSpeciality.class, new DoctorSpecialityId(doctorId, specialtyId))
        != null;
  }
}
