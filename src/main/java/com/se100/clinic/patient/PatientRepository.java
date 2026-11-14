package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.IdentityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  Optional<Patient> findByPublicId(UUID publicId);

  Page<Patient> findByPhone(String phone, Pageable pageable);

  Page<Patient> findByPhoneAndUserId(String phone, Long userId, Pageable pageable);

  Page<Patient> findByIdentityNumber(String identityNumber, Pageable pageable);

  Page<Patient> findByIdentityNumberAndUserId(
      String identityNumber, Long userId, Pageable pageable);

  Optional<Patient> findByUserId(Long userId);

  boolean existsByUserId(Long userId);

  boolean existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
      IdentityType identityType, String identityIssuer, String identityNumber);

  boolean existsByCardNumber(String cardNumber);

  // Cards that are still valid and expire within (from, to]. The caller's Pageable must sort by id
  // so limit/offset paging is stable across pages.
  @Query(
      "SELECT p FROM Patient p "
          + "WHERE p.cardExpiresAt IS NOT NULL AND p.cardRevokedAt IS NULL "
          + "AND p.cardExpiresAt > :from AND p.cardExpiresAt <= :to")
  Slice<Patient> findPatientCardExpiresToRemind(Instant from, Instant to, Pageable pageable);
}
