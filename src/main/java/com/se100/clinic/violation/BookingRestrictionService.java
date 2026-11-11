package com.se100.clinic.violation;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingRestrictionService {
  private final EntityManager entityManager;

  @Transactional(readOnly = true)
  public Optional<Instant> restrictedUntil(Long patientId, Instant now) {
    return Optional.ofNullable(
        entityManager
            .createQuery(
                """
        select max(v.bookingRestrictedUntil) from PatientViolation v
        where v.patientId = :patientId and v.revokedAt is null
          and v.bookingRestrictedFrom <= :now and v.bookingRestrictedUntil > :now
        """,
                Instant.class)
            .setParameter("patientId", patientId)
            .setParameter("now", now)
            .getSingleResultOrNull());
  }
}
