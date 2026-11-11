package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.PatientClass;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PatientBookingService {
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public Optional<BookingProfile> forUser(Long userId) {
    return findForUser(userId, LockModeType.NONE);
  }

  /** Every writer checking patient conflicts must take this lock before locking schedules. */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<BookingProfile> lockForUser(Long userId) {
    return findForUser(userId, LockModeType.PESSIMISTIC_WRITE);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<BookingProfile> lockForPatient(Long patientId) {
    return Optional.ofNullable(
            entityManager.find(Patient.class, patientId, LockModeType.PESSIMISTIC_WRITE))
        .map(BookingProfile::from);
  }

  private Optional<BookingProfile> findForUser(Long userId, LockModeType lock) {
    return entityManager
        .createQuery("select p from Patient p where p.userId = :userId", Patient.class)
        .setParameter("userId", userId)
        .setLockMode(lock)
        .getResultList()
        .stream()
        .findFirst()
        .map(BookingProfile::from);
  }

  public record BookingProfile(
      Long id, LocalDate dateOfBirth, Gender gender, PatientClass patientClass) {
    static BookingProfile from(Patient patient) {
      return new BookingProfile(
          patient.getId(),
          patient.getDateOfBirth(),
          patient.getGender(),
          patient.getPatientClass());
    }
  }
}
