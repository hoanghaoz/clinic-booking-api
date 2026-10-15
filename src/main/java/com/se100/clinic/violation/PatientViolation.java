package com.se100.clinic.violation;

import com.se100.clinic.shared.BaseAuditEntity;
import com.se100.clinic.violation.enums.PatientViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "patient_violations",
    indexes = {
      @Index(
          name = "idx_patient_violations_patient_type_occurred",
          columnList = "patient_id, type, occurred_at"),
      @Index(
          name = "idx_patient_violations_patient_restricted_until",
          columnList = "patient_id, booking_restricted_until")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PatientViolation extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 50)
  private String code;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "appointment_id", nullable = false)
  private Long appointmentId;

  @Column(name = "medical_order_id")
  private Long medicalOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private PatientViolationType type;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "occurred_at", nullable = false, columnDefinition = "timestamptz")
  private Instant occurredAt;

  @Column(name = "occurrence_count_snapshot")
  private Integer occurrenceCountSnapshot;

  @Column(name = "booking_restricted_from", columnDefinition = "timestamptz")
  private Instant bookingRestrictedFrom;

  @Column(name = "booking_restricted_until", columnDefinition = "timestamptz")
  private Instant bookingRestrictedUntil;

  @Column(name = "recorded_by_staff_id")
  private Long recordedByStaffId;

  @Column(name = "revoked_at", columnDefinition = "timestamptz")
  private Instant revokedAt;

  @Column(name = "revoke_reason", columnDefinition = "text")
  private String revokeReason;
}
