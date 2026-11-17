package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.IdentityType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

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

  boolean existsByIdentityTypeAndIdentityIssuerAndIdentityNumberAndIdNot(
      IdentityType identityType, String identityIssuer, String identityNumber, Long id);

  // Walk-in profile (no account yet) that matches the identity document and phone exactly.
  Optional<Patient> findByIdentityTypeAndIdentityIssuerAndIdentityNumberAndPhoneAndUserIdIsNull(
      IdentityType identityType, String identityIssuer, String identityNumber, String phone);

  /**
   * Links an account to a profile only while it is still unlinked, so two accounts cannot claim the
   * same profile. A bulk update skips auditing, hence the explicit {@code updatedAt}. Returns the
   * number of rows changed (0 = someone linked it first).
   */
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "UPDATE Patient p SET p.userId = :userId, p.updatedAt = :now "
          + "WHERE p.id = :id AND p.userId IS NULL")
  int linkUserIfUnlinked(Long id, Long userId, Instant now);

  boolean existsByCardNumber(String cardNumber);

  // Cards that are still valid and expire within (from, to]. The caller's Pageable must sort by id
  // so limit/offset paging is stable across pages.
  @Query(
      "SELECT p FROM Patient p "
          + "WHERE p.cardExpiresAt IS NOT NULL AND p.cardRevokedAt IS NULL "
          + "AND p.cardExpiresAt > :from AND p.cardExpiresAt <= :to")
  Slice<Patient> findPatientCardExpiresToRemind(Instant from, Instant to, Pageable pageable);
}
