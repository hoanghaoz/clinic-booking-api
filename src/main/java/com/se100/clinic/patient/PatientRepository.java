package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.IdentityType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
