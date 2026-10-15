package com.se100.clinic.patient;

import com.se100.clinic.patient.enums.MedicalHistoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(
    name = "patient_medical_histories",
    indexes =
        @Index(
            name = "idx_patient_medical_histories_patient_type",
            columnList = "patient_id, type"))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class PatientMedicalHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private MedicalHistoryType type;

  @Column(name = "description", nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "family_relationship", length = 50)
  private String familyRelationship;

  @Column(name = "recorded_by_staff_id")
  private Long recordedByStaffId;

  @Column(name = "recorded_by_doctor_id")
  private Long recordedByDoctorId;

  @CreatedDate
  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
