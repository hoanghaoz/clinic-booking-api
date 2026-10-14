package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.Gender;
import com.se100.clinic.patient.enums.PatientClass;
import com.se100.clinic.shared.BaseAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(
    name = "patients",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_patients_identity",
            columnNames = {"identity_type", "identity_issuer", "identity_number"}),
    indexes = @Index(name = "idx_patients_phone", columnList = "phone"))
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Patient extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "user_id", unique = true)
  private Long userId;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(name = "gender", nullable = false, length = 20)
  private Gender gender;

  @Column(name = "phone", nullable = false, length = 25)
  private String phone;

  @Column(name = "contact_email", length = 254)
  private String contactEmail;

  @Column(name = "address", nullable = false, columnDefinition = "text")
  private String address;

  @Column(name = "identity_type", nullable = false, length = 20)
  private String identityType;

  @Column(name = "identity_number", nullable = false, length = 40)
  private String identityNumber;

  @Column(name = "identity_issuer", nullable = false, length = 80)
  private String identityIssuer;

  @Enumerated(EnumType.STRING)
  @Column(name = "class", nullable = false)
  @ColumnDefault("'PERSONAL'")
  private PatientClass patientClass = PatientClass.PERSONAL;

  @Column(name = "card_number", unique = true, length = 40)
  private String cardNumber;

  @Column(name = "card_activated_at")
  private Instant cardActivatedAt;

  @Column(name = "card_expires_at")
  private Instant cardExpiresAt;

  @Column(name = "card_revoked_at")
  private Instant cardRevokedAt;
}
