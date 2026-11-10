package com.se100.clinic.patient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRepository extends JpaRepository<Patient, Long> {
  Optional<Patient> findByPublicId(UUID publicId);

  List<Patient> findByPhoneOrderByIdAsc(String phone);

  List<Patient> findByPhoneAndUserIdOrderByIdAsc(String phone, Long userId);

  List<Patient> findByIdentityNumberOrderByIdAsc(String identityNumber);

  List<Patient> findByIdentityNumberAndUserIdOrderByIdAsc(String identityNumber, Long userId);

  boolean existsByUserId(Long userId);

  boolean existsByIdentityTypeAndIdentityIssuerAndIdentityNumber(
      String identityType, String identityIssuer, String identityNumber);

  boolean existsByCardNumber(String cardNumber);
}
