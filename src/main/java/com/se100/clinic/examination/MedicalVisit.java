package com.se100.clinic.examination;

import com.se100.clinic.examination.enums.MedicalDocumentStatus;
import com.se100.clinic.examination.enums.MedicalVisitStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "medical_visits",
    indexes = {
      @Index(name = "idx_medical_visits_patient_received", columnList = "patient_id, received_at"),
      @Index(name = "idx_medical_visits_doctor_status", columnList = "primary_doctor_id, status")
    })
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class MedicalVisit extends BaseAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, unique = true)
  private UUID publicId = UUID.randomUUID();

  @Column(name = "code", nullable = false, unique = true, length = 40)
  private String code;

  @Column(name = "patient_id", nullable = false)
  private Long patientId;

  @Column(name = "appointment_id", nullable = false, unique = true)
  private Long appointmentId;

  @Column(name = "received_by_staff_id", nullable = false)
  private Long receivedByStaffId;

  @Column(name = "primary_doctor_id")
  private Long primaryDoctorId;

  @Column(name = "received_at", nullable = false, columnDefinition = "timestamptz")
  private Instant receivedAt;

  @Column(name = "symptoms", columnDefinition = "text")
  private String symptoms;

  @Column(name = "clinical_notes", columnDefinition = "text")
  private String clinicalNotes;

  @Column(name = "initial_diagnosis", columnDefinition = "text")
  private String initialDiagnosis;

  @Column(name = "final_diagnosis", columnDefinition = "text")
  private String finalDiagnosis;

  @Column(name = "conclusion", columnDefinition = "text")
  private String conclusion;

  @Column(name = "advice", columnDefinition = "text")
  private String advice;

  @Column(name = "followup_recommended_at", columnDefinition = "timestamptz")
  private Instant followupRecommendedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "summary_status", nullable = false)
  @ColumnDefault("'DRAFT'")
  private MedicalDocumentStatus summaryStatus = MedicalDocumentStatus.DRAFT;

  @Column(name = "summary_issued_at", columnDefinition = "timestamptz")
  private Instant summaryIssuedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "prescription", columnDefinition = "jsonb")
  private String prescription;

  @Column(name = "prescribed_by_doctor_id")
  private Long prescribedByDoctorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "prescription_status", nullable = false)
  @ColumnDefault("'DRAFT'")
  private MedicalDocumentStatus prescriptionStatus = MedicalDocumentStatus.DRAFT;

  @Column(name = "prescribed_at", columnDefinition = "timestamptz")
  private Instant prescribedAt;

  @Column(name = "height_cm", precision = 5, scale = 2)
  private BigDecimal heightCm;

  @Column(name = "weight_kg", precision = 5, scale = 2)
  private BigDecimal weightKg;

  @Column(name = "temperature_c", precision = 4, scale = 1)
  private BigDecimal temperatureC;

  @Column(name = "heart_rate")
  private Integer heartRate;

  @Column(name = "systolic_bp")
  private Integer systolicBp;

  @Column(name = "diastolic_bp")
  private Integer diastolicBp;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  @ColumnDefault("'WAITING'")
  private MedicalVisitStatus status = MedicalVisitStatus.WAITING;

  @Column(name = "completed_at", columnDefinition = "timestamptz")
  private Instant completedAt;
}
